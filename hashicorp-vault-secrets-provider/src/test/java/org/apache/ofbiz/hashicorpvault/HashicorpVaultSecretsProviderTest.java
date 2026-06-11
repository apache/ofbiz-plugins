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

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.jopenlibs.vault.VaultException;

import org.apache.ofbiz.base.util.GeneralException;
import org.junit.Test;

public class HashicorpVaultSecretsProviderTest {

    private static final long ONE_HOUR_MS = 3_600_000L;

    // -- Happy path --

    @Test
    public void getSecretReturnsSingleFieldSecret() throws GeneralException {
        HashicorpVaultReader reader = fixedReader("secret/mykey", singleEntry("value", "s3cr3t"));
        HashicorpVaultSecretsProvider provider = provider(reader, "", "");

        assertEquals("s3cr3t", provider.getSecret("mykey"));
    }

    @Test
    public void getSecretExtractsNamedField() throws GeneralException {
        HashicorpVaultReader reader = fixedReader("secret/mykey", twoEntry("username", "user", "password", "s3cr3t"));
        HashicorpVaultSecretsProvider provider = provider(reader, "", "password");

        assertEquals("s3cr3t", provider.getSecret("mykey"));
    }

    @Test
    public void getSecretAppliesKvMountAndPrefix() throws GeneralException {
        HashicorpVaultReader reader = fixedReader("kv/prod/jdbc-password.ofbiz", singleEntry("value", "dbpass"));
        HashicorpVaultSecretsProvider provider = new HashicorpVaultSecretsProvider(reader, "kv", "prod/", "", ONE_HOUR_MS);

        assertEquals("dbpass", provider.getSecret("jdbc-password.ofbiz"));
    }

    @Test
    public void getSecretCachePreventsDuplicateReaderCall() throws GeneralException {
        AtomicInteger calls = new AtomicInteger();
        HashicorpVaultReader reader = path -> {
            calls.incrementAndGet();
            return singleEntry("value", "s3cr3t");
        };
        HashicorpVaultSecretsProvider provider = provider(reader, "", "");

        provider.getSecret("mykey");
        provider.getSecret("mykey"); // second call — cache hit

        assertEquals(1, calls.get());
    }

    @Test
    public void invalidateCacheForcesRefetchOnNextCall() throws GeneralException {
        AtomicInteger calls = new AtomicInteger();
        HashicorpVaultReader reader = path -> {
            calls.incrementAndGet();
            return singleEntry("value", "val");
        };
        HashicorpVaultSecretsProvider provider = provider(reader, "", "");

        provider.getSecret("mykey");
        provider.invalidateCache();
        provider.getSecret("mykey");

        assertEquals(2, calls.get());
    }

    @Test
    public void getSecretExpiredCacheEntryTriggersRefetch() throws GeneralException {
        AtomicInteger calls = new AtomicInteger();
        HashicorpVaultReader reader = path -> {
            calls.incrementAndGet();
            return singleEntry("value", "val");
        };
        // TTL of -1 ms means entries expire immediately
        HashicorpVaultSecretsProvider provider = new HashicorpVaultSecretsProvider(reader, "secret", "", "", -1L);

        provider.getSecret("mykey");
        provider.getSecret("mykey");

        assertEquals(2, calls.get());
    }

    // -- Error handling --

    @Test(expected = GeneralException.class)
    public void getSecretThrowsWhenDataIsEmpty() throws GeneralException {
        HashicorpVaultReader reader = path -> Collections.emptyMap();
        provider(reader, "", "").getSecret("missing");
    }

    @Test(expected = GeneralException.class)
    public void getSecretThrowsWhenFieldMissing() throws GeneralException {
        HashicorpVaultReader reader = fixedReader("secret/mykey", singleEntry("username", "dbuser"));
        provider(reader, "", "password").getSecret("mykey"); // "password" field not present
    }

    @Test(expected = GeneralException.class)
    public void getSecretThrowsWhenMultiFieldAndNoFieldConfigured() throws GeneralException {
        HashicorpVaultReader reader = fixedReader("secret/mykey", twoEntry("username", "u", "password", "p"));
        provider(reader, "", "").getSecret("mykey"); // ambiguous — 2 fields, no field config
    }

    @Test(expected = GeneralException.class)
    public void getSecretThrowsOnVaultException() throws GeneralException {
        HashicorpVaultReader reader = path -> { throw new VaultException("connection refused", 503); };
        provider(reader, "", "").getSecret("mykey");
    }

    // -- helpers --

    private static HashicorpVaultSecretsProvider provider(HashicorpVaultReader reader, String prefix, String field) {
        return new HashicorpVaultSecretsProvider(reader, "secret", prefix, field, ONE_HOUR_MS);
    }

    private static HashicorpVaultReader fixedReader(String expectedPath, Map<String, String> data) {
        return path -> expectedPath.equals(path) ? data : Collections.emptyMap();
    }

    private static Map<String, String> singleEntry(String k, String v) {
        return Collections.singletonMap(k, v);
    }

    private static Map<String, String> twoEntry(String k1, String v1, String k2, String v2) {
        Map<String, String> m = new HashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }
}
