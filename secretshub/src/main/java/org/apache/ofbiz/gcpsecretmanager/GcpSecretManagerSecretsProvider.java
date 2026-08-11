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
package org.apache.ofbiz.gcpsecretmanager;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;

import org.apache.ofbiz.base.crypto.ConfigCryptoUtil;

import org.apache.ofbiz.base.lang.ThreadSafe;
import org.apache.ofbiz.base.secret.SecretProvider;
import org.apache.ofbiz.base.secret.SecretProviderUtil;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilProperties;

/**
 * {@link SecretProvider} implementation backed by Google Cloud Secret Manager.
 *
 * <h2>Authentication</h2>
 * <p>Two modes are supported via {@code gcp.credentials.file}:</p>
 * <ul>
 *   <li><strong>Application Default Credentials (ADC)</strong> — leave the property empty.
 *       GCP automatically provides credentials when running on GKE, GCE, Cloud Run, or
 *       after {@code gcloud auth application-default login} locally.</li>
 *   <li><strong>Service Account key file</strong> — set {@code gcp.credentials.file} to the
 *       path of a downloaded JSON key file. Suitable for on-premise deployments.</li>
 * </ul>
 *
 * <h2>Secret name mapping</h2>
 * <p>GCP secret names may only contain letters, digits, hyphens and underscores.
 * OFBiz keys contain dots (e.g. {@code jdbc-password.mysql-ofbiz}), which are invalid.
 * Set {@code gcp.secret.name.dot.replacement=-} (the default) to replace dots with
 * hyphens, so the key maps to {@code jdbc-password-mysql-ofbiz} in the vault.</p>
 *
 * <h2>Secret resource name</h2>
 * <p>Built as:
 * {@code projects/{projectId}/secrets/{prefix}{sanitizedKey}/versions/{version}}</p>
 *
 * <h2>Per-key naming overrides</h2>
 * <p>If even the dot-replacement convention above doesn't produce an acceptable name for a
 * specific key, set {@code key.alias.<logicalKey>=<vaultSecretName>} to store that one key
 * under an explicit name instead. The alias is checked first; only keys with no alias entry
 * fall through to the dot-replacement convention.</p>
 *
 * <p>Configure via {@code plugins/gcp-secretmanager-secrets-provider/config/gcp-secret-manager.properties}.</p>
 */
@ThreadSafe
public final class GcpSecretManagerSecretsProvider implements SecretProvider {

    private static final String MODULE = GcpSecretManagerSecretsProvider.class.getName();
    private static final String CONFIG_RESOURCE = "gcp-secret-manager";

    private final GcpSecretReader secretReader;
    private final SecretManagerServiceClient gcpClient; // null when injected via test constructor
    private final String projectId;
    private final String secretNamePrefix;
    private final String version;
    private final String dotReplacement;
    private final long cacheTtlMs;
    private final Map<String, String> keyAliases;

    private final SecretProviderUtil.Cache<String, String> cache = new SecretProviderUtil.Cache<>();

    /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
    public GcpSecretManagerSecretsProvider() throws GeneralException {
        SecretManagerServiceClient client = buildClient();
        this.secretReader = readerFrom(client);
        this.gcpClient = client;
        this.projectId = prop("gcp.project.id", "");
        this.secretNamePrefix = prop("gcp.secret.name.prefix", "");
        this.version = prop("gcp.secret.version", "latest");
        this.dotReplacement = prop("gcp.secret.name.dot.replacement", "-");
        this.cacheTtlMs = SecretProviderUtil.readTtlMs(CONFIG_RESOURCE, "gcp.cache.ttl.seconds", 3600, MODULE);
        this.keyAliases = SecretProviderUtil.loadKeyAliases(CONFIG_RESOURCE);
    }

    /** Package-private constructor used by unit tests to inject a {@link GcpSecretReader} lambda. */
    GcpSecretManagerSecretsProvider(GcpSecretReader secretReader, String projectId,
            String secretNamePrefix, String version, String dotReplacement, long cacheTtlMs) {
        this(secretReader, projectId, secretNamePrefix, version, dotReplacement, cacheTtlMs, Map.of());
    }

