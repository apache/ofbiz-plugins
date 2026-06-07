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

import java.util.Map;

import io.github.jopenlibs.vault.VaultException;

/**
 * Thin seam over Vault KV reads used by {@link HashicorpVaultSecretsProvider}.
 * Kept package-private so tests can substitute a lambda without launching a real Vault server.
 */
@FunctionalInterface
interface HashicorpVaultReader {
    /**
     * Reads the KV secret at the given path.
     *
     * @param path the full KV path (e.g. {@code "secret/myapp/jdbc-password"})
     * @return the data fields; never {@code null}
     * @throws VaultException if the read fails or the path does not exist
     */
    Map<String, String> read(String path) throws VaultException;
}
