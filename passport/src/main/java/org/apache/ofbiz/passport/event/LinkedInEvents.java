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
package org.apache.ofbiz.passport.event;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Random;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.ofbiz.passport.user.LinkedInAuthenticator;
import org.apache.ofbiz.passport.util.PassportUtil;
import org.apache.ofbiz.base.conversion.ConversionException;
import org.apache.ofbiz.base.conversion.JSONConverters.JSONToMap;
import org.apache.ofbiz.base.crypto.HashCrypt;
import org.apache.ofbiz.base.lang.JSON;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.common.authentication.api.AuthenticatorException;
import org.apache.ofbiz.common.login.LoginServices;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.product.store.ProductStoreWorker;
import org.apache.ofbiz.service.LocalDispatcher;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * LinkedEvents - Events for LinkedIn login.
 * 
 * Refs: https://developer.linkedin.com/documents/authentication
 * 
 */
public class LinkedInEvents {

    private static final String MODULE = LinkedInEvents.class.getName();
    private static final String RESOURCE = "PassportUiLabels";
    public static final String AuthorizeUri = "/uas/oauth2/authorization";
    public static final String TokenServiceUri = "/uas/oauth2/accessToken";
    public static final String UserApiUri = "/v1/people/~";
    public static final String DEFAULT_SCOPE = "r_basicprofile%20r_emailaddress";
    public static final String TokenEndpoint = "https://www.linkedin.com";
    public static final String SESSION_LINKEDIN_STATE = "_LINKEDIN_STATE_";
    public static final String envPrefix = UtilProperties.getPropertyValue(LinkedInAuthenticator.props, "linkedin.env.prefix", "test");