    /** Package-private constructor used by unit tests to inject a reader and key aliases. */
    GcpSecretManagerSecretsProvider(GcpSecretReader secretReader, String projectId,
            String secretNamePrefix, String version, String dotReplacement, long cacheTtlMs,
            Map<String, String> keyAliases) {
        this.secretReader = secretReader;
        this.gcpClient = null;
        this.projectId = projectId;
        this.secretNamePrefix = secretNamePrefix;
        this.version = version;
        this.dotReplacement = dotReplacement;
        this.cacheTtlMs = cacheTtlMs;
        this.keyAliases = keyAliases;
    }

    @Override
    public String getSecret(String key) throws GeneralException {
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        String physicalKey = keyAliases.get(key);
        String sanitizedKey = physicalKey != null ? physicalKey
                : dotReplacement.isEmpty() ? key : key.replace(".", dotReplacement);
        String secretName = secretNamePrefix + sanitizedKey;
        String resourceName = "projects/" + projectId + "/secrets/" + secretName + "/versions/" + version;

        String value;
        try {
            value = secretReader.read(resourceName);
        } catch (Exception e) {
            throw new GeneralException(
                    "GCP Secret Manager read failed for '" + resourceName + "': " + e.getMessage(), e);
        }

        if (value == null || value.isEmpty()) {
            throw new GeneralException(
                    "GCP Secret Manager returned empty value for '" + resourceName + "'");
        }

        value = ConfigCryptoUtil.decryptIfEncrypted(value, key);

        cache.put(key, value, cacheTtlMs);
        return value;
    }

    /**
     * Clears the in-memory cache, forcing the next {@link #getSecret(String)} call
     * to re-fetch from GCP. Useful after a secret rotation.
     */
    @Override
    public void invalidateCache() {
        cache.clear();
        Debug.logInfo("GcpSecretManagerSecretsProvider: secret cache invalidated", MODULE);
    }

    /** Closes the underlying {@link SecretManagerServiceClient} and releases its gRPC channel. */
    @Override
    public void close() {
        if (gcpClient != null) {
            gcpClient.close();
            Debug.logInfo("GcpSecretManagerSecretsProvider: SecretManagerServiceClient closed", MODULE);
        }
    }

    @Override
    public boolean isFallbackEnabled() {
        return Boolean.parseBoolean(prop("gcp.fallback.enabled", "true"));
    }

    // -- private helpers --

    private static GcpSecretReader readerFrom(SecretManagerServiceClient client) {
        return resourceName -> {
            try {
                return client.accessSecretVersion(resourceName)
                        .getPayload()
                        .getData()
                        .toStringUtf8();
            } catch (Exception e) {
                throw new IOException(e.getMessage(), e);
            }
        };
    }

    private static SecretManagerServiceClient buildClient() throws GeneralException {
        String credentialsFile = prop("gcp.credentials.file", "");
        try {
            // HTTP/JSON transport avoids gRPC/Netty entirely — no --add-opens JVM flags needed on Java 17
            SecretManagerServiceSettings.Builder builder = SecretManagerServiceSettings.newHttpJsonBuilder();

            if (!credentialsFile.isEmpty()) {
                try (FileInputStream fis = new FileInputStream(credentialsFile)) {
                    GoogleCredentials credentials = GoogleCredentials
                            .fromStream(fis)
                            .createScoped("https://www.googleapis.com/auth/cloud-platform");
                    builder.setCredentialsProvider(FixedCredentialsProvider.create(credentials));
                }
            }
            // else: Application Default Credentials are used automatically

            Debug.logInfo("GcpSecretManagerSecretsProvider: initialized project="
                    + prop("gcp.project.id", "") + " version=" + prop("gcp.secret.version", "latest"), MODULE);
            return SecretManagerServiceClient.create(builder.build());

        } catch (IOException e) {
            throw new GeneralException(
                    "GcpSecretManagerSecretsProvider: initialization failed: " + e.getMessage(), e);
        }
    }

    private static String prop(String key, String defaultValue) {
        return UtilProperties.getPropertyValue(CONFIG_RESOURCE, key, defaultValue);
    }

}
