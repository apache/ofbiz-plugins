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
package org.apache.ofbiz.secretshub;

import java.util.Locale;
import java.util.Properties;

import org.apache.ofbiz.awssecrets.AwsSecretsManagerProvider;
import org.apache.ofbiz.azurekeyvault.AzureKeyVaultSecretsProvider;
import org.apache.ofbiz.base.secret.SecretProvider;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.bitwarden.BitwardenSecretsProvider;
import org.apache.ofbiz.envvar.EnvVarSecretProvider;
import org.apache.ofbiz.gcpsecretmanager.GcpSecretManagerSecretsProvider;
import org.apache.ofbiz.hashicorpvault.HashicorpVaultSecretsProvider;
import org.apache.ofbiz.onepassword.OnePasswordSecretsProvider;

/**
 * {@link SecretProvider} that delegates to exactly one of the bundled providers, selected via
 * {@code secret.provider.active} in {@code secretshub.properties}.
 *
 * <p>This is the only class the {@code secretshub} component registers under
 * {@code META-INF/services/org.apache.ofbiz.base.secret.SecretProvider}. Concatenating the 7
 * bundled providers' own service registrations would make {@link java.util.ServiceLoader}
 * discover and construct all of them, eagerly building SDK clients for providers that were
 * never configured. Routing everything through this single delegating class keeps the
 * "only one provider active at a time" contract that used to be enforced by each plugin's own
 * {@code ofbiz-component.xml enabled} flag.</p>
 */
public final class ActiveSecretProvider implements SecretProvider {

    private static final String MODULE = ActiveSecretProvider.class.getName();

    private static final String VALID_VALUES = "aws | azure | bitwarden | env-var | gcp | hashicorp-vault | onepassword";

    private final SecretProvider delegate;

    /** Required no-arg constructor for {@link java.util.ServiceLoader}. */
    public ActiveSecretProvider() throws GeneralException {
        Properties props = UtilProperties.getProperties("secretshub");
        String active = props == null ? null : props.getProperty("secret.provider.active");
        active = active == null ? "" : active.trim().toLowerCase(Locale.ROOT);
        this.delegate = build(active);
        Debug.logInfo("ActiveSecretProvider: secret.provider.active=" + active
                + ", delegating to " + delegate.getClass().getName(), MODULE);
    }

    /** Package-private for direct unit testing of the selection/fail-fast logic. */
    static SecretProvider build(String active) throws GeneralException {
        switch (active) {
        case "aws":
            return new AwsSecretsManagerProvider();
        case "azure":
            return new AzureKeyVaultSecretsProvider();
        case "bitwarden":
            return new BitwardenSecretsProvider();
        case "env-var":
            return new EnvVarSecretProvider();
        case "gcp":
            return new GcpSecretManagerSecretsProvider();
        case "hashicorp-vault":
            return new HashicorpVaultSecretsProvider();
        case "onepassword":
            return new OnePasswordSecretsProvider();
        case "":
            throw new IllegalStateException("secret.provider.active is blank in secretshub.properties. "
                    + "Set it to one of: " + VALID_VALUES);
        default:
            throw new IllegalStateException("Unrecognized secret.provider.active value '" + active
                    + "' in secretshub.properties. Valid values: " + VALID_VALUES);
        }
    }

    @Override
    public String getSecret(String key) throws GeneralException {
        return delegate.getSecret(key);
    }

    @Override
    public boolean isFallbackEnabled() {
        return delegate.isFallbackEnabled();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public void invalidateCache() {
        delegate.invalidateCache();
    }
}
