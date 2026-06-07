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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.ofbiz.base.util.GeneralException;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests for {@link BitwardenSecretsProvider}.
 *
 * <p>A stable 64-byte test key is generated once for the test class.
 * Helper methods produce properly-encrypted cipher strings so the decryption
 * tests exercise the real AES-256-CBC + HMAC-SHA256 path, not just mocked output.</p>
 */
public class BitwardenSecretsProviderTest {

    private static final long ONE_HOUR_MS = 3_600_000L;
    private static final String API_URL = "https://api.bitwarden.com";
    private static final String IDENTITY_URL = "https://identity.bitwarden.com";
    private static final String ORG_ID = "org-abc";
    private static final String BEARER_TOKEN_RESPONSE =
            "{\"access_token\":\"test-bearer\",\"expires_in\":3600}";

    private static byte[] TEST_ENC_KEY;
    private static byte[] TEST_MAC_KEY;

    @BeforeClass
    public static void generateTestKey() {
        byte[] keyBytes = new byte[64];
        new SecureRandom().nextBytes(keyBytes);
        TEST_ENC_KEY = Arrays.copyOfRange(keyBytes, 0, 32);
        TEST_MAC_KEY = Arrays.copyOfRange(keyBytes, 32, 64);
    }

    // -- Decryption unit tests (no HTTP, pure crypto) --

    @Test
    public void decrypt_roundTrip() throws Exception {
        String plaintext = "my-secret-password";
        String cipherString = encrypt(plaintext);

        BitwardenSecretsProvider provider = provider(mockHttpForSecret("mykey", plaintext));
        assertEquals(plaintext, provider.decrypt(cipherString));
    }

    @Test(expected = GeneralException.class)
    public void decrypt_throwsOnWrongCipherType() throws Exception {
        BitwardenSecretsProvider provider = provider(mock(BitwardenHttpClient.class));
        provider.decrypt("1.abc|def|ghi"); // type 1, not 2
    }

    @Test(expected = GeneralException.class)
    public void decrypt_throwsOnTamperedCiphertext() throws Exception {
        String cipherString = encrypt("secret");
        // Flip a byte in the ciphertext part
        String[] parts = cipherString.substring(2).split("\\|");
        byte[] ct = Base64.getDecoder().decode(parts[1]);
        ct[0] ^= 0xFF;
        String tampered = "2." + parts[0] + "|" + Base64.getEncoder().encodeToString(ct) + "|" + parts[2];

        BitwardenSecretsProvider provider = provider(mock(BitwardenHttpClient.class));
        provider.decrypt(tampered);
    }

    // -- Full flow tests (with mock HTTP) --

    @Test
    public void getSecret_fetchesAndDecryptsSecret() throws Exception {
        String secretValue = "db-password-123";
        BitwardenHttpClient client = mockHttpForSecret("jdbc-password.mydb", secretValue);
        BitwardenSecretsProvider provider = provider(client);

        assertEquals(secretValue, provider.getSecret("jdbc-password.mydb"));
    }

    @Test
    public void getSecret_appliesSecretNamePrefix() throws Exception {
        String secretValue = "dbpass";
        BitwardenHttpClient client = mockHttpForSecret("prod/jdbc-password.mydb", secretValue);
        BitwardenSecretsProvider provider = providerWithPrefix(client, "prod/");

        assertEquals(secretValue, provider.getSecret("jdbc-password.mydb"));
    }

    @Test
    public void getSecret_cachePreventsSecondApiCall() throws Exception {
        BitwardenHttpClient client = mockHttpForSecret("mykey", "val");
        BitwardenSecretsProvider provider = provider(client);

        provider.getSecret("mykey");
        provider.getSecret("mykey");

        // Only 1 identity POST + 1 list GET + 1 secret GET = 3 calls for the first fetch
        verify(client, times(1)).post(anyString(), anyString());
        verify(client, times(1)).get(contains("/organizations/"), anyString());
        verify(client, times(1)).get(contains("/secrets/"), anyString());
    }

    @Test
    public void invalidateCache_forcesRefetch() throws Exception {
        BitwardenHttpClient client = mockHttpForSecret("mykey", "val");
        BitwardenSecretsProvider provider = provider(client);

        provider.getSecret("mykey");
        provider.invalidateCache();
        provider.getSecret("mykey");

        // Two full fetches — bearer token is still valid so POST called once
        verify(client, times(2)).get(contains("/organizations/"), anyString());
        verify(client, times(2)).get(contains("/secrets/"), anyString());
    }

    @Test(expected = GeneralException.class)
    public void getSecret_throwsWhenSecretNotFound() throws Exception {
        BitwardenHttpClient client = mock(BitwardenHttpClient.class);
        when(client.post(anyString(), anyString())).thenReturn(BEARER_TOKEN_RESPONSE);
        when(client.get(contains("/organizations/"), anyString()))
                .thenReturn("{\"data\":[]}"); // empty list

        provider(client).getSecret("missing");
    }

    @Test(expected = GeneralException.class)
    public void getSecret_throwsOnHttpError() throws Exception {
        BitwardenHttpClient client = mock(BitwardenHttpClient.class);
        when(client.post(anyString(), anyString())).thenThrow(new IOException("connection refused"));

        provider(client).getSecret("mykey");
    }

    // -- helpers --

    private BitwardenSecretsProvider provider(BitwardenHttpClient client) throws GeneralException {
        return providerWithPrefix(client, "");
    }

    private BitwardenSecretsProvider providerWithPrefix(BitwardenHttpClient client, String prefix)
            throws GeneralException {
        return new BitwardenSecretsProvider(client, API_URL, IDENTITY_URL,
                ORG_ID, prefix, ONE_HOUR_MS,
                "service-account.test-id", "test-secret", TEST_ENC_KEY, TEST_MAC_KEY);
    }

    /**
     * Builds a mock HTTP client that returns a bearer token on POST and serves a
     * secrets list + secret detail using properly encrypted cipher strings.
     */
    private BitwardenHttpClient mockHttpForSecret(String secretKey, String secretValue) throws Exception {
        String encryptedKey = encrypt(secretKey);
        String encryptedValue = encrypt(secretValue);
        String secretId = "secret-id-001";

        BitwardenHttpClient client = mock(BitwardenHttpClient.class);
        when(client.post(anyString(), anyString())).thenReturn(BEARER_TOKEN_RESPONSE);
        when(client.get(contains("/organizations/"), eq("test-bearer")))
                .thenReturn("{\"data\":[{\"id\":\"" + secretId + "\",\"key\":\""
                        + encryptedKey + "\"}]}");
        when(client.get(contains("/secrets/" + secretId), eq("test-bearer")))
                .thenReturn("{\"id\":\"" + secretId + "\",\"key\":\"" + encryptedKey
                        + "\",\"value\":\"" + encryptedValue + "\"}");
        return client;
    }

    /** Produces a valid Bitwarden type-2 cipher string using the test key material. */
    private String encrypt(String plaintext) throws Exception {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(TEST_ENC_KEY, "AES"), new IvParameterSpec(iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(TEST_MAC_KEY, "HmacSHA256"));
        mac.update(iv);
        byte[] hmac = mac.doFinal(ciphertext);

        Base64.Encoder b64 = Base64.getEncoder();
        return "2." + b64.encodeToString(iv) + "|"
                + b64.encodeToString(ciphertext) + "|"
                + b64.encodeToString(hmac);
    }
}
