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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.ofbiz.base.util.GeneralException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class OnePasswordSecretsProviderTest {

    private static final long ONE_HOUR_MS = 3_600_000L;
    private static final String BASE_URL = "http://localhost:8080";
    private static final String TOKEN = "test-token";
    private static final String VAULT_ID = "vault-abc";
    private static final String ITEM_ID = "item-1";

    // -- Happy path --

    @Test
    public void getSecretReturnsFieldValue() throws Exception {
        OnePasswordHttpClient client = buildMockClient("jdbc-password.mydb", ITEM_ID, "s3cr3t");
        assertEquals("s3cr3t", provider(client, "password", "").getSecret("jdbc-password.mydb"));
    }

    @Test
    public void getSecretAppliesSecretNamePrefix() throws Exception {
        OnePasswordHttpClient client = buildMockClient("prod/jdbc-password.mydb", ITEM_ID, "dbpass");
        assertEquals("dbpass", provider(client, "password", "prod/").getSecret("jdbc-password.mydb"));
    }

    @Test
    public void getSecretCachePreventsSecondHttpCall() throws Exception {
        OnePasswordHttpClient client = buildMockClient("mykey", ITEM_ID, "val");
        OnePasswordSecretsProvider p = provider(client, "password", "");

        p.getSecret("mykey");
        p.getSecret("mykey"); // cache hit — no HTTP calls

        // First getSecret = 2 calls (1 search + 1 fetch); second = 0 (cache)
        verify(client, times(2)).get(anyString(), anyString());
    }

    @Test
    public void invalidateCacheForcesRefetch() throws Exception {
        OnePasswordHttpClient client = buildMockClient("mykey", ITEM_ID, "val");
        OnePasswordSecretsProvider p = provider(client, "password", "");

        p.getSecret("mykey");
        p.invalidateCache();
        p.getSecret("mykey"); // cache cleared — full re-fetch

        // Two full fetches × 2 HTTP calls each = 4 total
        verify(client, times(4)).get(anyString(), anyString());
    }

    @Test
    public void getSecretExpiredCacheEntryTriggersRefetch() throws Exception {
        OnePasswordHttpClient client = buildMockClient("mykey", ITEM_ID, "val");
        OnePasswordSecretsProvider p = provider(client, "password", "", -1L); // instant expiry

        p.getSecret("mykey");
        p.getSecret("mykey"); // expired — must re-fetch

        verify(client, times(4)).get(anyString(), anyString());
    }

    // -- Key aliasing --

    @Test
    public void getSecretUsesAliasedTitle() throws Exception {
        String aliasedTitle = "prod-ofbiz-mysql-db-password";
        OnePasswordHttpClient client = buildMockClient(aliasedTitle, ITEM_ID, "dbpass");
        OnePasswordSecretsProvider p = providerWithAliases(client, "password", "",
                Map.of("jdbc-password.mysql-ofbiz", aliasedTitle));

        assertEquals("dbpass", p.getSecret("jdbc-password.mysql-ofbiz"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(client, atLeastOnce()).get(urlCaptor.capture(), eq(TOKEN));
        String expectedFilter = URLEncoder.encode("title eq \"" + aliasedTitle + "\"", StandardCharsets.UTF_8);
        assertTrue(urlCaptor.getAllValues().stream().anyMatch(u -> u.contains(expectedFilter)));
    }

    @Test
    public void getSecretFallsBackToLogicalKeyWhenNoAliasConfigured() throws Exception {
        OnePasswordHttpClient client = buildMockClient("jdbc-password.mysql-ofbiz", ITEM_ID, "dbpass");
        OnePasswordSecretsProvider p = providerWithAliases(client, "password", "",
                Map.of("some.other.key", "some-other-alias"));

        assertEquals("dbpass", p.getSecret("jdbc-password.mysql-ofbiz"));
    }

    // -- Error handling --

    @Test
    public void getSecretThrowsWhenItemNotFound() throws Exception {
        OnePasswordHttpClient client = mock(OnePasswordHttpClient.class);
        when(client.get(anyString(), anyString())).thenReturn("[]");

        assertThrows(GeneralException.class, () -> provider(client, "password", "").getSecret("missing"));
    }

    @Test
    public void getSecretThrowsWhenFieldNotPresent() throws Exception {
        // Item exists but has no "password" field — only "username"
        OnePasswordHttpClient client = buildMockClient("mykey", ITEM_ID, "username", "dbuser", "password");
        assertThrows(GeneralException.class, () -> provider(client, "password", "").getSecret("mykey"));
    }

    @Test
    public void getSecretThrowsOnHttpError() throws Exception {
        OnePasswordHttpClient client = mock(OnePasswordHttpClient.class);
        when(client.get(anyString(), anyString())).thenThrow(new IOException("connection refused"));

        assertThrows(GeneralException.class, () -> provider(client, "password", "").getSecret("mykey"));
    }

    // -- helpers --

    private static OnePasswordSecretsProvider provider(OnePasswordHttpClient client, String field, String prefix) {
        return provider(client, field, prefix, ONE_HOUR_MS);
    }

    private static OnePasswordSecretsProvider provider(OnePasswordHttpClient client, String field,
            String prefix, long ttlMs) {
        return new OnePasswordSecretsProvider(client, BASE_URL, TOKEN, VAULT_ID, field, prefix, ttlMs);
    }

    private static OnePasswordSecretsProvider providerWithAliases(OnePasswordHttpClient client, String field,
            String prefix, Map<String, String> keyAliases) {
        return new OnePasswordSecretsProvider(client, BASE_URL, TOKEN, VAULT_ID, field, prefix, ONE_HOUR_MS,
                keyAliases);
    }

    /**
     * Builds a mock that routes by URL: search requests get the item list,
     * item-fetch requests get the full item with a "password" field.
     */
    private static OnePasswordHttpClient buildMockClient(String title, String itemId, String fieldValue)
            throws IOException {
        return buildMockClient(title, itemId, "password", fieldValue, "password");
    }

    /**
     * Builds a mock that exposes an item with the given fieldLabel/fieldValue,
     * regardless of which field the provider is configured to look for.
     */
    private static OnePasswordHttpClient buildMockClient(String title, String itemId,
            String fieldLabel, String fieldValue, String ignoredField) throws IOException {
        OnePasswordHttpClient client = mock(OnePasswordHttpClient.class);
        when(client.get(anyString(), eq(TOKEN))).thenAnswer(inv -> {
            String url = inv.getArgument(0, String.class);
            if (url.contains("filter=")) {
                return "[{\"id\":\"" + itemId + "\",\"title\":\"" + title + "\"}]";
            }
            // item detail
            return "{\"id\":\"" + itemId + "\",\"fields\":["
                    + "{\"label\":\"" + fieldLabel + "\",\"value\":\"" + fieldValue + "\"}"
                    + "]}";
        });
        return client;
    }
}
