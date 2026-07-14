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

/**
 * Thin seam over Azure Key Vault reads used by {@link AzureKeyVaultSecretsProvider}.
 * Kept package-private so tests can substitute a lambda without connecting to Azure.
 */
@FunctionalInterface
interface AzureKeyVaultReader {
    /**
     * Retrieves the current value of the named secret.
     *
     * @param secretName the Key Vault secret name (letters, digits and hyphens only)
     * @return the plaintext secret value; never {@code null}
     * @throws Exception if the read fails or the secret does not exist
     */
    String read(String secretName) throws Exception;
}
