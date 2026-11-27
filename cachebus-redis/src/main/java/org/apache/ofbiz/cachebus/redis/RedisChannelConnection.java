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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
 * thread, which alone touches {@code nextBackoffMillis}, {@code heartbeat} and {@code unsubscribedBeats}. Received
 * payloads and gaps are handed to the receiver on a second thread of their own, in the order they come, so that clearing
 * the caches never holds up Lettuce's I/O thread, which also reads the heartbeat's PING replies.
 */
final class RedisChannelConnection {

    private static final String MODULE = RedisChannelConnection.class.getName();
    private static final String CLIENT_NAME = "ofbiz-cachebus";
    private static final long STOP_WAIT_SECONDS = 5;
    /** Bounds every command, connection handshake included, so a hung publish completes as a failure. */
    private static final Duration COMMAND_TIMEOUT = Duration.ofMillis(CacheBusRedisSettings.COMMAND_TIMEOUT_MILLIS);
    /**
     * Bytes of waiting payloads, overhead included, that may wait for the delivery thread; at least max-payload-bytes so
     * that any accepted payload fits. Beyond it a payload is dropped and a gap reported instead, so a flood of messages,
     * small or large, cannot exhaust the heap.
     */
    private static final long MIN_QUEUED_BYTES = 16L * 1024 * 1024;
    /** What a waiting payload costs besides its bytes (task, queue node, string header), counted against the same bound. */
    private static final int QUEUED_PAYLOAD_OVERHEAD_BYTES = 128;
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
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            runnable -> newThread(runnable, "OFBiz-cachebus-redis"));
    /** Runs the receiver calls one at a time, in the order the payloads and gaps came. */
    private final ExecutorService delivery = Executors.newSingleThreadExecutor(
            runnable -> newThread(runnable, "OFBiz-cachebus-redis-delivery"));
    private final long maxQueuedBytes;
    /** Bytes of the payloads waiting for the delivery thread, with their overhead. */
    private final AtomicLong queuedBytes = new AtomicLong();
    /**
     * Whether a gap is waiting for the delivery thread. While it is, no other gap is queued, and the payloads queued before
     * it are skipped, as the gap clears what they would invalidate.
     */
    private final AtomicBoolean gapQueued = new AtomicBoolean();
    /** Read-held while calling the receiver; stop takes the write lock, so nothing is delivered after stop returns. */
    private final ReentrantReadWriteLock deliveryLock = new ReentrantReadWriteLock();
    /** Cleared by {@link #end()} under the write lock of {@code deliveryLock}; read under its read lock. */
    private InvalidationReceiver receiver;
    /** Set once, by {@link #end()}; from then on nothing connects, delivers or reports this instance as connected. */
    private volatile boolean stopped;

    // Scheduler thread only.
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
     * Whether the subscriber connection dropped after its subscribe returned and has not been resubscribed since. Guarded
     * by this instance's monitor: cleared when a connect begins and by a resubscription, set by a disconnect. A connect
     * that took a while to open the publisher then does not count a subscriber that Lettuce reopened, but could not
     * resubscribe, as up.
     */
    private boolean subscriberDropped;
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
        this.maxQueuedBytes = Math.max(MIN_QUEUED_BYTES, settings.maxPayloadBytes());
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
        // what is still queued finds the bus stopped and does nothing
        delivery.shutdown();
        updateConnection(false, false);
        statistics.setConnected(false);
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
            // end() reports the bus disconnected once; a late call from a stopped instance must not overwrite what a
            // later start of the bus, which shares the statistics, reports
            connected = false;
            return;
        }
        connected = subscriberUp && publisherUp;
        statistics.setConnected(connected);
    }

    /** Records that the subscriber connection dropped; it is up again once resubscribed. */
    synchronized void subscriberLost() {
        subscriberDropped = true;
        updateConnection(false, publisherUp);
    }

    /** Records that the subscriber connection is subscribed again after a reconnect. */
    synchronized void subscriberRestored() {
        subscriberDropped = false;
        updateConnection(true, publisherUp);
    }

    /**
     * Records the state of both connections once a connect opened them: the subscriber counts as up only if it has not
     * dropped since its subscribe returned, or was resubscribed since.
     * @param subscriberOpen whether the subscriber connection is open
     * @param publisherOpen whether the publisher connection is open
     */
    synchronized void connectionsOpened(boolean subscriberOpen, boolean publisherOpen) {
        updateConnection(subscriberOpen && !subscriberDropped, publisherOpen);
    }

    /**
     * Whether both connections are up and the bus is not stopped.
     * @return true while connected
     */
    boolean isConnected() {
        return connected;
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

    /**
     * Queues a gap for the delivery thread, which counts it and tells the receiver unless stopped. A gap already waiting
     * stands for this one too.
     */
    void reportGap() {
        if (stopped || !gapQueued.compareAndSet(false, true)) {
            return;
        }
        if (!runOnDelivery(() -> {
            gapQueued.set(false);
            deliver(r -> {
                statistics.gap();
                r.onGap();
            });
        })) {
            gapQueued.set(false);
        }
    }

    /**
     * Queues a received payload for the delivery thread, which counts it and hands it to the receiver unless stopped. When
     * the queue is full the payload is dropped and a gap reported instead.
     * @param payload the payload
     * @param bytes its size in UTF-8
     */
    void deliverPayload(String payload, int bytes) {
        if (stopped) {
            return;
        }
        long cost = (long) bytes + QUEUED_PAYLOAD_OVERHEAD_BYTES;
        if (queuedBytes.addAndGet(cost) > maxQueuedBytes) {
            queuedBytes.addAndGet(-cost);
            if (!gapQueued.get()) {
                Debug.logWarning("Redis cache invalidation bus: more than " + maxQueuedBytes + " bytes of received payloads"
                        + " are waiting to be applied; dropping them and clearing distributed caches", MODULE);
            }
            reportGap();
            return;
        }
        if (!runOnDelivery(() -> {
            queuedBytes.addAndGet(-cost);
            // a gap queued behind this payload clears what it would invalidate
            if (!gapQueued.get()) {
                deliver(r -> {
                    statistics.received(bytes);
                    r.onPayload(payload);
                });
            }
        })) {
            queuedBytes.addAndGet(-cost);
        }
    }

    private boolean runOnDelivery(Runnable task) {
        try {
            delivery.execute(() -> {
                try {
                    task.run();
                } catch (Throwable t) {
                    // the receiver's failure must not end the delivery thread
                    Debug.logError(t, "Redis cache invalidation bus: error delivering a payload or gap", MODULE);
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            // stopped
            return false;
        }
    }

    /**
     * Test seam: waits until everything queued so far has been delivered.
     * @throws InterruptedException if the wait is interrupted
     * @throws ExecutionException never, the task does nothing
     * @throws TimeoutException if the deliveries take longer than the stop wait
     */
    void awaitDeliveries() throws InterruptedException, ExecutionException, TimeoutException {
        delivery.submit(() -> { }).get(STOP_WAIT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Whether a pub/sub connection is the one this start currently subscribes on.
     * @param connection a connection
     * @return true if it is the current subscriber connection
     */
    boolean isSubscriber(StatefulRedisPubSubConnection<String, String> connection) {
        return connection == subscriber;
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
            synchronized (this) {
                subscriberDropped = false;
            }
            StatefulRedisPubSubConnection<String, String> sub = client.connectPubSub();
            subscriber = sub;
            sub.addListener(new ChannelListener(settings, busId, this, sub));
            sub.sync().subscribe(settings.channel(), settings.gapChannel());
            StatefulRedisConnection<String, String> pub = client.connect();
            publisher = pub;
            nextBackoffMillis = initialBackoffMillis;
            // Always a gap, the first connection included: invalidations published before the subscription was confirmed are
            // lost, and before the bus was installed the caches were filled without any invalidation from the other servers.
            // Queued before the connection shows as up, so that a payload sent once it is up comes after the gap.
            reportGap();
            connectionsOpened(sub.isOpen(), pub.isOpen());
            if (stopped) {
                closeConnections();
                return;
            }
            unsubscribedBeats = 0;
            Debug.logInfo("Redis cache invalidation bus connected to channel " + settings.channel(), MODULE);
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
     * Checks both connections, on the scheduler thread. While Lettuce reconnects, the subscription is not confirmed;
     * one beat later it still is not, its resubscribe failed (Lettuce logs it and does not retry, for instance after a
     * command timeout or NOPERM) and the bus would stay deaf, so the connections are reopened. Otherwise a PING on each
     * connection checks that the server still answers on both, since a half-open publisher connection would otherwise
     * fail every publish until TCP gives up.
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
        // the publisher may be up while the subscriber is not
        announceLostPublishes();
        if (!subscriberUp) {
            unsubscribedBeats++;
            if (unsubscribedBeats > 1) {
                heartbeatFailed(sub, new IllegalStateException("the channel subscription was not restored after a reconnect"));
            }
            return;
        }
        unsubscribedBeats = 0;
        ping(sub, sub, "pub/sub");
        StatefulRedisConnection<String, String> pub = publisher;
        // while Lettuce reconnects the publisher, its commands are rejected at once; the reconnect is its own check
        if (pub != null && publisherUp) {
            ping(pub, sub, "publishing");
        }
    }

    /**
     * Sends a heartbeat PING on a connection; a failure or a late answer reopens both connections.
     * @param connection the connection to check
     * @param sub the subscriber connection of this connect, so that a late failure does not act on a later connect
     * @param name the connection's name in the log
     */
    private void ping(StatefulRedisConnection<String, String> connection, StatefulRedisPubSubConnection<String, String> sub,
            String name) {
        try {
            connection.async().ping().toCompletableFuture().orTimeout(settings.heartbeatTimeoutMillis(), TimeUnit.MILLISECONDS)
                    .whenComplete((reply, failure) -> {
                        if (failure != null) {
                            Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                                    ? failure.getCause() : failure;
                            runOnScheduler(() -> heartbeatFailed(sub, new IllegalStateException("no answer to the PING on the "
                                    + name + " connection", cause)));
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
     * gap once its subscription is confirmed, as every connection does.
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

    private static Thread newThread(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
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
                    owner.subscriberLost();
                } else if (connection == owner.publisher) {
                    owner.publisherState(false);
                }
            } catch (RuntimeException e) {
                Debug.logError(e, "Redis cache invalidation bus: error handling a disconnect", MODULE);
            }
        }
    }
}
