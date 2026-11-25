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

import java.net.SocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.cache.InvalidationReceiver;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisChannelHandler;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionStateListener;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.protocol.ProtocolVersion;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

/**
 * One start-to-stop period of the bus: its client, thread, connections and state. A connection attempt, heartbeat or
 * Lettuce callback works on the instance it was created for; there is one instance per start, so a callback left over
 * from before a stop cannot act on a later start. The connect attempts and the heartbeat run on the single scheduler
 * thread, which alone touches {@code failedAttempts}, {@code nextBackoffMillis}, {@code heartbeat} and
 * {@code unsubscribedBeats}.
 */
final class RedisChannelConnection {

    private static final String MODULE = RedisChannelConnection.class.getName();
    private static final String CLIENT_NAME = "ofbiz-cachebus";
    private static final long STOP_WAIT_SECONDS = 5;
    /** Bounds every command, connection handshake included, so a hung publish completes as a failure. */
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);
    /** Commands queued while a server does not answer; more are rejected at once, so a stuck server cannot grow the heap. */
    private static final int REQUEST_QUEUE_SIZE = 10_000;
    /** TCP keepalive, defence in depth behind the heartbeat for connections the network silently dropped. */
    private static final SocketOptions SOCKET_OPTIONS = SocketOptions.builder()
            .connectTimeout(COMMAND_TIMEOUT)
            .keepAlive(SocketOptions.KeepAliveOptions.builder().enable().idle(Duration.ofSeconds(30)).interval(Duration.ofSeconds(10))
                    .count(3).build())
            .build();

    private final CacheBusRedisSettings settings;
    private final BusStatistics statistics;
    private final String busId;
    private final AtomicBoolean lostPublishes;
    private final long initialBackoffMillis;
    private final long maxBackoffMillis;
    private final RedisClient client;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(RedisChannelConnection::newThread);
    /** Read-held while calling the receiver; stop takes the write lock, so nothing is delivered after stop returns. */
    private final ReentrantReadWriteLock deliveryLock = new ReentrantReadWriteLock();
    /** Cleared by {@link #end()} under the write lock of {@code deliveryLock}; read under its read lock. */
    private InvalidationReceiver receiver;
    /** Set once, by {@link #end()}; from then on nothing connects, delivers or reports this instance as connected. */
    private volatile boolean stopped;

    // Scheduler thread only.
    private int failedAttempts;
    private long nextBackoffMillis;
    private ScheduledFuture<?> heartbeat;
    private int unsubscribedBeats;

    // Written by connect (scheduler thread), cleared by closeConnections; compared by StateListener, publisher read by publish.
    private volatile StatefulRedisPubSubConnection<String, String> subscriber;
    private volatile StatefulRedisConnection<String, String> publisher;
    /**
     * Whether the subscriber connection is open and subscribed. Written only by {@link #updateConnection}: set by a
     * successful connect and by a resubscription ({@link ChannelListener#subscribed}), cleared by a disconnect
     * ({@code StateListener}), by closing the connections and by {@link #end()}. While Lettuce reconnects it stays false
     * until the subscription is confirmed again, which the heartbeat watches.
     */
    private volatile boolean subscriberUp;
    /**
     * Whether the publisher connection is open. Written only by {@link #updateConnection}: set by a successful connect
     * and by a reconnect ({@code StateListener}), cleared by a disconnect, by closing the connections and by
     * {@link #end()}. The bus publishes only while it is true; it may be true while the subscriber is down.
     */
    private volatile boolean publisherUp;
    /**
     * Both connections up and not stopped; derived by {@link #updateConnection} and mirrored in the statistics. Read by
     * {@code StateListener} to log a lost subscriber connection only when it was connected.
     */
    private volatile boolean connected;

    /**
     * Creates the client for one start of the bus; nothing connects before {@link #begin()}.
     * @param settings the bus's settings
     * @param statistics the bus's counters, updated here
     * @param busId the id the bus announces lost publishes with
     * @param lostPublishes the bus's flag, set by a failed publish and cleared here once the announcement is sent
     * @param receiver the receiver of payloads and gaps
     * @param initialBackoffMillis the delay before the second connection attempt
     * @param maxBackoffMillis the longest delay between connection attempts
     */
    RedisChannelConnection(CacheBusRedisSettings settings, BusStatistics statistics, String busId, AtomicBoolean lostPublishes,
            InvalidationReceiver receiver, long initialBackoffMillis, long maxBackoffMillis) {
        this.settings = settings;
        this.statistics = statistics;
        this.busId = busId;
        this.lostPublishes = lostPublishes;
        this.receiver = receiver;
        this.initialBackoffMillis = initialBackoffMillis;
        this.maxBackoffMillis = maxBackoffMillis;
        this.nextBackoffMillis = initialBackoffMillis;
        client = RedisClient.create(redisUri(settings));
        client.setOptions(ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                // RESP3 delivers the PING reply on a subscribed connection like any other reply; Valkey and Redis 6+ speak it.
                .protocolVersion(ProtocolVersion.RESP3)
                .timeoutOptions(TimeoutOptions.enabled(COMMAND_TIMEOUT))
                .requestQueueSize(REQUEST_QUEUE_SIZE)
                .socketOptions(SOCKET_OPTIONS)
                .build());
        client.addListener(new StateListener(this));
    }

    private static RedisURI redisUri(CacheBusRedisSettings settings) {
        RedisURI.Builder builder = RedisURI.builder(RedisURI.create(settings.uri())).withClientName(CLIENT_NAME).withTimeout(COMMAND_TIMEOUT);
        if (!settings.password().isEmpty()) {
            if (settings.username().isEmpty()) {
                builder.withPassword(settings.password());
            } else {
                builder.withAuthentication(settings.username(), settings.password());
            }
        }
        return builder.build();
    }

    /**
     * The connection the bus publishes on.
     * @return the publisher connection, or null while not connected
     */
    StatefulRedisConnection<String, String> publisher() {
        return publisher;
    }

    /**
     * Whether the publisher connection is up.
     * @return true while a publish can be sent
     */
    boolean publisherUp() {
        return publisherUp;
    }

    /** Starts connecting on the scheduler thread. */
    void begin() {
        scheduler.execute(this::connect);
    }

    /** Stops for good: no receiver call after it returns, and the connections, client and thread are closed. */
    void end() {
        stopped = true;
        deliveryLock.writeLock().lock();
        try {
            receiver = null;
        } finally {
            deliveryLock.writeLock().unlock();
        }
        updateConnection(false, false);
        scheduler.shutdownNow();
        closeConnections();
        try {
            client.shutdown();
        } catch (RuntimeException e) {
            Debug.logWarning(e, "Redis cache invalidation bus: error shutting down the client", MODULE);
        }
        try {
            scheduler.awaitTermination(STOP_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Records the state of the two connections; the bus is connected only while both are up and it is not stopped.
     * Synchronized so that the last writer computes from the latest state.
     */
    synchronized void updateConnection(boolean subscriberState, boolean publisherState) {
        subscriberUp = subscriberState;
        publisherUp = publisherState;
        if (stopped) {
            connected = false;
            statistics.setConnected(false);
            return;
        }
        connected = subscriberUp && publisherUp;
        statistics.setConnected(connected);
    }

    /** Records whether the subscriber is up, keeping the publisher's state. */
    synchronized void subscriberState(boolean up) {
        updateConnection(up, publisherUp);
    }

    /** Records whether the publisher is up, keeping the subscriber's state. */
    synchronized void publisherState(boolean up) {
        updateConnection(subscriberUp, up);
    }

    /** Runs a receiver call unless the bus is stopped; stop waits for one in progress. */
    private void deliver(Consumer<InvalidationReceiver> call) {
        deliveryLock.readLock().lock();
        try {
            if (!stopped && receiver != null) {
                call.accept(receiver);
            }
        } finally {
            deliveryLock.readLock().unlock();
        }
    }

    /** Counts a gap and tells the receiver, unless stopped. */
    void reportGap() {
        deliver(r -> {
            statistics.gap();
            r.onGap();
        });
    }

    /** Counts a received payload and hands it to the receiver, unless stopped. */
    void deliverPayload(String payload, int bytes) {
        deliver(r -> {
            statistics.received(bytes);
            r.onPayload(payload);
        });
    }

    private void runOnScheduler(Runnable task) {
        try {
            scheduler.execute(task);
        } catch (RejectedExecutionException e) {
            // stopped
        }
    }

    /** One connection attempt, on the scheduler thread; schedules the next one when it fails. */
    private void connect() {
        if (stopped) {
            return;
        }
        try {
            StatefulRedisPubSubConnection<String, String> sub = client.connectPubSub();
            subscriber = sub;
            sub.addListener(new ChannelListener(settings, busId, this));
            sub.sync().subscribe(settings.channel(), settings.gapChannel());
            StatefulRedisConnection<String, String> pub = client.connect();
            publisher = pub;
            boolean missedMessages = failedAttempts > 0;
            failedAttempts = 0;
            nextBackoffMillis = initialBackoffMillis;
            updateConnection(sub.isOpen(), pub.isOpen());
            if (stopped) {
                closeConnections();
                return;
            }
            unsubscribedBeats = 0;
            Debug.logInfo("Redis cache invalidation bus connected to channel " + settings.channel(), MODULE);
            if (missedMessages) {
                reportGap();
            }
            announceLostPublishes();
            heartbeat = scheduler.scheduleWithFixedDelay(this::heartbeat, settings.heartbeatMillis(), settings.heartbeatMillis(),
                    TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            closeConnections();
            retryLater(e);
        } catch (Throwable t) {
            // an escaping throwable would end the retry chain silently
            Debug.logError(t, "Redis cache invalidation bus connect failed unexpectedly", MODULE);
            closeConnections();
            if (!stopped) {
                retryLater(new IllegalStateException(describe(t), t));
            }
        }
    }

    private void retryLater(RuntimeException cause) {
        if (stopped) {
            return;
        }
        failedAttempts++;
        long delay = nextBackoffMillis;
        nextBackoffMillis = Math.min(nextBackoffMillis * 2, maxBackoffMillis);
        try {
            scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            return;
        }
        Debug.logWarning("Redis cache invalidation bus cannot connect (" + settings + "): " + describe(cause) + "; retrying in "
                + delay + " ms", MODULE);
    }

    /**
     * Checks the subscribed connection, on the scheduler thread. While Lettuce reconnects, the subscription is not
     * confirmed; one beat later it still is not, its resubscribe failed (Lettuce logs it and does not retry, for
     * instance after a command timeout or NOPERM) and the bus would stay deaf, so the connections are reopened.
     * Otherwise a PING checks that the server still answers.
     */
    private void heartbeat() {
        try {
            doHeartbeat();
        } catch (Throwable t) {
            // an escaping throwable would silently cancel the scheduleWithFixedDelay task
            Debug.logError(t, "Redis cache invalidation bus heartbeat failed unexpectedly", MODULE);
        }
    }

    private void doHeartbeat() {
        StatefulRedisPubSubConnection<String, String> sub = subscriber;
        if (stopped || sub == null) {
            return;
        }
        if (!subscriberUp) {
            unsubscribedBeats++;
            if (unsubscribedBeats > 1) {
                heartbeatFailed(sub, new IllegalStateException("the channel subscription was not restored after a reconnect"));
            }
            return;
        }
        unsubscribedBeats = 0;
        announceLostPublishes();
        try {
            sub.async().ping().toCompletableFuture().orTimeout(settings.heartbeatTimeoutMillis(), TimeUnit.MILLISECONDS)
                    .whenComplete((pong, failure) -> {
                        if (failure != null) {
                            runOnScheduler(() -> heartbeatFailed(sub, failure));
                        }
                    });
        } catch (RuntimeException e) {
            heartbeatFailed(sub, e);
        }
    }

    /**
     * Tells the other servers, on the scheduler thread, that this one lost published invalidations, once it can publish
     * again. A failed announcement is retried on the next beat.
     */
    private void announceLostPublishes() {
        StatefulRedisConnection<String, String> pub = publisher;
        if (pub == null || !publisherUp || !lostPublishes.getAndSet(false)) {
            return;
        }
        try {
            pub.async().publish(settings.gapChannel(), busId).whenComplete((receivers, failure) -> {
                if (failure != null) {
                    lostPublishes.set(true);
                }
            });
        } catch (RuntimeException e) {
            lostPublishes.set(true);
        }
    }

    /**
     * Drops both connections after a missed heartbeat and connects again. The next successful connection reports a
     * gap once its subscription is confirmed, as after any failed attempt.
     */
    private void heartbeatFailed(StatefulRedisPubSubConnection<String, String> sub, Throwable failure) {
        if (stopped || subscriber != sub) {
            return;
        }
        Debug.logWarning("Redis cache invalidation bus heartbeat failed (" + describe(failure) + "); reconnecting", MODULE);
        if (heartbeat != null) {
            heartbeat.cancel(false);
            heartbeat = null;
        }
        closeConnections();
        failedAttempts++;
        runOnScheduler(this::connect);
    }

    private void closeConnections() {
        StatefulRedisPubSubConnection<String, String> sub = subscriber;
        StatefulRedisConnection<String, String> pub = publisher;
        subscriber = null;
        publisher = null;
        updateConnection(false, false);
        closeQuietly(pub);
        closeQuietly(sub);
    }

    private static Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "OFBiz-cachebus-redis");
        thread.setDaemon(true);
        return thread;
    }

    private static void closeQuietly(StatefulConnection<?, ?> connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (RuntimeException e) {
                Debug.logWarning(e, "Redis cache invalidation bus: error closing a connection", MODULE);
            }
        }
    }

    /** The messages of an exception and its cause, which say why, such as a refused connection or a wrong password. */
    private static String describe(Throwable failure) {
        Throwable shown = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        Throwable cause = shown.getCause();
        if (cause == null || cause == shown || message(cause).equals(message(shown))) {
            return message(shown);
        }
        return message(shown) + " (" + message(cause) + ")";
    }

    private static String message(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    /** Tracks whether each connection is up; Lettuce reconnects them on its own. */
    private static final class StateListener implements RedisConnectionStateListener {

        private final RedisChannelConnection owner;

        StateListener(RedisChannelConnection owner) {
            this.owner = owner;
        }

        @Override
        public void onRedisConnected(RedisChannelHandler<?, ?> connection, SocketAddress socketAddress) {
            try {
                // The subscriber is up again only once it is resubscribed, see ChannelListener.subscribed.
                if (connection == owner.publisher) {
                    owner.publisherState(true);
                }
            } catch (RuntimeException e) {
                Debug.logError(e, "Redis cache invalidation bus: error handling a connect", MODULE);
            }
        }

        @Override
        public void onRedisDisconnected(RedisChannelHandler<?, ?> connection) {
            try {
                if (connection == owner.subscriber) {
                    if (owner.connected) {
                        Debug.logWarning("Redis cache invalidation bus lost its pub/sub connection; reconnecting", MODULE);
                    }
                    owner.subscriberState(false);
                } else if (connection == owner.publisher) {
                    owner.publisherState(false);
                }
            } catch (RuntimeException e) {
                Debug.logError(e, "Redis cache invalidation bus: error handling a disconnect", MODULE);
            }
        }
    }
}
