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

import java.util.List;
import java.util.Map;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

public class AiServices {

    private static final String MODULE = AiServices.class.getName();

    public static Map<String, Object> generate(DispatchContext dctx, Map<String, Object> context) {
        List<Map<String, Object>> messages = UtilGenerics.cast(context.get("messages"));
        try {
            String response = AiWorker.generate(dctx, messages);
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("response", response);
            return result;
        } catch (GeneralException e) {
            Debug.logError(e, e.getMessage(), MODULE);
            return ServiceUtil.returnError(e.getMessage());
        }
    }

    public static Map<String, Object> generateStructured(DispatchContext dctx, Map<String, Object> context) {
        List<Map<String, Object>> messages = UtilGenerics.cast(context.get("messages"));
        Map<String, Object> schema = UtilGenerics.cast(context.get("schema"));
        try {
            Map<String, Object> aiResult = AiWorker.generateStructured(dctx, messages, schema);
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("result", aiResult);
            return result;
        } catch (GeneralException e) {
            Debug.logError(e, e.getMessage(), MODULE);
            return ServiceUtil.returnError(e.getMessage());
        }
    }
}