    /**
     * Redirect to LinkedIn login page.
     * @return
     */
    public static String linkedInRedirect(HttpServletRequest request, HttpServletResponse response) {
        GenericValue oauth2LinkedIn = getOAuth2LinkedInConfig(request);
        if (UtilValidate.isEmpty(oauth2LinkedIn)) {
            return "error";
        }

        String clientId = oauth2LinkedIn.getString(PassportUtil.ApiKeyLabel);
        String returnURI = oauth2LinkedIn.getString(envPrefix + PassportUtil.ReturnUrlLabel);

        // Get user authorization code
        try {
            String state = System.currentTimeMillis() + String.valueOf((new Random(10)).nextLong());
            request.getSession().setAttribute(SESSION_LINKEDIN_STATE, state);
            String redirectUrl = TokenEndpoint + AuthorizeUri
                    + "?client_id=" + clientId
                    + "&response_type=code"
                    + "&scope=" + DEFAULT_SCOPE
                    + "&redirect_uri=" + URLEncoder.encode(returnURI, "UTF-8")
                    + "&state=" + state;
            response.sendRedirect(redirectUrl);
        } catch (NullPointerException e) {
            String errMsg = UtilProperties.getMessage(RESOURCE, "LinkedInRedirectToOAuth2NullException", UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        } catch (IOException e) {
            Map<String, String> messageMap = UtilMisc.toMap("errorMessage", e.toString());
            String errMsg = UtilProperties.getMessage(RESOURCE, "LinkedInRedirectToOAuth2Error", messageMap, UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }

        return "success";
    }

    /**
     * Parse LinkedIn login response and login the user if possible.
     * @return
     */
    public static String parseLinkedInResponse(HttpServletRequest request, HttpServletResponse response) {
        String authorizationCode = request.getParameter(PassportUtil.COMMON_CODE);
        String state = request.getParameter(PassportUtil.COMMON_STATE);
        if (!state.equals(request.getSession().getAttribute(SESSION_LINKEDIN_STATE))) {
            String errMsg = UtilProperties.getMessage(RESOURCE, "LinkedInFailedToMatchState", UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }
        if (UtilValidate.isEmpty(authorizationCode)) {
            String error = request.getParameter(PassportUtil.COMMON_ERROR);
            String errorDescpriton = request.getParameter(PassportUtil.COMMON_ERROR_DESCRIPTION);
            String errMsg = null;
            try {
                errMsg = UtilProperties.getMessage(RESOURCE, "LinkedInFailedToGetAuthorizationCode", UtilMisc.toMap(PassportUtil.COMMON_ERROR, error, PassportUtil.COMMON_ERROR_DESCRIPTION, URLDecoder.decode(errorDescpriton, "UTF-8")), UtilHttp.getLocale(request));
            } catch (UnsupportedEncodingException e) {
                errMsg = UtilProperties.getMessage(RESOURCE, "LinkedInGetAuthorizationCodeError", UtilHttp.getLocale(request));
            }
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }
        // Debug.logInfo("LinkedIn authorization code: " + authorizationCode, MODULE);

        GenericValue oauth2LinkedIn = getOAuth2LinkedInConfig(request);
        if (UtilValidate.isEmpty(oauth2LinkedIn)) {
            String errMsg = UtilProperties.getMessage(RESOURCE, "LinkedInGetOAuth2ConfigError", UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }
        String clientId = oauth2LinkedIn.getString(PassportUtil.ApiKeyLabel);
        String secret = oauth2LinkedIn.getString(PassportUtil.SecretKeyLabel);
        String returnURI = oauth2LinkedIn.getString(envPrefix + PassportUtil.ReturnUrlLabel);

        // Grant token from authorization code and oauth2 token
        // Use the authorization code to obtain an access token
        String accessToken = null;

        try {
            URI uri = new URIBuilder()
                    .setScheme(TokenEndpoint.substring(0, TokenEndpoint.indexOf(":")))
                    .setHost(TokenEndpoint.substring(TokenEndpoint.indexOf(":") + 3))
                    .setPath(TokenServiceUri)
                    .setParameter("client_id", clientId)
                    .setParameter("client_secret", secret)
                    .setParameter("grant_type", "authorization_code")
                    .setParameter("code", authorizationCode)
                    .setParameter("redirect_uri", returnURI)
                    .build();
            HttpPost postMethod = new HttpPost(uri);
            CloseableHttpClient jsonClient = HttpClients.custom().build();
            // Debug.logInfo("LinkedIn get access token query string: " + postMethod.getURI(), MODULE);
            postMethod.setConfig(PassportUtil.StandardRequestConfig);
            CloseableHttpResponse postResponse = jsonClient.execute(postMethod);
            String responseString = new BasicResponseHandler().handleResponse(postResponse);
            // Debug.logInfo("LinkedIn get access token response code: " + postResponse.getStatusLine().getStatusCode(), MODULE);
            // Debug.logInfo("LinkedIn get access token response content: " + responseString, MODULE);
            if (postResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                // Debug.logInfo("Json Response from LinkedIn: " + responseString, MODULE);
                JSON jsonObject = JSON.from(responseString);
                JSONToMap jsonMap = new JSONToMap();
                Map<String, Object> userMap = jsonMap.convert(jsonObject);
                accessToken = (String) userMap.get("access_token");
                // Debug.logInfo("Generated Access Token : " + accessToken, MODULE);
            } else {
                String errMsg = UtilProperties.getMessage(RESOURCE, "LinkedInGetOAuth2AccessTokenError", UtilMisc.toMap("error", responseString), UtilHttp.getLocale(request));
                request.setAttribute("_ERROR_MESSAGE_", errMsg);
                return "error";
            }
        } catch (URISyntaxException | ConversionException | IOException e) {
            request.setAttribute("_ERROR_MESSAGE_", e.toString());
            return "error";
        }

        // Get User Profile
        HttpGet getMethod = new HttpGet(TokenEndpoint + UserApiUri + "?oauth2_access_token=" + accessToken);
        Document userInfo = null;
        try {
            userInfo = LinkedInAuthenticator.getUserInfo(getMethod, UtilHttp.getLocale(request));
        } catch (IOException | ParserConfigurationException | SAXException | AuthenticatorException e) {
            request.setAttribute("_ERROR_MESSAGE_", e.toString());
            return "error";
        } finally {
            getMethod.releaseConnection();
        }
        // Debug.logInfo("LinkedIn User Info:" + userInfo, MODULE);

        // Store the user info and check login the user
        return checkLoginLinkedInUser(request, userInfo, accessToken);
    }

    private static String checkLoginLinkedInUser(HttpServletRequest request, Document userInfo, String accessToken) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        String productStoreId = ProductStoreWorker.getProductStoreId(request);
        String linkedInUserId = LinkedInAuthenticator.getLinkedInUserId(userInfo);
        GenericValue linkedInUser = null;
        try {
            linkedInUser = EntityQuery.use(delegator).from("LinkedInUser").where("linkedInUserId", linkedInUserId).queryOne();
        } catch (GenericEntityException e) {
            request.setAttribute("_ERROR_MESSAGE_", e.getMessage());
            return "error";
        }
        if (linkedInUser != null) {
            boolean dataChanged = false;
            if (!accessToken.equals(linkedInUser.getString("accessToken"))) {
                linkedInUser.set("accessToken", accessToken);
                dataChanged = true;
            }
            if (!envPrefix.equals(linkedInUser.getString("envPrefix"))) {
                linkedInUser.set("envPrefix", envPrefix);
                dataChanged = true;
            }
            if (!productStoreId.equals(linkedInUser.getString("productStoreId"))) {
                linkedInUser.set("productStoreId", productStoreId);
                dataChanged = true;
            }
            if (dataChanged) {
                try {
                    linkedInUser.store();
                } catch (GenericEntityException e) {
                    Debug.logError(e.getMessage(), MODULE);
                }
            }
        } else {
            linkedInUser = delegator.makeValue("LinkedInUser", UtilMisc.toMap("accessToken", accessToken,
                                                                          "productStoreId", productStoreId,
                                                                          "envPrefix", envPrefix,
                                                                          "linkedInUserId", linkedInUserId));
            try {
                linkedInUser.create();
            } catch (GenericEntityException e) {
                Debug.logError(e.getMessage(), MODULE);
            }
        }
        try {
            GenericValue userLogin = EntityQuery.use(delegator).from("UserLogin").where("externalAuthId", linkedInUserId).queryFirst();
            LinkedInAuthenticator authn = new LinkedInAuthenticator();
            authn.initialize(dispatcher);
            if (UtilValidate.isEmpty(userLogin)) {
                String userLoginId = authn.createUser(userInfo);
                userLogin = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", userLoginId).queryOne();
            }
            String autoPassword = RandomStringUtils.randomAlphanumeric(EntityUtilProperties.getPropertyAsInteger("security", "password.length.min", 5));
            boolean useEncryption = "true".equals(UtilProperties.getPropertyValue("security", "password.encrypt"));
            userLogin.set("currentPassword", useEncryption ? HashCrypt.digestHash(LoginServices.getHashType(), null, autoPassword) : autoPassword);
            userLogin.store();
            request.setAttribute("USERNAME", userLogin.getString("userLoginId"));
            request.setAttribute("PASSWORD", autoPassword);
        } catch (GenericEntityException | AuthenticatorException e) {
            Debug.logError(e.getMessage(), MODULE);
            request.setAttribute("_ERROR_MESSAGE_", e.toString());
            return "error";
        }
        return "success";
    }

    public static GenericValue getOAuth2LinkedInConfig(HttpServletRequest request) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        String productStoreId = ProductStoreWorker.getProductStoreId(request);
        try {
            return getOAuth2LinkedInConfig(delegator, productStoreId);
        } catch (GenericEntityException e) {
            Map<String, String> messageMap = UtilMisc.toMap("errorMessage", e.toString());
            String errMsg = UtilProperties.getMessage(RESOURCE, "LinkedInGetOAuth2Error", messageMap, UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
        }
        return null;
    }

    public static GenericValue getOAuth2LinkedInConfig(Delegator delegator, String productStoreId) throws GenericEntityException {
        return EntityQuery.use(delegator).from("OAuth2LinkedIn").where("productStoreId", productStoreId).filterByDate().queryFirst();
    }
}
