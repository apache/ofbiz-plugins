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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.ofbiz.base.container.ContainerException;
import org.apache.ofbiz.base.util.cache.CacheInvalidation;
import org.apache.ofbiz.base.util.cache.InvalidationBus;
import org.apache.ofbiz.base.util.cache.InvalidationReceiver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public final class CacheBusRedisContainerTest {

    private static CacheBusRedisSettings settings(Map<String, String> properties) {
        return CacheBusRedisSettings.from(name -> null, properties::get, name -> null);
    }

    @Test
    public void passwordSourcesIgnoreSystemPropertyOverrides() {
        Function<String, String> lookup = CacheBusRedisContainer.deploymentProperty(key -> "file:" + key, key -> "db:" + key);
        assertEquals("file:cachebus.redis.password-env", lookup.apply("cachebus.redis.password-env"));
        assertEquals("file:cachebus.redis.password-file", lookup.apply("cachebus.redis.password-file"));
        for (String key : new String[] {"cachebus.redis.uri", "cachebus.redis.username", "cachebus.redis.password",
                "cachebus.redis.cluster"}) {
            assertEquals("db:" + key, lookup.apply(key));
        }
    }

    @Test
    public void notConfiguredInstallsNothing() throws Exception {
        CacheBusRedisContainer.SettingsLoader loader = () -> settings(new HashMap<>());
        CacheBusRedisContainer container = new CacheBusRedisContainer(loader);
        container.init(List.of(), "cachebus-redis-container", "ofbiz-component.xml");
        assertEquals("cachebus-redis-container", container.getName());
        assertTrue(container.start());
        assertFalse(CacheInvalidation.isActive());
        container.stop();
        assertFalse(CacheInvalidation.isActive());
    }

    @Test
    public void configurationErrorStopsStartup() throws Exception {
        Map<String, String> properties = new HashMap<>();
        properties.put("cachebus.redis.cluster", "bad cluster!");
        CacheBusRedisContainer.SettingsLoader loader = () -> settings(properties);
        CacheBusRedisContainer container = new CacheBusRedisContainer(loader);
        container.init(List.of(), "c", "f");
        ContainerException e = assertThrows(ContainerException.class, container::start);
        assertTrue(e.getMessage().contains("cachebus.redis.cluster"), e.getMessage());
    }

    @Test
    public void redisClientDebugLoggingIsQuietedUnlessConfigured() {
        LoggerContext context = new LoggerContext("cachebus-redis-test");
        try {
            context.start();
            Configuration configuration = context.getConfiguration();
            configuration.getRootLogger().setLevel(Level.ALL);
            configuration.addLogger("io.netty", LoggerConfig.newBuilder().setLoggerName("io.netty").setLevel(Level.DEBUG)
                    .setAdditivity(true).setConfig(configuration).build());
            context.updateLoggers();
            assertTrue(context.getLogger("io.lettuce.core.protocol.CommandHandler").isDebugEnabled());

            assertEquals(List.of("io.lettuce"), CacheBusRedisContainer.quietClientLogging(context));
            assertFalse(context.getLogger("io.lettuce.core.protocol.CommandHandler").isDebugEnabled());
            assertTrue(context.getLogger("io.lettuce.core.protocol.CommandHandler").isWarnEnabled());
            assertTrue(context.getLogger("io.netty.channel.Channel").isDebugEnabled(), "a configured logger stays as configured");
            assertEquals(List.of(), CacheBusRedisContainer.quietClientLogging(context));
        } finally {
            context.stop();
        }
    }

    @AfterEach
    public void cleanUp() {
        CacheInvalidation.uninstall();
    }

    @Test
    public void configuredInstallsAndStopRemoves() throws Exception {
        Map<String, String> properties = new HashMap<>();
        properties.put("cachebus.redis.uri", "redis://localhost:1");
        CacheBusRedisContainer.SettingsLoader loader = () -> settings(properties);
        CacheBusRedisContainer container = new CacheBusRedisContainer(loader);
        container.init(List.of(), "c", "f");
        assertTrue(container.start());
        assertTrue(CacheInvalidation.isActive());
        container.stop();
        assertFalse(CacheInvalidation.isActive());
    }

    @Test
    public void clashWithInstalledBusFailsAndStopLeavesItAlone() throws Exception {
        CacheInvalidation.install(new InvalidationBus() {
            @Override
            public String getName() {
                return "other";
            }

            @Override
            public void start(InvalidationReceiver receiver) { }

            @Override
            public void publish(String payload) { }

            @Override
            public void stop() { }
        });
        Map<String, String> properties = new HashMap<>();
        properties.put("cachebus.redis.uri", "redis://localhost:1");
        CacheBusRedisContainer.SettingsLoader loader = () -> settings(properties);
        CacheBusRedisContainer container = new CacheBusRedisContainer(loader);
        container.init(List.of(), "c", "f");
        assertThrows(ContainerException.class, container::start);
        container.stop();
        assertTrue(CacheInvalidation.isActive());
    }
}
