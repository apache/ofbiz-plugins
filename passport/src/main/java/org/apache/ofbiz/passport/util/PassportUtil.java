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
package org.apache.ofbiz.passport.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.servlet.http.HttpServletRequest;

import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.ofbiz.base.util.Debug;

public class PassportUtil {

    private static final String MODULE = PassportUtil.class.getName();
    private static final String CLIENT_ID_LABEL = "ClientId";
    private static final String SECRET_LABEL = "Secret";
    private static final String TOKEN_END_POINT_LABEL = "TokenEndpoint";
    private static final String GRANT_TYPE_LABEL = "grantType";
    private static final String CONTENT_TYPE_LABEL = "contentType";
    private static final String USER_PROFILE_URL_LABEL = "UserProfileUrl";
    private static final String GRANT_TYPE_PARAM = "grant_type";
    private static final String CONTENT_TYPE_PARAM = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final String RESTAPI_END_POINT_LABEL = "RESTApiEndpoint";
    private static final String COMMON_SCOPE = "scope";
    private static final String AUTHOR_CODE_GRANT_TYPE = "authorization_code";
    private static final String API_ID_LABEL = "apiId";
    private static final String APP_KEY_LABEL = "appKey";
    private static final String APP_SECRET_LABEL = "appSecret";
    private static final String APP_ID_LABEL = "appId";
    private static final String COMMON_APP_KEY = "AppKey";
    private static final String COMMON_APP_SECRET = "AppSecret";

    // TODO: Following should be made private
    public static final String COMMON_CLIENT_SECRET = "clientSecret";
    public static final String COMMON_CODE = "code";
    public static final String RETURN_URL_LABEL = "ReturnUrl";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String ACCEPT_HEADER = "Accept";
    public static final String COMMON_STATE = "state";
    public static final String COMMON_ERROR = "error";
    public static final String COMMON_ERROR_DESCRIPTION = "error_description";
    public static final String API_KEY_LABEL = "apiKey";
    public static final String SECRET_KEY_LABEL = "secretKey";
    public static final String COMMON_CLIENT_ID = "clientId";
    public static final String COMMON_RETURN_RUL = "returnUrl";
    public static final RequestConfig STANDARD_REQ_CONFIG = RequestConfig.custom()
                                                                           .setCookieSpec(CookieSpecs.STANDARD)
                                                                           .build();

    protected PassportUtil() {
        // empty constructor
    }

    public static PassportUtil getInstance() {
        return new PassportUtil();
    }

    public static String getEnvPrefixByHost(HttpServletRequest request) {
        String prefix = "test";
        try {
            InetAddress[] addresses = InetAddress.getAllByName(request.getServerName());
            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress() || address.isLinkLocalAddress() || address.isLoopbackAddress()) {
                    return prefix;
                }
            }
            prefix = "live";
        } catch (UnknownHostException e) {
            Debug.logError(e.getMessage(), MODULE);
        }
        return prefix;
    }
}
