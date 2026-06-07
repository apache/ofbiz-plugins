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

import java.io.IOException;

/**
 * Thin seam over HTTP calls used by {@link BitwardenSecretsProvider}.
 * Kept package-private so tests can substitute a mock without making real
 * network calls.
 */
interface BitwardenHttpClient {

    /**
     * Performs an authenticated GET request.
     *
     * @param url         the full request URL
     * @param bearerToken the OAuth bearer token
     * @return the response body as a UTF-8 string
     * @throws IOException if the request fails or the server returns a non-2xx status
     */
    String get(String url, String bearerToken) throws IOException;

    /**
     * Performs a form-encoded POST request (no auth header — used for token exchange).
     *
     * @param url      the full request URL
     * @param formBody {@code application/x-www-form-urlencoded} encoded body
     * @return the response body as a UTF-8 string
     * @throws IOException if the request fails or the server returns a non-2xx status
     */
    String post(String url, String formBody) throws IOException;
}
