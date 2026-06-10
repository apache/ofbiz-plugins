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
package org.apache.ofbiz.awssecrets;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.ofbiz.base.crypto.ConfigCryptoUtil;
import org.apache.ofbiz.base.lang.ThreadSafe;
import org.apache.ofbiz.base.secret.SecretProvider;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilProperties;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

/**
 * {@link SecretProvider} implementation backed by AWS Secrets Manager.
 *
 * <p>Authentication is handled by the AWS Default Credential Provider Chain —
 * no credentials are stored in this file or in properties files. The preferred
 * approach for EC2/ECS/EKS deployments is an IAM instance role or task role,
 * which requires zero credential configuration on the server.</p>
 *
 * <p>Resolved secret values are cached in memory for the duration configured by
 * {@code aws.secretsmanager.cache.ttl.seconds} (default 1 hour) to avoid
 * per-request API calls. Call {@link #invalidateCache()} to force an immediate
 * re-fetch, for example after a manual secret rotation.</p>
 *
 * <p>Configure via {@code plugins/aws-secrets-provider/config/aws-secrets-manager.properties}.</p>
 */
@ThreadSafe
public final class AwsSecretsManagerProvider implements SecretProvider {

    private static final String MODULE = AwsSecretsManagerProvider.class.getName();
    private static final String CONFIG_RESOURCE = "aws-secrets-manager";

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final SecretsManagerClient client;
    private final long cacheTtlMs;
    private final String secretNamePrefix;
    private final String jsonField;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private static final class CacheEntry {
        private final String value;
        private final long expiresAt;

        CacheEntry(String value, long ttlMs) {
            this.value = value;
            this.expiresAt = System.currentTimeMillis() + ttlMs;
        }

        String getValue() {
            return value;
        }

        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAt;
        }
    }

    /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
    public AwsSecretsManagerProvider() {
        this(buildClient(), readTtlMs(),
                prop("aws.secretsmanager.secret.name.prefix", ""),
                prop("aws.secretsmanager.json.field", ""));
    }

    /** Package-private constructor used by unit tests to inject a mock client. */
    AwsSecretsManagerProvider(SecretsManagerClient client, long cacheTtlMs,
            String secretNamePrefix, String jsonField) {
        this.client = client;
        this.cacheTtlMs = cacheTtlMs;
        this.secretNamePrefix = secretNamePrefix;
        this.jsonField = jsonField;
    }

    @Override
    public String getSecret(String key) throws GeneralException {
        CacheEntry cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.getValue();
        }

        String secretName = secretNamePrefix + key;
        String secretValue = fetchFromAws(secretName);

        if (!jsonField.isEmpty()) {
            secretValue = extractJsonField(secretValue, secretName);
        }

        if (secretValue == null || secretValue.isEmpty()) {
            throw new GeneralException("Secret '" + secretName + "' resolved to an empty value");
        }

        secretValue = ConfigCryptoUtil.decryptIfEncrypted(secretValue, secretName);

        cache.put(key, new CacheEntry(secretValue, cacheTtlMs));
        return secretValue;
    }

    /**
     * Clears the in-memory cache, forcing the next {@link #getSecret(String)} call
     * for each key to re-fetch from AWS Secrets Manager. Useful after a manual
     * secret rotation to pick up the new value without restarting OFBiz.
     */
    public void invalidateCache() {
        cache.clear();
        Debug.logInfo("AwsSecretsManagerProvider: secret cache invalidated", MODULE);
    }

    @Override
    public boolean isFallbackEnabled() {
        return Boolean.parseBoolean(prop("aws.secretsmanager.fallback.enabled", "true"));
    }

    // -- private helpers --

    private static String prop(String key, String defaultValue) {
        return UtilProperties.getPropertyValue(CONFIG_RESOURCE, key, defaultValue);
    }

    private String fetchFromAws(String secretName) throws GeneralException {
        try {
            GetSecretValueResponse response = client.getSecretValue(
                    GetSecretValueRequest.builder().secretId(secretName).build());
            String value = response.secretString();
            if (value == null) {
                throw new GeneralException("Secret '" + secretName
                        + "' contains binary data; only string secrets are supported");
            }
            return value;
        } catch (ResourceNotFoundException e) {
            throw new GeneralException("Secret '" + secretName + "' not found in AWS Secrets Manager", e);
        } catch (SdkException e) {
            throw new GeneralException("AWS Secrets Manager error for '" + secretName + "': " + e.getMessage(), e);
        }
    }

    /**
     * Extracts a single string field from a flat JSON secret value.
     * AWS typically stores database credentials as {"username":"u","password":"p"}.
     */
    private String extractJsonField(String json, String secretName) throws GeneralException {
        try {
            JsonNode root = JSON_MAPPER.readTree(json);
            JsonNode node = root.get(jsonField);
            if (node == null || node.isNull()) {
                throw new GeneralException(
                        "JSON field '" + jsonField + "' not found in secret '" + secretName + "'");
            }
            return node.asText();
        } catch (JsonProcessingException e) {
            throw new GeneralException("Failed to parse JSON for secret '" + secretName + "': " + e.getMessage(), e);
        }
    }

    private static SecretsManagerClient buildClient() {
        String region = prop("aws.secretsmanager.region", "");
        String endpointOverride = prop("aws.secretsmanager.endpoint.override", "");
        String accessKeyId = prop("aws.secretsmanager.access.key.id", "");
        String secretAccessKey = prop("aws.secretsmanager.secret.access.key", "");

        SecretsManagerClientBuilder builder = SecretsManagerClient.builder()
                .httpClient(UrlConnectionHttpClient.builder().build());

        if (!region.isEmpty()) {
            builder.region(Region.of(region));
        }
        if (!endpointOverride.isEmpty()) {
            builder.endpointOverride(URI.create(endpointOverride));
        }
        if (!accessKeyId.isEmpty() && !secretAccessKey.isEmpty()) {
            try {
                secretAccessKey = ConfigCryptoUtil.decryptIfEncrypted(
                        secretAccessKey, "aws.secretsmanager.secret.access.key");
            } catch (GeneralException e) {
                throw new IllegalStateException(
                        "Failed to decrypt aws.secretsmanager.secret.access.key: " + e.getMessage(), e);
            }
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey)));
            Debug.logInfo("AwsSecretsManagerProvider: using static credentials from "
                    + "aws-secrets-manager.properties", MODULE);
        }

        Debug.logInfo("AwsSecretsManagerProvider: initialized"
                + (region.isEmpty() ? " (region from environment)" : " region=" + region), MODULE);
        return builder.build();
    }

    private static long readTtlMs() {
        String raw = prop("aws.secretsmanager.cache.ttl.seconds", "3600");
        try {
            return Long.parseLong(raw.trim()) * 1000L;
        } catch (NumberFormatException e) {
            Debug.logWarning("Invalid aws.secretsmanager.cache.ttl.seconds '" + raw
                    + "', defaulting to 3600s", MODULE);
            return 3_600_000L;
        }
    }
}
