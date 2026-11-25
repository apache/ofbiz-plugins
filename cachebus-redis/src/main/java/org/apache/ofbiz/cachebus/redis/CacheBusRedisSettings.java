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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.regex.Pattern;

import io.lettuce.core.RedisURI;

/**
 * Settings of the Redis/Valkey cache invalidation bus, built from the container properties in {@code ofbiz-component.xml} and
 * {@code cachebus-redis.properties}.
 *
 * @param uri the Redis/Valkey URI; empty means the plugin does nothing
 * @param username the ACL user name, may be empty
 * @param password the resolved password, may be empty (no password)
 * @param cluster the cluster name, part of the channel name
 * @param maxPayloadBytes received payloads larger than this are dropped and treated as a gap
 * @param tombstoneMillis how long a received key invalidation blocks plain puts of that key
 * @param heartbeatMillis how often the subscribed connection is checked with a PING
 * @param heartbeatTimeoutMillis how long a PING may take before the connections are dropped and reopened
 */
public record CacheBusRedisSettings(String uri, String username, String password, String cluster, int maxPayloadBytes,
        long tombstoneMillis, long heartbeatMillis, long heartbeatTimeoutMillis) {

    private static final String CONTAINER = "cachebus-redis-container property ";
    private static final String PREFIX = "cachebus.redis.";
    private static final Pattern CLUSTER_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Pattern SCHEME_PATTERN = Pattern.compile("(redis|rediss|redis-sentinel)://");
    private static final int MIN_PAYLOAD_BYTES = 1024;
    private static final int MAX_PAYLOAD_BYTES = 64 * 1024 * 1024;
    private static final long MAX_TOMBSTONE_MILLIS = 60000L;
    private static final long MIN_HEARTBEAT_MILLIS = 100L;
    private static final long MAX_HEARTBEAT_MILLIS = 600000L;
    private static final long MIN_HEARTBEAT_TIMEOUT_MILLIS = 10L;

    public CacheBusRedisSettings {
        uri = uri == null ? "" : uri;
        username = username == null ? "" : username;
        password = password == null ? "" : password;
        if (!uri.isEmpty()) {
            try {
                if (!SCHEME_PATTERN.matcher(uri).lookingAt()) {
                    throw new IllegalArgumentException("scheme");
                }
                RedisURI.create(uri);
            } catch (RuntimeException e) {
                // deliberately neither the URI nor the cause message: both may carry credentials
                throw new IllegalArgumentException(PREFIX + "uri is not a valid redis://, rediss:// or redis-sentinel:// URI");
            }
        }
        if (cluster == null || !CLUSTER_PATTERN.matcher(cluster).matches()) {
            throw new IllegalArgumentException(PREFIX + "cluster must be non-empty and match [A-Za-z0-9._-]+");
        }
        if (maxPayloadBytes < MIN_PAYLOAD_BYTES || maxPayloadBytes > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(CONTAINER + "max-payload-bytes must be between " + MIN_PAYLOAD_BYTES + " and "
                    + MAX_PAYLOAD_BYTES);
        }
        if (tombstoneMillis < 0 || tombstoneMillis > MAX_TOMBSTONE_MILLIS) {
            throw new IllegalArgumentException(CONTAINER + "tombstone-ms must be between 0 and " + MAX_TOMBSTONE_MILLIS);
        }
        if (heartbeatMillis < MIN_HEARTBEAT_MILLIS || heartbeatMillis > MAX_HEARTBEAT_MILLIS) {
            throw new IllegalArgumentException(CONTAINER + "heartbeat-ms must be between " + MIN_HEARTBEAT_MILLIS + " and "
                    + MAX_HEARTBEAT_MILLIS);
        }
        if (heartbeatTimeoutMillis < MIN_HEARTBEAT_TIMEOUT_MILLIS || heartbeatTimeoutMillis >= heartbeatMillis) {
            throw new IllegalArgumentException(CONTAINER + "heartbeat-timeout-ms must be at least " + MIN_HEARTBEAT_TIMEOUT_MILLIS
                    + " and less than " + CONTAINER + "heartbeat-ms (" + heartbeatMillis + ")");
        }
    }

    /**
     * Builds the settings from their three sources.
     * @param containerProperty looks up a container property of {@code ofbiz-component.xml} by its bare name, e.g. heartbeat-ms
     * @param property looks up a deployment property by its full key, e.g. cachebus.redis.uri; {@code LOOKUP(key)} values and
     *        SystemProperty overrides are the lookup's business
     * @param env looks up an environment variable
     * @return the settings
     */
    public static CacheBusRedisSettings from(Function<String, String> containerProperty, Function<String, String> property,
            Function<String, String> env) {
        String password = resolvePassword(property, env);
        return new CacheBusRedisSettings(
                value(property, PREFIX + "uri", ""),
                value(property, PREFIX + "username", ""),
                password,
                value(property, PREFIX + "cluster", "test"),
                parsePayloadLimit(value(containerProperty, "max-payload-bytes", "1048576")),
                parseNumber(value(containerProperty, "tombstone-ms", "2000"), CONTAINER + "tombstone-ms"),
                parseNumber(value(containerProperty, "heartbeat-ms", "5000"), CONTAINER + "heartbeat-ms"),
                parseNumber(value(containerProperty, "heartbeat-timeout-ms", "3000"), CONTAINER + "heartbeat-timeout-ms"));
    }

    private static String value(Function<String, String> lookup, String key, String defaultValue) {
        String v = lookup.apply(key);
        v = v == null ? "" : v.trim();
        return v.isEmpty() ? defaultValue : v;
    }

    private static int parsePayloadLimit(String text) {
        long limit = parseNumber(text, CONTAINER + "max-payload-bytes");
        if (limit < MIN_PAYLOAD_BYTES || limit > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(CONTAINER + "max-payload-bytes must be between " + MIN_PAYLOAD_BYTES + " and "
                    + MAX_PAYLOAD_BYTES);
        }
        return (int) limit;
    }

    private static Long parseNumber(String text, String name) {
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " is not a number: " + text, e);
        }
    }

    private static String resolvePassword(Function<String, String> properties, Function<String, String> env) {
        String envName = value(properties, PREFIX + "password-env", "");
        if (!envName.isEmpty()) {
            String fromEnv = env.apply(envName);
            if (fromEnv == null || fromEnv.isEmpty()) {
                throw new IllegalArgumentException(PREFIX + "password-env names the environment variable " + envName
                        + ", which is not set or empty");
            }
            return fromEnv;
        }
        String file = value(properties, PREFIX + "password-file", "");
        if (!file.isEmpty()) {
            String content;
            try {
                content = Files.readString(Path.of(file));
            } catch (IOException | RuntimeException e) {
                throw new IllegalArgumentException(PREFIX + "password-file cannot be read: " + file, e);
            }
            if (content.endsWith("\r\n")) {
                content = content.substring(0, content.length() - 2);
            } else if (content.endsWith("\n")) {
                content = content.substring(0, content.length() - 1);
            }
            if (content.isEmpty()) {
                throw new IllegalArgumentException(PREFIX + "password-file is empty: " + file);
            }
            return content;
        }
        String plain = properties.apply(PREFIX + "password");
        return plain == null ? "" : plain;
    }

    /**
     * Whether a Redis/Valkey server is configured.
     * @return true when the URI is not empty
     */
    public boolean configured() {
        return !uri.isEmpty();
    }

    /**
     * The pub/sub channel the bus uses.
     * @return the channel name
     */
    public String channel() {
        return "ofbiz:cachebus:" + cluster;
    }

    /**
     * The pub/sub channel on which a server announces that it lost published invalidations.
     * @return the gap channel name
     */
    public String gapChannel() {
        return channel() + ":gap";
    }

    private String maskedUri() {
        int scheme = uri.indexOf("://");
        if (scheme < 0) {
            return uri;
        }
        int slash = uri.indexOf('/', scheme + 3);
        int end = slash < 0 ? uri.length() : slash;
        // the credentials end at the last '@' of the authority, as a password may itself contain '@'
        int at = uri.lastIndexOf('@', end - 1);
        if (at < scheme + 3) {
            return uri;
        }
        return uri.substring(0, scheme + 3) + "***" + uri.substring(at);
    }

    @Override
    public String toString() {
        return "CacheBusRedisSettings[uri=" + maskedUri() + ", username=" + username + ", password=" + (password.isEmpty() ? "" : "***")
                + ", cluster=" + cluster + ", maxPayloadBytes=" + maxPayloadBytes + ", tombstoneMillis=" + tombstoneMillis
                + ", heartbeatMillis=" + heartbeatMillis + ", heartbeatTimeoutMillis=" + heartbeatTimeoutMillis + "]";
    }
}
