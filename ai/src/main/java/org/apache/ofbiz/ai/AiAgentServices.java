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

import java.util.Map;

import org.apache.ofbiz.ai.agent.AgentRunner;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.GenericValue;
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
}
