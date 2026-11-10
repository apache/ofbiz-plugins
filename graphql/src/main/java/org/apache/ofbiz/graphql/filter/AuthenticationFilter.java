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
package org.apache.ofbiz.graphql.filter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.graphql.GraphQLErrorType;
import org.apache.ofbiz.graphql.config.OFBizGraphQLObjectMapperConfigurer;
import org.apache.ofbiz.service.ModelService;
import org.apache.ofbiz.webapp.control.JWTManager;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.kickstart.execution.GraphQLObjectMapper;


public class AuthenticationFilter implements Filter {

    private static final String MODULE = AuthenticationFilter.class.getName();
    private GraphQLObjectMapper mapper;
    private static final String AUTHENTICATION_SCHEME = "Bearer";
    private static final String REALM = "OFBiz-GraphQl";
    private static final String INTROSPECTION_QUERY_PATH = "/schema.json";

    {
        mapper = GraphQLObjectMapper.newBuilder().withObjectMapperConfigurer(new OFBizGraphQLObjectMapperConfigurer()).build();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String authorizationHeader = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (!isTokenBasedAuthentication(authorizationHeader)) {
            abortWithUnauthorized(httpResponse, false, "Authentication Required");
            return;
        }
        ServletContext servletContext = request.getServletContext();
        Delegator delegator = (Delegator) servletContext.getAttribute("delegator");
        String jwtToken = JWTManager.getHeaderAuthBearerToken(httpRequest);
        Map<String, Object> claims = JWTManager.validateToken(jwtToken, JWTManager.getJWTKey(delegator));
        if (claims.containsKey(ModelService.ERROR_MESSAGE)) {
            abortWithUnauthorized(httpResponse, true, (String) claims.get(ModelService.ERROR_MESSAGE));
            return;
        } else {
            GenericValue userLogin = extractUserLoginFromJwtClaim(delegator, claims);
            if (UtilValidate.isEmpty(userLogin)) {
                abortWithUnauthorized(httpResponse, true, "There was a problem with the JWT token. Could not find provided userLogin");
                return;
            }
            httpRequest.setAttribute("userLogin", userLogin);
            httpRequest.setAttribute("delegator", delegator);
        }
        chain.doFilter(request, response);
    }

    /**
     * @param request
     * @return
     */
    private boolean isIntrospectionQuery(HttpServletRequest request) {
        String path = Optional.ofNullable(request.getPathInfo()).orElseGet(request::getServletPath).toLowerCase();
        return path.contentEquals(INTROSPECTION_QUERY_PATH);
    }

    /**
     * @param requestContext
     * @throws IOException
     */
    private void abortWithUnauthorized(HttpServletResponse httpResponse, boolean isAuthHeaderPresent, String message) throws IOException {
        httpResponse.reset();
        httpResponse.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        if (!isAuthHeaderPresent) {
            httpResponse.addHeader(HttpHeaders.WWW_AUTHENTICATE, AUTHENTICATION_SCHEME + " realm=\"" + REALM + "\"");
        }
        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        GraphQLError error = GraphqlErrorBuilder.newError().message(message, (Object[]) null).errorType(GraphQLErrorType.AuthenticationError).build();
        ExecutionResultImpl result = new ExecutionResultImpl(error);
        mapper.serializeResultAsJson(httpResponse.getWriter(), result);

    }

    /**
     * /**
     *
     * @param authorizationHeader
     * @return
     */
    private boolean isTokenBasedAuthentication(String authorizationHeader) {
        return authorizationHeader != null && authorizationHeader.toLowerCase().startsWith(AUTHENTICATION_SCHEME.toLowerCase() + " ");
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
