/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.ofbiz.ebaystore;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;

public class EbayStoreAutoPrefEvents {

    private static final String MODULE = EbayStoreAutoPrefEvents.class.getName();

    public static String ebayAutoPrefCond (HttpServletRequest request, HttpServletResponse response) {
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        HttpSession session = request.getSession(true);
        GenericValue userLogin = (GenericValue) session.getAttribute("userLogin");
        Map<String, Object> requestParams = UtilHttp.getParameterMap(request);
        String enabled = (String) requestParams.get("automateEnable");
        String productStoreId = (String) requestParams.get("productStoreId");
        String condition1 = (String) requestParams.get("kindOfPrice");
        String condition2 = (String) requestParams.get("acceptBestOfferValue");
        String condition3 = (String) requestParams.get("rejectOffer");
        String condition4 = (String) requestParams.get("ignoreOfferMessage");
        String condition5 = (String) requestParams.get("rejectGreaterEnable");
        String condition6 = (String) requestParams.get("greaterValue");
        String condition7 = (String) requestParams.get("lessValue");
        String condition8 = (String) requestParams.get("rejectGreaterMsg");
        String condition9 = (String) requestParams.get("rejectLessEnable");
        String condition10 = (String) requestParams.get("lessThanValue");
        String condition11 = (String) requestParams.get("rejectLessMsg");
        if (UtilValidate.isNotEmpty(enabled)) {
            if ("Y".equals(enabled) && UtilValidate.isEmpty(condition1)) {
                String errMsg = "Please select Based Price.";
                request.setAttribute("_ERROR_MESSAGE_", errMsg);
                return "error";
            }
            if ("Y".equals(enabled) && UtilValidate.isEmpty(condition2)) {
                String errMsg = "Please enter \"Percent value to accept.\"";
                request.setAttribute("_ERROR_MESSAGE_", errMsg);
                return "error";
            }
            if ("Y".equals(enabled) && "Y".equals(condition5) && (UtilValidate.isEmpty(condition6) || UtilValidate.isEmpty(condition7) || UtilValidate.isEmpty(condition8))) {
                String errMsg = "Please enter \"Greater price percen\" , \"Less price percent \" and \"Message\"";
                request.setAttribute("_ERROR_MESSAGE_", errMsg);
                return "error";
            } else if ("Y".equals(enabled) && "N".equals(condition5) && (UtilValidate.isNotEmpty(condition6) || UtilValidate.isNotEmpty(condition7) || UtilValidate.isNotEmpty(condition8))) {
                String errMsg = "Please enable rejection notification before.";
                request.setAttribute("_ERROR_MESSAGE_", errMsg);
                return "error";
            }
            if ("Y".equals(enabled) && "Y".equals(condition9) && (UtilValidate.isEmpty(condition10) || UtilValidate.isEmpty(condition11))) {
                String errMsg = "Please enter \"Less price percent \" and \"Message\"";
                request.setAttribute("_ERROR_MESSAGE_", errMsg);
                return "error";
            } else if ("Y".equals(enabled) && "N".equals(condition9) && (UtilValidate.isNotEmpty(condition10) || UtilValidate.isNotEmpty(condition11))) {
                String errMsg = "Please enable rejection notification before.";
                request.setAttribute("_ERROR_MESSAGE_", errMsg);
                return "error";
            }
        }

        Map<String, Object> bestOfferCondition = new HashMap<>();
        bestOfferCondition.put("productStoreId", productStoreId);
        bestOfferCondition.put("userLogin", userLogin);
        bestOfferCondition.put("enabled", enabled);
        bestOfferCondition.put("condition1", condition1);
        bestOfferCondition.put("condition2", condition2);
        bestOfferCondition.put("condition3", condition3);
        bestOfferCondition.put("condition4", condition4);
        bestOfferCondition.put("condition5", condition5);
        bestOfferCondition.put("condition6", condition6);
        bestOfferCondition.put("condition7", condition7);
        bestOfferCondition.put("condition8", condition8);
        bestOfferCondition.put("condition9", condition9);
        bestOfferCondition.put("condition10", condition10);
        bestOfferCondition.put("condition11", condition11);
        try {
            Map<String, Object> result = dispatcher.runSync("ebayBestOfferPrefCond", bestOfferCondition);
            if (ServiceUtil.isError(result)) {
                request.setAttribute("_ERROR_MESSAGE_", ServiceUtil.getErrorMessage(result));
                Debug.log(ServiceUtil.getErrorMessage(result), MODULE);
                return "error";
            }
        } catch (GenericServiceException e) {
            Debug.logError(e, MODULE);
            return "error";
        }
        return "Success.";
    }
}
