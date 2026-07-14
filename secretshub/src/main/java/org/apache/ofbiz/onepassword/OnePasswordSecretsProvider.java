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
package org.apache.ofbiz.onepassword;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.ofbiz.base.crypto.ConfigCryptoUtil;
import org.apache.ofbiz.base.lang.ThreadSafe;
import org.apache.ofbiz.base.secret.SecretProvider;
import org.apache.ofbiz.base.secret.SecretProviderUtil;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilProperties;

/**
 * {@link SecretProvider} implementation backed by a 1Password Connect Server.
 *
 * <p>Looks up items in a designated 1Password vault by matching the item title
 * to the OFBiz secret key (with an optional prefix). The value of the configured
 * field (default {@code "password"}) is returned as the secret.</p>
 *
 * <p>Authentication uses a static Connect Server access token configured in
 * {@code onepassword.connect.token}. Inject this token at deploy time — do not commit it.</p>
 *
 * <p>API flow per lookup:</p>
 * <ol>
 *   <li>{@code GET /v1/vaults/{vaultId}/items?filter=title eq "{title}"} — find the item UUID</li>
 *   <li>{@code GET /v1/vaults/{vaultId}/items/{itemId}} — fetch the full item with field values</li>
 *   <li>Scan {@code fields[]} for the entry whose {@code label} matches {@code onepassword.field}</li>
 * </ol>
 *
 * <h2>Per-key naming overrides</h2>
 * <p>Items are looked up by matching the OFBiz key (with prefix) against the item's
 * <em>title</em> in 1Password. If a specific key needs a different title, set
 * {@code key.alias.<logicalKey>=<itemTitle>}. The alias is checked first; keys with no
 * alias entry use the logical key unchanged.</p>
 *
 * <p>Configure via {@code plugins/onepassword-secrets-provider/config/onepassword.properties}.</p>
 */
@ThreadSafe
public final class OnePasswordSecretsProvider implements SecretProvider {

    private static final String MODULE = OnePasswordSecretsProvider.class.getName();
    private static final String CONFIG_RESOURCE = "onepassword";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final OnePasswordHttpClient httpClient;
    private final String connectUrl;
    private final String token;
    private final String vaultId;
    private final String field;
    private final String secretNamePrefix;
    private final long cacheTtlMs;
    private final Map<String, String> keyAliases;

    private final SecretProviderUtil.Cache<String, String> cache = new SecretProviderUtil.Cache<>();

    /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
    public OnePasswordSecretsProvider() {
        this(buildHttpClient(),
                prop("onepassword.connect.url", "http://localhost:8080").replaceAll("/+$", ""),
                prop("onepassword.connect.token", ""),
                prop("onepassword.vault.id", ""),
                prop("onepassword.field", "password"),
                prop("onepassword.secret.name.prefix", ""),
                SecretProviderUtil.readTtlMs(CONFIG_RESOURCE, "onepassword.cache.ttl.seconds", 3600, MODULE),
                SecretProviderUtil.loadKeyAliases(CONFIG_RESOURCE));
    }

    /** Package-private constructor used by unit tests to inject a mock HTTP client. */
    OnePasswordSecretsProvider(OnePasswordHttpClient httpClient, String connectUrl, String token,
            String vaultId, String field, String secretNamePrefix, long cacheTtlMs) {
        this(httpClient, connectUrl, token, vaultId, field, secretNamePrefix, cacheTtlMs, Map.of());
    }

    /** Package-private constructor used by unit tests to inject a mock HTTP client and key aliases. */
    OnePasswordSecretsProvider(OnePasswordHttpClient httpClient, String connectUrl, String token,
            String vaultId, String field, String secretNamePrefix, long cacheTtlMs,
            Map<String, String> keyAliases) {
        this.httpClient = httpClient;
        this.connectUrl = connectUrl;
        this.token = token;
        this.vaultId = vaultId;
        this.field = field;
        this.secretNamePrefix = secretNamePrefix;
        this.cacheTtlMs = cacheTtlMs;
        this.keyAliases = keyAliases;
    }

    @Override
    public String getSecret(String key) throws GeneralException {
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        String title = secretNamePrefix + keyAliases.getOrDefault(key, key);
        String value = fetchFromConnect(title);
        value = ConfigCryptoUtil.decryptIfEncrypted(value, key);

        cache.put(key, value, cacheTtlMs);
        return value;
    }

