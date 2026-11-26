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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ofbiz.base.util.cache.InvalidationReceiver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

/**
 * Routes messages through a {@link ChannelListener} on a connection that never connects, so no server is needed. The
 * listener is given no pub/sub connection, which matches the connection's current subscriber, also none.
 */
public final class ChannelListenerTest {

    private static final int MAX_PAYLOAD_BYTES = 1024;
    private static final CacheBusRedisSettings SETTINGS = new CacheBusRedisSettings("redis://localhost:1", "", "", "unit",
            MAX_PAYLOAD_BYTES, 2000, 5000, 3000);
    private static final String BUS_ID = UUID.randomUUID().toString();

    private final RecordingReceiver receiver = new RecordingReceiver();
    private final BusStatistics statistics = new BusStatistics();
    private RedisChannelConnection connection;
    private ChannelListener listener;

    /** Collects what the connection delivers. */
    private static final class RecordingReceiver implements InvalidationReceiver {
        private final List<String> payloads = new CopyOnWriteArrayList<>();
        private final AtomicInteger gaps = new AtomicInteger();
        /** When set, the first payload holds the delivery thread until it is counted down. */
        private volatile CountDownLatch hold;
        /** Counted down once the delivery thread holds a payload. */
        private final CountDownLatch holding = new CountDownLatch(1);

        @Override
        public void onPayload(String payload) {
            payloads.add(payload);
            CountDownLatch latch = hold;
            if (latch != null) {
                hold = null;
                holding.countDown();
                try {
                    latch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public void onGap() {
            gaps.incrementAndGet();
        }
    }

    @BeforeEach
    public void createListener() {
        connection = new RedisChannelConnection(SETTINGS, statistics, BUS_ID, new AtomicBoolean(), receiver, 100, 500);
        listener = new ChannelListener(SETTINGS, BUS_ID, connection, null);
    }

    @AfterEach
    public void endConnection() {
        connection.end();
    }

    private void delivered() throws Exception {
        connection.awaitDeliveries();
    }

    private long counter(String name) {
        return (Long) statistics.snapshot().get(name);
    }

    @Test
    public void mainChannelPayloadIsDeliveredAndCounted() throws Exception {
        listener.message(SETTINGS.channel(), "payload-1");
        delivered();
        assertEquals(List.of("payload-1"), receiver.payloads);
        assertEquals(0, receiver.gaps.get());
        assertEquals(1L, counter("received"));
        assertEquals((long) RedisInvalidationBus.byteLength("payload-1"), counter("receivedBytes"));
    }

    @Test
    public void oversizedPayloadIsAGapNotADelivery() throws Exception {
        listener.message(SETTINGS.channel(), "x".repeat(MAX_PAYLOAD_BYTES + 1));
        delivered();
        assertTrue(receiver.payloads.isEmpty());
        assertEquals(1, receiver.gaps.get());
        assertEquals(0L, counter("received"));
        assertEquals(1L, counter("gaps"));
    }

    @Test
    public void payloadAtTheLimitIsDelivered() throws Exception {
        listener.message(SETTINGS.channel(), "x".repeat(MAX_PAYLOAD_BYTES));
        delivered();
        assertEquals(1, receiver.payloads.size());
        assertEquals(0, receiver.gaps.get());
    }

    @Test
    public void anotherBusAnnouncementIsAGap() throws Exception {
        listener.message(SETTINGS.gapChannel(), UUID.randomUUID().toString());
        delivered();
        assertEquals(1, receiver.gaps.get());
        assertEquals(1L, counter("gaps"));
        assertTrue(receiver.payloads.isEmpty());
    }

    @Test
    public void ownAnnouncementIsIgnored() throws Exception {
        listener.message(SETTINGS.gapChannel(), BUS_ID);
        delivered();
        assertEquals(0, receiver.gaps.get());
        assertEquals(0L, counter("gaps"));
    }

    @Test
    public void malformedAnnouncementIsStillAGap() throws Exception {
        listener.message(SETTINGS.gapChannel(), "not an id\nforged log line");
        delivered();
        listener.message(SETTINGS.gapChannel(), null);
        delivered();
        assertEquals(2, receiver.gaps.get());
    }

    @Test
    public void unknownChannelIsIgnored() throws Exception {
        listener.message("some:other:channel", "payload");
        delivered();
        assertTrue(receiver.payloads.isEmpty());
        assertEquals(0, receiver.gaps.get());
        assertEquals(0L, counter("received"));
    }

    @Test
    public void onlyAResubscriptionIsAGap() throws Exception {
        listener.subscribed(SETTINGS.gapChannel(), 1);
        listener.subscribed(SETTINGS.channel(), 2);
        delivered();
        assertEquals(0, receiver.gaps.get());
        listener.subscribed(SETTINGS.gapChannel(), 1);
        delivered();
        assertEquals(0, receiver.gaps.get());
        listener.subscribed(SETTINGS.channel(), 2);
        delivered();
        assertEquals(1, receiver.gaps.get());
    }

    @Test
    public void aResubscriptionOfAReplacedConnectionIsIgnored() throws Exception {
        @SuppressWarnings("unchecked")
        StatefulRedisPubSubConnection<String, String> replaced = (StatefulRedisPubSubConnection<String, String>) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {StatefulRedisPubSubConnection.class}, (proxy, method, args) -> null);
        ChannelListener stale = new ChannelListener(SETTINGS, BUS_ID, connection, replaced);
        stale.subscribed(SETTINGS.channel(), 1);
        stale.subscribed(SETTINGS.channel(), 1);
        delivered();
        assertEquals(0, receiver.gaps.get());
    }

    @Test
    public void aFullQueueDropsThePayloadsAndReportsOneGap() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        receiver.hold = release;
        listener.message(SETTINGS.channel(), "first");
        assertTrue(receiver.holding.await(10, TimeUnit.SECONDS));
        // the delivery thread holds the first payload; 16 MB of payloads with their overhead fill the queue, the next ones
        // overflow it
        String payload = "x".repeat(MAX_PAYLOAD_BYTES);
        for (int i = 0; i < 16 * 1024 + 10; i++) {
            listener.message(SETTINGS.channel(), payload);
        }
        release.countDown();
        delivered();
        assertEquals(List.of("first"), receiver.payloads);
        assertEquals(1, receiver.gaps.get());
        listener.message(SETTINGS.channel(), "after");
        delivered();
        assertEquals(List.of("first", "after"), receiver.payloads);
        assertEquals(1, receiver.gaps.get());
    }

