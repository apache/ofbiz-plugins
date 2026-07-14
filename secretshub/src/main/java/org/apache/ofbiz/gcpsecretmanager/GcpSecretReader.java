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

/**
 * Thin seam over GCP Secret Manager reads used by {@link GcpSecretManagerSecretsProvider}.
 * Kept package-private so tests can substitute a lambda without connecting to GCP.
 */
@FunctionalInterface
interface GcpSecretReader {
    /**
     * Accesses the secret version at the given fully-qualified resource name.
     *
     * @param resourceName e.g. {@code "projects/my-project/secrets/my-secret/versions/latest"}
     * @return the plaintext secret value; never {@code null}
     * @throws Exception if the access fails or the secret does not exist
     */
    String read(String resourceName) throws Exception;
}
