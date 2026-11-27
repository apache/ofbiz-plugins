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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.apache.ofbiz.base.util.cache.InvalidationReceiver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** Tests the Redis/Valkey bus against real Valkey containers; skipped when Docker is not available. */
public final class RedisInvalidationBusTest {

    private static final DockerImageName VALKEY = DockerImageName.parse("valkey/valkey:8.1.12-alpine");
    private static final int REDIS_PORT = 6379;
    private static final long DEADLINE_MILLIS = 10000;
    private static final long TEST_INITIAL_BACKOFF_MILLIS = 100;
    private static final long TEST_MAX_BACKOFF_MILLIS = 500;
    private static final String PASSWORD = "s3cret-pass";
    /** Production defaults, so a loaded machine does not cause reconnects that break exact gap counts. */
    private static final long HEARTBEAT_MILLIS = 5000;
    private static final long HEARTBEAT_TIMEOUT_MILLIS = 3000;
    /** For the tests about the heartbeat itself. */
    private static final long FAST_HEARTBEAT_MILLIS = 1000;
    private static final long FAST_HEARTBEAT_TIMEOUT_MILLIS = 800;
    private static final String ACL_USER = "cachebus-resub";
    private static final String ACL_PASSWORD = "resub-pass";

    private static GenericContainer<?> valkey;
    private static GenericContainer<?> securedValkey;

    private final List<RedisInvalidationBus> buses = new ArrayList<>();
    private final List<GenericContainer<?>> extraContainers = new ArrayList<>();

    /** Collects what a bus delivers. */
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

