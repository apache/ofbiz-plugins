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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * {@link AiChatClient} implementation for Anthropic's Messages API.
 *
 * <p>Translates the canonical OFBiz message format (OpenAI-shaped) to
 * Anthropic's wire format on the way in, and normalises the Anthropic
 * response back to the canonical {@link ChatResponse} on the way out.
 * This allows {@link AgentRunner} to remain provider-agnostic.
 */
public final class AnthropicChatClient implements AiChatClient {

    private static final String MODULE = AnthropicChatClient.class.getName();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int BODY_SNIPPET_MAX_CHARS = 500;
    private static final int DEFAULT_MAX_TOKENS = 4096;

    private final HttpClient httpClient;

    public AnthropicChatClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public ChatResponse chat(List<Map<String, Object>> messages,
            List<ObjectNode> toolSchemas,
            String model,
            ProviderConfig provider) throws GeneralException {

        String requestBody = buildRequestBody(messages, toolSchemas, model, provider);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(provider.getBaseUrl() + "/messages"))
                .timeout(Duration.ofSeconds(provider.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("x-api-key", provider.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));

        for (Map.Entry<String, String> header : provider.getExtraHeaders().entrySet()) {
            requestBuilder.header(header.getKey(), header.getValue());
        }

        String responseBody;
        int statusCode;
        try {
            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            statusCode = response.statusCode();
            responseBody = response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeneralException("AnthropicChatClient: request interrupted.", e);
        } catch (IOException e) {
            throw new GeneralException("AnthropicChatClient: I/O error: " + e.getMessage(), e);
        }

        if (statusCode < 200 || statusCode >= 300) {
            String snippet = responseBody != null
                    ? responseBody.substring(0, Math.min(responseBody.length(), BODY_SNIPPET_MAX_CHARS))
                    : "(empty body)";
            throw new GeneralException("AnthropicChatClient: provider returned HTTP "
                    + statusCode + ": " + snippet);
        }

        return parseResponse(responseBody);
    }

    // ---------------------------------------------------------------------------
    // Request building
    // ---------------------------------------------------------------------------

