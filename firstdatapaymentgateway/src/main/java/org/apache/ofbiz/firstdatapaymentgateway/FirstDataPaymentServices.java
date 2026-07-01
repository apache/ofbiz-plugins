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
package org.apache.ofbiz.firstdatapaymentgateway;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.template.FreeMarkerWorker;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import freemarker.template.TemplateException;

public class FirstDataPaymentServices {
    private static final String MODULE = FirstDataPaymentServices.class.getName();

    public static Map<String, Object> ccAuth(DispatchContext ctx, Map<String, Object> context) {
        Delegator delegator = ctx.getDelegator();
        BigDecimal processAmount = (BigDecimal) context.get("processAmount");
        String currency = (String) context.get("currency");
        String cardSecurityCode = (String) context.get("cardSecurityCode");
        GenericValue creditCard = (GenericValue) context.get("creditCard");
        String paymentGatewayConfigId = (String) context.get("paymentGatewayConfigId");
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("processAmount", processAmount);
        Boolean isSuccess = Boolean.FALSE;
        String gatewayMessage = "";
        String cardNumber = creditCard.getString("cardNumber");

        try {
            Properties fdProperties = buildFDProperties(paymentGatewayConfigId, delegator);
            String clientRequestId = UUID.randomUUID().toString();
            String epochTime = String.valueOf(System.currentTimeMillis());
            Date expireDate = new SimpleDateFormat("MM/yyyy").parse(creditCard.getString("expireDate"));
            SimpleDateFormat df = new SimpleDateFormat("MM");
            String strMonth = df.format(expireDate);
            df = new SimpleDateFormat("yy");
            String strYear = df.format(expireDate);

            Map<String, Object> ccAuthReqContext = new HashMap<>();
            ccAuthReqContext.put("amount", processAmount);
            ccAuthReqContext.put("currency", currency);
            ccAuthReqContext.put("cardSecurityCode", cardSecurityCode);
            ccAuthReqContext.put("cardNumber", cardNumber);
            ccAuthReqContext.put("expireMonth", strMonth);
            ccAuthReqContext.put("expireYear", strYear);

            StringWriter outWriter = new StringWriter();
            String firstDataPreAuthTemplate = EntityUtilProperties.getPropertyValue("firstdata", "paymentgateway.firstdata.template.preauth"
                    + ".location", delegator);
            FreeMarkerWorker.renderTemplate(firstDataPreAuthTemplate, ccAuthReqContext, outWriter);
            String requestBody = outWriter.toString();

            String messageSignature = buildMessageSignature(fdProperties, requestBody, clientRequestId, epochTime);

            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                StringEntity stringEntity = new StringEntity(requestBody);
                HttpPost httpPost = new HttpPost(fdProperties.getProperty("transactionUrl") + "/payments");
                httpPost.setEntity(stringEntity);
                httpPost.setHeader("Client-Request-Id", clientRequestId);
                httpPost.setHeader("Api-Key", fdProperties.getProperty("apiKey"));
                httpPost.setHeader("Timestamp", epochTime);
                httpPost.setHeader("Message-Signature", messageSignature);
                httpPost.setHeader("Content-Type", "application/json");

                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    HttpEntity entity = response.getEntity();
                    String responseString = EntityUtils.toString(entity);

                    ObjectMapper objectMapper = new ObjectMapper();
                    Map<String, Object> convertedMap = objectMapper.readValue(responseString, new TypeReference<Map<String, Object>>() {
                    });

                    String transactionStatus = (String) convertedMap.get("transactionStatus");
                    String transactionId = (String) convertedMap.get("ipgTransactionId");
                    String fdOrderId = (String) convertedMap.get("orderId");
                    result.put("authRefNum", transactionId);
                    result.put("authAltRefNum", fdOrderId);
                    if ("APPROVED".equalsIgnoreCase(transactionStatus) || "WAITING".equalsIgnoreCase(transactionStatus)) {
                        Object processorObj = convertedMap.get("processor");
                        if (processorObj != null) {
                            Map<String, Object> processor = objectMapper.convertValue(processorObj, new TypeReference<Map<String, Object>>() {
                            });
                            gatewayMessage = (String) processor.get("responseMessage");
                        }
                        int statusCode = response.getStatusLine().getStatusCode();
                        result.put("authCode", String.valueOf(statusCode));
                        result.put("authMessage", gatewayMessage);
                        if (UtilValidate.isNotEmpty(transactionId)) {
                            isSuccess = Boolean.TRUE;
                        }
                    }
                    if (!isSuccess) {
                        String errorMessage = "Transaction Type:" + (String) convertedMap.get("transactionType") + " Transaction Id: " + transactionId
                                + " Transaction Status: " + transactionStatus;
                        gatewayMessage = UtilValidate.isNotEmpty(gatewayMessage) ? gatewayMessage : "";
                        errorMessage = errorMessage + " Gateway Message: " + gatewayMessage;
                        result.put("authMessage", errorMessage);
                    }
                }
            }
        } catch (ParseException | TemplateException | IOException e) {
            Debug.logError(e, "Could not complete First Data transaction: " + e.toString(), MODULE);
        }
        result.put("authResult", isSuccess);
        return result;
    }

    public static Map<String, Object> ccCapture(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        BigDecimal captureAmount = (BigDecimal) context.get("captureAmount");
        String paymentGatewayConfigId = (String) context.get("paymentGatewayConfigId");
        String currency = (String) context.get("currency");
        GenericValue orderPaymentPreference = (GenericValue) context.get("orderPaymentPreference");

        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("captureAmount", captureAmount);
        Boolean isSuccess = Boolean.FALSE;

        try {
            Properties fdProperties = buildFDProperties(paymentGatewayConfigId, delegator);
            String clientRequestId = UUID.randomUUID().toString();
            String epochTime = String.valueOf(System.currentTimeMillis());

            Map<String, Object> ccPostAuthReqContext = new HashMap<>();
            ccPostAuthReqContext.put("amount", captureAmount);
            ccPostAuthReqContext.put("currency", currency);

            StringWriter outWriter = new StringWriter();
            String firstDataPostAuthTemplate = EntityUtilProperties.getPropertyValue("firstdata", "paymentgateway.firstdata.template.postauth"
                    + ".location", delegator);
            FreeMarkerWorker.renderTemplate(firstDataPostAuthTemplate, ccPostAuthReqContext, outWriter);
            String requestBody = outWriter.toString();

            String messageSignature = buildMessageSignature(fdProperties, requestBody, clientRequestId, epochTime);

            GenericValue paymentGatewayResponse = EntityQuery.use(delegator).from("PaymentGatewayResponse")
                    .where("orderPaymentPreferenceId", orderPaymentPreference.getString("orderPaymentPreferenceId"), "paymentMethodId",
                            orderPaymentPreference.getString("paymentMethodId"), "transCodeEnumId", "PGT_AUTHORIZE", "paymentServiceTypeEnumId",
                            "PRDS_PAY_AUTH")
                    .queryFirst();
            if (paymentGatewayResponse == null) {
                return ServiceUtil.returnError("No authorization payment gateway response found for orderPaymentPreferenceId: "
                        + orderPaymentPreference.getString("orderPaymentPreferenceId"));
            }
            String authTransactionId = UtilValidate.isNotEmpty(paymentGatewayResponse.getString("referenceNum"))
                    ? paymentGatewayResponse.getString("referenceNum")
                    : paymentGatewayResponse.getString("altReference");

            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                StringEntity stringEntity = new StringEntity(requestBody);
                HttpPost httpPost = new HttpPost(fdProperties.getProperty("transactionUrl") + "/payments/" + authTransactionId);
                httpPost.setEntity(stringEntity);
                httpPost.setHeader("Client-Request-Id", clientRequestId);
                httpPost.setHeader("Api-Key", fdProperties.getProperty("apiKey"));
                httpPost.setHeader("Timestamp", epochTime);
                httpPost.setHeader("Message-Signature", messageSignature);
                httpPost.setHeader("Content-Type", "application/json");

                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    HttpEntity entity = response.getEntity();
                    String responseString = EntityUtils.toString(entity);

                    ObjectMapper objectMapper = new ObjectMapper();
                    Map<String, Object> convertedMap = objectMapper.readValue(responseString, new TypeReference<Map<String, Object>>() {
                    });

                    String transactionStatus = (String) convertedMap.get("transactionStatus");
                    String transactionId = (String) convertedMap.get("ipgTransactionId");
                    String fdOrderId = (String) convertedMap.get("orderId");
                    int statusCode = response.getStatusLine().getStatusCode();
                    String gatewayMessage = "";
                    Object processorObj = convertedMap.get("processor");
                    if (processorObj != null) {
                        Map<String, Object> processor = objectMapper.convertValue(processorObj, new TypeReference<Map<String, Object>>() {
                        });
                        gatewayMessage = (String) processor.get("responseMessage");
                    }
                    result.put("captureCode", String.valueOf(statusCode));
                    result.put("captureMessage", gatewayMessage);
                    if (UtilValidate.isNotEmpty(transactionId)) {
                        result.put("captureRefNum", transactionId);
                        result.put("captureAltRefNum", fdOrderId);
                        if ("APPROVED".equalsIgnoreCase(transactionStatus)) {
                            isSuccess = Boolean.TRUE;
                        }
                    }
                    if (!isSuccess) {
                        String errorMessage = "Transaction Type:" + (String) convertedMap.get("transactionType") + " Transaction Id: " + transactionId
                                + " Transaction Status: " + transactionStatus + " Message: " + statusCode + "-" + gatewayMessage;
                        result.put("captureMessage", errorMessage);
                    }
                }
            }
        } catch (TemplateException | IOException | GenericEntityException e) {
            Debug.logError(e, "Could not complete First Data transaction: " + e.toString(), MODULE);
        }
        result.put("captureResult", isSuccess);
        return result;
    }

    public static Map<String, Object> ccRefund(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        BigDecimal refundAmount = (BigDecimal) context.get("refundAmount");
        String paymentGatewayConfigId = (String) context.get("paymentGatewayConfigId");
        String currency = (String) context.get("currency");
        GenericValue orderPaymentPreference = (GenericValue) context.get("orderPaymentPreference");

        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("refundAmount", refundAmount);
        Boolean isSuccess = Boolean.FALSE;

        try {
            Properties fdProperties = buildFDProperties(paymentGatewayConfigId, delegator);
            String clientRequestId = UUID.randomUUID().toString();
            String epochTime = String.valueOf(System.currentTimeMillis());

            Map<String, Object> ccRefundReqContext = new HashMap<>();
            ccRefundReqContext.put("amount", refundAmount);
            ccRefundReqContext.put("currency", currency);

            StringWriter outWriter = new StringWriter();
            String firstDataRefundTemplate = EntityUtilProperties.getPropertyValue("firstdata", "paymentgateway.firstdata.template.refund"
                    + ".location", delegator);
            FreeMarkerWorker.renderTemplate(firstDataRefundTemplate, ccRefundReqContext, outWriter);
            String requestBody = outWriter.toString();

            String messageSignature = buildMessageSignature(fdProperties, requestBody, clientRequestId, epochTime);

            GenericValue paymentGatewayResponse = EntityQuery.use(delegator)
                    .from("PaymentGatewayResponse")
                    .where("orderPaymentPreferenceId", orderPaymentPreference.getString("orderPaymentPreferenceId"), "paymentMethodId",
                            orderPaymentPreference.getString("paymentMethodId"), "transCodeEnumId", "PGT_CAPTURE",
                            "paymentServiceTypeEnumId", "PRDS_PAY_CAPTURE")
                    .queryFirst();
            if (paymentGatewayResponse == null) {
                return ServiceUtil.returnError("No capture payment gateway response found for orderPaymentPreferenceId: "
                        + orderPaymentPreference.getString("orderPaymentPreferenceId"));
            }
            String captureTransactionId = UtilValidate.isNotEmpty(paymentGatewayResponse.getString("referenceNum"))
                    ? paymentGatewayResponse.getString("referenceNum")
                    : paymentGatewayResponse.getString("altReference");

            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                StringEntity stringEntity = new StringEntity(requestBody);
                HttpPost httpPost = new HttpPost(fdProperties.getProperty("transactionUrl") + "/payments/" + captureTransactionId);
                httpPost.setEntity(stringEntity);
                httpPost.setHeader("Client-Request-Id", clientRequestId);
                httpPost.setHeader("Api-Key", fdProperties.getProperty("apiKey"));
                httpPost.setHeader("Timestamp", epochTime);
                httpPost.setHeader("Message-Signature", messageSignature);
                httpPost.setHeader("Content-Type", "application/json");

                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    HttpEntity entity = response.getEntity();
                    String responseString = EntityUtils.toString(entity);

                    ObjectMapper objectMapper = new ObjectMapper();
                    Map<String, Object> convertedMap = objectMapper.readValue(responseString, new TypeReference<Map<String, Object>>() {
                    });

                    String transactionStatus = (String) convertedMap.get("transactionStatus");
                    String transactionId = (String) convertedMap.get("ipgTransactionId");
                    String fdOrderId = (String) convertedMap.get("orderId");
                    int statusCode = response.getStatusLine().getStatusCode();
                    String gatewayMessage = "";
                    Object processorObj = convertedMap.get("processor");
                    if (processorObj != null) {
                        Map<String, Object> processor = objectMapper.convertValue(processorObj, new TypeReference<Map<String, Object>>() {
                        });
                        gatewayMessage = (String) processor.get("responseMessage");
                    }
                    result.put("refundCode", String.valueOf(statusCode));
                    result.put("refundMessage", gatewayMessage);
                    if (UtilValidate.isNotEmpty(transactionId)) {
                        result.put("refundRefNum", transactionId);
                        result.put("refundAltRefNum", fdOrderId);
                        if ("APPROVED".equalsIgnoreCase(transactionStatus)) {
                            isSuccess = Boolean.TRUE;
                        }
                    }
                    if (!isSuccess) {
                        String errorMessage = "Transaction Type:" + (String) convertedMap.get("transactionType") + " Transaction Id: " + transactionId
                                + " Transaction Status: " + transactionStatus + " Message: " + statusCode + "-" + gatewayMessage;
                        result.put("refundMessage", errorMessage);
                    }
                }
            }
        } catch (TemplateException | IOException | GenericEntityException e) {
            Debug.logError(e, "Could not complete First Data transaction: " + e.toString(), MODULE);
        }
        result.put("refundResult", isSuccess);
        return result;
    }

    public static Map<String, Object> ccRelease(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        BigDecimal releaseAmount = (BigDecimal) context.get("releaseAmount");
        String paymentGatewayConfigId = (String) context.get("paymentGatewayConfigId");
        String currency = (String) context.get("currency");
        GenericValue orderPaymentPreference = (GenericValue) context.get("orderPaymentPreference");

        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("releaseAmount", releaseAmount);
        Boolean isSuccess = Boolean.FALSE;

        try {
            Properties fdProperties = buildFDProperties(paymentGatewayConfigId, delegator);
            String clientRequestId = UUID.randomUUID().toString();
            String epochTime = String.valueOf(System.currentTimeMillis());

            Map<String, Object> ccReleaseReqContext = new HashMap<>();
            ccReleaseReqContext.put("comments",
                    "The amount " + currency + " " + releaseAmount + " against OrderPaymentPreferenceId " + orderPaymentPreference.getString(
                            "orderPaymentPreferenceId") + " is released.");

            StringWriter outWriter = new StringWriter();
            String firstDataReleaseTemplate = EntityUtilProperties.getPropertyValue("firstdata", "paymentgateway.firstdata.template.release"
                    + ".location", delegator);
            FreeMarkerWorker.renderTemplate(firstDataReleaseTemplate, ccReleaseReqContext, outWriter);
            String requestBody = outWriter.toString();

            String messageSignature = buildMessageSignature(fdProperties, requestBody, clientRequestId, epochTime);

            GenericValue paymentGatewayResponse = EntityQuery.use(delegator).from("PaymentGatewayResponse")
                    .where("orderPaymentPreferenceId", orderPaymentPreference.getString("orderPaymentPreferenceId"), "paymentMethodId",
                            orderPaymentPreference.getString("paymentMethodId"), "transCodeEnumId", "PGT_AUTHORIZE", "paymentServiceTypeEnumId",
                            "PRDS_PAY_AUTH")
                    .queryFirst();
            if (paymentGatewayResponse == null) {
                return ServiceUtil.returnError("No authorization payment gateway response found for orderPaymentPreferenceId: "
                        + orderPaymentPreference.getString("orderPaymentPreferenceId"));
            }
            String releaseTransactionId = UtilValidate.isNotEmpty(paymentGatewayResponse.getString("referenceNum"))
                    ? paymentGatewayResponse.getString("referenceNum")
                    : paymentGatewayResponse.getString("altReference");

            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                StringEntity stringEntity = new StringEntity(requestBody);
                HttpPost httpPost = new HttpPost(fdProperties.getProperty("transactionUrl") + "/payments/" + releaseTransactionId);
                httpPost.setEntity(stringEntity);
                httpPost.setHeader("Client-Request-Id", clientRequestId);
                httpPost.setHeader("Api-Key", fdProperties.getProperty("apiKey"));
                httpPost.setHeader("Timestamp", epochTime);
                httpPost.setHeader("Message-Signature", messageSignature);
                httpPost.setHeader("Content-Type", "application/json");

                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    HttpEntity entity = response.getEntity();
                    String responseString = EntityUtils.toString(entity);

                    ObjectMapper objectMapper = new ObjectMapper();
                    Map<String, Object> convertedMap = objectMapper.readValue(responseString, new TypeReference<Map<String, Object>>() {
                    });

                    String transactionStatus = (String) convertedMap.get("transactionStatus");
                    String transactionId = (String) convertedMap.get("ipgTransactionId");
                    String fdOrderId = (String) convertedMap.get("orderId");
                    int statusCode = response.getStatusLine().getStatusCode();
                    String gatewayMessage = "";
                    Object processorObj = convertedMap.get("processor");
                    if (processorObj != null) {
                        Map<String, Object> processor = objectMapper.convertValue(processorObj, new TypeReference<Map<String, Object>>() {
                        });
                        gatewayMessage = (String) processor.get("responseMessage");
                    }
                    result.put("releaseCode", String.valueOf(statusCode));
                    result.put("releaseMessage", gatewayMessage);
                    if (UtilValidate.isNotEmpty(transactionId)) {
                        result.put("releaseRefNum", transactionId);
                        result.put("releaseAltRefNum", fdOrderId);
                        if ("APPROVED".equalsIgnoreCase(transactionStatus)) {
                            isSuccess = Boolean.TRUE;
                        }
                    }
                    if (!isSuccess) {
                        String errorMessage = "Transaction Type:" + (String) convertedMap.get("transactionType") + " Transaction Id: " + transactionId
                                + " Transaction Status: " + transactionStatus + " Message: " + statusCode + "-" + gatewayMessage;
                        result.put("releaseMessage", errorMessage);
                    }
                }
            }
        } catch (TemplateException | IOException | GenericEntityException e) {
            Debug.logError(e, "Could not complete First Data transaction: " + e.toString(), MODULE);
        }
        result.put("releaseResult", isSuccess);
        return result;
    }

    private static Properties buildFDProperties(String paymentGatewayConfigId, Delegator delegator) {
        Properties props = new Properties();
        props.put("transactionUrl", getPaymentGatewayConfigValue(delegator, paymentGatewayConfigId, "transactionUrl"));
        props.put("appName", getPaymentGatewayConfigValue(delegator, paymentGatewayConfigId, "appName"));
        props.put("apiKey", getPaymentGatewayConfigValue(delegator, paymentGatewayConfigId, "apiKey"));
        props.put("apiSecret", getPaymentGatewayConfigValue(delegator, paymentGatewayConfigId, "apiSecret"));
        return props;
    }

    private static String getPaymentGatewayConfigValue(Delegator delegator, String paymentGatewayConfigId, String paymentGatewayConfigParameterName) {
        String returnValue = null;
        if (UtilValidate.isNotEmpty(paymentGatewayConfigId)) {
            try {
                GenericValue paymentGatewayFirstData = EntityQuery.use(delegator).from("PaymentGatewayFirstData").where("paymentGatewayConfigId",
                        paymentGatewayConfigId).queryOne();
                if (paymentGatewayFirstData != null) {
                    Object payflowProField = paymentGatewayFirstData.get(paymentGatewayConfigParameterName);
                    if (payflowProField != null) {
                        returnValue = payflowProField.toString().trim();
                    }
                }
            } catch (GenericEntityException e) {
                Debug.logError(e, MODULE);
            }
        }
        return returnValue;
    }

    private static String buildMessageSignature(Properties fdProperties, String requestBody, String clientRequestId, String epochTime) {
        String apiKey = fdProperties.getProperty("apiKey");
        final HmacUtils hmacHelper = new HmacUtils(HmacAlgorithms.HMAC_SHA_256, fdProperties.getProperty("apiSecret"));
        final Hex hexHelper = new Hex();
        final String msg = apiKey + clientRequestId + epochTime + requestBody;
        final byte[] raw = hmacHelper.hmac(msg);
        final byte[] hex = hexHelper.encode(raw);
        return Base64.encodeBase64String(hex);
    }
}
