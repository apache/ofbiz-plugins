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
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.ofbiz.base.conversion.ConversionException;
import org.apache.ofbiz.base.conversion.JSONConverters.JSONToMap;
import org.apache.ofbiz.base.crypto.HashCrypt;
import org.apache.ofbiz.base.lang.JSON;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.StringUtil;
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
import org.apache.ofbiz.passport.user.GitHubAuthenticator;
import org.apache.ofbiz.passport.util.PassportUtil;
import org.apache.ofbiz.product.store.ProductStoreWorker;
import org.apache.ofbiz.service.LocalDispatcher;

/**
 * GitHubEvents - Events for GitHub login.
 * Refs: https://developer.github.com/v3/oauth/
 */
public class GitHubEvents {

    private static final String MODULE = GitHubEvents.class.getName();
    private static final String RESOURCE = "PassportUiLabels";
    private static final String AUTHORIZE_URI = "/login/oauth/authorize";
    private static final String TOKEN_SERVICE_URI = "/login/oauth/access_token";
    private static final String USER_API_URI = "/user";
    private static final String DEFAULT_SCOPE = "user,gist";
    private static final String API_END_POINT = "https://api.github.com";
    private static final String TOKEN_END_POINT = "https://github.com";
    private static final String SESSION_GITHUB_STATE = "_GITHUB_STATE_";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int AUTO_PASSWORD_LENGTH = 16;

    public static final String ENV_PREFIX = UtilProperties.getPropertyValue(GitHubAuthenticator.PROPS, "github.env.prefix", "test");

    public static String getApiEndPoint() {
        return API_END_POINT;
    }

    public static String getUserApiUri() {
        return USER_API_URI;
    }

