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
package org.apache.ofbiz.envvar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.apache.ofbiz.base.util.GeneralException;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link EnvVarSecretProvider}.
 *
 * <p>Uses a fake {@link EnvVarReader} backed by a plain {@link Map}, since a
 * running JVM cannot set real environment variables for itself.
 * {@code ENC(...)} decryption itself is covered by
 * {@code ConfigCryptoUtilTest}, not re-tested here — consistent with how the
 * other six provider plugins' test classes treat that integration.</p>
 */
public class EnvVarSecretProviderTest {

    private EnvVarSecretProvider provider(Map<String, String> env) {
        return providerWithPrefix(env, "OFBIZ_");
    }

    private EnvVarSecretProvider providerWithPrefix(Map<String, String> env, String prefix) {
        return new EnvVarSecretProvider(env::get, prefix);
    }

    private EnvVarSecretProvider providerWithAliases(Map<String, String> env, Map<String, String> keyAliases) {
        return new EnvVarSecretProvider(env::get, "OFBIZ_", keyAliases);
    }

    @Test
    public void getSecretReturnsValueFromEnvVar() throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("OFBIZ_JDBC_PASSWORD_MYSQL_OFBIZ", "demo-secret-from-vault-poc");

        assertEquals("demo-secret-from-vault-poc",
                provider(env).getSecret("jdbc-password.mysql-ofbiz"));
    }

    @Test
    public void toEnvVarNameTransformsDotsAndDashesToUnderscoresAndUppercases() {
        EnvVarSecretProvider provider = provider(new HashMap<>());

        assertEquals("OFBIZ_JDBC_PASSWORD_MYSQL_OFBIZ",
                provider.toEnvVarName("jdbc-password.mysql-ofbiz"));
        assertEquals("OFBIZ_PAYMENT_AUTHORIZEDOTNET_TRANKEY",
                provider.toEnvVarName("payment.authorizedotnet.trankey"));
    }

    @Test
    public void getSecretAppliesCustomPrefix() throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("MYAPP_JDBC_PASSWORD_MYSQL_OFBIZ", "custom-prefixed-value");

        assertEquals("custom-prefixed-value",
                providerWithPrefix(env, "MYAPP_").getSecret("jdbc-password.mysql-ofbiz"));
    }

    @Test
    public void getSecretPassesThroughPlainValueUnchanged() throws Exception {
        // Confirms the ConfigCryptoUtil.decryptIfEncrypted() call is a no-op
        // for ordinary (non-ENC(...)) values - it must not mangle them.
        Map<String, String> env = new HashMap<>();
        env.put("OFBIZ_SOME_KEY", "plain-value-no-wrapping");

        assertEquals("plain-value-no-wrapping", provider(env).getSecret("some.key"));
    }

    // -- Key aliasing --

    @Test
    public void getSecretUsesAliasedEnvVarName() throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("PROD_OFBIZ_MYSQL_DB_PASSWORD", "dbpass");

        EnvVarSecretProvider provider = providerWithAliases(env,
                Map.of("jdbc-password.mysql-ofbiz", "PROD_OFBIZ_MYSQL_DB_PASSWORD"));

        assertEquals("dbpass", provider.getSecret("jdbc-password.mysql-ofbiz"));
    }

    @Test
    public void getSecretFallsBackToFixedTransformWhenNoAliasConfigured() throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("OFBIZ_JDBC_PASSWORD_MYSQL_OFBIZ", "dbpass");

        EnvVarSecretProvider provider = providerWithAliases(env,
                Map.of("some.other.key", "SOME_OTHER_ALIAS"));

        assertEquals("dbpass", provider.getSecret("jdbc-password.mysql-ofbiz"));
    }

    @Test
    public void getSecretThrowsWhenEnvVarNotSet() {
        assertThrows(GeneralException.class, () -> provider(new HashMap<>()).getSecret("jdbc-password.mysql-ofbiz"));
    }

    @Test
    public void getSecretThrowsWhenEnvVarEmpty() {
        Map<String, String> env = new HashMap<>();
        env.put("OFBIZ_JDBC_PASSWORD_MYSQL_OFBIZ", "");

        assertThrows(GeneralException.class, () -> provider(env).getSecret("jdbc-password.mysql-ofbiz"));
    }
}
