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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CacheBusRedisSettingsTest {

    private static final Function<String, String> NO_ENV = name -> null;
    /** The settings read from the container properties; the helper routes the test keys. */
    private static final Set<String> TUNING = Set.of("heartbeat-ms", "heartbeat-timeout-ms", "max-payload-bytes", "tombstone-ms");

    private static CacheBusRedisSettings build(Map<String, String> properties, Function<String, String> env) {
        Map<String, String> container = new HashMap<>();
        Map<String, String> file = new HashMap<>();
        properties.forEach((key, value) -> {
            if (key.startsWith("cachebus.redis.") && TUNING.contains(key.substring("cachebus.redis.".length()))) {
                container.put(key.substring("cachebus.redis.".length()), value);
            } else {
                file.put(key, value);
            }
        });
        return CacheBusRedisSettings.from(container::get, file::get, env);
    }

    private static String rejection(Map<String, String> properties, Function<String, String> env) {
        return assertThrows(IllegalArgumentException.class, () -> build(properties, env)).getMessage();
    }

    private static Map<String, String> props(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    public void defaults() {
        CacheBusRedisSettings s = build(props(), NO_ENV);
        assertEquals("test", s.cluster());
        assertEquals(1048576, s.maxPayloadBytes());
        assertEquals(2000L, s.tombstoneMillis());
        assertEquals("", s.uri());
        assertFalse(s.configured());
        assertEquals("ofbiz:cachebus:test", s.channel());
        assertEquals(5000L, s.heartbeatMillis());
        assertEquals(3000L, s.heartbeatTimeoutMillis());
    }

    @Test
    public void heartbeatSettings() {
        CacheBusRedisSettings s = build(props(
                "cachebus.redis.heartbeat-ms", " 200 ", "cachebus.redis.heartbeat-timeout-ms", " 100 "), NO_ENV);
        assertEquals(200L, s.heartbeatMillis());
        assertEquals(100L, s.heartbeatTimeoutMillis());
        assertTrue(s.toString().contains("heartbeatMillis=200"));
        for (String bad : new String[] {"99", "600001", "x", "-1"}) {
            String msg = rejection(props("cachebus.redis.heartbeat-ms", bad), NO_ENV);
            assertTrue(msg.contains("cachebus-redis-container property heartbeat-ms"), msg);
        }
        for (String bad : new String[] {"9", "5000", "6000", "x"}) {
            String msg = rejection(props("cachebus.redis.heartbeat-timeout-ms", bad), NO_ENV);
            assertTrue(msg.contains("cachebus-redis-container property heartbeat-timeout-ms"), msg);
        }
        String msg = rejection(props("cachebus.redis.heartbeat-ms", "2000"), NO_ENV);
        assertTrue(msg.contains("heartbeat-timeout-ms") && msg.contains("less than cachebus-redis-container property heartbeat-ms (2000)"), msg);
    }

    @Test
    public void valuesAreTrimmed() {
        CacheBusRedisSettings s = build(props(
                "cachebus.redis.uri", "  redis://h:6379 ", "cachebus.redis.username", " u ",
                "cachebus.redis.cluster", " prod ", "cachebus.redis.max-payload-bytes", " 2048 ",
                "cachebus.redis.tombstone-ms", " 10 "), NO_ENV);
        assertTrue(s.configured());
        assertEquals("redis://h:6379", s.uri());
        assertEquals("u", s.username());
        assertEquals("prod", s.cluster());
        assertEquals(2048, s.maxPayloadBytes());
        assertEquals(10L, s.tombstoneMillis());
        assertEquals("ofbiz:cachebus:prod", s.channel());
    }

    @Test
    public void invalidValuesRejected() {
        for (String bad : new String[] {"a b", "a:b", "a/b"}) {
            String msg = rejection(props("cachebus.redis.cluster", bad), NO_ENV);
            assertTrue(msg.contains("cachebus.redis.cluster"));
        }
        for (String bad : new String[] {"1023", "67108865", "4296015872", "abc", "-1"}) {
            String msg = rejection(props("cachebus.redis.max-payload-bytes", bad), NO_ENV);
            assertTrue(msg.contains("cachebus-redis-container property max-payload-bytes"));
        }
        for (String bad : new String[] {"-1", "60001", "x"}) {
            String msg = rejection(props("cachebus.redis.tombstone-ms", bad), NO_ENV);
            assertTrue(msg.contains("cachebus-redis-container property tombstone-ms"));
        }
        assertEquals(1024, build(props("cachebus.redis.max-payload-bytes", "1024"), NO_ENV)
                .maxPayloadBytes());
        assertEquals(0L, build(props("cachebus.redis.tombstone-ms", "0"), NO_ENV).tombstoneMillis());
        assertEquals(60000L, build(props("cachebus.redis.tombstone-ms", "60000"), NO_ENV)
                .tombstoneMillis());
    }

    private static Path secretFile(Path tmp, String content) throws IOException {
        Path f = Files.createTempFile(tmp, "pw", ".txt");
        Files.writeString(f, content);
        return f;
    }

    @Test
    public void envWinsOverFileAndProperty(@TempDir Path tmp) throws IOException {
        Path f = secretFile(tmp, "fromfile\n");
        CacheBusRedisSettings s = build(props(
                "cachebus.redis.password-env", "PW", "cachebus.redis.password-file", f.toString(),
                "cachebus.redis.password", "fromprop"), name -> "PW".equals(name) ? "fromenv" : null);
        assertEquals("fromenv", s.password());
    }

    @Test
    public void fileWinsOverProperty(@TempDir Path tmp) throws IOException {
        Path f = secretFile(tmp, "secret\n");
        CacheBusRedisSettings s = build(props(
                "cachebus.redis.password-file", f.toString(), "cachebus.redis.password", "fromprop"), NO_ENV);
        assertEquals("secret", s.password());
        assertEquals("secret", build(props(
                "cachebus.redis.password-file", secretFile(tmp, "secret\r\n").toString()), NO_ENV).password());
        assertEquals("secret\n", build(props(
                "cachebus.redis.password-file", secretFile(tmp, "secret\n\n").toString()), NO_ENV).password());
    }

    @Test
    public void emptyClusterDefaultsToTest() {
        assertEquals("test", build(props("cachebus.redis.cluster", " "), NO_ENV).cluster());
    }

    @Test
    public void emptyPasswordFileRejected(@TempDir Path tmp) throws IOException {
        for (String content : new String[] {"", "\n", "\r\n"}) {
            String msg = rejection(props("cachebus.redis.password-file", secretFile(tmp, content).toString()), NO_ENV);
            assertTrue(msg.contains("cachebus.redis.password-file"));
        }
    }

    @Test
    public void passwordPropertyIsNotTrimmed() {
        assertEquals(" pw ", build(props("cachebus.redis.password", " pw "), NO_ENV).password());
    }

    @Test
    public void toStringMasksUriCredentials() {
        String s = build(props("cachebus.redis.uri", "redis://user:pw1@host:6379/0"), NO_ENV).toString();
        assertFalse(s.contains("pw1"));
        assertFalse(s.contains("user:"));
        assertTrue(s.contains("redis://***@host:6379/0"));
        assertTrue(build(props("cachebus.redis.uri", "redis://host:6379/0"), NO_ENV).toString()
                .contains("redis://host:6379/0"));
    }

    @Test
    public void toStringMasksPasswordsContainingAt() {
        String s = build(props("cachebus.redis.uri", "redis://:p@ss@host:6379"), NO_ENV).toString();
        assertFalse(s.contains("p@ss"));
        assertFalse(s.contains("ss@"));
        assertTrue(s.contains("redis://***@host:6379"));
        s = build(props("cachebus.redis.uri", "redis-sentinel://u:p@h1:26379,h2:26379/0#mymaster"), NO_ENV)
                .toString();
        assertFalse(s.contains("u:p"));
        assertTrue(s.contains("redis-sentinel://***@h1:26379,h2:26379/0#mymaster"));
    }

    @Test
    public void propertyAlone() {
        assertEquals("fromprop", build(props("cachebus.redis.password", "fromprop"), NO_ENV).password());
        assertEquals("", build(props(), NO_ENV).password());
    }

    @Test
    public void unresolvablePasswordSourcesRejected(@TempDir Path tmp) {
        String msg = rejection(props("cachebus.redis.password-env", "PW"), NO_ENV);
        assertTrue(msg.contains("cachebus.redis.password-env"));
        msg = rejection(props("cachebus.redis.password-env", "PW"), name -> "");
        assertTrue(msg.contains("cachebus.redis.password-env"));
        String missing = tmp.resolve("nope").toString();
        msg = rejection(props("cachebus.redis.password-file", missing), NO_ENV);
        assertTrue(msg.contains("cachebus.redis.password-file"));
    }

    @Test
    public void toStringMasksPassword() {
        CacheBusRedisSettings s = build(props(
                "cachebus.redis.uri", "redis://h", "cachebus.redis.password", "hunter2"), NO_ENV);
        assertFalse(s.toString().contains("hunter2"));
        assertTrue(s.toString().contains("password=***"));
    }

    @Test
    public void containerPropertyErrorNamesTheContainerProperty() {
        Map<String, String> container = Map.of("heartbeat-ms", "x");
        String msg = assertThrows(IllegalArgumentException.class, () -> CacheBusRedisSettings.from(container::get, k -> null, NO_ENV))
                .getMessage();
        assertTrue(msg.contains("cachebus-redis-container property heartbeat-ms"), msg);
        assertFalse(msg.contains("cachebus.redis."), msg);
    }

    @Test
    public void statisticsSnapshot() {
        BusStatistics st = new BusStatistics();
        st.published();
        st.publishFailed();
        st.received(10);
        st.received(5);
        st.gap();
        st.setConnected(true);
        Map<String, Object> snap = st.snapshot();
        assertEquals(1L, snap.get("published"));
        assertEquals(1L, snap.get("publishFailures"));
        assertEquals(2L, snap.get("received"));
        assertEquals(15L, snap.get("receivedBytes"));
        assertEquals(1L, snap.get("gaps"));
        assertEquals(true, snap.get("connected"));
        assertTrue((Long) snap.get("lastMessageMillis") > 0L);
    }

    @Test
    public void invalidUriNamesPropertyWithoutLeakingIt() {
        for (String bad : new String[] {"http://user:secret@host:6379", "redis://user:secret@host:99999", "user:secret@host"}) {
            String message = rejection(props("cachebus.redis.uri", bad), NO_ENV);
            assertTrue(message.contains("cachebus.redis.uri"), message);
            assertFalse(message.contains("secret"), message);
            assertFalse(message.contains("host"), message);
        }
    }
}
