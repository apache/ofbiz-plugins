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
package org.apache.ofbiz.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.ofbiz.ai.agent.AiChatClient;
import org.apache.ofbiz.ai.agent.AiHttpClient;
import org.apache.ofbiz.ai.agent.ProviderConfig;
import org.apache.ofbiz.ai.container.AiContainer;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.service.DispatchContext;

/**
 * Utility class providing simple LLM call helpers used by the
 * {@code ai.generate} and {@code ai.generateStructured} OFBiz services.
 *
 * <p>Both methods locate an available provider from {@link AiContainer},
 * delegate to {@link AiHttpClient} for the actual HTTP call, and return
 * the parsed result.  No LangChain4j types are used here.
 */
public final class AiWorker {

    private static final String MODULE = AiWorker.class.getName();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_PROVIDER = "openai-default";

    private AiWorker() { }

    /**
     * Sends a chat request to the default configured provider and returns the
     * assistant's text response.
     *
     * <p>Provider lookup order:
     * <ol>
     *   <li>Provider named {@value #DEFAULT_PROVIDER} in {@code ai.properties}.</li>
     *   <li>First available provider if {@value #DEFAULT_PROVIDER} is not configured.</li>
     * </ol>
     *
     * @param dctx     the dispatch context (unused directly, retained for API symmetry)
     * @param messages ordered list of role/content message maps
     * @return the assistant's text, or a human-readable error string if the AI
     *         service is not configured
     * @throws GeneralException if the HTTP request fails or the response cannot be parsed
     */
    public static String generate(DispatchContext dctx,
            List<Map<String, Object>> messages) throws GeneralException {
        ProviderConfig provider = resolveProvider();
        if (provider == null) {
            return "AI service is not available. Check ai.properties configuration.";
        }
        AiChatClient client = new AiHttpClient();
        AiChatClient.ChatResponse response = client.chat(messages,
                Collections.emptyList(), null, provider, null);
        return response.getContent();
    }

    /**
     * Sends a chat request instructing the LLM to respond with JSON matching the
     * supplied schema, then parses and returns that JSON as a {@link Map}.
     *
     * <p>The schema instruction is appended as an additional system message so
     * that providers without native structured-output support can still fulfil
     * the request via prompt guidance.
     *
     * @param dctx     the dispatch context (unused directly, retained for API symmetry)
     * @param messages ordered list of role/content message maps
     * @param schema   the expected response schema expressed as a {@code Map} whose
     *                 values are JSON-serialisable descriptors
     * @return the parsed JSON object returned by the LLM
     * @throws GeneralException if the AI service is not configured, the HTTP
     *                          request fails, or the response is not valid JSON
     */
    public static Map<String, Object> generateStructured(DispatchContext dctx,
            List<Map<String, Object>> messages,
            Map<String, Object> schema) throws GeneralException {
        ProviderConfig provider = resolveProvider();
        if (provider == null) {
            throw new GeneralException(
                    "AI service is not available. Check ai.properties configuration.");
        }

        // Build schema instruction message
        String schemaJson;
        try {
            schemaJson = OBJECT_MAPPER.writeValueAsString(schema);
        } catch (Exception e) {
            Debug.logWarning("AiWorker: could not serialise schema map: " + e.getMessage(), MODULE);
            schemaJson = schema.toString();
        }

        List<Map<String, Object>> augmentedMessages = new ArrayList<>(messages);
        Map<String, Object> schemaInstruction = new java.util.LinkedHashMap<>();
        schemaInstruction.put("role", "system");
        schemaInstruction.put("content",
                "Respond with a JSON object matching this schema: " + schemaJson);
        augmentedMessages.add(schemaInstruction);

        AiChatClient client = new AiHttpClient();
        AiChatClient.ChatResponse response = client.chat(augmentedMessages,
                Collections.emptyList(), null, provider, null);

        String content = response.getContent();
        if (UtilValidate.isEmpty(content)) {
            throw new GeneralException("AiWorker: LLM returned empty content for generateStructured.");
        }

        try {
            return OBJECT_MAPPER.readValue(content,
                    new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            Debug.logError(e, "AiWorker: generateStructured failed to parse LLM response as JSON", MODULE);
            throw new GeneralException(
                    "AI generateStructured failed: response was not valid JSON. " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /**
     * Resolves the provider to use for simple generate calls.
     * Returns {@code null} if no providers are configured.
     */
    private static ProviderConfig resolveProvider() {
        if (AiContainer.getProviderRegistry() == null) {
            return null;
        }
        ProviderConfig provider = AiContainer.getProviderRegistry().getProvider(DEFAULT_PROVIDER);
        if (provider == null && !AiContainer.getProviderRegistry().getProviderNames().isEmpty()) {
            String firstName = AiContainer.getProviderRegistry().getProviderNames().iterator().next();
            provider = AiContainer.getProviderRegistry().getProvider(firstName);
            Debug.logInfo("AiWorker: '" + DEFAULT_PROVIDER
                    + "' not configured; falling back to provider '" + firstName + "'.", MODULE);
        }
        return provider;
    }
}
