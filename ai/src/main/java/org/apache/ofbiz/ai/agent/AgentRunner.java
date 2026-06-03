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
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.security.Security;
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

    // Optional thread id for multi-turn conversation memory
    private String threadId;

    // When true, any tool_calls batch triggers human approval suspension
    private boolean approvalRequired = false;

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

    /**
     * Sets the conversation thread id for multi-turn memory.  When set, the runner
     * will load prior messages from {@code AiConversationThread} / {@code AiConversationMessage}
     * before the loop and persist the new exchange after the loop completes.
     *
     * @param threadId the thread identifier, or {@code null} to disable persistence
     */
    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    /**
     * When set to {@code true}, any tool_calls batch returned by the LLM will cause
     * the agent loop to suspend and return a {@link RunResult} with stop reason
     * {@code "approval_required"} rather than executing the tools immediately.
     *
     * @param approvalRequired {@code true} to enable human approval gating
     */
    public void setApprovalRequired(boolean approvalRequired) {
        this.approvalRequired = approvalRequired;
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

        // 1. Load agent definition — use test seam if available, otherwise query DB
        AgentDefinition agent;
        if (testAgentDef != null) {
            agent = testAgentDef;
        } else if (dctx != null) {
            agent = loadAgentFromDb(agentName, dctx.getDelegator());
        } else {
            throw new GeneralException("Unknown agent: " + agentName
                    + " (no delegator available for DB lookup)");
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

        // 5b. Load prior conversation history when a threadId is supplied
        Delegator delegatorForThread = dctx != null ? dctx.getDelegator() : null;
        if (threadId != null && delegatorForThread != null) {
            loadThreadHistory(messages, threadId, delegatorForThread, agent.getSystemPrompt());
        }

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

        // 7–9. Execute the agent loop
        RunResult loopResult = runLoop(messages, toolSchemas, agent, provider,
                allowedTools, runId, runRecord);

        // 10. Conversation memory — persist user + assistant messages when threadId is set
        if (threadId != null && delegatorForThread != null) {
            String userLoginId = userLogin != null ? userLogin.getString("userLoginId") : null;
            saveThreadMessages(threadId, agentName, userLoginId, userMessage,
                    loopResult.getAssistantMessage(), delegatorForThread);
        }

        return loopResult;
    }

    /**
     * Resumes the agent loop after a human has approved a proposal.
     * The caller is responsible for executing the pending tool calls and
     * appending their results to {@code messagesWithToolResults} before
     * calling this method.
     *
     * <p>If any tool encountered during the resumed loop also has
     * {@code requires-approval="true"}, the loop will suspend again and return
     * a new result with stop reason {@code "approval_required"}.
     *
     * @param agentName              name of the agent declared in an {@code *.agent.xml} file
     * @param messagesWithToolResults conversation messages including tool results for the approved calls
     * @param userLogin              the authenticated user for subsequent tool invocations
     * @param dctx                   dispatch context used to run OFBiz services as tools
     * @param existingRunId          run ID of the original {@code AiAgentRun} record to update
     * @return the final run result
     * @throws GeneralException if the agent or provider is not configured, or if the
     *                          framework container is not started
     */
    public static RunResult continueFromApproval(
            String agentName,
            List<Map<String, Object>> messagesWithToolResults,
            GenericValue userLogin,
            DispatchContext dctx,
            String existingRunId) throws GeneralException {

        if (AiContainer.getProviderRegistry() == null) {
            throw new GeneralException("ProviderRegistry is not available — AiContainer may not be started");
        }
        AgentDefinition agent = loadAgentFromDb(agentName, dctx.getDelegator());
        ProviderConfig provider = AiContainer.getProviderRegistry().getProvider(agent.getProviderName());
        if (provider == null) {
            throw new GeneralException("Unconfigured provider: " + agent.getProviderName());
        }

        ToolCatalog toolCatalog = AiContainer.getToolCatalog();
        Map<String, ToolDescriptor> allowedTools = new LinkedHashMap<>();
        for (String toolName : agent.getToolAllowList()) {
            ToolDescriptor d = toolCatalog.getTool(toolName);
            if (d != null) {
                allowedTools.put(toolName, d);
            }
        }

        List<ObjectNode> toolSchemas = new ArrayList<>();
        for (ToolDescriptor d : allowedTools.values()) {
            toolSchemas.add(d.getJsonSchema());
        }

        // Load the existing run record so runLoop can update it
        Delegator delegator = dctx != null ? dctx.getDelegator() : null;
        GenericValue runRecord = null;
        if (delegator != null && existingRunId != null) {
            try {
                runRecord = EntityQuery.use(delegator)
                        .from("AiAgentRun").where("runId", existingRunId).queryOne();
            } catch (GenericEntityException e) {
                Debug.logWarning("AgentRunner: could not load run record for continuation: "
                        + e.getMessage(), MODULE);
            }
        }

        AgentRunner runner = new AgentRunner(agentName, "", userLogin, dctx);
        return runner.runLoop(new ArrayList<>(messagesWithToolResults),
                toolSchemas, agent, provider, allowedTools, existingRunId, runRecord);
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /**
     * Executes the agent loop (steps 7–9): iterates up to {@code maxIterations},
     * calling the LLM and dispatching tool calls, then updates the run record.
     * Returns a {@link RunResult} describing how the loop terminated.
     *
     * <p>When {@link #approvalRequired} is {@code true}, or when any tool in a
     * tool_calls batch has {@link ToolDescriptor#isRequiresApproval()} set, the
     * loop suspends immediately — persisting a proposal record — and returns a
     * result with stop reason {@code "approval_required"}.
     *
     * @param messages     the conversation message list (mutated in place)
     * @param toolSchemas  JSON schemas for the tools available to this agent
     * @param agent        the resolved agent definition
     * @param provider     the resolved provider configuration
     * @param allowedTools map of tool name to descriptor for this agent
     * @param runId        the identifier of the {@code AiAgentRun} record
     * @param runRecord    the {@code AiAgentRun} GenericValue to update on completion
     * @return the loop result
     * @throws GeneralException if a chat request fails
     */
    private RunResult runLoop(
            List<Map<String, Object>> messages,
            List<ObjectNode> toolSchemas,
            AgentDefinition agent,
            ProviderConfig provider,
            Map<String, ToolDescriptor> allowedTools,
            String runId,
            GenericValue runRecord) throws GeneralException {

        Delegator delegator = dctx != null ? dctx.getDelegator() : null;

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

                // Check if human approval is required for any tool in this batch
                boolean needsApproval = this.approvalRequired;
                if (!needsApproval) {
                    for (Map<String, Object> tc : toolCalls) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> fnCheck = (Map<String, Object>) tc.get("function");
                        if (fnCheck != null) {
                            ToolDescriptor tdCheck = allowedTools.get((String) fnCheck.get("name"));
                            if (tdCheck != null && tdCheck.isRequiresApproval()) {
                                needsApproval = true;
                                break;
                            }
                        }
                    }
                }

                if (needsApproval) {
                    // Append the assistant tool_calls message before suspending
                    Map<String, Object> assistantSuspendMsg = new LinkedHashMap<>();
                    assistantSuspendMsg.put("role", "assistant");
                    assistantSuspendMsg.put("content", null);
                    assistantSuspendMsg.put("tool_calls", toolCalls);
                    messages.add(assistantSuspendMsg);

                    String proposalId = null;
                    if (delegator != null && runId != null) {
                        proposalId = persistProposal(delegator, runId, toolCalls, messages);
                    }
                    loopResult = new RunResult(null, "approval_required", iteration + 1, proposalId);
                    break;
                }

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
            String stopReason = loopResult.getStopReason();
            String runStatus;
            if ("stop".equals(stopReason)) {
                runStatus = "AI_RUN_COMPLETED";
            } else if ("approval_required".equals(stopReason)) {
                runStatus = "AI_RUN_SUSPENDED";
            } else {
                runStatus = "AI_RUN_FAILED";
            }
            runRecord.set("statusId", runStatus);
            try {
                runRecord.store();
            } catch (GenericEntityException e) {
                Debug.logError(e, "AgentRunner: failed to update AiAgentRun record for runId=" + runId, MODULE);
            }
        }

        return loopResult;
    }

    /**
     * Persists an {@code AiAgentProposal} and associated {@code AiAgentProposalTool} rows
     * for a suspended tool_calls batch awaiting human approval.
     *
     * @param delegator  entity delegator for database access
     * @param runId      the parent run identifier
     * @param toolCalls  the tool call batch to persist
     * @param messages   the full conversation message list at time of suspension
     * @return the generated proposal identifier, or {@code null} if persistence failed
     */
    private String persistProposal(Delegator delegator, String runId,
            List<Map<String, Object>> toolCalls, List<Map<String, Object>> messages) {
        try {
            String proposalId = delegator.getNextSeqId("AiAgentProposal");
            String messagesJson = MAPPER.writeValueAsString(messages);

            GenericValue proposal = delegator.makeValue("AiAgentProposal");
            proposal.set("proposalId", proposalId);
            proposal.set("runId", runId);
            proposal.set("agentName", agentName);
            proposal.set("userLoginId", userLogin != null ? userLogin.getString("userLoginId") : null);
            proposal.set("messagesJson", messagesJson);
            proposal.set("statusId", "AI_PROPOSAL_PENDING");
            delegator.create(proposal);

            for (Map<String, Object> tc : toolCalls) {
                String toolCallId = (String) tc.get("id");
                @SuppressWarnings("unchecked")
                Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                if (fn == null) {
                    continue;
                }
                String toolName = (String) fn.get("name");
                String callArgs = (String) fn.get("arguments");

                GenericValue propTool = delegator.makeValue("AiAgentProposalTool");
                propTool.set("proposalToolId", delegator.getNextSeqId("AiAgentProposalTool"));
                propTool.set("proposalId", proposalId);
                propTool.set("toolCallId", toolCallId);
                propTool.set("toolName", toolName);
                propTool.set("callArguments", callArgs);
                delegator.create(propTool);
            }
            return proposalId;
        } catch (Exception e) {
            Debug.logError(e, "AgentRunner: failed to persist proposal for run " + runId, MODULE);
            return null;
        }
    }

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

        // Permission check — enforce before dispatching the service
        String requiredPermission = descriptor.getRequiredPermission();
        if (requiredPermission != null && userLogin != null && dctx != null) {
            Security security = dctx.getSecurity();
            if (!security.hasPermission(requiredPermission, userLogin)) {
                String permDenied = "{\"error\": \"Permission denied: requires "
                        + requiredPermission + "\"}";
                persistToolCall(delegator, runId, descriptor.getName(), toolArgsJson,
                        permDenied, true);
                return permDenied;
            }
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
     * Loads an {@link AgentDefinition} from the {@code AiAgentDef} database
     * entity and its associated {@code AiAgentToolGrant} rows.
     *
     * @param name      agent name to look up
     * @param delegator OFBiz delegator for DB access
     * @return the populated {@link AgentDefinition}
     * @throws GeneralException if the agent is not found, is disabled, or a DB error occurs
     */
    private static AgentDefinition loadAgentFromDb(String name, Delegator delegator)
            throws GeneralException {
        try {
            GenericValue row = EntityQuery.use(delegator)
                    .from("AiAgentDef").where("agentName", name).queryOne();
            if (row == null) {
                throw new GeneralException("Unknown agent: " + name);
            }
            if ("AI_AGENT_DISABLED".equals(row.getString("statusId"))) {
                throw new GeneralException("Agent '" + name + "' is disabled.");
            }
            List<GenericValue> grants = EntityQuery.use(delegator)
                    .from("AiAgentToolGrant").where("agentName", name).queryList();
            List<String> toolAllowList = new ArrayList<>();
            for (GenericValue grant : grants) {
                toolAllowList.add(grant.getString("toolName"));
            }
            String modelOverride = row.getString("modelName");
            if (UtilValidate.isEmpty(modelOverride)) {
                modelOverride = null;
            }
            long maxIterLong = row.getLong("maxIterations") != null
                    ? row.getLong("maxIterations") : 6L;
            return new AgentDefinition(
                    name,
                    row.getString("providerName"),
                    modelOverride,
                    (int) maxIterLong,
                    row.getString("systemPrompt"),
                    toolAllowList);
        } catch (GenericEntityException e) {
            throw new GeneralException("Failed to load agent '" + name + "' from database", e);
        }
    }

    /**
     * Loads prior conversation messages for the given thread into {@code messages}.
     * Messages are inserted between the system prompt (index 0) and the current user
     * message (last entry), oldest first.  If the thread does not exist, is archived,
     * or the history would exceed the token budget, oldest pairs are trimmed until it fits.
     *
     * @param messages     the message list being built (must contain [system, user] already)
     * @param threadId     the conversation thread identifier
     * @param delegator    entity delegator for database access
     * @param systemPrompt the agent's system prompt text (used for token budget estimation)
     */
    private static void loadThreadHistory(List<Map<String, Object>> messages,
            String threadId, Delegator delegator, String systemPrompt) {
        try {
            // Check thread exists and is not archived
            GenericValue thread = EntityQuery.use(delegator)
                    .from("AiConversationThread")
                    .where("threadId", threadId)
                    .queryOne();
            if (thread == null || "AI_THREAD_ARCHIVED".equals(thread.getString("statusId"))) {
                return; // No history to load
            }

            // Load messages ordered by sequenceNum
            List<GenericValue> history = EntityQuery.use(delegator)
                    .from("AiConversationMessage")
                    .where("threadId", threadId)
                    .orderBy("sequenceNum")
                    .queryList();

            // Estimate token budget — rough heuristic: 1 token ≈ 4 chars
            // Budget: 80,000 tokens (reserve space for system prompt + user message + LLM response)
            int tokenBudget = 80000;
            int systemPromptTokens = systemPrompt != null ? systemPrompt.length() / 4 : 0;
            int remaining = tokenBudget - systemPromptTokens;

            // Build history message list
            List<Map<String, Object>> historyMsgs = new ArrayList<>();
            for (GenericValue msg : history) {
                String role = msg.getString("role");
                String content = msg.getString("content");
                if (content == null) {
                    content = "";
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("role", role);
                m.put("content", content);
                historyMsgs.add(m);
            }

            // Trim oldest message pairs until within budget
            int totalChars = historyMsgs.stream()
                    .mapToInt(m -> ((String) m.get("content")).length()).sum();
            while (totalChars > remaining * 4 && historyMsgs.size() >= 2) {
                // Drop first user + assistant pair (2 messages)
                int pair0Chars = ((String) historyMsgs.get(0).get("content")).length();
                int pair1Chars = ((String) historyMsgs.get(1).get("content")).length();
                historyMsgs.remove(0);
                historyMsgs.remove(0);
                totalChars -= (pair0Chars + pair1Chars);
            }

            // Insert history after system prompt (index 1), before current user message (last)
            // messages currently: [system, user]
            // After insert:       [system, <history...>, user]
            messages.addAll(1, historyMsgs);
        } catch (GenericEntityException e) {
            Debug.logWarning("AgentRunner: failed to load thread history for '"
                    + threadId + "': " + e.getMessage(), MODULE);
        }
    }

    /**
     * Persists the user message and assistant response as {@code AiConversationMessage} rows.
     * If the thread record does not exist it is created; otherwise {@code lastActiveAt} is updated.
     *
     * @param threadId         the conversation thread identifier
     * @param agentName        agent name stored on a new thread record
     * @param userMessage      the user's input text
     * @param assistantMessage the LLM's response text (may be {@code null})
     * @param delegator        entity delegator for database access
     */
    private static void saveThreadMessages(String threadId, String agentName,
            String userLoginId, String userMessage, String assistantMessage, Delegator delegator) {
        try {
            java.sql.Timestamp now = UtilDateTime.nowTimestamp();

            // Upsert the thread record
            GenericValue thread = EntityQuery.use(delegator)
                    .from("AiConversationThread")
                    .where("threadId", threadId)
                    .queryOne();
            if (thread == null) {
                thread = delegator.makeValue("AiConversationThread");
                thread.set("threadId", threadId);
                thread.set("agentName", agentName);
                thread.set("userLoginId", userLoginId);
                thread.set("createdAt", now);
                thread.set("statusId", "AI_THREAD_ACTIVE");
                delegator.create(thread);
            } else {
                thread.set("lastActiveAt", now);
                thread.store();
            }

            // Get the current max sequence number — fetch only the most recent row
            GenericValue latest = EntityQuery.use(delegator)
                    .from("AiConversationMessage")
                    .where("threadId", threadId)
                    .orderBy("-sequenceNum")
                    .queryFirst();
            long nextSeq = (latest != null && latest.getLong("sequenceNum") != null)
                    ? latest.getLong("sequenceNum") + 1L : 1L;

            // Save user message
            GenericValue userMsg = delegator.makeValue("AiConversationMessage");
            userMsg.set("messageId", delegator.getNextSeqId("AiConversationMessage"));
            userMsg.set("threadId", threadId);
            userMsg.set("role", "user");
            userMsg.set("content", userMessage);
            userMsg.set("sequenceNum", nextSeq);
            userMsg.set("createdAt", now);
            delegator.create(userMsg);

            // Save assistant message if present
            if (assistantMessage != null) {
                GenericValue assistMsg = delegator.makeValue("AiConversationMessage");
                assistMsg.set("messageId", delegator.getNextSeqId("AiConversationMessage"));
                assistMsg.set("threadId", threadId);
                assistMsg.set("role", "assistant");
                assistMsg.set("content", assistantMessage);
                assistMsg.set("sequenceNum", nextSeq + 1L);
                assistMsg.set("createdAt", now);
                delegator.create(assistMsg);
            }
        } catch (GenericEntityException e) {
            Debug.logWarning("AgentRunner: failed to save thread messages for '"
                    + threadId + "': " + e.getMessage(), MODULE);
        }
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
        private final String proposalId;  // null when no suspension

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
            this(assistantMessage, stopReason, iterationsUsed, null);
        }

        /**
         * Constructs a run result with an optional proposal identifier.
         *
         * @param assistantMessage the final text response from the assistant, or
         *                         {@code null} if the loop ended without a stop
         * @param stopReason       one of {@code "stop"}, {@code "max_iterations"},
         *                         {@code "approval_required"}, or an unexpected finish reason string
         * @param iterationsUsed   number of loop iterations consumed
         * @param proposalId       the proposal identifier when stopReason is
         *                         {@code "approval_required"}, or {@code null} otherwise
         */
        public RunResult(String assistantMessage, String stopReason,
                int iterationsUsed, String proposalId) {
            this.assistantMessage = assistantMessage;
            this.stopReason = stopReason;
            this.iterationsUsed = iterationsUsed;
            this.proposalId = proposalId;
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

        /**
         * Returns the proposal identifier when stopReason is {@code "approval_required"},
         * or {@code null} otherwise.
         *
         * @return proposal identifier, or {@code null}
         */
        public String getProposalId() {
            return proposalId;
        }
    }
}
