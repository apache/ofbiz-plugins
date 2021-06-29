package org.apache.ofbiz.shopify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ofbiz.base.lang.JSON;
import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ModelService;
import org.apache.ofbiz.service.ServiceUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class ShopifyServices {

    public static final String MODULE = ShopifyServices.class.getName();
    public static final String resource = "ShopifyUiLabels";

    public static Map<String, Object> createUpdateShopifyConfiguration(DispatchContext dctx, Map<String, Object> context) {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Locale locale = (Locale) context.get("locale");
        Map<String, Object> result = new HashMap<>();
        String apiUrl = (String) context.get("apiUrl");
        String username = (String) context.get("username");
        String password = (String) context.get("password");
        try {
            if (UtilValidate.isNotEmpty(apiUrl)) {
                GenericValue apiUrlProperty = EntityQuery.use(delegator).from("SystemProperty").where("systemResourceId", "ShopifyConfig", "systemPropertyId", "shopifyApiUrl").queryOne();
                if (apiUrlProperty == null) {
                    GenericValue systemProperty = delegator.makeValue("SystemProperty", UtilMisc.toMap("systemResourceId", "ShopifyConfig", "systemPropertyId", "shopifyApiUrl", "systemPropertyValue", apiUrl));
                    systemProperty.create();
                } else {
                    apiUrlProperty.set("systemPropertyValue", apiUrl);
                    apiUrlProperty.store();
                }
            }
            if (UtilValidate.isNotEmpty(username)) {
                GenericValue usernameProperty = EntityQuery.use(delegator).from("SystemProperty").where("systemResourceId", "ShopifyConfig", "systemPropertyId", "shopifyUsername").queryOne();
                if (usernameProperty == null) {
                    GenericValue systemProperty = delegator.makeValue("SystemProperty", UtilMisc.toMap("systemResourceId", "ShopifyConfig", "systemPropertyId", "shopifyUsername", "systemPropertyValue", username));
                    systemProperty.create();
                } else {
                    usernameProperty.set("systemPropertyValue", username);
                    usernameProperty.store();
                }
            }
            if (UtilValidate.isNotEmpty(password)) {
                GenericValue passwordProperty = EntityQuery.use(delegator).from("SystemProperty").where("systemResourceId", "ShopifyConfig", "systemPropertyId", "shopifyPassword").queryOne();
                if (passwordProperty == null) {
                    GenericValue systemProperty = delegator.makeValue("SystemProperty", UtilMisc.toMap("systemResourceId", "ShopifyConfig", "systemPropertyId", "shopifyPassword", "systemPropertyValue", password));
                    systemProperty.create();
                } else {
                    passwordProperty.set("systemPropertyValue", password);
                    passwordProperty.store();
                }
            }
        } catch (GenericEntityException e) {
            Debug.logError("Exception in createUpdateShopifyConfiguration " + e, MODULE);
            return ServiceUtil.returnFailure(UtilProperties.getMessage(resource, "ExceptionInCreateUpdateShopifyConfiguration", locale));
        }
        result.put(ModelService.SUCCESS_MESSAGE, UtilProperties.getMessage(resource, "ShopifyConfigurationAdded", locale));
        return result;
    }

    private static Map<String, Object> sendShopifyRequest(Map<String, Object> context) {
        Map<String, Object> parameters = (Map) (context.get("parameters"));
        Map<String, String> queryMap = (Map) (context.get("queryMap"));
        Map<String, String> headerMap = (Map) (context.get("headerMap"));
        Delegator delegator = (Delegator) context.get("delegator");
        Locale locale = (Locale) context.get("locale");
        String endpoint = (String) context.get("endpoint");
        String contentType = (String) context.get("contentType");
        String requestType = (String) context.get("requestType");
        Map<String, Object> httpResponse = new HashMap<>();

        try {
            Properties configProperties = EntityUtilProperties.getProperties(delegator, "ShopifyConfig");
            String apiUrl = (String) configProperties.get("shopifyApiUrl");
            if (UtilValidate.isEmpty(apiUrl)) {
                Debug.logError(UtilProperties.getMessage(resource, "ShopifyApiUrlMissing", locale), MODULE);
                return ServiceUtil.returnError(UtilProperties.getMessage(resource, "ShopifyApiUrlMissing", locale));
            } else {
                String nextLink = "";
                List<String> responseStringList = new ArrayList<>();
                String username = (String) configProperties.get("shopifyUsername");
                String password = (String) configProperties.get("shopifyPassword");
                if (UtilValidate.isEmpty(username)) {
                    Debug.logError(UtilProperties.getMessage(resource, "ShopifyApiUsernameMissing", locale), MODULE);
                    return ServiceUtil.returnError(UtilProperties.getMessage(resource, "ShopifyApiUsernameMissing", locale));
                }
                if (UtilValidate.isEmpty(password)) {
                    Debug.logError(UtilProperties.getMessage(resource, "ShopifyApiPasswordMissing", locale), MODULE);
                    return ServiceUtil.returnError(UtilProperties.getMessage(resource, "ShopifyApiPasswordMissing", locale));
                }
                do {
                    String url = "";
                    url = apiUrl + endpoint;
                    if (UtilValidate.isNotEmpty(nextLink)) {
                        url = nextLink;
                    }
                    HttpClient httpClient = new HttpClient(url);
                    httpClient.setBasicAuthInfo(username, password);
                    if (UtilValidate.isNotEmpty(headerMap)) {
                        httpClient.setHeaders(headerMap);
                    }

                    // Setting default limit
                    if (UtilValidate.isEmpty(parameters.get("limit"))) {
                        parameters.put("limit", 250);
                    }

                    if (UtilValidate.isNotEmpty(parameters)) {
                        httpClient.setParameters(parameters);
                    }
                    if (UtilValidate.isNotEmpty(queryMap)) {
                        JSON json = JSON.from(queryMap);
                        String jsonStr = null;
                        if (UtilValidate.isNotEmpty(json)) {
                            jsonStr = json.toString();
                        }
                        httpClient.setRawStream(jsonStr);
                    }
                    httpClient.setTimeout(60 * 1000);
                    httpClient.setContentType(contentType);
                    httpClient.setDebug(false);

                    String httpResponseString = null;
                    if ("GET".equalsIgnoreCase(requestType)) {
                        httpResponseString = httpClient.get();
                    } else {
                        httpResponseString = httpClient.post();
                    }
                    if (httpResponseString != null) {
                        //Preparing a list of responses, this will be later iterated to prepare a combined response coming from paginated requests
                        responseStringList.add(httpResponseString);
                    }

                    /* Handling pagination, Shopify returns 250 records at maximum. It does return links to previous and next set of records in the response header.*/
                    String paginationLink = httpClient.getResponseHeader("Link");
                    List<String> links = StringUtil.split(paginationLink, ","); // May contain previous and next link
                    if (UtilValidate.isNotEmpty(links)) {
                        for (String link : links) {
                            List<String> parsedLink = StringUtil.split(link, ";");
                            // obtaining next link
                            if (link.contains("\"next\"")) {
                                nextLink = parsedLink.get(0);
                                if (UtilValidate.isNotEmpty(nextLink)) {
                                    if (nextLink.indexOf('<') >= 0 && nextLink.lastIndexOf('>') >= 0) {
                                        nextLink = nextLink.substring(nextLink.indexOf('<') + 1, nextLink.lastIndexOf('>'));
                                    }
                                }
                            } else {
                                nextLink = "";
                            }
                        }
                    } else {
                        nextLink = "";
                    }
                } while (UtilValidate.isNotEmpty(nextLink));

                if (UtilValidate.isNotEmpty(responseStringList)) {
                    if (responseStringList.size() > 1) {
                        String key = "";
                        List<Map<String, Object>> value = new ArrayList<>();
                        for (String response : responseStringList) {
                            Map<String,Object> responseMap = new HashMap<String,Object>();
                            ObjectMapper mapper = new ObjectMapper();
                            try {
                                //convert JSON string to Map
                                responseMap = mapper.readValue(response, new TypeReference<HashMap<String,Object>>(){});
                            } catch (Exception e) {
                                Debug.logError(e, MODULE);
                            }
                            for (Map.Entry<String, ? extends Object> entry : responseMap.entrySet()) {
                                key = entry.getKey();
                                if (entry.getValue() instanceof List) {
                                    if (UtilValidate.isNotEmpty(value)) {
                                        value.addAll((List) entry.getValue());
                                    } else {
                                        value = (List) (entry.getValue());
                                    }
                                }
                            }
                        }
                        JSON json = JSON.from(UtilMisc.toMap(key, value));
                        String combinedResponse = null;
                        if (UtilValidate.isNotEmpty(json)) {
                            combinedResponse = json.toString();
                        }
                        httpResponse.put("httpResponse", combinedResponse);
                    } else {
                        httpResponse.put("httpResponse", responseStringList.get(0));
                    }
                }
            }
        } catch (HttpClientException | IOException e) {
            Debug.logError(e, "Could not complete transaction: " + e.toString(), MODULE);
        }
        return httpResponse;
    }

}