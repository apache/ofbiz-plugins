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
package org.apache.ofbiz.azurekeyvault;

import java.util.concurrent.ConcurrentHashMap;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;

import org.apache.ofbiz.base.lang.ThreadSafe;
import org.apache.ofbiz.base.secret.SecretProvider;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilProperties;

/**
 * {@link SecretProvider} implementation backed by Azure Key Vault.
 *
 * <h2>Authentication</h2>
 * <p>Controlled by {@code azure.auth.method}:</p>
 * <ul>
 *   <li><strong>default</strong> — Uses {@code DefaultAzureCredential}, which tries
 *       Managed Identity, environment variables, Azure CLI, and more in sequence.
 *       Recommended for Azure-hosted deployments (AKS, App Service, VMs with MI).</li>
 *   <li><strong>client_secret</strong> — Authenticates as a service principal using
 *       {@code azure.tenant.id}, {@code azure.client.id}, and {@code azure.client.secret}.
 *       Suitable for on-premise or multi-cloud deployments.</li>
 * </ul>
 *
 * <h2>Secret name mapping</h2>
 * <p>Azure Key Vault secret names may only contain letters, digits and hyphens.
 * OFBiz keys contain dots (e.g. {@code jdbc-password.mysql-ofbiz}), which are invalid.
 * Set {@code azure.secret.name.dot.replacement=-} (the default) to replace dots with
 * hyphens, so the key maps to {@code jdbc-password-mysql-ofbiz} in the vault.</p>
 *
 * <p>Configure via {@code plugins/azure-keyvault-secrets-provider/config/azure-keyvault.properties}.</p>
 */
@ThreadSafe
public final class AzureKeyVaultSecretsProvider implements SecretProvider {

    private static final String MODULE = AzureKeyVaultSecretsProvider.class.getName();
    private static final String CONFIG_RESOURCE = "azure-keyvault";

    private final AzureKeyVaultReader vaultReader;
    private final String secretNamePrefix;
    private final String dotReplacement;
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
    public AzureKeyVaultSecretsProvider() {
        this(readerFrom(buildClient()),
                prop("azure.secret.name.prefix", ""),
                prop("azure.secret.name.dot.replacement", "-"),
                readTtlMs());
    }

    /** Package-private constructor used by unit tests to inject an {@link AzureKeyVaultReader} lambda. */
    AzureKeyVaultSecretsProvider(AzureKeyVaultReader vaultReader, String secretNamePrefix,
            String dotReplacement, long cacheTtlMs) {
        this.vaultReader = vaultReader;
        this.secretNamePrefix = secretNamePrefix;
        this.dotReplacement = dotReplacement;
        this.cacheTtlMs = cacheTtlMs;
    }

    @Override
    public String getSecret(String key) throws GeneralException {
        CacheEntry cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.value;
        }

        String sanitizedKey = dotReplacement.isEmpty() ? key : key.replace(".", dotReplacement);
        String secretName = secretNamePrefix + sanitizedKey;

        String value;
        try {
            value = vaultReader.read(secretName);
        } catch (Exception e) {
            throw new GeneralException(
                    "Azure Key Vault read failed for secret '" + secretName + "': " + e.getMessage(), e);
        }

        if (value == null || value.isEmpty()) {
            throw new GeneralException(
                    "Azure Key Vault returned empty value for secret '" + secretName + "'");
        }

        cache.put(key, new CacheEntry(value, cacheTtlMs));
        return value;
    }

    /**
     * Clears the in-memory cache, forcing the next {@link #getSecret(String)} call
     * to re-fetch from Azure Key Vault. Useful after a secret rotation.
     */
    public void invalidateCache() {
        cache.clear();
        Debug.logInfo("AzureKeyVaultSecretsProvider: secret cache invalidated", MODULE);
    }

    // -- private helpers --

    private static AzureKeyVaultReader readerFrom(SecretClient client) {
        return secretName -> client.getSecret(secretName).getValue();
    }

    private static SecretClient buildClient() {
        String vaultUrl = prop("azure.keyvault.url", "");
        String authMethod = prop("azure.auth.method", "default");

        TokenCredential credential;
        if ("client_secret".equalsIgnoreCase(authMethod)) {
            credential = new ClientSecretCredentialBuilder()
                    .tenantId(prop("azure.tenant.id", ""))
                    .clientId(prop("azure.client.id", ""))
                    .clientSecret(prop("azure.client.secret", ""))
                    .build();
        } else {
            credential = new DefaultAzureCredentialBuilder().build();
        }

        Debug.logInfo("AzureKeyVaultSecretsProvider: initialized vault=" + vaultUrl
                + " auth=" + authMethod, MODULE);

        return new SecretClientBuilder()
                .vaultUrl(vaultUrl)
                .credential(credential)
                .buildClient();
    }

    private static long readTtlMs() {
        String raw = prop("azure.cache.ttl.seconds", "3600");
        try {
            return Long.parseLong(raw.trim()) * 1000L;
        } catch (NumberFormatException e) {
            Debug.logWarning("Invalid azure.cache.ttl.seconds '" + raw + "', defaulting to 3600s", MODULE);
            return 3_600_000L;
        }
    }

    private static String prop(String key, String defaultValue) {
        return UtilProperties.getPropertyValue(CONFIG_RESOURCE, key, defaultValue);
    }
}
