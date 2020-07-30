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
package org.apache.ofbiz.ws.rs.security.auth;

import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.ModelService;
import org.apache.ofbiz.webapp.control.JWTManager;
import org.apache.ofbiz.ws.rs.security.Secured;
import org.apache.ofbiz.ws.rs.util.RestApiUtil;

/**
 * Api Security
 */
@Secured
@Provider
public class APIAuthFilter implements ContainerRequestFilter {

    private static final String MODULE = APIAuthFilter.class.getName();

    @Context
    private ResourceInfo resourceInfo;

    @Context
    private HttpServletRequest httpRequest;

    @Context
    private ServletContext servletContext;

    private static final String AUTHENTICATION_SCHEME = "Bearer";
    private static final String REALM = "OFBiz";

    /**
     *
     */
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String authorizationHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        Delegator delegator = (Delegator) servletContext.getAttribute("delegator");
        if (!isTokenBasedAuthentication(authorizationHeader)) {
            abortWithUnauthorized(requestContext, false, "Unauthorized: Access is denied due to invalid or absent Authorization header");
            return;
        }
        String jwtToken = JWTManager.getHeaderAuthBearerToken(httpRequest);
        Map<String, Object> claims = JWTManager.validateToken(jwtToken, JWTManager.getJWTKey(delegator));
        if (claims.containsKey(ModelService.ERROR_MESSAGE)) {
            abortWithUnauthorized(requestContext, true, (String) claims.get(ModelService.ERROR_MESSAGE));
        } else {
            GenericValue userLogin = extractUserLoginFromJwtClaim(delegator, claims);
            if (UtilValidate.isEmpty(userLogin)) {
                abortWithUnauthorized(requestContext, true, "Access Denied: User does not exist in the system");
                return;
            }
            httpRequest.setAttribute("userLogin", userLogin);
        }

    }

    /**
     * @param authorizationHeader
     * @return
     */
    private boolean isTokenBasedAuthentication(String authorizationHeader) {
        return authorizationHeader != null && authorizationHeader.toLowerCase().startsWith(AUTHENTICATION_SCHEME.toLowerCase() + " ");
    }

    /**
     * @param requestContext
     */
    private void abortWithUnauthorized(ContainerRequestContext requestContext, boolean isAuthHeaderPresent, String message) {
        if (!isAuthHeaderPresent) {
            requestContext.abortWith(
                    RestApiUtil.errorBuilder(Response.Status.UNAUTHORIZED.getStatusCode(), Response.Status.UNAUTHORIZED.getReasonPhrase(), message)
                            .header(HttpHeaders.WWW_AUTHENTICATE, AUTHENTICATION_SCHEME + " realm=\"" + REALM + "\"").build());
        } else {
            requestContext
                    .abortWith(RestApiUtil.error(Response.Status.FORBIDDEN.getStatusCode(), Response.Status.FORBIDDEN.getReasonPhrase(), message));
        }

    }

    private GenericValue extractUserLoginFromJwtClaim(Delegator delegator, Map<String, Object> claims) {
        String userLoginId = (String) claims.get("userLoginId");
        if (UtilValidate.isEmpty(userLoginId)) {
            Debug.logWarning("No userLoginId found in the JWT token.", MODULE);
            return null;
        }
        GenericValue userLogin = null;
        try {
            userLogin = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", userLoginId).queryOne();
            if (UtilValidate.isEmpty(userLogin)) {
                Debug.logWarning("There was a problem with the JWT token. Could not find provided userLogin " + userLoginId, MODULE);
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, "Unable to get UserLogin information from JWT Token: " + e.getMessage(), MODULE);
        }
        return userLogin;
    }

}