    /**
     * Redirect to GitHub login page.
     * @return string "success" or "error"
     */
    public static String gitHubRedirect(HttpServletRequest request, HttpServletResponse response) {
        GenericValue oauth2GitHub = getOAuth2GitHubConfig(request);
        if (UtilValidate.isEmpty(oauth2GitHub)) {
            return "error";
        }
        String clientId = oauth2GitHub.getString(PassportUtil.COMMON_CLIENT_ID);
        String returnURI = oauth2GitHub.getString(PassportUtil.COMMON_RETURN_RUL);

        // Get user authorization code
        try {
            String state = System.currentTimeMillis() + String.valueOf((SECURE_RANDOM.nextLong()));
            request.getSession().setAttribute(SESSION_GITHUB_STATE, state);
            String redirectUrl = TOKEN_END_POINT + AUTHORIZE_URI
                    + "?client_id=" + clientId
                    + "&scope=" + DEFAULT_SCOPE
                    + "&redirect_uri=" + URLEncoder.encode(returnURI, "UTF-8")
                    + "&state=" + state;
            Debug.logInfo("Request to GitHub: " + redirectUrl, MODULE);
            response.sendRedirect(redirectUrl);
        } catch (NullPointerException e) {
            String errMsg = UtilProperties.getMessage(RESOURCE, "GitHubRedirectToOAuth2NullException", UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        } catch (IOException e) {
            Map<String, String> messageMap = UtilMisc.toMap("errorMessage", e.toString());
            String errMsg = UtilProperties.getMessage(RESOURCE, "GitHubRedirectToOAuth2Error", messageMap, UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }

        return "success";
    }

    /**
     * Parse GitHub login response and login the user if possible.
     * @return string "success" or "error"
     */
    public static String parseGitHubResponse(HttpServletRequest request, HttpServletResponse response) {
        String authorizationCode = request.getParameter(PassportUtil.COMMON_CODE);
        String state = request.getParameter(PassportUtil.COMMON_STATE);
        if (!state.equals(request.getSession().getAttribute(SESSION_GITHUB_STATE))) {
            String errMsg = UtilProperties.getMessage(RESOURCE, "GitHubFailedToMatchState", UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }
        if (UtilValidate.isEmpty(authorizationCode)) {
            String error = request.getParameter(PassportUtil.COMMON_ERROR);
            String errorDescpriton = request.getParameter(PassportUtil.COMMON_ERROR_DESCRIPTION);
            String errMsg = null;
            try {
                errMsg = UtilProperties.getMessage(RESOURCE, "FailedToGetGitHubAuthorizationCode", UtilMisc.toMap(PassportUtil.COMMON_ERROR,
                        error, PassportUtil.COMMON_ERROR_DESCRIPTION, URLDecoder.decode(errorDescpriton, "UTF-8")), UtilHttp.getLocale(request));
            } catch (UnsupportedEncodingException e) {
                errMsg = UtilProperties.getMessage(RESOURCE, "GitHubGetAuthorizationCodeError", UtilHttp.getLocale(request));
            }
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }

        GenericValue oauth2GitHub = getOAuth2GitHubConfig(request);
        if (UtilValidate.isEmpty(oauth2GitHub)) {
            String errMsg = UtilProperties.getMessage(RESOURCE, "GitHubGetOAuth2ConfigError", UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }
        String clientId = oauth2GitHub.getString(PassportUtil.COMMON_CLIENT_ID);
        String secret = oauth2GitHub.getString(PassportUtil.COMMON_CLIENT_SECRET);
        String returnURI = oauth2GitHub.getString(PassportUtil.COMMON_RETURN_RUL);

        // Grant token from authorization code and oauth2 token
        // Use the authorization code to obtain an access token
        String accessToken = null;
        String tokenType = null;

        try {
            // send the client credentials and the code in the request body, not in the URL
            HttpPost postMethod = new HttpPost(TOKEN_END_POINT + TOKEN_SERVICE_URI);
            postMethod.setEntity(new UrlEncodedFormEntity(List.of(
                    new BasicNameValuePair("client_id", clientId),
                    new BasicNameValuePair("client_secret", secret),
                    new BasicNameValuePair("code", authorizationCode),
                    new BasicNameValuePair("redirect_uri", returnURI)), StandardCharsets.UTF_8));
            CloseableHttpClient jsonClient = HttpClients.custom().build();
            // Debug.logInfo("GitHub get access token query string: " + postMethod.getURI(), MODULE);
            postMethod.setConfig(PassportUtil.STANDARD_REQ_CONFIG);
            postMethod.setHeader(PassportUtil.ACCEPT_HEADER, "application/json");
            CloseableHttpResponse postResponse = jsonClient.execute(postMethod);
            String responseString = new BasicResponseHandler().handleResponse(postResponse);
            // Debug.logInfo("GitHub get access token response code: " + postResponse.getStatusLine().getStatusCode(), MODULE);
            // Debug.logInfo("GitHub get access token response content: " + responseString, MODULE);
            if (postResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                JSON jsonObject = JSON.from(responseString);
                JSONToMap jsonMap = new JSONToMap();
                Map<String, Object> userMap = jsonMap.convert(jsonObject);
                accessToken = (String) userMap.get("access_token");
                tokenType = (String) userMap.get("token_type");
                // Debug.logInfo("Generated Access Token : " + accessToken, MODULE);
                // Debug.logInfo("Token Type: " + tokenType, MODULE);
            } else {
                String errMsg = UtilProperties.getMessage(RESOURCE, "GitHubGetOAuth2AccessTokenError",
                        UtilMisc.toMap("error", responseString), UtilHttp.getLocale(request));
                request.setAttribute("_ERROR_MESSAGE_", errMsg);
                return "error";
            }
        } catch (ConversionException | IOException e) {
            request.setAttribute("_ERROR_MESSAGE_", e.toString());
            return "error";
        }

        // Get User Profile
        HttpGet getMethod = new HttpGet(API_END_POINT + USER_API_URI);
        Map<String, Object> userInfo = null;
        try {
            userInfo = GitHubAuthenticator.getUserInfo(getMethod, accessToken, tokenType, UtilHttp.getLocale(request));
        } catch (AuthenticatorException e) {
            request.setAttribute("_ERROR_MESSAGE_", e.toString());
            return "error";
        } finally {
            getMethod.releaseConnection();
        }
        // Debug.logInfo("GitHub User Info:" + userInfo, MODULE);

        // Store the user info and check login the user
        return checkLoginGitHubUser(request, userInfo, accessToken);
    }

    private static String checkLoginGitHubUser(HttpServletRequest request, Map<String, Object> userInfo, String accessToken) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        String productStoreId = ProductStoreWorker.getProductStoreId(request);
        // GitHub login names can be renamed and taken over by someone else, so users are identified by their numeric id
        String gitHubUserId = GitHubAuthenticator.getGitHubUserId(userInfo);
        String gitHubLogin = (String) userInfo.get("login");
        if (UtilValidate.isEmpty(gitHubUserId)) {
            request.setAttribute("_ERROR_MESSAGE_", UtilProperties.getMessage(RESOURCE, "GitHubGetOAuth2AccessTokenError",
                    UtilMisc.toMap("error", "missing user id"), UtilHttp.getLocale(request)));
            return "error";
        }
        GenericValue gitHubUser = null;
        try {
            gitHubUser = EntityQuery.use(delegator).from("GitHubUser").where("gitHubUserId", gitHubUserId).queryOne();
        } catch (GenericEntityException e) {
            request.setAttribute("_ERROR_MESSAGE_", e.getMessage());
            return "error";
        }
        if (UtilValidate.isNotEmpty(gitHubUser)) {
            boolean dataChanged = false;
            if (!accessToken.equals(gitHubUser.getString("accessToken"))) {
                gitHubUser.set("accessToken", accessToken);
                dataChanged = true;
            }
            if (!ENV_PREFIX.equals(gitHubUser.getString("envPrefix"))) {
                gitHubUser.set("envPrefix", ENV_PREFIX);
                dataChanged = true;
            }
            if (!productStoreId.equals(gitHubUser.getString("productStoreId"))) {
                gitHubUser.set("productStoreId", productStoreId);
                dataChanged = true;
            }
            if (dataChanged) {
                try {
                    gitHubUser.store();
                } catch (GenericEntityException e) {
                    Debug.logError(e.getMessage(), MODULE);
                }
            }
        } else {
            gitHubUser = delegator.makeValue("GitHubUser", UtilMisc.toMap("accessToken", accessToken,
                                                                          "productStoreId", productStoreId,
                                                                          "envPrefix", ENV_PREFIX,
                                                                          "gitHubUserId", gitHubUserId));
            try {
                gitHubUser.create();
            } catch (GenericEntityException e) {
                Debug.logError(e.getMessage(), MODULE);
            }
        }
        try {
            GenericValue userLogin = EntityQuery.use(delegator).from("UserLogin").where("externalAuthId", gitHubUserId).queryFirst();
            if (UtilValidate.isEmpty(userLogin) && UtilValidate.isNotEmpty(gitHubLogin)) {
                // a user linked by login name before the numeric id was used is moved to the numeric id
                userLogin = EntityQuery.use(delegator).from("UserLogin").where("externalAuthId", gitHubLogin).queryFirst();
                if (UtilValidate.isNotEmpty(userLogin)) {
                    userLogin.set("externalAuthId", gitHubUserId);
                }
            }
            GitHubAuthenticator authn = new GitHubAuthenticator();
            authn.initialize(dispatcher);
            if (UtilValidate.isEmpty(userLogin)) {
                String userLoginId = authn.createUser(userInfo);
                userLogin = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", userLoginId).queryOne();
            }
            String autoPassword = StringUtil.randomString(AUTO_PASSWORD_LENGTH, StringUtil.ALPHANUMERIC_CHARS);
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

    public static GenericValue getOAuth2GitHubConfig(HttpServletRequest request) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        String productStoreId = ProductStoreWorker.getProductStoreId(request);
        try {
            return getOAuth2GitHubConfig(delegator, productStoreId);
        } catch (GenericEntityException e) {
            Map<String, String> messageMap = UtilMisc.toMap("errorMessage", e.toString());
            String errMsg = UtilProperties.getMessage(RESOURCE, "GitHubGetOAuth2Error", messageMap, UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
        }
        return null;
    }

    public static GenericValue getOAuth2GitHubConfig(Delegator delegator, String productStoreId) throws GenericEntityException {
        return EntityQuery.use(delegator).from("OAuth2GitHub").where("productStoreId", productStoreId).filterByDate().queryFirst();
    }
}