    @Test
    public void payloadsAndGapsKeepTheirOrder() throws Exception {
        listener.message(SETTINGS.channel(), "before");
        delivered();
        connection.reportGap();
        listener.message(SETTINGS.channel(), "after");
        delivered();
        assertEquals(List.of("before", "after"), receiver.payloads);
        assertEquals(1, receiver.gaps.get());
    }

    @Test
    public void aPayloadQueuedAheadOfAPendingGapIsSkipped() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        receiver.hold = release;
        listener.message(SETTINGS.channel(), "first");
        assertTrue(receiver.holding.await(10, TimeUnit.SECONDS));
        listener.message(SETTINGS.channel(), "ahead-of-the-gap");
        connection.reportGap();
        listener.message(SETTINGS.channel(), "after-the-gap");
        release.countDown();
        delivered();
        assertEquals(List.of("first", "after-the-gap"), receiver.payloads);
        assertEquals(1, receiver.gaps.get());
    }

    @Test
    public void endWaitsForADeliveryInProgress() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        receiver.hold = release;
        listener.message(SETTINGS.channel(), "first");
        assertTrue(receiver.holding.await(10, TimeUnit.SECONDS));
        listener.message(SETTINGS.channel(), "queued");
        Thread ender = new Thread(connection::end);
        ender.start();
        ender.join(300);
        assertTrue(ender.isAlive(), "end() must wait for the receiver call in progress");
        release.countDown();
        ender.join(10000);
        assertFalse(ender.isAlive());
        listener.message(SETTINGS.channel(), "late");
        Thread.sleep(100);
        assertEquals(List.of("first"), receiver.payloads, "nothing is delivered after end() returns");
    }

    @Test
    public void byteLengthMatchesTheUtf8Encoding() {
        for (String text : new String[] {"", "ascii", "\u00e9t\u00e9", "\u20ac uro", "\ud83d\ude00 emoji", "lone \ud83d high",
                "lone \ude00 low", "\ude00\ud83d reversed"}) {
            assertEquals(text.getBytes(StandardCharsets.UTF_8).length, RedisInvalidationBus.byteLength(text), text);
        }
    }

    @Test
    public void nothingIsDeliveredAfterEnd() {
        connection.end();
        listener.message(SETTINGS.channel(), "late");
        listener.message(SETTINGS.gapChannel(), UUID.randomUUID().toString());
        assertTrue(receiver.payloads.isEmpty());
        assertEquals(0, receiver.gaps.get());
    }
}
