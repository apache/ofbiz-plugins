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
import java.util.List;
import java.util.Map;

import org.apache.ofbiz.ai.agent.AgentDefinition;
import org.apache.ofbiz.ai.agent.AgentRunner;
import org.apache.ofbiz.ai.agent.ProviderConfig;
import org.apache.ofbiz.ai.container.AiContainer;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

public class AiAgentServices {

    private static final String MODULE = AiAgentServices.class.getName();

    public static Map<String, Object> agentRun(DispatchContext dctx,
            Map<String, ? extends Object> context) {
        String agentName = (String) context.get("agentName");
        String userMessage = (String) context.get("userMessage");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        if (UtilValidate.isEmpty(agentName)) {
            return ServiceUtil.returnError("agentName is required");
        }
        if (UtilValidate.isEmpty(userMessage)) {
            return ServiceUtil.returnError("userMessage is required");
        }

        try {
            AgentRunner runner = new AgentRunner(agentName, userMessage, userLogin, dctx);
            AgentRunner.RunResult result = runner.run();
            Map<String, Object> serviceResult = ServiceUtil.returnSuccess();
            serviceResult.put("assistantMessage", result.getAssistantMessage());
            serviceResult.put("stopReason", result.getStopReason());
            serviceResult.put("iterationsUsed", result.getIterationsUsed());
            return serviceResult;
        } catch (GeneralException e) {
            Debug.logError(e, "agentRun failed: " + e.getMessage(), MODULE);
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
}
