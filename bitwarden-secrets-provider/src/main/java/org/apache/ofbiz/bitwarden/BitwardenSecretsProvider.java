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
package org.apache.ofbiz.bitwarden;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.ofbiz.base.crypto.ConfigCryptoUtil;
import org.apache.ofbiz.base.lang.ThreadSafe;
import org.apache.ofbiz.base.secret.SecretProvider;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilProperties;

/**
 * {@link SecretProvider} implementation backed by Bitwarden Secrets Manager.
 *
 * <h2>Authentication</h2>
 * <p>Uses a machine account <em>access token</em> (created in the Bitwarden SM console).
 * The token has the format {@code 0.<serviceAccountId>.<clientSecret>:<base64EncKey>}.
 * The provider parses the token to extract OAuth credentials for the identity service
 * and the 64-byte symmetric key for client-side decryption.</p>
 *
 * <h2>End-to-end encryption</h2>
 * <p>Bitwarden SM encrypts all data at rest and in transit. Secret <em>keys</em> (names)
 * and <em>values</em> are returned from the API in encrypted form. This provider decrypts
 * them using AES-256-CBC with HMAC-SHA256 integrity verification before returning the
 * plaintext to OFBiz. No plaintext ever leaves the JVM.</p>
 *
 * <h2>Cipher format</h2>
 * <p>Encrypted strings use Bitwarden's type-2 cipher format:
 * {@code 2.<iv_base64>|<ciphertext_base64>|<hmac_base64>}</p>
 * <ul>
 *   <li>MAC key (bytes 32–63 of the 64-byte symmetric key) verifies integrity via
 *       HMAC-SHA256 over {@code IV || ciphertext}.</li>
 *   <li>Enc key (bytes 0–31) decrypts with AES-256-CBC after MAC is verified.</li>
 * </ul>
 *
 * <h2>API flow per lookup</h2>
 * <ol>
 *   <li>POST to identity service to exchange client credentials for a bearer token.</li>
 *   <li>GET the organization's secret list; decrypt each secret key to find the match.</li>
 *   <li>GET the matched secret by ID; decrypt and return the value.</li>
 * </ol>
 *
 * <p>Configure via {@code plugins/bitwarden-secrets-provider/config/bitwarden-secrets.properties}.</p>
 */
@ThreadSafe
public final class BitwardenSecretsProvider implements SecretProvider {

    private static final String MODULE = BitwardenSecretsProvider.class.getName();
    private static final String CONFIG_RESOURCE = "bitwarden-secrets";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final BitwardenHttpClient httpClient;
    private final String apiUrl;
    private final String identityUrl;
    private final String organizationId;
    private final String secretNamePrefix;
    private final long cacheTtlMs;

    // Parsed from the access token — never stored in config
    private final String oauthClientId;
    private final String oauthClientSecret;
    private final byte[] encKey; // bytes  0-31 of the 64-byte symmetric key
    private final byte[] macKey; // bytes 32-63

    // Cached OAuth bearer token + its expiry
    private volatile String bearerToken = null;
    private volatile long bearerTokenExpiresAt = 0L;

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
    public BitwardenSecretsProvider() throws GeneralException {
        this(buildHttpClient(),
                prop("bitwarden.api.url", "https://api.bitwarden.com").replaceAll("/+$", ""),
                prop("bitwarden.identity.url", "https://identity.bitwarden.com").replaceAll("/+$", ""),
                prop("bitwarden.organization.id", ""),
                prop("bitwarden.secret.name.prefix", ""),
                readTtlMs(),
                prop("bitwarden.access.token", ""));
    }

    /**
     * Package-private constructor used by unit tests to inject mock HTTP client and
     * pre-parsed key material.
     */
    BitwardenSecretsProvider(BitwardenHttpClient httpClient, String apiUrl, String identityUrl,
            String organizationId, String secretNamePrefix, long cacheTtlMs,
            String clientId, String clientSecret, byte[] encKey, byte[] macKey) {
        this.httpClient = httpClient;
        this.apiUrl = apiUrl;
        this.identityUrl = identityUrl;
        this.organizationId = organizationId;
        this.secretNamePrefix = secretNamePrefix;
        this.cacheTtlMs = cacheTtlMs;
        this.oauthClientId = clientId;
        this.oauthClientSecret = clientSecret;
        this.encKey = encKey;
        this.macKey = macKey;
    }

