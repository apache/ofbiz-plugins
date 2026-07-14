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

import java.util.Collections;
import java.util.Map;

import io.github.jopenlibs.vault.SslConfig;
import io.github.jopenlibs.vault.Vault;
import io.github.jopenlibs.vault.VaultConfig;
import io.github.jopenlibs.vault.VaultException;

import org.apache.ofbiz.base.crypto.ConfigCryptoUtil;

import org.apache.ofbiz.base.lang.ThreadSafe;
import org.apache.ofbiz.base.secret.SecretProvider;
import org.apache.ofbiz.base.secret.SecretProviderUtil;
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
 * <p>Both KV v1 and KV v2 engines are supported via the {@code hashicorp.vault.kv.version}
 * property. The driver handles the {@code /data/} path rewrite for KV v2
 * automatically when {@code engineVersion(2)} is configured.</p>
 *
 * <p>Resolved secret values are cached in memory for the TTL configured by
 * {@code hashicorp.vault.cache.ttl.seconds} (default 1 hour).</p>
 *
 * <h2>Per-key naming overrides</h2>
 * <p>Vault KV paths generally accept the dot-separated OFBiz key verbatim, but if a specific
 * mount or policy requires a different path segment, set
 * {@code key.alias.<logicalKey>=<vaultPathSegment>} to override that one key. The alias is
 * checked first; keys with no alias entry use the logical key unchanged.</p>
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
    private final Map<String, String> keyAliases;

    private final SecretProviderUtil.Cache<String, String> cache = new SecretProviderUtil.Cache<>();

    /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
    public HashicorpVaultSecretsProvider() {
        this(readerFrom(buildVault()),
                prop("hashicorp.vault.kv.mount", "secret"),
                prop("hashicorp.vault.secret.name.prefix", ""),
                prop("hashicorp.vault.field", "password"),
                SecretProviderUtil.readTtlMs(CONFIG_RESOURCE, "hashicorp.vault.cache.ttl.seconds", 3600, MODULE),
                SecretProviderUtil.loadKeyAliases(CONFIG_RESOURCE));
    }

    /** Package-private constructor used by unit tests to inject a {@link HashicorpVaultReader} lambda. */
    HashicorpVaultSecretsProvider(HashicorpVaultReader vaultReader, String kvMount, String secretNamePrefix,
            String field, long cacheTtlMs) {
        this(vaultReader, kvMount, secretNamePrefix, field, cacheTtlMs, Map.of());
    }

    /** Package-private constructor used by unit tests to inject a vault reader and key aliases. */
    HashicorpVaultSecretsProvider(HashicorpVaultReader vaultReader, String kvMount, String secretNamePrefix,
            String field, long cacheTtlMs, Map<String, String> keyAliases) {
        this.vaultReader = vaultReader;
        this.kvMount = kvMount;
        this.secretNamePrefix = secretNamePrefix;
        this.field = field;
        this.cacheTtlMs = cacheTtlMs;
        this.keyAliases = keyAliases;
    }

    @Override
    public String getSecret(String key) throws GeneralException {
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        String physicalKey = keyAliases.getOrDefault(key, key);
        String path = kvMount + "/" + secretNamePrefix + physicalKey;
        String value = readFromVault(path);
        value = ConfigCryptoUtil.decryptIfEncrypted(value, key);

        cache.put(key, value, cacheTtlMs);
        return value;
    }

    /**
     * Clears the in-memory cache, forcing the next {@link #getSecret(String)} call
     * for each key to re-fetch from Vault. Useful after a secret rotation.
     */
    @Override
    public void invalidateCache() {
        cache.clear();
        Debug.logInfo("HashicorpVaultSecretsProvider: secret cache invalidated", MODULE);
    }

    @Override
    public boolean isFallbackEnabled() {
        return Boolean.parseBoolean(prop("hashicorp.vault.fallback.enabled", "true"));
    }

    @Override
    public void close() {
        // The Vault Java driver does not expose a close() method on the Vault client;
        // connection resources are managed internally by the library.
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
                + " fields — set hashicorp.vault.field to select one");
    }

    private static HashicorpVaultReader readerFrom(Vault vault) {
        return path -> {
            Map<String, String> data = vault.logical().read(path).getData();
            return data != null ? data : Collections.emptyMap();
        };
    }

    private static Vault buildVault() {
        String address = prop("hashicorp.vault.address", "http://127.0.0.1:8200");
        String authMethod = prop("hashicorp.vault.auth.method", "token");
        int kvVersion = parseKvVersion(prop("hashicorp.vault.kv.version", "2"));
        boolean sslVerify = Boolean.parseBoolean(prop("hashicorp.vault.ssl.verify", "true"));

        try {
            SslConfig ssl = new SslConfig().verify(sslVerify).build();
            String token;

            if ("approle".equalsIgnoreCase(authMethod)) {
                String roleId = prop("hashicorp.vault.approle.role_id", "");
                String secretId = prop("hashicorp.vault.approle.secret_id", "");
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
                token = prop("hashicorp.vault.token", "");
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
        Debug.logWarning("Invalid hashicorp.vault.kv.version '" + raw + "', defaulting to 2", MODULE);
        return 2;
    }

    private static String prop(String key, String defaultValue) {
        return UtilProperties.getPropertyValue(CONFIG_RESOURCE, key, defaultValue);
    }

}
