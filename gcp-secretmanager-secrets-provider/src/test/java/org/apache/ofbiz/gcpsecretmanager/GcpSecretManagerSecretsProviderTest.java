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

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ofbiz.base.util.GeneralException;
import org.junit.Test;

public class GcpSecretManagerSecretsProviderTest {

    private static final long ONE_HOUR_MS = 3_600_000L;
    private static final String PROJECT = "my-project";

    // -- Happy path --

    @Test
    public void getSecretReturnsSecretValue() throws GeneralException {
        GcpSecretReader reader = fixedReader(
                "projects/my-project/secrets/mykey/versions/latest", "s3cr3t");
        assertEquals("s3cr3t", provider(reader, "", "-").getSecret("mykey"));
    }

    @Test
    public void getSecretSanitizesDotInKey() throws GeneralException {
        // OFBiz key "jdbc-password.mysql-ofbiz" → GCP name "jdbc-password-mysql-ofbiz"
        GcpSecretReader reader = fixedReader(
                "projects/my-project/secrets/jdbc-password-mysql-ofbiz/versions/latest", "dbpass");
        assertEquals("dbpass", provider(reader, "", "-").getSecret("jdbc-password.mysql-ofbiz"));
    }

    @Test
    public void getSecretAppliesPrefixAfterSanitizing() throws GeneralException {
        GcpSecretReader reader = fixedReader(
                "projects/my-project/secrets/prod-jdbc-password-mydb/versions/latest", "prodpass");
        assertEquals("prodpass", provider(reader, "prod-", "-").getSecret("jdbc-password.mydb"));
    }

    @Test
    public void getSecretUsesConfiguredVersion() throws GeneralException {
        GcpSecretReader reader = fixedReader(
                "projects/my-project/secrets/mykey/versions/3", "v3val");
        GcpSecretManagerSecretsProvider p = new GcpSecretManagerSecretsProvider(
                reader, PROJECT, "", "3", "-", ONE_HOUR_MS);
        assertEquals("v3val", p.getSecret("mykey"));
    }

    @Test
    public void getSecretCachePreventsDuplicateReaderCall() throws GeneralException {
        AtomicInteger calls = new AtomicInteger();
        GcpSecretReader reader = resourceName -> {
            calls.incrementAndGet();
            return "val";
        };
        GcpSecretManagerSecretsProvider p = provider(reader, "", "-");

        p.getSecret("mykey");
        p.getSecret("mykey"); // cache hit

        assertEquals(1, calls.get());
    }

    @Test
    public void invalidateCacheForcesRefetchOnNextCall() throws GeneralException {
        AtomicInteger calls = new AtomicInteger();
        GcpSecretReader reader = resourceName -> {
            calls.incrementAndGet();
            return "val";
        };
        GcpSecretManagerSecretsProvider p = provider(reader, "", "-");

        p.getSecret("mykey");
        p.invalidateCache();
        p.getSecret("mykey");

        assertEquals(2, calls.get());
    }

    @Test
    public void getSecretExpiredCacheTriggersRefetch() throws GeneralException {
        AtomicInteger calls = new AtomicInteger();
        GcpSecretReader reader = resourceName -> {
            calls.incrementAndGet();
            return "val";
        };
        // TTL of -1 ms — entries expire immediately
        GcpSecretManagerSecretsProvider p = new GcpSecretManagerSecretsProvider(
                reader, PROJECT, "", "latest", "-", -1L);

        p.getSecret("mykey");
        p.getSecret("mykey");

        assertEquals(2, calls.get());
    }

    @Test
    public void getSecretDotReplacementDisabledKeepsDotsInName() throws GeneralException {
        // dot.replacement="" → no sanitization
        GcpSecretReader reader = fixedReader(
                "projects/my-project/secrets/my.key/versions/latest", "val");
        assertEquals("val", provider(reader, "", "").getSecret("my.key"));
    }

    // -- Error handling --

    @Test(expected = GeneralException.class)
    public void getSecretThrowsOnReaderException() throws GeneralException {
        GcpSecretReader reader = resourceName -> {
            throw new Exception("NOT_FOUND");
        };
        provider(reader, "", "-").getSecret("missing");
    }

    @Test(expected = GeneralException.class)
    public void getSecretThrowsOnEmptyValue() throws GeneralException {
        GcpSecretReader reader = resourceName -> "";
        provider(reader, "", "-").getSecret("mykey");
    }

    // -- helpers --

    private static GcpSecretManagerSecretsProvider provider(GcpSecretReader reader,
            String prefix, String dotReplacement) {
        return new GcpSecretManagerSecretsProvider(reader, PROJECT, prefix, "latest",
                dotReplacement, ONE_HOUR_MS);
    }

    private static GcpSecretReader fixedReader(String expectedResource, String value) {
        return resourceName -> expectedResource.equals(resourceName) ? value : null;
    }
}
