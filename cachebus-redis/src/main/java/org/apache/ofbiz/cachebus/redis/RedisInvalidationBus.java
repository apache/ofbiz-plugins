/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.apache.ofbiz.cachebus.redis;

import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.cache.InvalidationBus;
import org.apache.ofbiz.base.util.cache.InvalidationReceiver;

import io.lettuce.core.api.StatefulRedisConnection;

/**
 * Carries cache invalidations over Redis/Valkey pub/sub with the Lettuce client. One pub/sub connection receives the
 * payloads of the cluster's channel and a second connection publishes. Nothing here waits for the network on the
 * caller's thread: connecting and the heartbeat run on a daemon thread, publishing is asynchronous, and received
 * payloads are handed to the receiver on a delivery thread of their own. A server that dropped published invalidations
 * announces it on the gap channel once it is connected again, and a server receiving such an announcement reports a gap.
 * Each start works through its own {@link RedisChannelConnection}.
 */
public final class RedisInvalidationBus implements InvalidationBus {

    private static final String MODULE = RedisInvalidationBus.class.getName();
    private static final long INITIAL_BACKOFF_MILLIS = 1000;
    private static final long MAX_BACKOFF_MILLIS = 30000;
    private static final long WARNING_INTERVAL_MILLIS = 60000;

    private static volatile RedisInvalidationBus current;

    private final CacheBusRedisSettings settings;
    private final long initialBackoffMillis;
    private final long maxBackoffMillis;
    private final BusStatistics statistics = new BusStatistics();
    private final AtomicLong lastOversizeWarning = new AtomicLong();
    private final AtomicLong lastPublishFailureWarning = new AtomicLong();
    private final Object lock = new Object();
    /** Names this bus in its gap announcements; not the framework node id. */
    private final String busId = UUID.randomUUID().toString();
    /** Set when a payload could not be published; cleared when the announcement on the gap channel is sent. */
    private final AtomicBoolean lostPublishes = new AtomicBoolean();

    // Written under lock; read without it by publish.
    private volatile RedisChannelConnection connection;

    /**
     * Creates a bus; nothing connects before {@link #start(InvalidationReceiver)}.
     * @param settings the settings
     */
    public RedisInvalidationBus(CacheBusRedisSettings settings) {
        this(settings, INITIAL_BACKOFF_MILLIS, MAX_BACKOFF_MILLIS);
    }

    /** Test seam: the same bus with a shorter delay between connection attempts. */
    RedisInvalidationBus(CacheBusRedisSettings settings, long initialBackoffMillis, long maxBackoffMillis) {
        this.settings = settings;
        this.initialBackoffMillis = initialBackoffMillis;
        this.maxBackoffMillis = maxBackoffMillis;
    }

    /**
     * The started bus, for the status page.
     * @return the bus started last and not stopped yet, or null
     */
    public static RedisInvalidationBus current() {
        return current;
    }

    @Override
    public String getName() {
        return "redis";
    }

    @Override
    public long tombstoneMillis() {
        return settings.tombstoneMillis();
    }

    /**
     * The settings the bus was created with, for the status page.
     * @return the settings
     */
    public CacheBusRedisSettings settings() {
        return settings;
    }

    /**
     * The bus's counters, for the status page.
     * @return the statistics
     */
    public BusStatistics statistics() {
        return statistics;
    }

    @Override
    public void start(InvalidationReceiver receiver) {
        synchronized (lock) {
            if (connection != null) {
                throw new IllegalStateException("The Redis cache invalidation bus is already started");
            }
            RedisChannelConnection started = new RedisChannelConnection(settings, statistics, busId, lostPublishes, receiver,
                    initialBackoffMillis, maxBackoffMillis);
            connection = started;
            current = this;
            started.begin();
        }
        Debug.logInfo("Redis cache invalidation bus starting: " + settings, MODULE);
    }

    @Override
    public void publish(String payload) {
        RedisChannelConnection started = connection;
        StatefulRedisConnection<String, String> pub = started == null ? null : started.publisher();
        if (pub == null || !started.publisherUp()) {
            publishFailed("not connected");
            return;
        }
        // A char takes at most 3 bytes in UTF-8, so a short payload needs no encoding to be measured.
        if (payload.length() * 3L > settings.maxPayloadBytes() && byteLength(payload) > settings.maxPayloadBytes()
                && shouldWarn(lastOversizeWarning)) {
            Debug.logWarning("Redis cache invalidation bus publishes a payload larger than max-payload-bytes ("
                    + settings.maxPayloadBytes() + "); servers with the same limit drop it and clear their distributed caches",
                    MODULE);
        }
        try {
            pub.async().publish(settings.channel(), payload).whenComplete(this::published);
        } catch (RuntimeException e) {
            publishFailed(failureMessage(e));
        }
    }

    private void published(Long receivers, Throwable failure) {
        if (failure == null) {
            statistics.published();
        } else {
            publishFailed(failureMessage(failure));
        }
    }

    /** Counts a lost publish, to be announced once connected, and warns at most once a minute. */
    private void publishFailed(String reason) {
        statistics.publishFailed();
        lostPublishes.set(true);
        if (shouldWarn(lastPublishFailureWarning)) {
            Debug.logWarning("Redis cache invalidation bus could not publish an invalidation (" + reason + "); the other servers"
                    + " are told to clear their distributed caches once it can publish again", MODULE);
        }
    }

    private static String failureMessage(Throwable failure) {
        Throwable shown = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        return shown.getMessage() == null ? shown.getClass().getSimpleName() : shown.getMessage();
    }

    private static boolean shouldWarn(AtomicLong lastWarning) {
        long now = System.currentTimeMillis();
        long last = lastWarning.get();
        return now - last >= WARNING_INTERVAL_MILLIS && lastWarning.compareAndSet(last, now);
    }

    /**
     * The size of a payload in UTF-8, as the server and the receiving listeners measure it, counted without encoding it.
     * An unpaired surrogate counts as one byte, as the encoder replaces it with '?'.
     */
    static int byteLength(String payload) {
        int bytes = 0;
        int length = payload.length();
        for (int i = 0; i < length; i++) {
            char c = payload.charAt(i);
            if (c < 0x80) {
                bytes++;
            } else if (c < 0x800) {
                bytes += 2;
            } else if (Character.isHighSurrogate(c) && i + 1 < length && Character.isLowSurrogate(payload.charAt(i + 1))) {
                bytes += 4;
                i++;
            } else if (Character.isSurrogate(c)) {
                bytes++;
            } else {
                bytes += 3;
            }
        }
        return bytes;
    }

    @Override
    public void stop() {
        synchronized (lock) {
            RedisChannelConnection stopping = connection;
            if (stopping == null) {
                return;
            }
            connection = null;
            if (current == this) {
                current = null;
            }
            stopping.end();
        }
        Debug.logInfo("Redis cache invalidation bus stopped", MODULE);
    }
}
