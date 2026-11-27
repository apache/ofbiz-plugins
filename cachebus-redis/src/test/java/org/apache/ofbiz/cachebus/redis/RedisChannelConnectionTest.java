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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.ofbiz.base.util.cache.InvalidationReceiver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Checks, on a connection that never connects, how the connection state follows the events Lettuce reports, in the
 * orders they can arrive while a connect opens the publisher after the subscribe.
 */
public final class RedisChannelConnectionTest {

    private static final CacheBusRedisSettings SETTINGS = new CacheBusRedisSettings("redis://localhost:1", "", "", "unit",
            1024, 2000, 5000, 3000);

    private RedisChannelConnection connection;

    @BeforeEach
    public void createConnection() {
        InvalidationReceiver receiver = new InvalidationReceiver() {
            @Override
            public void onPayload(String payload) { }

            @Override
            public void onGap() { }
        };
        connection = new RedisChannelConnection(SETTINGS, new BusStatistics(), UUID.randomUUID().toString(), new AtomicBoolean(),
                receiver, 100, 500);
    }

    @AfterEach
    public void endConnection() {
        connection.end();
    }

    @Test
    public void openConnectionsAreUp() {
        connection.connectionsOpened(true, true);
        assertTrue(connection.isConnected());
    }

    @Test
    public void aSubscriberDroppedDuringTheConnectIsNotUpThoughReopened() {
        // dropped after the subscribe returned; Lettuce reopened it, but its resubscribe has not been confirmed
        connection.subscriberLost();
        connection.connectionsOpened(true, true);
        assertFalse(connection.isConnected());
        connection.subscriberRestored();
        assertTrue(connection.isConnected());
    }

    @Test
    public void aSubscriberResubscribedBeforeTheConnectEndsIsUp() {
        connection.subscriberLost();
        connection.subscriberRestored();
        connection.connectionsOpened(true, true);
        assertTrue(connection.isConnected());
    }

    @Test
    public void aClosedConnectionIsNotUp() {
        connection.connectionsOpened(true, false);
        assertFalse(connection.isConnected());
        connection.connectionsOpened(false, true);
        assertFalse(connection.isConnected());
    }
}
