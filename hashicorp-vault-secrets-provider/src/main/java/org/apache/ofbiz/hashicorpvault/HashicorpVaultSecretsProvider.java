/*******************************************************************************
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
package org.apache.ofbiz.hashicorpvault;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.util.Collections;

import io.github.jopenlibs.vault.SslConfig;
import io.github.jopenlibs.vault.Vault;
import io.github.jopenlibs.vault.VaultConfig;
import io.github.jopenlibs.vault.VaultException;

import org.apache.ofbiz.base.lang.ThreadSafe;
import org.apache.ofbiz.base.secret.SecretProvider;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilProperties;

/**
 * {@link SecretProvider} implementation backed by HashiCorp Vault.
 *
 * <p>Supports both Token and AppRole authentication methods. The preferred
 * production approach is AppRole — the role_id is non-sensitive and can live
 * in config, while the secret_id is injected at deploy time via an environment
 * variable or a secrets bootstrap mechanism.</p>
 *
 * <p>Both KV v1 and KV v2 engines are supported via the {@code vault.kv.version}
 * property. The driver handles the {@code /data/} path rewrite for KV v2
 * automatically when {@code engineVersion(2)} is configured.</p>
 *
 * <p>Resolved secret values are cached in memory for the TTL configured by
 * {@code vault.cache.ttl.seconds} (default 1 hour).</p>
 *
 * <p>Configure via {@code plugins/hashicorp-vault-secrets-provider/config/hashicorp-vault-secrets.properties}.</p>
 */
@ThreadSafe
public final class HashicorpVaultSecretsProvider implements SecretProvider {

    private static final String MODULE = HashicorpVaultSecretsProvider.class.getName();
    private static final String CONFIG_RESOURCE = "hashicorp-vault-secrets";

    private final HashicorpVaultReader vaultReader;
    private final String kvMount;
    private final String secretNamePrefix;
    private final String field;
    private final long cacheTtlMs;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private static final class CacheEntry {
        final String value;
        final long expiresAt;

        CacheEntry(String value, long ttlMs) {
            this.value = value;
            this.expiresAt = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAt;
        }
    }

    /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
    public HashicorpVaultSecretsProvider() {
        this(readerFrom(buildVault()),
                prop("vault.kv.mount", "secret"),
                prop("vault.secret.name.prefix", ""),
                prop("vault.field", "password"),
                readTtlMs());
    }

    /** Package-private constructor used by unit tests to inject a {@link HashicorpVaultReader} lambda. */
    HashicorpVaultSecretsProvider(HashicorpVaultReader vaultReader, String kvMount, String secretNamePrefix,
            String field, long cacheTtlMs) {
        this.vaultReader = vaultReader;
        this.kvMount = kvMount;
        this.secretNamePrefix = secretNamePrefix;
        this.field = field;
        this.cacheTtlMs = cacheTtlMs;
    }

    @Override
    public String getSecret(String key) throws GeneralException {
        CacheEntry cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.value;
        }

        String path = kvMount + "/" + secretNamePrefix + key;
        String value = readFromVault(path);

        cache.put(key, new CacheEntry(value, cacheTtlMs));
        return value;
    }

    /**
     * Clears the in-memory cache, forcing the next {@link #getSecret(String)} call
     * for each key to re-fetch from Vault. Useful after a secret rotation.
     */
    public void invalidateCache() {
        cache.clear();
        Debug.logInfo("HashicorpVaultSecretsProvider: secret cache invalidated", MODULE);
    }

    // -- private helpers --

    private String readFromVault(String path) throws GeneralException {
        Map<String, String> data;
        try {
            data = vaultReader.read(path);
        } catch (VaultException e) {
            throw new GeneralException("Vault error reading path '" + path + "': " + e.getMessage(), e);
        }

        if (data == null || data.isEmpty()) {
            throw new GeneralException("No data found at Vault path '" + path + "' — check the path and auth policy");
        }

        if (!field.isEmpty()) {
            String value = data.get(field);
            if (value == null) {
                throw new GeneralException(
                        "Field '" + field + "' not found in Vault secret at '" + path + "'");
            }
            return value;
        }

        if (data.size() == 1) {
            return data.values().iterator().next();
        }

        throw new GeneralException("Secret at '" + path + "' has " + data.size()
                + " fields — set vault.field to select one");
    }

    private static HashicorpVaultReader readerFrom(Vault vault) {
        return path -> {
            Map<String, String> data = vault.logical().read(path).getData();
            return data != null ? data : Collections.emptyMap();
        };
    }

    private static Vault buildVault() {
        String address = prop("vault.address", "http://127.0.0.1:8200");
        String authMethod = prop("vault.auth.method", "token");
        int kvVersion = parseKvVersion(prop("vault.kv.version", "2"));
        boolean sslVerify = Boolean.parseBoolean(prop("vault.ssl.verify", "true"));

        try {
            SslConfig ssl = new SslConfig().verify(sslVerify).build();
            String token;

            if ("approle".equalsIgnoreCase(authMethod)) {
                String roleId = prop("vault.approle.role_id", "");
                String secretId = prop("vault.approle.secret_id", "");
                VaultConfig bootConfig = new VaultConfig()
                        .address(address)
                        .engineVersion(kvVersion)
                        .sslConfig(ssl)
                        .build();
                token = new Vault(bootConfig)
                        .auth()
                        .loginByAppRole(roleId, secretId)
                        .getAuthClientToken();
            } else {
                token = prop("vault.token", "");
            }

            VaultConfig config = new VaultConfig()
                    .address(address)
                    .token(token)
                    .engineVersion(kvVersion)
                    .sslConfig(ssl)
                    .build();

            Debug.logInfo("HashicorpVaultSecretsProvider: initialized address=" + address
                    + " auth=" + authMethod + " kv-version=" + kvVersion, MODULE);
            return new Vault(config);

        } catch (VaultException e) {
            throw new RuntimeException("HashicorpVaultSecretsProvider: initialization failed: " + e.getMessage(), e);
        }
    }

    private static int parseKvVersion(String raw) {
        try {
            int v = Integer.parseInt(raw.trim());
            if (v == 1 || v == 2) return v;
        } catch (NumberFormatException ignored) { }
        Debug.logWarning("Invalid vault.kv.version '" + raw + "', defaulting to 2", MODULE);
        return 2;
    }

    private static long readTtlMs() {
        String raw = prop("vault.cache.ttl.seconds", "3600");
        try {
            return Long.parseLong(raw.trim()) * 1000L;
        } catch (NumberFormatException e) {
            Debug.logWarning("Invalid vault.cache.ttl.seconds '" + raw + "', defaulting to 3600s", MODULE);
            return 3_600_000L;
        }
    }

    private static String prop(String key, String defaultValue) {
        return UtilProperties.getPropertyValue(CONFIG_RESOURCE, key, defaultValue);
    }
}
