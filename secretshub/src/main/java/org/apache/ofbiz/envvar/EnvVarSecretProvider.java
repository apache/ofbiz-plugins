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

import java.util.Locale;
import java.util.Map;

import org.apache.ofbiz.base.crypto.ConfigCryptoUtil;
import org.apache.ofbiz.base.lang.ThreadSafe;
import org.apache.ofbiz.base.secret.SecretProvider;
import org.apache.ofbiz.base.secret.SecretProviderUtil;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;

/**
 * {@link SecretProvider} implementation that resolves secrets from this
 * process's own environment variables, instead of calling out to a remote
 * vault SDK/API the way the other six provider plugins do.
 *
 * <p>This is the generic counterpart to the file-mount mechanism the CSI
 * Secrets Store Driver provides: whatever external tool already placed a
 * value into this JVM's environment before OFBiz started — a Kubernetes
 * External Secrets Operator sync into {@code envFrom}, a Doppler/Infisical
 * {@code run} wrapper, a plain systemd {@code EnvironmentFile} — this
 * provider can resolve, with no knowledge of which tool did the placing.</p>
 *
 * <h2>Name transform</h2>
 * <p>Environment variable names are restricted to shell-identifier
 * characters, unlike OFBiz secret keys (e.g. {@code jdbc-password.mysql-ofbiz}),
 * so the key is deterministically transformed: uppercased, every character
 * that isn't {@code A-Z} or {@code 0-9} replaced with {@code _}, then
 * prefixed with {@code envvar.name.prefix} (default {@code OFBIZ_}):</p>
 * <pre>
 *   jdbc-password.mysql-ofbiz  -&gt;  OFBIZ_JDBC_PASSWORD_MYSQL_OFBIZ
 * </pre>
 * <p>This transform is fixed by default — see {@code config/env-var-secrets.properties}
 * — so any external tool can predict the expected env var name without reading OFBiz
 * source. A deployment that needs a specific key to resolve to a different env var name
 * (e.g. a name collision with something else already in its environment) may set
 * {@code key.alias.<logicalKey>=<ENV_VAR_NAME>} as an explicit per-key override; this is
 * opt-in and does not change the default transform for every other key.</p>
 *
 * <p>No caching is implemented: unlike a remote API call, reading this
 * process's own environment is already an in-memory lookup, and a JVM's
 * environment variables are immutable for its lifetime, so there is
 * nothing to gain from caching the result.</p>
 */
@ThreadSafe
public final class EnvVarSecretProvider implements SecretProvider {

    private static final String MODULE = EnvVarSecretProvider.class.getName();
    private static final String CONFIG_RESOURCE = "env-var-secrets";

    private final EnvVarReader reader;
    private final String namePrefix;
    private final Map<String, String> keyAliases;

    /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
    public EnvVarSecretProvider() {
        this(System::getenv, prop("envvar.name.prefix", "OFBIZ_"), SecretProviderUtil.loadKeyAliases(CONFIG_RESOURCE));
    }

    /** Package-private constructor used by unit tests to inject a fake environment. */
    EnvVarSecretProvider(EnvVarReader reader, String namePrefix) {
        this(reader, namePrefix, Map.of());
    }

    /** Package-private constructor used by unit tests to inject a fake environment and key aliases. */
    EnvVarSecretProvider(EnvVarReader reader, String namePrefix, Map<String, String> keyAliases) {
        this.reader = reader;
        this.namePrefix = namePrefix;
        this.keyAliases = keyAliases;
    }

    @Override
    public String getSecret(String key) throws GeneralException {
        String envName = keyAliases.getOrDefault(key, toEnvVarName(key));
        String value = reader.getenv(envName);
        if (UtilValidate.isEmpty(value)) {
            throw new GeneralException("Secret key '" + key + "' not found — expected environment "
                    + "variable '" + envName + "' to be set");
        }
        return ConfigCryptoUtil.decryptIfEncrypted(value, key);
    }

    @Override
    public boolean isFallbackEnabled() {
        return Boolean.parseBoolean(prop("envvar.fallback.enabled", "true"));
    }

    /**
     * Transforms an OFBiz secret key into the environment variable name this
     * provider will look up. Package-private so it can be unit tested directly.
     */
    String toEnvVarName(String key) {
        return namePrefix + key.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
    }

    private static String prop(String name, String defaultValue) {
        return UtilProperties.getPropertyValue(CONFIG_RESOURCE, name, defaultValue);
    }

}
