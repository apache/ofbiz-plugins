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

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ofbiz.base.util.GeneralException;
import org.junit.Test;

public class AzureKeyVaultSecretsProviderTest {

    private static final long ONE_HOUR_MS = 3_600_000L;

    // -- Happy path --

    @Test
    public void getSecret_returnsSecretValue() throws GeneralException {
        AzureKeyVaultReader reader = fixedReader("mykey", "s3cr3t");
        assertEquals("s3cr3t", provider(reader, "", "-").getSecret("mykey"));
    }

    @Test
    public void getSecret_sanitizesDotInKey() throws GeneralException {
        // OFBiz key "jdbc-password.mysql-ofbiz" → Azure name "jdbc-password-mysql-ofbiz"
        AzureKeyVaultReader reader = fixedReader("jdbc-password-mysql-ofbiz", "dbpass");
        assertEquals("dbpass", provider(reader, "", "-").getSecret("jdbc-password.mysql-ofbiz"));
    }

    @Test
    public void getSecret_appliesPrefixAfterSanitizing() throws GeneralException {
        AzureKeyVaultReader reader = fixedReader("prod-jdbc-password-mydb", "prodpass");
        assertEquals("prodpass", provider(reader, "prod-", "-").getSecret("jdbc-password.mydb"));
    }

    @Test
    public void getSecret_cachePreventsDuplicateReaderCall() throws GeneralException {
        AtomicInteger calls = new AtomicInteger();
        AzureKeyVaultReader reader = secretName -> {
            calls.incrementAndGet();
            return "val";
        };
        AzureKeyVaultSecretsProvider p = provider(reader, "", "-");

        p.getSecret("mykey");
        p.getSecret("mykey"); // cache hit

        assertEquals(1, calls.get());
    }

    @Test
    public void invalidateCache_forcesRefetchOnNextCall() throws GeneralException {
        AtomicInteger calls = new AtomicInteger();
        AzureKeyVaultReader reader = secretName -> {
            calls.incrementAndGet();
            return "val";
        };
        AzureKeyVaultSecretsProvider p = provider(reader, "", "-");

        p.getSecret("mykey");
        p.invalidateCache();
        p.getSecret("mykey");

        assertEquals(2, calls.get());
    }

    @Test
    public void getSecret_expiredCacheTriggersRefetch() throws GeneralException {
        AtomicInteger calls = new AtomicInteger();
        AzureKeyVaultReader reader = secretName -> {
            calls.incrementAndGet();
            return "val";
        };
        // TTL of -1 ms — entries expire immediately
        AzureKeyVaultSecretsProvider p = new AzureKeyVaultSecretsProvider(
                reader, "", "-", -1L);

        p.getSecret("mykey");
        p.getSecret("mykey");

        assertEquals(2, calls.get());
    }

    @Test
    public void getSecret_dotReplacementDisabled_keepsDotsInName() throws GeneralException {
        AzureKeyVaultReader reader = fixedReader("my.key", "val");
        assertEquals("val", provider(reader, "", "").getSecret("my.key"));
    }

    // -- Error handling --

    @Test(expected = GeneralException.class)
    public void getSecret_throwsOnReaderException() throws GeneralException {
        AzureKeyVaultReader reader = secretName -> { throw new RuntimeException("SecretNotFound"); };
        provider(reader, "", "-").getSecret("missing");
    }

    @Test(expected = GeneralException.class)
    public void getSecret_throwsOnEmptyValue() throws GeneralException {
        AzureKeyVaultReader reader = secretName -> "";
        provider(reader, "", "-").getSecret("mykey");
    }

    // -- helpers --

    private static AzureKeyVaultSecretsProvider provider(AzureKeyVaultReader reader,
            String prefix, String dotReplacement) {
        return new AzureKeyVaultSecretsProvider(reader, prefix, dotReplacement, ONE_HOUR_MS);
    }

    private static AzureKeyVaultReader fixedReader(String expectedName, String value) {
        return secretName -> expectedName.equals(secretName) ? value : null;
    }
}
