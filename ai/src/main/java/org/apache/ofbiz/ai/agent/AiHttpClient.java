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
import org.apache.ofbiz.base.util.UtilValidate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Production implementation of {@link AiChatClient} that sends
 * OpenAI-compatible {@code /chat/completions} requests using
 * {@link java.net.http.HttpClient} (Java 11+) and Jackson for
 * JSON serialisation and parsing.
 *
 * <p>A single instance is created at container startup and shared across all
 * agent invocations; the underlying {@link HttpClient} is thread-safe.
 */
public final class AiHttpClient implements AiChatClient {

    private static final String MODULE = AiHttpClient.class.getName();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int BODY_SNIPPET_MAX_CHARS = 500;

    private final HttpClient httpClient;

    /**
     * Constructs a new client with a 10-second TCP connect timeout.
     * Per-request read timeouts are taken from {@link ProviderConfig#getTimeoutSeconds()}.
     */
    public AiHttpClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** {@inheritDoc} */
    @Override
    public ChatResponse chat(List<Map<String, Object>> messages,
            List<ObjectNode> toolSchemas,
            String model,
            ProviderConfig provider,
            String responseSchema) throws GeneralException {

        String requestBody = buildRequestBody(messages, toolSchemas, model, provider, responseSchema);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(provider.getBaseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(provider.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + provider.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));

        for (Map.Entry<String, String> header : provider.getExtraHeaders().entrySet()) {
            requestBuilder.header(header.getKey(), header.getValue());
        }

        HttpRequest request = requestBuilder.build();

        String responseBody;
        int statusCode;
        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            statusCode = response.statusCode();
            responseBody = response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeneralException("AiHttpClient: request interrupted.", e);
        } catch (IOException e) {
            throw new GeneralException("AiHttpClient: I/O error during HTTP request: "
                    + e.getMessage(), e);
        }

        if (statusCode < 200 || statusCode >= 300) {
            String snippet = UtilValidate.isNotEmpty(responseBody)
                    ? responseBody.substring(0, Math.min(responseBody.length(), BODY_SNIPPET_MAX_CHARS))
                    : "(empty body)";
            throw new GeneralException("AiHttpClient: provider returned HTTP " + statusCode
                    + ": " + snippet);
        }

        return parseResponse(responseBody, responseSchema);
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private String buildRequestBody(List<Map<String, Object>> messages,
            List<ObjectNode> toolSchemas,
            String model,
            ProviderConfig provider,
            String responseSchema) throws GeneralException {

        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", UtilValidate.isNotEmpty(model) ? model : provider.getModel());

        ArrayNode msgArray = MAPPER.createArrayNode();
        for (Map<String, Object> msg : messages) {
            ObjectNode msgNode = MAPPER.createObjectNode();
            for (Map.Entry<String, Object> entry : msg.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof String) {
                    msgNode.put(entry.getKey(), (String) val);
                } else if (val instanceof List) {
                    // tool_calls or content arrays — serialise via MAPPER
                    try {
                        msgNode.set(entry.getKey(),
                                MAPPER.valueToTree(val));
                    } catch (IllegalArgumentException e) {
                        Debug.logWarning("AiHttpClient: could not serialise message field '"
                                + entry.getKey() + "': " + e.getMessage(), MODULE);
                    }
                } else if (val != null) {
                    msgNode.putPOJO(entry.getKey(), val);
                }
            }
            msgArray.add(msgNode);
        }
        root.set("messages", msgArray);

        if (toolSchemas != null && !toolSchemas.isEmpty()) {
            ArrayNode toolsArray = MAPPER.createArrayNode();
            for (ObjectNode schema : toolSchemas) {
                ObjectNode toolNode = MAPPER.createObjectNode();
                toolNode.put("type", "function");
                // ToolCatalog stores the schema with Anthropic-style "input_schema".
                // OpenAI-compatible endpoints expect "parameters" instead.
                ObjectNode functionNode = schema.deepCopy();
                JsonNode inputSchema = functionNode.remove("input_schema");
                if (inputSchema != null) {
                    functionNode.set("parameters", inputSchema);
                }
                toolNode.set("function", functionNode);
                toolsArray.add(toolNode);
            }
            root.set("tools", toolsArray);
            root.put("tool_choice", "auto");
        }

        if (responseSchema != null && !responseSchema.isBlank()) {
            try {
                ObjectNode jsonSchemaNode = (ObjectNode) MAPPER.readTree(responseSchema);
                ObjectNode responseFormat = MAPPER.createObjectNode();
                responseFormat.put("type", "json_schema");
                ObjectNode jsonSchemaWrapper = MAPPER.createObjectNode();
                jsonSchemaWrapper.put("name", "agent_response");
                jsonSchemaWrapper.set("schema", jsonSchemaNode);
                jsonSchemaWrapper.put("strict", true);
                responseFormat.set("json_schema", jsonSchemaWrapper);
                root.set("response_format", responseFormat);
            } catch (Exception e) {
                Debug.logWarning("AiHttpClient: could not parse responseSchema for "
                        + "response_format, sending without it: " + e.getMessage(), MODULE);
            }
        }

        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new GeneralException(
                    "AiHttpClient: failed to serialise request body: " + e.getMessage(), e);
        }
    }

    private ChatResponse parseResponse(String responseBody, String responseSchema) throws GeneralException {
        JsonNode root;
        try {
            root = MAPPER.readTree(responseBody);
        } catch (JsonProcessingException e) {
            throw new GeneralException(
                    "AiHttpClient: failed to parse response JSON: " + e.getMessage(), e);
        }

        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.size() == 0) {
            throw new GeneralException(
                    "AiHttpClient: response has no choices array.");
        }

        JsonNode firstChoice = choices.get(0);
        String finishReason = firstChoice.path("finish_reason").asText("stop");
        JsonNode messageNode = firstChoice.path("message");

        String content = null;
        JsonNode contentNode = messageNode.path("content");
        if (!contentNode.isMissingNode() && !contentNode.isNull()) {
            content = contentNode.asText();
        }

        List<Map<String, Object>> toolCalls = null;
        JsonNode toolCallsNode = messageNode.path("tool_calls");
        if (toolCallsNode.isArray() && toolCallsNode.size() > 0) {
            toolCalls = new ArrayList<>();
            for (JsonNode tc : toolCallsNode) {
                toolCalls.add(toolCallToMap(tc));
            }
        }

        int inputTokens = root.path("usage").path("prompt_tokens").asInt(0);
        int outputTokens = root.path("usage").path("completion_tokens").asInt(0);

        Map<String, Object> structuredResult = null;
        if (responseSchema != null && content != null && !content.isBlank()) {
            try {
                structuredResult = MAPPER.readValue(content,
                        new com.fasterxml.jackson.core.type.TypeReference<
                                java.util.Map<String, Object>>() { });
            } catch (Exception e) {
                Debug.logWarning("AiHttpClient: structured output response is not valid JSON, "
                        + "returning as text: " + e.getMessage(), MODULE);
            }
        }

        return new ChatResponse(finishReason, content, toolCalls,
                inputTokens, outputTokens, structuredResult);
    }

    private Map<String, Object> toolCallToMap(JsonNode tc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", tc.path("id").asText());
        map.put("type", tc.path("type").asText("function"));

        Map<String, Object> function = new LinkedHashMap<>();
        JsonNode fnNode = tc.path("function");
        function.put("name", fnNode.path("name").asText());
        function.put("arguments", fnNode.path("arguments").asText());

        map.put("function", function);
        return map;
    }
}
