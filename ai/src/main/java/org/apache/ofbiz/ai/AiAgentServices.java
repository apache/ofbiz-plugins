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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.ofbiz.ai.agent.AgentDefinition;
import org.apache.ofbiz.ai.agent.AgentRunner;
import org.apache.ofbiz.ai.agent.AiChatClient;
import org.apache.ofbiz.ai.agent.AiHttpClient;
import org.apache.ofbiz.ai.agent.ProviderConfig;
import org.apache.ofbiz.ai.agent.ToolCatalog;
import org.apache.ofbiz.ai.agent.ToolDescriptor;
import org.apache.ofbiz.ai.container.AiContainer;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AiAgentServices {

    private static final String MODULE = AiAgentServices.class.getName();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> MSG_LIST_TYPE =
            new TypeReference<List<Map<String, Object>>>() { };
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<Map<String, Object>>() { };

    public static Map<String, Object> agentRun(DispatchContext dctx,
            Map<String, ? extends Object> context) {
        String agentName = (String) context.get("agentName");
        String userMessage = (String) context.get("userMessage");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        String threadId = (String) context.get("threadId");
        Boolean approvalRequired = (Boolean) context.get("approvalRequired");

        if (UtilValidate.isEmpty(agentName)) {
            return ServiceUtil.returnError("agentName is required");
        }
        if (UtilValidate.isEmpty(userMessage)) {
            return ServiceUtil.returnError("userMessage is required");
        }

        try {
            AgentRunner runner = new AgentRunner(agentName, userMessage, userLogin, dctx);
            if (threadId != null) {
                runner.setThreadId(threadId);
            }
            if (Boolean.TRUE.equals(approvalRequired)) {
                runner.setApprovalRequired(true);
            }
            AgentRunner.RunResult result = runner.run();
            Map<String, Object> serviceResult = ServiceUtil.returnSuccess();
            serviceResult.put("assistantMessage", result.getAssistantMessage());
            serviceResult.put("stopReason", result.getStopReason());
            serviceResult.put("iterationsUsed", result.getIterationsUsed());
            serviceResult.put("threadId", threadId);
            serviceResult.put("proposalId", result.getProposalId());
            return serviceResult;
        } catch (GeneralException e) {
            Debug.logError(e, "agentRun failed: " + e.getMessage(), MODULE);
            return ServiceUtil.returnError(e.getMessage());
        }
    }

    public static Map<String, Object> approveAgentProposal(DispatchContext dctx,
            Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String proposalId = (String) context.get("proposalId");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        if (UtilValidate.isEmpty(proposalId)) {
            return ServiceUtil.returnError("proposalId is required");
        }
        try {
            // Load and verify proposal is pending
            GenericValue proposal = EntityQuery.use(delegator)
                    .from("AiAgentProposal").where("proposalId", proposalId).queryOne();
            if (proposal == null) {
                return ServiceUtil.returnError("Proposal not found: " + proposalId);
            }
            if (!"AI_PROPOSAL_PENDING".equals(proposal.getString("statusId"))) {
                return ServiceUtil.returnError("Proposal is not pending: current status is "
                        + proposal.getString("statusId"));
            }

            // Mark approved
            proposal.set("statusId", "AI_PROPOSAL_APPROVED");
            proposal.set("reviewedByUserLoginId",
                    userLogin != null ? userLogin.getString("userLoginId") : null);
            proposal.set("reviewedAt", UtilDateTime.nowTimestamp());
            proposal.store();

            // Deserialize messages
            List<Map<String, Object>> messages = OBJECT_MAPPER.readValue(
                    proposal.getString("messagesJson"), MSG_LIST_TYPE);

            // Load and execute pending tool calls, append results
            List<GenericValue> propTools = EntityQuery.use(delegator)
                    .from("AiAgentProposalTool")
                    .where("proposalId", proposalId)
                    .orderBy("proposalToolId")
                    .queryList();

            ToolCatalog toolCatalog = AiContainer.getToolCatalog();

            for (GenericValue propTool : propTools) {
                String toolCallId = propTool.getString("toolCallId");
                String toolName = propTool.getString("toolName");
                String callArgsJson = propTool.getString("callArguments");

                ToolDescriptor toolDesc = toolCatalog != null ? toolCatalog.getTool(toolName) : null;
                if (toolDesc == null) {
                    Debug.logWarning("approveAgentProposal: tool '" + toolName
                            + "' not found in catalog, skipping", MODULE);
                    ObjectNode errNode = OBJECT_MAPPER.createObjectNode();
                    errNode.put("error", "tool not found in catalog: " + toolName);
                    messages.add(toolRoleMessage(toolCallId, errNode.toString()));
                    continue;
                }

                Map<String, Object> parsedArgs;
                try {
                    parsedArgs = OBJECT_MAPPER.readValue(callArgsJson, MAP_TYPE);
                } catch (Exception e) {
                    parsedArgs = new HashMap<>();
                }
                Map<String, Object> ctx = new HashMap<>(parsedArgs);
                ctx.put("userLogin", userLogin);

                String resultJson;
                try {
                    Map<String, Object> toolResult = dctx.getDispatcher()
                            .runSync(toolDesc.getServiceName(), ctx);
                    if (ServiceUtil.isError(toolResult)) {
                        String errMsg = ServiceUtil.getErrorMessage(toolResult);
                        Debug.logWarning("approveAgentProposal: tool '" + toolName
                                + "' returned service error: " + errMsg, MODULE);
                        ObjectNode errNode = OBJECT_MAPPER.createObjectNode();
                        errNode.put("error", errMsg);
                        resultJson = errNode.toString();
                    } else {
                        resultJson = OBJECT_MAPPER.writeValueAsString(toolResult);
                        if (resultJson.length() > 8000) {
                            resultJson = resultJson.substring(0, 8000) + "...[truncated]";
                        }
                    }
                } catch (Exception e) {
                    Debug.logError(e, "approveAgentProposal: tool '" + toolName
                            + "' dispatch failed", MODULE);
                    ObjectNode errNode = OBJECT_MAPPER.createObjectNode();
                    errNode.put("error", e.getMessage() != null ? e.getMessage() : "tool dispatch failed");
                    resultJson = errNode.toString();
                }

                messages.add(toolRoleMessage(toolCallId, resultJson));
            }

            // Resume the agent loop
            String agentName = proposal.getString("agentName");
            String runId = proposal.getString("runId");
            AgentRunner.RunResult result = AgentRunner.continueFromApproval(
                    agentName, messages, userLogin, dctx, runId);

            Map<String, Object> serviceResult = ServiceUtil.returnSuccess();
            serviceResult.put("assistantMessage", result.getAssistantMessage());
            serviceResult.put("stopReason", result.getStopReason());
            serviceResult.put("iterationsUsed", result.getIterationsUsed());
            return serviceResult;

        } catch (GeneralException e) {
            Debug.logError(e, "approveAgentProposal failed", MODULE);
            return ServiceUtil.returnError(e.getMessage());
        } catch (Exception e) {
            Debug.logError(e, "approveAgentProposal unexpected error", MODULE);
            return ServiceUtil.returnError(e.getMessage());
        }
    }

    public static Map<String, Object> rejectAgentProposal(DispatchContext dctx,
            Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String proposalId = (String) context.get("proposalId");
        String rejectionReason = (String) context.get("rejectionReason");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        if (UtilValidate.isEmpty(proposalId)) {
            return ServiceUtil.returnError("proposalId is required");
        }
        try {
            // Load and verify proposal is pending
            GenericValue proposal = EntityQuery.use(delegator)
                    .from("AiAgentProposal").where("proposalId", proposalId).queryOne();
            if (proposal == null) {
                return ServiceUtil.returnError("Proposal not found: " + proposalId);
            }
            if (!"AI_PROPOSAL_PENDING".equals(proposal.getString("statusId"))) {
                return ServiceUtil.returnError("Proposal is not pending: current status is "
                        + proposal.getString("statusId"));
            }

            // Mark rejected
            proposal.set("statusId", "AI_PROPOSAL_REJECTED");
            proposal.set("reviewedByUserLoginId",
                    userLogin != null ? userLogin.getString("userLoginId") : null);
            proposal.set("reviewedAt", UtilDateTime.nowTimestamp());
            if (UtilValidate.isNotEmpty(rejectionReason)) {
                proposal.set("rejectionReason", rejectionReason);
            }
            proposal.store();

            // Deserialize messages and append rejection
            List<Map<String, Object>> messages = OBJECT_MAPPER.readValue(
                    proposal.getString("messagesJson"), MSG_LIST_TYPE);

            String reason = UtilValidate.isNotEmpty(rejectionReason)
                    ? rejectionReason : "No reason provided.";
            Map<String, Object> rejectionMsg = new LinkedHashMap<>();
            rejectionMsg.put("role", "user");
            rejectionMsg.put("content",
                    "The proposed actions have been rejected by a human reviewer. Reason: "
                    + reason + " Please acknowledge and provide a helpful response.");
            messages.add(rejectionMsg);

            // Single LLM call for acknowledgment
            if (AiContainer.getAgentRegistry() == null) {
                return ServiceUtil.returnError("AgentRegistry not available");
            }
            AgentDefinition agentDef = AiContainer.getAgentRegistry()
                    .getAgent(proposal.getString("agentName"));
            if (agentDef == null) {
                return ServiceUtil.returnError("Agent not found: " + proposal.getString("agentName"));
            }
            if (AiContainer.getProviderRegistry() == null) {
                return ServiceUtil.returnError("ProviderRegistry not available");
            }
            ProviderConfig provider = AiContainer.getProviderRegistry()
                    .getProvider(agentDef.getProviderName());
            if (provider == null) {
                return ServiceUtil.returnError("Provider not configured: " + agentDef.getProviderName());
            }

            AiChatClient client = new AiHttpClient();
            AiChatClient.ChatResponse response = client.chat(
                    Collections.unmodifiableList(messages),
                    Collections.emptyList(),
                    agentDef.getModelOverride(),
                    provider);

            Map<String, Object> serviceResult = ServiceUtil.returnSuccess();
            serviceResult.put("assistantMessage", response.getContent());
            return serviceResult;

        } catch (GeneralException e) {
            Debug.logError(e, "rejectAgentProposal failed", MODULE);
            return ServiceUtil.returnError(e.getMessage());
        } catch (Exception e) {
            Debug.logError(e, "rejectAgentProposal unexpected error", MODULE);
            return ServiceUtil.returnError(e.getMessage());
        }
    }

    public static Map<String, Object> archiveConversationThread(DispatchContext dctx,
            Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String threadId = (String) context.get("threadId");
        if (UtilValidate.isEmpty(threadId)) {
            return ServiceUtil.returnError("threadId is required");
        }
        try {
            GenericValue thread = EntityQuery.use(delegator)
                    .from("AiConversationThread")
                    .where("threadId", threadId)
                    .queryOne();
            if (thread == null) {
                return ServiceUtil.returnError("Thread not found: " + threadId);
            }
            thread.set("statusId", "AI_THREAD_ARCHIVED");
            thread.store();
            return ServiceUtil.returnSuccess();
        } catch (GenericEntityException e) {
            Debug.logError(e, "archiveConversationThread failed", MODULE);
            return ServiceUtil.returnError(e.getMessage());
        }
    }

    public static Map<String, Object> getConversationHistory(DispatchContext dctx,
            Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String threadId = (String) context.get("threadId");
        try {
            List<GenericValue> rows = EntityQuery.use(delegator)
                    .from("AiConversationMessage")
                    .where("threadId", threadId)
                    .orderBy("sequenceNum")
                    .queryList();
            List<Map<String, Object>> messages = new ArrayList<>();
            for (GenericValue row : rows) {
                Map<String, Object> msg = new LinkedHashMap<>();
                msg.put("role", row.getString("role"));
                msg.put("content", row.getString("content"));
                msg.put("sequenceNum", row.getLong("sequenceNum"));
                messages.add(msg);
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("messages", messages);
            return result;
        } catch (GenericEntityException e) {
            Debug.logError(e, "getConversationHistory failed", MODULE);
            return ServiceUtil.returnError(e.getMessage());
        }
    }

    public static Map<String, Object> getUsageSummary(DispatchContext dctx,
            Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String agentNameFilter = (String) context.get("agentName");
        String userLoginId = (String) context.get("userLoginId");
        Timestamp fromDate = (Timestamp) context.get("fromDate");
        Timestamp thruDate = (Timestamp) context.get("thruDate");

        try {
            // Build conditions for completed runs only
            List<EntityCondition> conditions = new ArrayList<>();
            conditions.add(EntityCondition.makeCondition("statusId",
                    EntityOperator.EQUALS, "AI_RUN_COMPLETED"));
            if (UtilValidate.isNotEmpty(agentNameFilter)) {
                conditions.add(EntityCondition.makeCondition("agentName",
                        EntityOperator.EQUALS, agentNameFilter));
            }
            if (UtilValidate.isNotEmpty(userLoginId)) {
                conditions.add(EntityCondition.makeCondition("userLoginId",
                        EntityOperator.EQUALS, userLoginId));
            }
            if (fromDate != null) {
                conditions.add(EntityCondition.makeCondition("startedAt",
                        EntityOperator.GREATER_THAN_EQUAL_TO, fromDate));
            }
            if (thruDate != null) {
                conditions.add(EntityCondition.makeCondition("startedAt",
                        EntityOperator.LESS_THAN_EQUAL_TO, thruDate));
            }
            EntityCondition cond = conditions.size() == 1
                    ? conditions.get(0)
                    : EntityCondition.makeCondition(conditions, EntityOperator.AND);

            List<GenericValue> runs = EntityQuery.use(delegator)
                    .from("AiAgentRun")
                    .where(cond)
                    .queryList();

            long totalRuns = runs.size();
            long totalInputTokens = 0L;
            long totalOutputTokens = 0L;
            String lastAgentNameSeen = null;
            for (GenericValue run : runs) {
                if (run.getLong("inputTokens") != null) {
                    totalInputTokens += run.getLong("inputTokens");
                }
                if (run.getLong("outputTokens") != null) {
                    totalOutputTokens += run.getLong("outputTokens");
                }
                lastAgentNameSeen = run.getString("agentName");
            }

            // Resolve effective agent name for cost lookup
            String effectiveAgentName = UtilValidate.isNotEmpty(agentNameFilter)
                    ? agentNameFilter : lastAgentNameSeen;

            // Look up cost by resolving model from the agent definition
            BigDecimal estimatedCostUsd = null;
            if (effectiveAgentName != null && AiContainer.getAgentRegistry() != null) {
                AgentDefinition agentDef = AiContainer.getAgentRegistry().getAgent(effectiveAgentName);
                if (agentDef != null) {
                    ProviderConfig provider = AiContainer.getProviderRegistry() != null
                            ? AiContainer.getProviderRegistry().getProvider(agentDef.getProviderName())
                            : null;
                    String modelId = agentDef.getModelOverride() != null
                            ? agentDef.getModelOverride()
                            : (provider != null ? provider.getModel() : null);
                    if (modelId != null) {
                        GenericValue costRow = EntityQuery.use(delegator)
                                .from("AiProviderCost")
                                .where("modelId", modelId)
                                .orderBy("-effectiveDate")
                                .queryFirst();
                        if (costRow != null) {
                            BigDecimal inputCost = costRow.getBigDecimal("inputCostPerMillion");
                            BigDecimal outputCost = costRow.getBigDecimal("outputCostPerMillion");
                            if (inputCost != null && outputCost != null) {
                                BigDecimal million = new BigDecimal("1000000");
                                estimatedCostUsd = inputCost
                                        .multiply(BigDecimal.valueOf(totalInputTokens))
                                        .divide(million, 6, RoundingMode.HALF_UP)
                                        .add(outputCost
                                        .multiply(BigDecimal.valueOf(totalOutputTokens))
                                        .divide(million, 6, RoundingMode.HALF_UP));
                            }
                        }
                    }
                }
            }

            if (estimatedCostUsd == null && totalRuns > 0) {
                Debug.logWarning("getUsageSummary: " + totalRuns + " run(s) found but cost could not"
                        + " be estimated — agent/provider/model not resolved or not in AiProviderCost",
                        MODULE);
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("totalRuns", totalRuns);
            result.put("totalInputTokens", totalInputTokens);
            result.put("totalOutputTokens", totalOutputTokens);
            result.put("estimatedCostUsd", estimatedCostUsd);
            return result;

        } catch (GenericEntityException e) {
            Debug.logError(e, "getUsageSummary failed", MODULE);
            return ServiceUtil.returnError(e.getMessage());
        }
    }

    private static Map<String, Object> toolRoleMessage(String toolCallId, String content) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "tool");
        msg.put("tool_call_id", toolCallId);
        msg.put("content", content);
        return msg;
    }
}
