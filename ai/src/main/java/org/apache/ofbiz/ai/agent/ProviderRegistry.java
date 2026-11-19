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
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.service.DispatchContext;

/**
 * Loads named LLM provider blocks from {@code ai.properties} at container startup.
 *
 * <p>Each provider block follows the naming convention:
 * {@code ai.provider.<name>.<field>} where {@code <name>} is an arbitrary
 * identifier such as {@code openai-default} or {@code anthropic-default}.
 * Required fields are {@code baseUrl}, {@code apiKey}, and {@code model}.
 * Optional fields are {@code timeout} (default 60) and {@code extraHeaders}
 * (comma-separated {@code key:value} pairs).
 */
public final class ProviderRegistry {

    private static final String MODULE = ProviderRegistry.class.getName();
    private static final String PROVIDER_PREFIX = "ai.provider.";
    private static final String PLACEHOLDER_KEY = "REPLACE_WITH_YOUR_API_KEY";
    private static final int DEFAULT_TIMEOUT = 60;

    private final Map<String, ProviderConfig> providers;

    /**
     * Constructs the registry by scanning {@code ai.properties} for all
     * named-provider blocks.  Invalid or unconfigured providers are skipped
     * with a warning; the registry is never {@code null} even if no providers
     * are loaded.
     *
     * @param dctx the dispatch context (unused directly, retained for symmetry
     *             with other registry constructors)
     */
    public ProviderRegistry(DispatchContext dctx) {
        Map<String, ProviderConfig> loaded = new LinkedHashMap<>();

        Properties props = UtilProperties.getProperties("ai");
        if (props == null) {
            Debug.logWarning("ProviderRegistry: ai.properties not found; no providers loaded.", MODULE);
            this.providers = Collections.emptyMap();
            return;
        }

        // Collect distinct provider names from keys like ai.provider.<name>.<field>
        Set<String> names = new TreeSet<>();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith(PROVIDER_PREFIX)) {
                String remainder = key.substring(PROVIDER_PREFIX.length());
                int dot = remainder.indexOf('.');
                if (dot > 0) {
                    names.add(remainder.substring(0, dot));
                }
            }
        }

        for (String name : names) {
            String pfx = PROVIDER_PREFIX + name + ".";
            String baseUrl = props.getProperty(pfx + "baseUrl", "").trim();
            String apiKey = props.getProperty(pfx + "apiKey", "").trim();
            String model = props.getProperty(pfx + "model", "").trim();
            String timeoutStr = props.getProperty(pfx + "timeout", "").trim();
            String extraHeadersRaw = props.getProperty(pfx + "extraHeaders", "").trim();

            if (UtilValidate.isEmpty(baseUrl)) {
                Debug.logWarning("ProviderRegistry: provider '" + name
                        + "' has no baseUrl; skipping.", MODULE);
                continue;
            }
            if (UtilValidate.isEmpty(apiKey) || PLACEHOLDER_KEY.equals(apiKey)) {
                Debug.logWarning("ProviderRegistry: provider '" + name
                        + "' has no valid apiKey; skipping.", MODULE);
                continue;
            }
            if (UtilValidate.isEmpty(model)) {
                Debug.logWarning("ProviderRegistry: provider '" + name
                        + "' has no model; skipping.", MODULE);
                continue;
            }

            int timeout = DEFAULT_TIMEOUT;
            if (UtilValidate.isNotEmpty(timeoutStr)) {
                try {
                    timeout = Integer.parseInt(timeoutStr);
                } catch (NumberFormatException e) {
                    Debug.logWarning("ProviderRegistry: provider '" + name
                            + "' has invalid timeout '" + timeoutStr
                            + "'; using default " + DEFAULT_TIMEOUT + "s.", MODULE);
                }
            }

            Map<String, String> extraHeaders = new LinkedHashMap<>();
            if (UtilValidate.isNotEmpty(extraHeadersRaw)) {
                for (String pair : extraHeadersRaw.split(",")) {
                    pair = pair.trim();
                    String[] parts = pair.split(":", 2);
                    if (parts.length == 2) {
                        String hKey = parts[0].trim();
                        String hVal = parts[1].trim();
                        if (UtilValidate.isNotEmpty(hKey)) {
                            extraHeaders.put(hKey, hVal);
                        }
                    }
                }
            }

            String providerType = props.getProperty(pfx + "type", "openai").trim();
            if (providerType.isEmpty()) {
                providerType = "openai";
            }

            loaded.put(name, new ProviderConfig(name, baseUrl, apiKey, model, timeout, extraHeaders, providerType));
            Debug.logInfo("ProviderRegistry: loaded provider '" + name + "' (model=" + model + ").", MODULE);
        }

        this.providers = Collections.unmodifiableMap(loaded);
        Debug.logInfo("ProviderRegistry: " + this.providers.size() + " provider(s) configured.", MODULE);
    }

    /**
     * Returns the {@link ProviderConfig} for the given name, or {@code null}
     * if no such provider is configured.
     *
     * @param name the provider name (e.g. {@code "openai-default"})
     * @return the provider config, or {@code null}
     */
    public ProviderConfig getProvider(String name) {
        return providers.get(name);
    }

    /**
     * Returns an unmodifiable view of all configured provider names.
     *
     * @return set of provider names
     */
    public Set<String> getProviderNames() {
        return providers.keySet();
    }
}
