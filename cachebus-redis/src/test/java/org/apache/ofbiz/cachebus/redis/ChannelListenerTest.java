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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ofbiz.base.util.cache.InvalidationReceiver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Routes messages through a {@link ChannelListener} on a connection that never connects, so no server is needed. */
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

        @Override
        public void onPayload(String payload) {
            payloads.add(payload);
        }

        @Override
        public void onGap() {
            gaps.incrementAndGet();
        }
    }

    @BeforeEach
    public void createListener() {
        connection = new RedisChannelConnection(SETTINGS, statistics, BUS_ID, new AtomicBoolean(), receiver, 100, 500);
        listener = new ChannelListener(SETTINGS, BUS_ID, connection);
    }

    @AfterEach
    public void endConnection() {
        connection.end();
    }

    private long counter(String name) {
        return (Long) statistics.snapshot().get(name);
    }

    @Test
    public void mainChannelPayloadIsDeliveredAndCounted() {
        listener.message(SETTINGS.channel(), "payload-1");
        assertEquals(List.of("payload-1"), receiver.payloads);
        assertEquals(0, receiver.gaps.get());
        assertEquals(1L, counter("received"));
        assertEquals((long) RedisInvalidationBus.byteLength("payload-1"), counter("receivedBytes"));
    }

    @Test
    public void oversizedPayloadIsAGapNotADelivery() {
        listener.message(SETTINGS.channel(), "x".repeat(MAX_PAYLOAD_BYTES + 1));
        assertTrue(receiver.payloads.isEmpty());
        assertEquals(1, receiver.gaps.get());
        assertEquals(0L, counter("received"));
        assertEquals(1L, counter("gaps"));
    }

    @Test
    public void payloadAtTheLimitIsDelivered() {
        listener.message(SETTINGS.channel(), "x".repeat(MAX_PAYLOAD_BYTES));
        assertEquals(1, receiver.payloads.size());
        assertEquals(0, receiver.gaps.get());
    }

    @Test
    public void anotherBusAnnouncementIsAGap() {
        listener.message(SETTINGS.gapChannel(), UUID.randomUUID().toString());
        assertEquals(1, receiver.gaps.get());
        assertEquals(1L, counter("gaps"));
        assertTrue(receiver.payloads.isEmpty());
    }

    @Test
    public void ownAnnouncementIsIgnored() {
        listener.message(SETTINGS.gapChannel(), BUS_ID);
        assertEquals(0, receiver.gaps.get());
        assertEquals(0L, counter("gaps"));
    }

    @Test
    public void malformedAnnouncementIsStillAGap() {
        listener.message(SETTINGS.gapChannel(), "not an id\nforged log line");
        listener.message(SETTINGS.gapChannel(), null);
        assertEquals(2, receiver.gaps.get());
    }

    @Test
    public void unknownChannelIsIgnored() {
        listener.message("some:other:channel", "payload");
        assertTrue(receiver.payloads.isEmpty());
        assertEquals(0, receiver.gaps.get());
        assertEquals(0L, counter("received"));
    }

    @Test
    public void onlyAResubscriptionIsAGap() {
        listener.subscribed(SETTINGS.gapChannel(), 1);
        listener.subscribed(SETTINGS.channel(), 2);
        assertEquals(0, receiver.gaps.get());
        listener.subscribed(SETTINGS.gapChannel(), 1);
        assertEquals(0, receiver.gaps.get());
        listener.subscribed(SETTINGS.channel(), 2);
        assertEquals(1, receiver.gaps.get());
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
