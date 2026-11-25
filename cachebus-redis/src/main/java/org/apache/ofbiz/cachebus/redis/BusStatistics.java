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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Counters of the Redis/Valkey cache invalidation bus, shown on the status page. */
final class BusStatistics {

    private final AtomicLong published = new AtomicLong();
    private final AtomicLong publishFailures = new AtomicLong();
    private final AtomicLong received = new AtomicLong();
    private final AtomicLong receivedBytes = new AtomicLong();
    private final AtomicLong gaps = new AtomicLong();
    private volatile boolean connected;
    private volatile long lastMessageMillis;

    void published() {
        published.incrementAndGet();
    }

    void publishFailed() {
        publishFailures.incrementAndGet();
    }

    void received(long bytes) {
        received.incrementAndGet();
        receivedBytes.addAndGet(bytes);
        lastMessageMillis = System.currentTimeMillis();
    }

    void gap() {
        gaps.incrementAndGet();
    }

    void setConnected(boolean connected) {
        this.connected = connected;
    }

    Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("connected", connected);
        m.put("published", published.get());
        m.put("publishFailures", publishFailures.get());
        m.put("received", received.get());
        m.put("receivedBytes", receivedBytes.get());
        m.put("gaps", gaps.get());
        m.put("lastMessageMillis", lastMessageMillis);
        return m;
    }
}
