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

import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.ofbiz.base.container.Container;
import org.apache.ofbiz.base.container.ContainerConfig;
import org.apache.ofbiz.base.container.ContainerException;
import org.apache.ofbiz.base.start.StartupCommand;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.cache.CacheInvalidation;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.DelegatorFactory;
import org.apache.ofbiz.entity.util.EntityUtilProperties;

/**
 * Installs the Redis/Valkey cache invalidation bus when the plugin starts and removes it on shutdown.
 */
public class CacheBusRedisContainer implements Container {

    private static final String MODULE = CacheBusRedisContainer.class.getName();
    /** Lettuce and Netty log every Redis command, the heartbeat's included, at debug level. */
    private static final List<String> CLIENT_LOGGERS = List.of("io.lettuce", "io.netty");

    private static final String RESOURCE = "cachebus-redis";

    /** Test seam: loads the settings, in production from the container properties and the deployment properties. */
    @FunctionalInterface
    interface SettingsLoader {
        CacheBusRedisSettings load() throws ContainerException;
    }

    private final SettingsLoader settingsLoader;
    private String name;
    private boolean installed;

    /** Used by OFBiz when it instantiates the container. */
    public CacheBusRedisContainer() {
        this.settingsLoader = this::loadSettings;
    }

    /** Test seam: lets tests supply settings without a delegator or the real configuration. */
    CacheBusRedisContainer(SettingsLoader settingsLoader) {
        this.settingsLoader = settingsLoader;
    }

    @Override
    public void init(List<StartupCommand> ofbizCommands, String name, String configFile) {
        this.name = name;
    }

    /** Reads the container properties and, through the delegator, the file keys with their SystemProperty overrides. */
    private CacheBusRedisSettings loadSettings() throws ContainerException {
        ContainerConfig.Configuration cfg = ContainerConfig.getConfiguration(name);
        String delegatorName = ContainerConfig.getPropertyValue(cfg, "delegator-name", "default");
        Delegator delegator = DelegatorFactory.getDelegator(delegatorName);
        if (delegator == null) {
            throw new ContainerException("cachebus-redis: invalid configuration: no delegator named " + delegatorName
                    + " (cachebus-redis-container property delegator-name)");
        }
        return CacheBusRedisSettings.from(key -> ContainerConfig.getPropertyValue(cfg, key, ""),
                key -> EntityUtilProperties.getPropertyValue(RESOURCE, key, "", delegator), System::getenv);
    }

    @Override
    public boolean start() throws ContainerException {
        CacheBusRedisSettings settings;
        try {
            settings = settingsLoader.load();
        } catch (IllegalArgumentException e) {
            throw new ContainerException("cachebus-redis: invalid configuration: " + e.getMessage(), e);
        }
        if (!settings.configured()) {
            Debug.logInfo("cachebus-redis: cachebus.redis.uri is empty, the cache invalidation bus is not installed", MODULE);
            return true;
        }
        if (LogManager.getContext(false) instanceof LoggerContext context) {
            quietClientLogging(context);
        }
        try {
            CacheInvalidation.install(new RedisInvalidationBus(settings));
            installed = true;
        } catch (IllegalStateException e) {
            throw new ContainerException("cachebus-redis: cannot install the cache invalidation bus: " + e.getMessage(), e);
        }
        // the record's toString masks the URI user info and the password
        Debug.logInfo("cachebus-redis: cache invalidation bus installed, channel " + settings.channel() + ", " + settings, MODULE);
        return true;
    }

    /**
     * Logs the Redis client's own messages at warning level and above, since OFBiz's root logger accepts every level.
     * A logger the log4j2 configuration declares for io.lettuce or io.netty is left as configured.
     * @param context the log4j logger context
     * @return the names of the loggers added
     */
    static List<String> quietClientLogging(LoggerContext context) {
        Configuration configuration = context.getConfiguration();
        List<String> added = CLIENT_LOGGERS.stream().filter(logger -> !configuration.getLoggers().containsKey(logger)).toList();
        for (String logger : added) {
            configuration.addLogger(logger, LoggerConfig.newBuilder().setLoggerName(logger).setLevel(Level.WARN)
                    .setAdditivity(true).setConfig(configuration).build());
        }
        if (!added.isEmpty()) {
            context.updateLoggers();
        }
        return added;
    }

    @Override
    public void stop() {
        if (installed) {
            installed = false;
            CacheInvalidation.uninstall();
        }
    }

    @Override
    public String getName() {
        return name;
    }
}
