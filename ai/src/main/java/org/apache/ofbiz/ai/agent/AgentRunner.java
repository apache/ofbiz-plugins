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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.ofbiz.ai.container.AiContainer;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

/**
 * Executes the agentic loop for a single {@code agentRun} invocation.
 *
 * <p>The runner loads the agent definition and provider configuration from
 * {@link AiContainer}, builds the initial message list, then iterates up to
 * {@code maxIterations} times: calling the LLM, executing any requested tool
 * calls via {@link DispatchContext#getDispatcher()}, and feeding the results
 * back into the conversation.  The loop exits when the model returns a
 * {@code "stop"} finish reason, the iteration cap is reached, or the model
 * returns an unexpected finish reason.
 *
 * <p>A package-private {@link #setChatClient(AiChatClient)} setter is provided
 * as a test seam so that Phase 2 unit tests can substitute a stub without a
 * live network connection.
 */
public final class AgentRunner {

    private static final String MODULE = AgentRunner.class.getName();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TOOL_RESULT_MAX_CHARS = 8000;

    private final String agentName;
    private final String userMessage;
    private final GenericValue userLogin;
    private final DispatchContext dctx;

    // Non-final to allow Phase 2 test seam injection
    private AiChatClient chatClient = new AiHttpClient();

    /**
     * Constructs a runner for one agent invocation.
     *
     * @param agentName   name of the agent declared in an {@code *.agent.xml} file
     * @param userMessage the user's input message
     * @param userLogin   the authenticated user for service invocations
     * @param dctx        the dispatch context used to run OFBiz services as tools
     */
    public AgentRunner(String agentName, String userMessage,
            GenericValue userLogin, DispatchContext dctx) {
        this.agentName = agentName;
        this.userMessage = userMessage;
        this.userLogin = userLogin;
        this.dctx = dctx;
    }

    /**
     * Package-private setter that replaces the default {@link AiHttpClient} with
     * a custom implementation.  Intended only for unit tests.
     *
     * @param client the {@link AiChatClient} to use for this run
     */
    void setChatClient(AiChatClient client) {
        this.chatClient = client;
    }

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Executes the agent loop and returns a {@link RunResult} when complete.
     *
     * @return the result containing the final assistant message, stop reason, and
     *         iteration count
     * @throws GeneralException if the agent or provider is not configured, a
     *                          required tool is missing, or the LLM request fails
     */
    public RunResult run() throws GeneralException {

        // 1. Load agent definition
        AgentDefinition agent = AiContainer.getAgentRegistry().getAgent(agentName);
        if (agent == null) {
            throw new GeneralException("Unknown agent: " + agentName);
        }

        // 2. Load provider config
        ProviderConfig provider = AiContainer.getProviderRegistry().getProvider(agent.getProviderName());
        if (provider == null) {
            throw new GeneralException("Unconfigured provider: " + agent.getProviderName());
        }

        // 3. Resolve tool allow-list
        ToolCatalog toolCatalog = AiContainer.getToolCatalog();
        Map<String, ToolDescriptor> allowedTools = new LinkedHashMap<>();
        for (String toolName : agent.getToolAllowList()) {
            ToolDescriptor descriptor = toolCatalog.getTool(toolName);
            if (descriptor == null) {
                throw new GeneralException("Agent '" + agentName
                        + "' references unknown tool '" + toolName + "'");
            }
            allowedTools.put(toolName, descriptor);
        }

        // 4. Build tool schemas list
        List<ObjectNode> toolSchemas = new ArrayList<>();
        for (ToolDescriptor descriptor : allowedTools.values()) {
            toolSchemas.add(descriptor.getJsonSchema());
        }

        // 5. Build initial messages list
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> systemMsg = new LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", agent.getSystemPrompt());
        messages.add(systemMsg);

        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        // 6. Agent loop
        String modelToUse = agent.getModelOverride();
        int maxIterations = agent.getMaxIterations();
        AiChatClient.ChatResponse lastResponse = null;

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            AiChatClient.ChatResponse response = chatClient.chat(
                    Collections.unmodifiableList(messages), toolSchemas, modelToUse, provider);
            lastResponse = response;

            String finishReason = response.getFinishReason();

            if ("stop".equals(finishReason)) {
                return new RunResult(response.getContent(), "stop", iteration + 1);
            }

            if ("tool_calls".equals(finishReason)) {
                List<Map<String, Object>> toolCalls = response.getToolCalls();

                // Append the assistant message with tool_calls BEFORE tool results
                Map<String, Object> assistantMsg = new LinkedHashMap<>();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", null);
                assistantMsg.put("tool_calls", toolCalls);
                messages.add(assistantMsg);

                // Execute each tool call and append its result message
                for (Map<String, Object> toolCall : toolCalls) {
                    String toolCallId = (String) toolCall.get("id");

                    @SuppressWarnings("unchecked")
                    Map<String, Object> functionMap = (Map<String, Object>) toolCall.get("function");
                    if (functionMap == null) {
                        Debug.logWarning("AgentRunner: tool call missing 'function' field; skipping.", MODULE);
                        continue;
                    }
                    String toolName = (String) functionMap.get("name");
                    String toolArgsJson = (String) functionMap.get("arguments");

                    ToolDescriptor descriptor = allowedTools.get(toolName);
                    if (descriptor == null) {
                        Debug.logWarning("AgentRunner: tool '" + toolName
                                + "' called by LLM is not in agent allow-list; skipping.", MODULE);
                        continue;
                    }

                    String resultJson = invokeToolService(descriptor, toolArgsJson);

                    Map<String, Object> toolResultMsg = new LinkedHashMap<>();
                    toolResultMsg.put("role", "tool");
                    toolResultMsg.put("tool_call_id", toolCallId);
                    toolResultMsg.put("content", resultJson);
                    messages.add(toolResultMsg);
                }

            } else {
                // Unexpected finish reason — exit loop
                Debug.logWarning("AgentRunner: unexpected finish_reason '" + finishReason
                        + "' for agent '" + agentName + "'; stopping loop.", MODULE);
                String content = lastResponse != null ? lastResponse.getContent() : null;
                return new RunResult(content, finishReason, iteration + 1);
            }
        }