    @BeforeAll
    public static void startContainers() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");
        valkey = new GenericContainer<>(VALKEY).withExposedPorts(REDIS_PORT);
        valkey.start();
        securedValkey = new GenericContainer<>(VALKEY).withExposedPorts(REDIS_PORT)
                .withCommand("valkey-server", "--requirepass", PASSWORD);
        securedValkey.start();
    }

    @AfterAll
    public static void stopContainers() {
        if (valkey != null) {
            valkey.stop();
        }
        if (securedValkey != null) {
            securedValkey.stop();
        }
    }

    @AfterEach
    public void stopBuses() {
        for (RedisInvalidationBus bus : buses) {
            bus.stop();
        }
        for (GenericContainer<?> container : extraContainers) {
            container.stop();
        }
    }

    private static String uri(GenericContainer<?> container) {
        return "redis://" + container.getHost() + ":" + container.getMappedPort(REDIS_PORT);
    }

    private static CacheBusRedisSettings settings(String uri, String cluster) {
        return settings(uri, "", "", cluster, 1048576);
    }

    private static CacheBusRedisSettings settings(String uri, String username, String password, String cluster, int maxPayloadBytes) {
        return new CacheBusRedisSettings(uri, username, password, cluster, maxPayloadBytes, 2000, HEARTBEAT_MILLIS,
                HEARTBEAT_TIMEOUT_MILLIS);
    }

    private static CacheBusRedisSettings fastHeartbeat(String uri, String username, String password, String cluster) {
        return new CacheBusRedisSettings(uri, username, password, cluster, 1048576, 2000, FAST_HEARTBEAT_MILLIS,
                FAST_HEARTBEAT_TIMEOUT_MILLIS);
    }

    /** Runs a valkey-cli command and fails on a non-zero exit code or an error reply. */
    private static void exec(GenericContainer<?> container, String... command) throws IOException, InterruptedException {
        ExecResult result = container.execInContainer(command);
        String what = String.join(" ", command);
        assertEquals(0, result.getExitCode(), what + ": " + result.getStderr());
        assertFalse(result.getStdout().contains("ERR") || result.getStderr().contains("ERR"), what + ": " + result.getStdout()
                + result.getStderr());
    }

    private static void publishUntilBothReceive(RedisInvalidationBus publisher, RecordingReceiver r1, RecordingReceiver r2,
            String prefix) throws InterruptedException {
        AtomicInteger n = new AtomicInteger();
        await("both receive " + prefix, () -> {
            publisher.publish(prefix + n.incrementAndGet());
            sleepQuietly(100);
            return r1.payloads.stream().anyMatch(p -> p.startsWith(prefix)) && r2.payloads.stream().anyMatch(p -> p.startsWith(prefix));
        });
    }

    private RedisInvalidationBus start(CacheBusRedisSettings settings, RecordingReceiver receiver) {
        RedisInvalidationBus bus = new RedisInvalidationBus(settings, TEST_INITIAL_BACKOFF_MILLIS, TEST_MAX_BACKOFF_MILLIS);
        buses.add(bus);
        bus.start(receiver);
        return bus;
    }

    private static boolean connected(RedisInvalidationBus bus) {
        return Boolean.TRUE.equals(bus.statistics().snapshot().get("connected"));
    }

    private static long counter(RedisInvalidationBus bus, String name) {
        return (Long) bus.statistics().snapshot().get(name);
    }

    /** Connected, and the gap that every connection reports, the first one included, delivered. */
    private static boolean ready(RedisInvalidationBus bus, RecordingReceiver receiver) {
        return connected(bus) && receiver.gaps.get() >= 1;
    }

    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + DEADLINE_MILLIS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Timed out waiting for " + what);
            }
            Thread.sleep(20);
        }
    }

    @Test
    public void nameAndTombstoneComeFromTheSettings() {
        CacheBusRedisSettings settings = new CacheBusRedisSettings("redis://localhost:1", "", "", "c", 1024, 1234, 5000, 3000);
        RedisInvalidationBus bus = new RedisInvalidationBus(settings);
        assertEquals("redis", bus.getName());
        assertSame(settings, bus.settings());
        assertEquals(1234L, bus.tombstoneMillis());
    }

    @Test
    public void busesOfTheSameClusterReceiveEveryPayload() throws InterruptedException {
        RecordingReceiver r1 = new RecordingReceiver();
        RecordingReceiver r2 = new RecordingReceiver();
        RedisInvalidationBus b1 = start(settings(uri(valkey), "same"), r1);
        assertSame(b1, RedisInvalidationBus.current());
        RedisInvalidationBus b2 = start(settings(uri(valkey), "same"), r2);
        await("both connected", () -> ready(b1, r1) && ready(b2, r2));
        b1.publish("hello");
        await("both received", () -> r1.payloads.contains("hello") && r2.payloads.contains("hello"));
        await("publish counted", () -> counter(b1, "published") == 1);
        assertEquals(1, r1.gaps.get(), "the gap of the first connection only");
        assertEquals(1, r2.gaps.get(), "the gap of the first connection only");
        assertEquals(1L, counter(b2, "received"));
        b2.stop();
        assertNull(RedisInvalidationBus.current());
    }

    @Test
    public void aHealthyConnectionPassesTheHeartbeat() throws InterruptedException {
        RecordingReceiver r1 = new RecordingReceiver();
        RecordingReceiver r2 = new RecordingReceiver();
        RedisInvalidationBus b1 = start(fastHeartbeat(uri(valkey), "", "", "heartbeat"), r1);
        RedisInvalidationBus b2 = start(fastHeartbeat(uri(valkey), "", "", "heartbeat"), r2);
        await("both connected", () -> ready(b1, r1) && ready(b2, r2));
        // Several heartbeat periods: PING answers on the subscribed connection, so nothing reconnects.
        Thread.sleep(FAST_HEARTBEAT_MILLIS * 4);
        assertTrue(connected(b1) && connected(b2));
        assertEquals(1, r1.gaps.get(), "the gap of the first connection only");
        assertEquals(1, r2.gaps.get(), "the gap of the first connection only");
        b1.publish("beat");
        await("both received", () -> r1.payloads.contains("beat") && r2.payloads.contains("beat"));
    }

    @Test
    public void aFailedResubscribeIsDetectedByTheHeartbeat() throws Exception {
        // exactly the rule of the README's Security section, with the channel of this test's cluster
        exec(valkey, "valkey-cli", "ACL", "LOG", "RESET");
        exec(valkey, "valkey-cli", "ACL", "SETUSER", ACL_USER, "reset", "on", ">" + ACL_PASSWORD, "resetchannels", "&ofbiz:cachebus:*",
                "+subscribe", "+publish", "+ping", "+client|setname", "+client|setinfo", "+hello");
        RecordingReceiver deaf = new RecordingReceiver();
        RecordingReceiver other = new RecordingReceiver();
        RedisInvalidationBus bus = start(fastHeartbeat(uri(valkey), ACL_USER, ACL_PASSWORD, "resub"), deaf);
        RedisInvalidationBus publisher = start(settings(uri(valkey), "resub"), other);
        await("both connected", () -> ready(bus, deaf) && ready(publisher, other));
        try {
            // Lettuce reconnects at once, but its resubscribe fails with NOPERM and is never retried; PING still works.
            exec(valkey, "valkey-cli", "ACL", "SETUSER", ACL_USER, "resetchannels");
            exec(valkey, "valkey-cli", "CLIENT", "KILL", "USER", ACL_USER);
            await("the bus notices", () -> !connected(bus));
            Thread.sleep(FAST_HEARTBEAT_MILLIS * 3);
            assertFalse(connected(bus));
            assertEquals(1, deaf.gaps.get(), "no gap while unsubscribed");
        } finally {
            // back to the README's channel rule; the NOPERM entries of the outage above are expected, so start the log afresh
            exec(valkey, "valkey-cli", "ACL", "SETUSER", ACL_USER, "resetchannels", "&ofbiz:cachebus:*");
            exec(valkey, "valkey-cli", "ACL", "LOG", "RESET");
        }
        await("a gap once subscribed again", () -> deaf.gaps.get() >= 2);
        await("connected again", () -> connected(bus));
        publishUntilBothReceive(publisher, deaf, other, "after-acl-");
        // the bus publishes as the ACL user too, so it needs no permission beyond the README's rule
        deaf.payloads.clear();
        bus.publish("from-acl-user");
        await("the ACL user's publish arrives", () -> other.payloads.contains("from-acl-user") && deaf.payloads.contains("from-acl-user"));
        ExecResult log = valkey.execInContainer("valkey-cli", "ACL", "LOG");
        assertFalse(log.getStdout().contains(ACL_USER), "ACL LOG: " + log.getStdout());
        exec(valkey, "valkey-cli", "ACL", "DELUSER", ACL_USER);
    }

    @Test
    public void lostPublishesAreAnnouncedToTheOtherServers() throws Exception {
        exec(valkey, "valkey-cli", "ACL", "LOG", "RESET");
        exec(valkey, "valkey-cli", "ACL", "SETUSER", ACL_USER, "reset", "on", ">" + ACL_PASSWORD, "resetchannels", "&ofbiz:cachebus:*",
                "+subscribe", "+publish", "+ping", "+client|setname", "+client|setinfo", "+hello");
        try {
            RecordingReceiver ra = new RecordingReceiver();
            RecordingReceiver rb = new RecordingReceiver();
            RedisInvalidationBus a = start(fastHeartbeat(uri(valkey), ACL_USER, ACL_PASSWORD, "announce"), ra);
            RedisInvalidationBus b = start(fastHeartbeat(uri(valkey), "", "", "announce"), rb);
            await("both connected", () -> ready(a, ra) && ready(b, rb));
            publishUntilBothReceive(a, ra, rb, "before-");
            int gapsOfB = rb.gaps.get();
            int gapsOfA = ra.gaps.get();
            exec(valkey, "valkey-cli", "ACL", "SETUSER", ACL_USER, "-publish");
            a.publish("lost");
            await("the failed publish is counted", () -> counter(a, "publishFailures") >= 1);
            Thread.sleep(300);
            assertFalse(rb.payloads.contains("lost"));
            exec(valkey, "valkey-cli", "ACL", "SETUSER", ACL_USER, "+publish");
            await("B is told about the lost publish", () -> rb.gaps.get() > gapsOfB);
            Thread.sleep(FAST_HEARTBEAT_MILLIS * 2);
            assertEquals(gapsOfB + 1, rb.gaps.get(), "one announcement per lost episode");
            assertEquals(gapsOfA, ra.gaps.get(), "A must ignore its own announcement");
            assertFalse(rb.payloads.contains("lost"));
        } finally {
            exec(valkey, "valkey-cli", "ACL", "DELUSER", ACL_USER);
            exec(valkey, "valkey-cli", "ACL", "LOG", "RESET");
        }
    }

    @Test
    public void aStoppedBusDeliversNothingAndCanStartAgain() throws InterruptedException {
        RecordingReceiver r1 = new RecordingReceiver();
        RecordingReceiver r2 = new RecordingReceiver();
        RedisInvalidationBus b1 = start(settings(uri(valkey), "restart"), r1);
        RedisInvalidationBus b2 = start(settings(uri(valkey), "restart"), r2);
        await("both connected", () -> connected(b1) && connected(b2));
        b2.stop();
        assertFalse(connected(b2));
        b1.publish("after-stop");
        await("publisher received", () -> r1.payloads.contains("after-stop"));
        Thread.sleep(200);
        assertTrue(r2.payloads.isEmpty());
        assertFalse(connected(b2));
        b2.start(r2);
        await("restarted bus connected", () -> connected(b2));
        b1.publish("after-restart");
        await("restarted bus receives", () -> r2.payloads.contains("after-restart"));
        assertEquals(List.of("after-restart"), r2.payloads);
    }

    @Test
    public void busesOfDifferentClustersDoNotSeeEachOther() throws InterruptedException {
        RecordingReceiver ra = new RecordingReceiver();
        RecordingReceiver rb = new RecordingReceiver();
        RedisInvalidationBus a = start(settings(uri(valkey), "alpha"), ra);
        RedisInvalidationBus b = start(settings(uri(valkey), "beta"), rb);
        await("both connected", () -> connected(a) && connected(b));
        a.publish("for-alpha");
        b.publish("for-beta");
        await("own messages received", () -> ra.payloads.contains("for-alpha") && rb.payloads.contains("for-beta"));
        Thread.sleep(200);
        assertEquals(List.of("for-alpha"), ra.payloads);
        assertEquals(List.of("for-beta"), rb.payloads);
    }

    @Test
    public void droppedConnectionsReportAGapAndKeepReceiving() throws Exception {
        RecordingReceiver r1 = new RecordingReceiver();
        RecordingReceiver r2 = new RecordingReceiver();
        RedisInvalidationBus b1 = start(settings(uri(valkey), "reconnect"), r1);
        RedisInvalidationBus b2 = start(settings(uri(valkey), "reconnect"), r2);
        await("both connected", () -> ready(b1, r1) && ready(b2, r2));
        // The server closes the connections (a TCP reset); Lettuce reconnects and resubscribes.
        exec(valkey, "valkey-cli", "CLIENT", "KILL", "TYPE", "pubsub");
        exec(valkey, "valkey-cli", "CLIENT", "KILL", "TYPE", "normal");
        await("both report a gap", () -> r1.gaps.get() >= 2 && r2.gaps.get() >= 2);
        await("both reconnected", () -> connected(b1) && connected(b2));
        publishUntilBothReceive(b1, r1, r2, "after-");
    }

    @Test
    public void aPausedServerIsDetectedByTheHeartbeat() throws Exception {
        RecordingReceiver r1 = new RecordingReceiver();
        RecordingReceiver r2 = new RecordingReceiver();
        RedisInvalidationBus b1 = start(fastHeartbeat(uri(valkey), "", "", "pause"), r1);
        RedisInvalidationBus b2 = start(fastHeartbeat(uri(valkey), "", "", "pause"), r2);
        await("both connected", () -> ready(b1, r1) && ready(b2, r2));
        // A paused server keeps its TCP connections open and silent: only the heartbeat notices.
        valkey.getDockerClient().pauseContainerCmd(valkey.getContainerId()).exec();
        try {
            b1.publish("during-pause");
            await("both notice the pause", () -> !connected(b1) && !connected(b2));
            assertEquals(1, r1.gaps.get(), "no gap before the subscription is back");
            assertEquals(1, r2.gaps.get(), "no gap before the subscription is back");
        } finally {
            valkey.getDockerClient().unpauseContainerCmd(valkey.getContainerId()).exec();
        }
        await("both report a gap once resubscribed", () -> r1.gaps.get() >= 2 && r2.gaps.get() >= 2);
        await("both reconnected", () -> connected(b1) && connected(b2));
        await("the publish made during the pause failed", () -> counter(b1, "publishFailures") >= 1);
        publishUntilBothReceive(b1, r1, r2, "after-unpause-");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    public void aBusStartedBeforeTheServerConnectsAndReportsAGap() throws Exception {
        // the container and its port exist from the start, the server in it only later, so no other process can take the port
        GenericContainer<?> late = new GenericContainer<>(VALKEY).withExposedPorts(REDIS_PORT)
                .withCommand("tail", "-f", "/dev/null").waitingFor(Wait.forSuccessfulCommand("true"));
        extraContainers.add(late);
        late.start();
        RecordingReceiver receiver = new RecordingReceiver();
        RedisInvalidationBus bus = start(settings(uri(late), "late"), receiver);
        Thread.sleep(500);
        assertFalse(connected(bus));
        bus.publish("lost");
        assertEquals(1L, counter(bus, "publishFailures"));
        assertEquals(0L, counter(bus, "published"));
        exec(late, "valkey-server", "--daemonize", "yes", "--protected-mode", "no");
        await("connected once the server is there", () -> connected(bus));
        await("a gap reported", () -> receiver.gaps.get() == 1);
        Thread.sleep(200);
        assertEquals(1, receiver.gaps.get(), "one gap however many attempts failed");
        bus.publish("found");
        await("received", () -> receiver.payloads.contains("found"));
        assertFalse(receiver.payloads.contains("lost"));
    }

    @Test
    public void anOversizedPayloadIsDroppedAndReportedAsAGap() throws InterruptedException {
        RecordingReceiver bigReceiver = new RecordingReceiver();
        RecordingReceiver smallReceiver = new RecordingReceiver();
        RedisInvalidationBus big = start(settings(uri(valkey), "oversize"), bigReceiver);
        RedisInvalidationBus small = start(settings(uri(valkey), "", "", "oversize", 1024), smallReceiver);
        await("both connected", () -> ready(big, bigReceiver) && ready(small, smallReceiver));
        String oversized = "x".repeat(2000);
        big.publish(oversized);
        big.publish("small");
        await("small payload received", () -> smallReceiver.payloads.contains("small"));
        assertEquals(List.of("small"), smallReceiver.payloads);
        assertEquals(2, smallReceiver.gaps.get(), "the first connection's gap and the oversized payload's");
        assertEquals(2L, counter(small, "gaps"));
        assertEquals(1L, counter(small, "received"));
        await("big bus received both", () -> bigReceiver.payloads.size() == 2);
        assertTrue(bigReceiver.payloads.contains(oversized));
    }

    @Test
    public void aPasswordProtectedServerNeedsTheRightPassword() throws InterruptedException {
        String uri = uri(securedValkey);
        RecordingReceiver good = new RecordingReceiver();
        RecordingReceiver goodUser = new RecordingReceiver();
        RecordingReceiver bad = new RecordingReceiver();
        RedisInvalidationBus goodBus = start(settings(uri, "", PASSWORD, "acl", 1048576), good);
        RedisInvalidationBus userBus = start(settings(uri, "default", PASSWORD, "acl", 1048576), goodUser);
        RedisInvalidationBus badBus = start(settings(uri, "", "wrong-pass", "acl", 1048576), bad);
        await("right password connects", () -> connected(goodBus) && connected(userBus));
        goodBus.publish("secured");
        await("received", () -> good.payloads.contains("secured") && goodUser.payloads.contains("secured"));
        Thread.sleep(300);
        assertFalse(connected(badBus));
        assertTrue(bad.payloads.isEmpty());
        badBus.publish("rejected");
        assertEquals(1L, counter(badBus, "publishFailures"));
    }
}
