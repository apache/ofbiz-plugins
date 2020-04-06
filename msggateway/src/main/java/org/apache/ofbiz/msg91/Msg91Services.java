/*
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
 */

package org.apache.ofbiz.msg91;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.HttpClient;
import org.apache.ofbiz.base.util.HttpClientException;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

public class Msg91Services {
    public final static String MODULE = Msg91Services.class.getName();

    public static Map<String, Object> sendMsg91Sms(DispatchContext ctx, Map<String, ? extends Object> context) {
        Delegator delegator = ctx.getDelegator();
        Map<String, Object> result = ServiceUtil.returnSuccess();
        
        List<String> numbers = UtilGenerics.cast(context.get("numbers"));
        String message = (String) context.get("message");

        try {
            GenericValue msg91GatewayConfig = EntityQuery.use(delegator).from("Msg91GatewayConfig").queryFirst();
            if (msg91GatewayConfig != null) {
                Map<String, Object> paramMap = new HashMap<>();
                paramMap.put("country", msg91GatewayConfig.getString("country"));
                paramMap.put("sender", msg91GatewayConfig.getString("sender"));
                paramMap.put("route", msg91GatewayConfig.getString("route"));
                paramMap.put("mobiles", numbers);
                paramMap.put("message", message);
                paramMap.put("authkey", msg91GatewayConfig.getString("authkey"));

                HttpClient httpClient = new HttpClient(msg91GatewayConfig.getString("apiUrl"), paramMap);
                httpClient.setHeader("content-type", "application/text");
                String response = httpClient.get();
                result.put("response", response);
            } else {
                Debug.logError("Message not sent as the telecom gateway configuration settings are not found", MODULE);
                return ServiceUtil.returnError("Message not sent as the telecom gateway configuration settings are not found");
            }
        } catch (GenericEntityException | HttpClientException e) {
            Debug.logError(e.getMessage(), MODULE);
            return ServiceUtil.returnError(e.getMessage());
        }
        return result;
    }
}
