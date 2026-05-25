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
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
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

    // Package-private fields used only by the test constructor (null in production)
    private AgentDefinition testAgentDef;
    private ProviderConfig testProvider;
    private Map<String, ToolDescriptor> testTools;

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
     * Package-private constructor for unit tests — bypasses {@link AiContainer} registries.
     * Pass {@code null} for {@code dctx} when the test tool allow-list is empty and
     * {@link #invokeToolService} will never be called.
     *
     * @param agentDef       agent definition to use instead of registry lookup
     * @param provider       provider config to use instead of registry lookup
     * @param toolDescriptors list of tools available to this agent
     * @param userMessage    the user's input message
     * @param userLogin      authenticated user (may be {@code null} in tests)
     * @param dctx           dispatch context (may be {@code null} when no tools are invoked)
     */
    AgentRunner(AgentDefinition agentDef, ProviderConfig provider,
            List<ToolDescriptor> toolDescriptors,
            String userMessage, GenericValue userLogin, DispatchContext dctx) {
        this.agentName = agentDef.getName();
        this.userMessage = userMessage;
        this.userLogin = userLogin;
        this.dctx = dctx;
        this.testAgentDef = agentDef;
        this.testProvider = provider;
        this.testTools = buildToolMap(toolDescriptors);
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

        // 1. Load agent definition — use test seam if available
        AgentDefinition agent = testAgentDef != null
                ? testAgentDef
                : AiContainer.getAgentRegistry().getAgent(agentName);
        if (agent == null) {
            throw new GeneralException("Unknown agent: " + agentName);
        }

        // 2. Load provider config — use test seam if available
        ProviderConfig provider = testProvider != null
                ? testProvider
                : AiContainer.getProviderRegistry().getProvider(agent.getProviderName());
        if (provider == null) {
            throw new GeneralException("Unconfigured provider: " + agent.getProviderName());
        }

        // 3. Resolve tool allow-list — use test seam if available
        Map<String, ToolDescriptor> allowedTools;
        if (testTools != null) {
            allowedTools = testTools;
        } else {
            ToolCatalog toolCatalog = AiContainer.getToolCatalog();
            allowedTools = new LinkedHashMap<>();
            for (String toolName : agent.getToolAllowList()) {
                ToolDescriptor descriptor = toolCatalog.getTool(toolName);
                if (descriptor == null) {
                    throw new GeneralException("Agent '" + agentName
                            + "' references unknown tool '" + toolName + "'");
                }
                allowedTools.put(toolName, descriptor);
            }
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

        // 6. Persistence — create run record (skip when delegator is unavailable, e.g. unit tests)
        Delegator delegator = dctx != null ? dctx.getDelegator() : null;
        String runId = null;
        GenericValue runRecord = null;
        if (delegator != null) {
            runId = delegator.getNextSeqId("AiAgentRun");
            runRecord = delegator.makeValue("AiAgentRun");
            runRecord.set("runId", runId);
            runRecord.set("agentName", agentName);
            runRecord.set("userLoginId", userLogin != null ? userLogin.getString("userLoginId") : null);
            runRecord.set("startedAt", UtilDateTime.nowTimestamp());
            runRecord.set("userMessage", userMessage);
            runRecord.set("statusId", "AI_RUN_STARTED");
            try {
                delegator.create(runRecord);
            } catch (GenericEntityException e) {
                Debug.logError(e, "AgentRunner: failed to create AiAgentRun record", MODULE);
            }
        }

        // 7. Agent loop
        String modelToUse = agent.getModelOverride();
        int maxIterations = agent.getMaxIterations();
        AiChatClient.ChatResponse lastResponse = null;
        long totalInputTokens = 0L;
        long totalOutputTokens = 0L;
        RunResult loopResult = null;

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            AiChatClient.ChatResponse response = chatClient.chat(
                    Collections.unmodifiableList(messages), toolSchemas, modelToUse, provider);
            lastResponse = response;
            totalInputTokens += response.getInputTokens();
            totalOutputTokens += response.getOutputTokens();

            String finishReason = response.getFinishReason();

            if ("stop".equals(finishReason)) {
                loopResult = new RunResult(response.getContent(), "stop", iteration + 1);
                break;
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

                    String resultJson = invokeToolService(descriptor, toolArgsJson, runId, delegator);

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
                String content = lastResponse.getContent();
                loopResult = new RunResult(content, finishReason, iteration + 1);
                break;
            }
        }

        // 8. Loop exhausted without stop
        if (loopResult == null) {
            String lastContent = lastResponse != null ? lastResponse.getContent() : null;
            loopResult = new RunResult(lastContent, "max_iterations", maxIterations);
        }

        // 9. Persistence — update run record with completion data
        if (delegator != null && runRecord != null) {
            runRecord.set("endedAt", UtilDateTime.nowTimestamp());
            runRecord.set("assistantMessage", loopResult.getAssistantMessage());
            runRecord.set("iterationsUsed", (long) loopResult.getIterationsUsed());
            runRecord.set("inputTokens", totalInputTokens);
            runRecord.set("outputTokens", totalOutputTokens);
            boolean failed = "max_iterations".equals(loopResult.getStopReason())
                    || (!"stop".equals(loopResult.getStopReason()));
            runRecord.set("statusId", failed ? "AI_RUN_FAILED" : "AI_RUN_COMPLETED");
            try {
                runRecord.store();
            } catch (GenericEntityException e) {
                Debug.logError(e, "AgentRunner: failed to update AiAgentRun record for runId=" + runId, MODULE);
            }
        }

        return loopResult;
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /**
     * Invokes the OFBiz service backing a tool and serialises the result to JSON.
     * Persists an {@code AiAgentToolCall} row when {@code delegator} is non-null.
     *
     * @param descriptor   the tool descriptor
     * @param toolArgsJson the JSON string of arguments from the LLM
     * @param runId        the parent run identifier (may be {@code null} in tests)
     * @param delegator    the entity delegator for persistence (may be {@code null} in tests)
     * @return serialised service result (capped at {@value #TOOL_RESULT_MAX_CHARS} chars)
     */
    private String invokeToolService(ToolDescriptor descriptor, String toolArgsJson,
            String runId, Delegator delegator) {
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
        boolean callFailed = false;
        String resultJson;
        try {
            serviceResult = dctx.getDispatcher().runSync(descriptor.getServiceName(), ctx);
        } catch (Exception e) {
            Debug.logError(e, "AgentRunner: service invocation failed for tool '"
                    + descriptor.getName() + "'", MODULE);
            callFailed = true;
            persistToolCall(delegator, runId, descriptor.getName(), toolArgsJson,
                    "Error invoking service: " + e.getMessage(), callFailed);
            return "Error invoking service: " + e.getMessage();
        }

        // If service returned an error, surface that as the tool result
        if (ServiceUtil.isError(serviceResult)) {
            callFailed = true;
            String errorMsg = ServiceUtil.getErrorMessage(serviceResult);
            persistToolCall(delegator, runId, descriptor.getName(), toolArgsJson, errorMsg, callFailed);
            return errorMsg;
        }

        // Serialise result map to JSON string
        try {
            resultJson = MAPPER.writeValueAsString(serviceResult);
            if (resultJson.length() > TOOL_RESULT_MAX_CHARS) {
                resultJson = resultJson.substring(0, TOOL_RESULT_MAX_CHARS) + "...[truncated]";
            }
        } catch (JsonProcessingException e) {
            Debug.logWarning("AgentRunner: could not serialise result for tool '"
                    + descriptor.getName() + "': " + e.getMessage(), MODULE);
            callFailed = true;
            resultJson = "Error serialising result: " + e.getMessage();
        }

        persistToolCall(delegator, runId, descriptor.getName(), toolArgsJson, resultJson, callFailed);
        return resultJson;
    }

    /**
     * Builds a {@link Map} from tool name to {@link ToolDescriptor} from a list.
     * Used by the package-private test constructor.
     *
     * @param descriptors list of tool descriptors
     * @return ordered map keyed by tool name
     */
    private static Map<String, ToolDescriptor> buildToolMap(List<ToolDescriptor> descriptors) {
        Map<String, ToolDescriptor> map = new LinkedHashMap<>();
        if (descriptors != null) {
            for (ToolDescriptor d : descriptors) {
                map.put(d.getName(), d);
            }
        }
        return map;
    }

    /**
     * Persists one {@code AiAgentToolCall} row.  Errors are logged but never re-thrown
     * so that a persistence failure cannot abort a completed LLM interaction.
     *
     * @param delegator    entity delegator (no-op when {@code null})
     * @param runId        parent run identifier
     * @param toolName     name of the tool that was called
     * @param callArguments raw JSON arguments string from the LLM
     * @param callResult   serialised result (or error message)
     * @param callFailed   whether the tool invocation failed
     */
    private void persistToolCall(Delegator delegator, String runId, String toolName,
            String callArguments, String callResult, boolean callFailed) {
        if (delegator == null) {
            return;
        }
        try {
            String callId = delegator.getNextSeqId("AiAgentToolCall");
            GenericValue callRecord = delegator.makeValue("AiAgentToolCall");
            callRecord.set("callId", callId);
            callRecord.set("runId", runId);
            callRecord.set("toolName", toolName);
            callRecord.set("callArguments", callArguments);
            callRecord.set("callResult", callResult);
            callRecord.set("calledAt", UtilDateTime.nowTimestamp());
            callRecord.set("statusId", callFailed ? "AI_TOOL_FAILED" : "AI_TOOL_COMPLETED");
            delegator.create(callRecord);
        } catch (GenericEntityException e) {
            Debug.logError(e, "AgentRunner: failed to persist AiAgentToolCall for tool '"
                    + toolName + "' in run " + runId, MODULE);
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
