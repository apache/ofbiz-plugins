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
package org.apache.ofbiz.ai.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable value object holding configuration for one named LLM provider.
 * Instances are loaded from ai.properties by the framework bootstrap layer.
 */
public final class ProviderConfig {

    private final String name;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;
    private final Map<String, String> extraHeaders;
    private final String providerType;

    public ProviderConfig(String name, String baseUrl, String apiKey,
            String model, int timeoutSeconds, Map<String, String> extraHeaders,
            String providerType) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.extraHeaders = Collections.unmodifiableMap(
                new LinkedHashMap<>(extraHeaders != null ? extraHeaders : Collections.emptyMap()));
        this.providerType = (providerType != null && !providerType.isBlank()) ? providerType : "openai";
    }

    public String getName() {
        return name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getModel() {
        return model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public Map<String, String> getExtraHeaders() {
        return extraHeaders;
    }

    public String getProviderType() {
        return providerType;
    }
}