    /** Full constructor called by the public no-arg constructor after parsing the token. */
    private BitwardenSecretsProvider(BitwardenHttpClient httpClient, String apiUrl, String identityUrl,
            String organizationId, String secretNamePrefix, long cacheTtlMs,
            String rawAccessToken) throws GeneralException {
        this.httpClient = httpClient;
        this.apiUrl = apiUrl;
        this.identityUrl = identityUrl;
        this.organizationId = organizationId;
        this.secretNamePrefix = secretNamePrefix;
        this.cacheTtlMs = cacheTtlMs;

        // Parse: "0.<serviceAccountId>.<clientSecret>:<base64EncKey>"
        int colonIdx = rawAccessToken.lastIndexOf(':');
        if (colonIdx < 0) {
            throw new GeneralException(
                    "Invalid Bitwarden access token format — missing ':' separator");
        }
        String identityPart = rawAccessToken.substring(0, colonIdx);
        String encKeyBase64 = rawAccessToken.substring(colonIdx + 1);

        String[] dotParts = identityPart.split("\\.");
        if (dotParts.length != 3 || !"0".equals(dotParts[0])) {
            throw new GeneralException(
                    "Invalid Bitwarden access token format — expected '0.<id>.<secret>:<key>'");
        }
        String serviceAccountId = dotParts[1];
        String clientSecretPart = dotParts[2];

        this.oauthClientId = "service-account." + serviceAccountId;
        this.oauthClientSecret = clientSecretPart;

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encKeyBase64);
        } catch (IllegalArgumentException e) {
            throw new GeneralException("Invalid Bitwarden access token — enc key is not valid base64", e);
        }
        if (keyBytes.length != 64) {
            throw new GeneralException("Invalid Bitwarden access token — enc key must be 64 bytes, got "
                    + keyBytes.length);
        }
        this.encKey = Arrays.copyOfRange(keyBytes, 0, 32);
        this.macKey = Arrays.copyOfRange(keyBytes, 32, 64);

        Debug.logInfo("BitwardenSecretsProvider: initialized api=" + apiUrl
                + " org=" + organizationId, MODULE);
    }

    @Override
    public String getSecret(String key) throws GeneralException {
        CacheEntry cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.value;
        }

        String secretName = secretNamePrefix + key;
        String value = fetchFromBitwarden(secretName);
        value = ConfigCryptoUtil.decryptIfEncrypted(value, key);

        cache.put(key, new CacheEntry(value, cacheTtlMs));
        return value;
    }

    /**
     * Clears the in-memory cache, forcing the next {@link #getSecret(String)} call
     * to re-fetch and re-decrypt from Bitwarden SM.
     */
    public void invalidateCache() {
        cache.clear();
        Debug.logInfo("BitwardenSecretsProvider: secret cache invalidated", MODULE);
    }

    // -- private helpers --

    private String fetchFromBitwarden(String secretName) throws GeneralException {
        String bearer = ensureBearerToken();
        String secretId = findSecretId(secretName, bearer);
        return fetchAndDecryptValue(secretId, secretName, bearer);
    }

    /** Returns a valid OAuth bearer token, refreshing it if expired. */
    private synchronized String ensureBearerToken() throws GeneralException {
        if (bearerToken != null && System.currentTimeMillis() < bearerTokenExpiresAt) {
            return bearerToken;
        }

        String body = "grant_type=client_credentials"
                + "&scope=api.secrets"
                + "&client_id=" + oauthClientId
                + "&client_secret=" + oauthClientSecret;

        String responseJson;
        try {
            responseJson = httpClient.post(identityUrl + "/connect/token", body);
        } catch (IOException e) {
            throw new GeneralException("Bitwarden identity token request failed: " + e.getMessage(), e);
        }

        try {
            JsonNode root = JSON.readTree(responseJson);
            JsonNode tokenNode = root.get("access_token");
            JsonNode expiresNode = root.get("expires_in");
            if (tokenNode == null || tokenNode.isNull()) {
                throw new GeneralException("Bitwarden identity response missing 'access_token'");
            }
            bearerToken = tokenNode.asText();
            long expiresIn = expiresNode != null ? expiresNode.asLong(3600L) : 3600L;
            // Subtract 60 s to renew slightly before expiry
            bearerTokenExpiresAt = System.currentTimeMillis() + (expiresIn - 60L) * 1000L;
            return bearerToken;
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralException("Failed to parse Bitwarden identity response: " + e.getMessage(), e);
        }
    }

    /** Lists the organization's secrets and returns the ID of the one matching secretName. */
    private String findSecretId(String secretName, String bearer) throws GeneralException {
        String listUrl = apiUrl + "/organizations/" + organizationId + "/secrets";
        String responseJson;
        try {
            responseJson = httpClient.get(listUrl, bearer);
        } catch (IOException e) {
            throw new GeneralException("Bitwarden secrets list request failed: " + e.getMessage(), e);
        }

        try {
            JsonNode root = JSON.readTree(responseJson);
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                throw new GeneralException("Bitwarden secrets list response missing 'data' array");
            }

            for (JsonNode item : data) {
                JsonNode encKey = item.get("key");
                if (encKey == null) continue;

                String decryptedKey = decrypt(encKey.asText());
                if (secretName.equals(decryptedKey)) {
                    JsonNode idNode = item.get("id");
                    if (idNode == null || idNode.isNull()) {
                        throw new GeneralException("Bitwarden secret '" + secretName + "' has no id field");
                    }
                    return idNode.asText();
                }
            }
            throw new GeneralException("No Bitwarden secret found with key '" + secretName + "'");
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralException("Failed to parse Bitwarden secrets list: " + e.getMessage(), e);
        }
    }

    /** Fetches the secret by ID and decrypts its value. */
    private String fetchAndDecryptValue(String secretId, String secretName, String bearer)
            throws GeneralException {
        String secretUrl = apiUrl + "/secrets/" + secretId;
        String responseJson;
        try {
            responseJson = httpClient.get(secretUrl, bearer);
        } catch (IOException e) {
            throw new GeneralException("Bitwarden secret fetch failed for '" + secretName
                    + "': " + e.getMessage(), e);
        }

        try {
            JsonNode root = JSON.readTree(responseJson);
            JsonNode encValueNode = root.get("value");
            if (encValueNode == null || encValueNode.isNull()) {
                throw new GeneralException("Bitwarden secret '" + secretName + "' has no value field");
            }
            String plaintext = decrypt(encValueNode.asText());
            if (plaintext.isEmpty()) {
                throw new GeneralException("Bitwarden secret '" + secretName + "' decrypted to empty string");
            }
            return plaintext;
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralException("Failed to parse Bitwarden secret response: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts a Bitwarden type-2 cipher string.
     *
     * <p>Format: {@code 2.<iv_b64>|<ciphertext_b64>|<hmac_b64>}</p>
     * <ul>
     *   <li>HMAC-SHA256 is verified over {@code IV || ciphertext} using {@code macKey}.</li>
     *   <li>AES-256-CBC decryption uses {@code encKey} and the extracted IV.</li>
     * </ul>
     */
    String decrypt(String cipherString) throws GeneralException {
        if (!cipherString.startsWith("2.")) {
            throw new GeneralException(
                    "Unsupported Bitwarden cipher type — expected type 2 (AES-CBC-256-HMAC-SHA256)");
        }
        String[] parts = cipherString.substring(2).split("\\|");
        if (parts.length != 3) {
            throw new GeneralException(
                    "Invalid Bitwarden cipher string — expected '2.<iv>|<ct>|<hmac>'");
        }

        byte[] iv, ciphertext, expectedHmac;
        try {
            iv = Base64.getDecoder().decode(parts[0]);
            ciphertext = Base64.getDecoder().decode(parts[1]);
            expectedHmac = Base64.getDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new GeneralException("Invalid base64 in Bitwarden cipher string", e);
        }

        try {
            // Verify integrity: HMAC-SHA256(iv || ciphertext, macKey)
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(macKey, "HmacSHA256"));
            mac.update(iv);
            byte[] computedHmac = mac.doFinal(ciphertext);
            if (!MessageDigest.isEqual(computedHmac, expectedHmac)) {
                throw new GeneralException(
                        "Bitwarden HMAC verification failed — wrong key or tampered ciphertext");
            }

            // Decrypt: AES-256-CBC
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(encKey, "AES"),
                    new IvParameterSpec(iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);

        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralException("Bitwarden decryption failed: " + e.getMessage(), e);
        }
    }

    private static BitwardenHttpClient buildHttpClient() {
        int connectTimeout = parseSeconds(prop("bitwarden.connect.timeout.seconds", "5"));
        int readTimeout = parseSeconds(prop("bitwarden.read.timeout.seconds", "10"));

        HttpClient javaClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeout))
                .build();

        return new BitwardenHttpClient() {
            @Override
            public String get(String url, String bearerToken) throws IOException {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + bearerToken)
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(readTimeout))
                        .GET()
                        .build();
                return send(request);
            }

            @Override
            public String post(String url, String formBody) throws IOException {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(readTimeout))
                        .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                        .build();
                return send(request);
            }

            private String send(HttpRequest request) throws IOException {
                HttpResponse<String> response;
                try {
                    response = javaClient.send(request,
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("HTTP request interrupted", e);
                }
                int status = response.statusCode();
                if (status < 200 || status >= 300) {
                    throw new IOException("Bitwarden API returned HTTP " + status
                            + " for " + request.uri());
                }
                return response.body();
            }
        };
    }

    private static int parseSeconds(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    private static long readTtlMs() {
        String raw = prop("bitwarden.cache.ttl.seconds", "3600");
        try {
            return Long.parseLong(raw.trim()) * 1000L;
        } catch (NumberFormatException e) {
            Debug.logWarning("Invalid bitwarden.cache.ttl.seconds '" + raw + "', defaulting to 3600s", MODULE);
            return 3_600_000L;
        }
    }

    private static String prop(String key, String defaultValue) {
        return UtilProperties.getPropertyValue(CONFIG_RESOURCE, key, defaultValue);
    }
}