    private String buildRequestBody(List<Map<String, Object>> messages,
            List<ObjectNode> toolSchemas,
            String model,
            ProviderConfig provider) throws GeneralException {

        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", (model != null && !model.isBlank()) ? model : provider.getModel());
        root.put("max_tokens", DEFAULT_MAX_TOKENS);

        // Extract system message — Anthropic puts it in a top-level "system" field
        String systemPrompt = null;
        List<Map<String, Object>> nonSystemMessages = new ArrayList<>();
        for (Map<String, Object> msg : messages) {
            if ("system".equals(msg.get("role"))) {
                Object content = msg.get("content");
                if (content instanceof String) {
                    systemPrompt = (String) content;
                }
            } else {
                nonSystemMessages.add(msg);
            }
        }
        if (systemPrompt != null) {
            root.put("system", systemPrompt);
        }

        // Convert remaining messages to Anthropic format
        ArrayNode msgArray = MAPPER.createArrayNode();
        for (Map<String, Object> msg : nonSystemMessages) {
            String role = (String) msg.get("role");
            ObjectNode msgNode = convertMessage(role, msg);
            if (msgNode != null) {
                msgArray.add(msgNode);
            }
        }
        root.set("messages", msgArray);

        // Tools — ToolCatalog already stores schemas with "input_schema" key (Anthropic native)
        if (toolSchemas != null && !toolSchemas.isEmpty()) {
            ArrayNode toolsArray = MAPPER.createArrayNode();
            for (ObjectNode schema : toolSchemas) {
                // schema has: name, description, input_schema — exactly what Anthropic expects
                toolsArray.add(schema.deepCopy());
            }
            root.set("tools", toolsArray);
        }

        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new GeneralException(
                    "AnthropicChatClient: failed to serialise request body: " + e.getMessage(), e);
        }
    }

    /**
     * Converts a single canonical message map to an Anthropic-format ObjectNode.
     * Returns null if the message cannot be converted (logged as warning).
     */
    private ObjectNode convertMessage(String role, Map<String, Object> msg) {
        ObjectNode node = MAPPER.createObjectNode();

        if ("tool".equals(role)) {
            // Canonical: {role:"tool", tool_call_id:"...", content:"..."}
            // Anthropic: {role:"user", content:[{type:"tool_result", tool_use_id:"...", content:"..."}]}
            node.put("role", "user");
            ArrayNode contentArray = MAPPER.createArrayNode();
            ObjectNode resultBlock = MAPPER.createObjectNode();
            resultBlock.put("type", "tool_result");
            resultBlock.put("tool_use_id", (String) msg.get("tool_call_id"));
            Object content = msg.get("content");
            resultBlock.put("content", content != null ? content.toString() : "");
            contentArray.add(resultBlock);
            node.set("content", contentArray);
            return node;
        }

        if ("assistant".equals(role) && msg.containsKey("tool_calls")) {
            // Canonical: {role:"assistant", content:null, tool_calls:[{id, type:"function", function:{name, arguments:"{}"}}]}
            // Anthropic: {role:"assistant", content:[{type:"tool_use", id, name, input:{MAP}}]}
            node.put("role", "assistant");
            ArrayNode contentArray = MAPPER.createArrayNode();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) msg.get("tool_calls");
            if (toolCalls != null) {
                for (Map<String, Object> tc : toolCalls) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                    if (fn == null) {
                        continue;
                    }
                    ObjectNode toolUse = MAPPER.createObjectNode();
                    toolUse.put("type", "tool_use");
                    toolUse.put("id", (String) tc.get("id"));
                    toolUse.put("name", (String) fn.get("name"));
                    // arguments is a JSON string — parse it to a Map for Anthropic's "input"
                    String argsJson = (String) fn.get("arguments");
                    try {
                        Map<String, Object> inputMap = MAPPER.readValue(argsJson,
                                new TypeReference<Map<String, Object>>() { });
                        toolUse.set("input", MAPPER.valueToTree(inputMap));
                    } catch (Exception e) {
                        Debug.logWarning("AnthropicChatClient: could not parse tool arguments '"
                                + argsJson + "': " + e.getMessage(), MODULE);
                        toolUse.set("input", MAPPER.createObjectNode());
                    }
                    contentArray.add(toolUse);
                }
            }
            node.set("content", contentArray);
            return node;
        }

        // Regular user/assistant text message
        node.put("role", role);
        Object content = msg.get("content");
        if (content instanceof String) {
            node.put("content", (String) content);
        } else if (content == null) {
            node.put("content", "");
        } else {
            node.put("content", content.toString());
        }
        return node;
    }

    // ---------------------------------------------------------------------------
    // Response parsing
    // ---------------------------------------------------------------------------

    private ChatResponse parseResponse(String responseBody) throws GeneralException {
        JsonNode root;
        try {
            root = MAPPER.readTree(responseBody);
        } catch (JsonProcessingException e) {
            throw new GeneralException(
                    "AnthropicChatClient: failed to parse response JSON: " + e.getMessage(), e);
        }

        // Normalise stop_reason to canonical finish reason
        String stopReason = root.path("stop_reason").asText("end_turn");
        String finishReason;
        if ("tool_use".equals(stopReason)) {
            finishReason = "tool_calls";
        } else {
            finishReason = "stop";
        }

        // Parse content array
        String textContent = null;
        List<Map<String, Object>> toolCalls = null;
        JsonNode contentArray = root.path("content");
        if (contentArray.isArray()) {
            for (JsonNode block : contentArray) {
                String type = block.path("type").asText();
                if ("text".equals(type)) {
                    textContent = block.path("text").asText();
                } else if ("tool_use".equals(type)) {
                    if (toolCalls == null) {
                        toolCalls = new ArrayList<>();
                    }
                    toolCalls.add(toolUseBlockToCanonical(block));
                }
            }
        }

        int inputTokens = root.path("usage").path("input_tokens").asInt(0);
        int outputTokens = root.path("usage").path("output_tokens").asInt(0);

        return new ChatResponse(finishReason, textContent, toolCalls, inputTokens, outputTokens);
    }

    /**
     * Converts an Anthropic {@code tool_use} content block to the canonical tool-call Map.
     * Canonical: {@code {id, type:"function", function:{name, arguments:"{JSON_STRING}"}}}
     */
    private Map<String, Object> toolUseBlockToCanonical(JsonNode block) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", block.path("id").asText());
        map.put("type", "function");

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", block.path("name").asText());
        // Convert input Map back to JSON string to match canonical format
        JsonNode inputNode = block.path("input");
        String argsJson;
        try {
            argsJson = MAPPER.writeValueAsString(inputNode);
        } catch (JsonProcessingException e) {
            Debug.logWarning("AnthropicChatClient: could not serialise tool input: "
                    + e.getMessage(), MODULE);
            argsJson = "{}";
        }
        function.put("arguments", argsJson);
        map.put("function", function);
        return map;
    }
}