    /**
     * Clears the in-memory cache, forcing the next {@link #getSecret(String)} call
     * to re-fetch from the Connect Server. Useful after a secret update in 1Password.
     */
    @Override
    public void invalidateCache() {
        cache.clear();
        Debug.logInfo("OnePasswordSecretsProvider: secret cache invalidated", MODULE);
    }

    @Override
    public boolean isFallbackEnabled() {
        return Boolean.parseBoolean(prop("onepassword.fallback.enabled", "true"));
    }

    @Override
    public void close() {
        httpClient.close();
    }

    // -- private helpers --

    private String fetchFromConnect(String title) throws GeneralException {
        String itemId = findItemId(title);
        return fetchFieldValue(itemId, title);
    }

    private String findItemId(String title) throws GeneralException {
        String filter = "title eq \"" + title + "\"";
        String encodedFilter;
        try {
            encodedFilter = URLEncoder.encode(filter, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new GeneralException("Failed to encode filter for title '" + title + "'", e);
        }

        String searchUrl = connectUrl + "/v1/vaults/" + vaultId + "/items?filter=" + encodedFilter;
        String responseBody;
        try {
            responseBody = httpClient.get(searchUrl, token);
        } catch (IOException e) {
            throw new GeneralException("1Password Connect request failed: " + e.getMessage(), e);
        }

        try {
            JsonNode items = JSON.readTree(responseBody);
            if (!items.isArray() || items.size() == 0) {
                throw new GeneralException("No 1Password item found with title '" + title + "'");
            }
            JsonNode idNode = items.get(0).get("id");
            if (idNode == null || idNode.isNull()) {
                throw new GeneralException("1Password item for '" + title + "' has no id field");
            }
            return idNode.asText();
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralException("Failed to parse 1Password item search response: " + e.getMessage(), e);
        }
    }

    private String fetchFieldValue(String itemId, String title) throws GeneralException {
        String itemUrl = connectUrl + "/v1/vaults/" + vaultId + "/items/" + itemId;
        String responseBody;
        try {
            responseBody = httpClient.get(itemUrl, token);
        } catch (IOException e) {
            throw new GeneralException("1Password Connect request failed for item '" + itemId + "': " + e.getMessage(), e);
        }

        try {
            JsonNode item = JSON.readTree(responseBody);
            JsonNode fields = item.get("fields");
            if (fields == null || !fields.isArray()) {
                throw new GeneralException("1Password item '" + title + "' has no fields array");
            }

            for (JsonNode f : fields) {
                JsonNode labelNode = f.get("label");
                if (labelNode != null && field.equalsIgnoreCase(labelNode.asText())) {
                    JsonNode valueNode = f.get("value");
                    if (valueNode == null || valueNode.isNull()) {
                        throw new GeneralException(
                                "Field '" + field + "' in 1Password item '" + title + "' has a null value");
                    }
                    String value = valueNode.asText();
                    if (value.isEmpty()) {
                        throw new GeneralException(
                                "Field '" + field + "' in 1Password item '" + title + "' is empty");
                    }
                    return value;
                }
            }
            throw new GeneralException(
                    "Field '" + field + "' not found in 1Password item '" + title + "'");
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralException("Failed to parse 1Password item response: " + e.getMessage(), e);
        }
    }

    private static OnePasswordHttpClient buildHttpClient() {
        int connectTimeout = parseSeconds(prop("onepassword.connect.timeout.seconds", "5"));
        int readTimeout = parseSeconds(prop("onepassword.read.timeout.seconds", "10"));

        HttpClient javaClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeout))
                .build();

        Debug.logInfo("OnePasswordSecretsProvider: initialized connect-url=" + prop("onepassword.connect.url", ""),
                MODULE);

        return new OnePasswordHttpClient() {
            @Override
            public String get(String url, String bearerToken) throws IOException {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + bearerToken)
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(readTimeout))
                        .GET()
                        .build();

                HttpResponse<String> response;
                try {
                    response = javaClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("HTTP request interrupted", e);
                }

                int status = response.statusCode();
                if (status < 200 || status >= 300) {
                    throw new IOException("1Password Connect returned HTTP " + status + " for " + url);
                }
                return response.body();
            }

            @Override
            public void close() {
                if (javaClient instanceof AutoCloseable) {
                    try {
                        ((AutoCloseable) javaClient).close();
                    } catch (Exception ignored) { }
                }
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

    private static String prop(String key, String defaultValue) {
        return UtilProperties.getPropertyValue(CONFIG_RESOURCE, key, defaultValue);
    }

}