        // 7. Loop exhausted without stop
        String lastContent = lastResponse != null ? lastResponse.getContent() : null;
        return new RunResult(lastContent, "max_iterations", maxIterations);
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /**
     * Invokes the OFBiz service backing a tool and serialises the result to JSON.
     *
     * @param descriptor  the tool descriptor
     * @param toolArgsJson the JSON string of arguments from the LLM
     * @return serialised service result (capped at {@value #TOOL_RESULT_MAX_CHARS} chars)
     */
    private String invokeToolService(ToolDescriptor descriptor, String toolArgsJson) {
        // Parse tool arguments JSON string to Map
        Map<String, Object> parsedArgs;
        try {
            parsedArgs = MAPPER.readValue(toolArgsJson,
                    new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException e) {
            Debug.logWarning("AgentRunner: could not parse tool args JSON for tool '"
                    + descriptor.getName() + "': " + e.getMessage(), MODULE);
            parsedArgs = new HashMap<>();
        }

        // Build service context — omit hidden params, always include userLogin
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("userLogin", userLogin);
        for (Map.Entry<String, Object> entry : parsedArgs.entrySet()) {
            if (!descriptor.getHiddenParams().contains(entry.getKey())) {
                ctx.put(entry.getKey(), entry.getValue());
            }
        }

        // Invoke the service
        Map<String, Object> serviceResult;
        try {
            serviceResult = dctx.getDispatcher().runSync(descriptor.getServiceName(), ctx);
        } catch (Exception e) {
            Debug.logError(e, "AgentRunner: service invocation failed for tool '"
                    + descriptor.getName() + "'", MODULE);
            return "Error invoking service: " + e.getMessage();
        }

        // If service returned an error, surface that as the tool result
        if (ServiceUtil.isError(serviceResult)) {
            return ServiceUtil.getErrorMessage(serviceResult);
        }

        // Serialise result map to JSON string
        try {
            String resultJson = MAPPER.writeValueAsString(serviceResult);
            if (resultJson.length() > TOOL_RESULT_MAX_CHARS) {
                resultJson = resultJson.substring(0, TOOL_RESULT_MAX_CHARS) + "...[truncated]";
            }
            return resultJson;
        } catch (JsonProcessingException e) {
            Debug.logWarning("AgentRunner: could not serialise result for tool '"
                    + descriptor.getName() + "': " + e.getMessage(), MODULE);
            return "Error serialising result: " + e.getMessage();
        }
    }

    // ---------------------------------------------------------------------------
    // Result type
    // ---------------------------------------------------------------------------

    /**
     * Immutable result returned by {@link AgentRunner#run()}.
     */
    public static final class RunResult {

        private final String assistantMessage;
        private final String stopReason;
        private final int iterationsUsed;

        /**
         * Constructs a run result.
         *
         * @param assistantMessage the final text response from the assistant, or
         *                         {@code null} if the loop ended without a stop
         * @param stopReason       one of {@code "stop"}, {@code "max_iterations"},
         *                         or an unexpected finish reason string
         * @param iterationsUsed   number of loop iterations consumed
         */
        public RunResult(String assistantMessage, String stopReason, int iterationsUsed) {
            this.assistantMessage = assistantMessage;
            this.stopReason = stopReason;
            this.iterationsUsed = iterationsUsed;
        }

        /**
         * Returns the final assistant text, or {@code null} when the loop ended
         * without a {@code "stop"} finish reason.
         *
         * @return assistant message text
         */
        public String getAssistantMessage() {
            return assistantMessage;
        }

        /**
         * Returns the reason the loop stopped: {@code "stop"}, {@code "max_iterations"},
         * or the raw finish reason string from the provider.
         *
         * @return stop reason
         */
        public String getStopReason() {
            return stopReason;
        }

        /**
         * Returns the number of loop iterations that were executed.
         *
         * @return iterations used
         */
        public int getIterationsUsed() {
            return iterationsUsed;
        }
    }
}
