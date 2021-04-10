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
package org.apache.ofbiz.passport.user;

import java.io.IOException;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.transaction.Transaction;

import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.ofbiz.base.conversion.ConversionException;
import org.apache.ofbiz.base.conversion.JSONConverters.JSONToMap;
import org.apache.ofbiz.base.lang.JSON;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.common.authentication.api.Authenticator;
import org.apache.ofbiz.common.authentication.api.AuthenticatorException;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.transaction.GenericTransactionException;
import org.apache.ofbiz.entity.transaction.TransactionUtil;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.passport.event.GitHubEvents;
import org.apache.ofbiz.passport.util.PassportUtil;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

/**
 * GitHub OFBiz Authenticator
 */
public class GitHubAuthenticator implements Authenticator {

    private static final String MODULE = GitHubAuthenticator.class.getName();

    public static final String PROPS = "gitHubAuth.properties";

    private static final String RESOURCE = "PassportUiLabels";

    private LocalDispatcher dispatcher;

    private Delegator delegator;

    /**
     * Method called when authenticator is first initialized (the delegator
     * object can be obtained from the LocalDispatcher)
     * @param dispatcher The ServiceDispatcher to use for this Authenticator
     */
    @Override
    public void initialize(LocalDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        this.delegator = dispatcher.getDelegator();
    }

    /**
     * Method to authenticate a user.
     * For GitHub users, we only check if the username(userLoginId) exists an
     * externalAuthId, and the externalAuthId has a valid accessToken in
     * GitHubUser entity.
     * @param userLoginId   User's login id
     * @param password      User's password
     * @param isServiceAuth true if authentication is for a service call
     * @return true if the user is authenticated
     * @throws org.apache.ofbiz.common.authentication.api.AuthenticatorException
     *          when a fatal error occurs during authentication
     */
    @Override
    public boolean authenticate(String userLoginId, String password, boolean isServiceAuth) throws AuthenticatorException {
        Map<String, Object> user = null;
        HttpGet getMethod = null;
        try {
            GenericValue userLogin = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", userLoginId).queryOne();
            String externalAuthId = userLogin.getString("externalAuthId");
            GenericValue gitHubUser = EntityQuery.use(delegator).from("GitHubUser").where("gitHubUserId", externalAuthId).queryOne();
            if (UtilValidate.isNotEmpty(gitHubUser)) {
                String accessToken = gitHubUser.getString("accessToken");
                String tokenType = gitHubUser.getString("tokenType");
                if (UtilValidate.isNotEmpty(accessToken)) {
                    getMethod = new HttpGet(GitHubEvents.getApiEndPoint() + GitHubEvents.getUserApiUri());
                    user = GitHubAuthenticator.getUserInfo(getMethod, accessToken, tokenType, Locale.getDefault());
                }
            }
        } catch (GenericEntityException | AuthenticatorException e) {
            throw new AuthenticatorException(e.getMessage(), e);
        } finally {
            if (getMethod != null) {
                getMethod.releaseConnection();
            }
        }

        Debug.logInfo("GitHub auth called; returned user info: " + user, MODULE);
        return user != null;
    }

    /**
     * Logs a user out
     * @param username User's username
     * @throws org.apache.ofbiz.common.authentication.api.AuthenticatorException
     *          when logout fails
     */
    @Override
    public void logout(String username) throws AuthenticatorException {
    }

    /**
     * Reads user information and syncs it to OFBiz (i.e. UserLogin, Person, etc)
     * @param userLoginId
     * @throws org.apache.ofbiz.common.authentication.api.AuthenticatorException
     *          user synchronization fails
     */
    @Override
    public void syncUser(String userLoginId) throws AuthenticatorException {
        Map<String, Object> userMap = getGitHubUserinfo(userLoginId);
        GenericValue system;
        try {
            system = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", "system").cache().queryOne();
        } catch (GenericEntityException e) {
            throw new AuthenticatorException(e.getMessage(), e);
        }

        GenericValue userLogin;
        try {
            userLogin = EntityQuery.use(delegator).from("UserLogin").where("externalAuthId", userMap.get("id")).queryFirst();
        } catch (GenericEntityException e) {
            throw new AuthenticatorException(e.getMessage(), e);
        }

        // suspend the current transaction and load the user
        Transaction parentTx = null;
        boolean beganTransaction = false;

        try {
            try {
                parentTx = TransactionUtil.suspend();
            } catch (GenericTransactionException e) {
                Debug.logError(e, "Could not suspend transaction: " + e.getMessage(), MODULE);
            }

            try {
                beganTransaction = TransactionUtil.begin();

                if (userLogin == null) {
                    // create the user
                    createUser(userMap, system);
                } else {
                    // update the user information
                    updateUser(userMap, system, userLogin);
                }

            } catch (GenericTransactionException e) {
                Debug.logError(e, "Could not suspend transaction: " + e.getMessage(), MODULE);
            } finally {
                try {
                    TransactionUtil.commit(beganTransaction);
                } catch (GenericTransactionException e) {
                    Debug.logError(e, "Could not commit nested transaction: " + e.getMessage(), MODULE);
                }
            }
        } finally {
            // resume/restore parent transaction
            if (parentTx != null) {
                try {
                    TransactionUtil.resume(parentTx);
                    if (Debug.verboseOn()) {
                        Debug.logVerbose("Resumed the parent transaction.", MODULE);
                    }
                } catch (GenericTransactionException e) {
                    Debug.logError(e, "Could not resume parent nested transaction: " + e.getMessage(), MODULE);
                }
            }
        }
    }

    private Map<String, Object> getGitHubUserinfo(String userLoginId) throws AuthenticatorException {
        Map<String, Object> user = null;
        HttpGet getMethod = null;
        try {
            GenericValue userLogin = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", userLoginId).queryOne();
            String externalAuthId = userLogin.getString("externalAuthId");
            GenericValue gitHubUser = EntityQuery.use(delegator).from("GitHubUser").where("gitHubUserId", externalAuthId).queryOne();
            if (UtilValidate.isNotEmpty(gitHubUser)) {
                String accessToken = gitHubUser.getString("accessToken");
                String tokenType = gitHubUser.getString("tokenType");
                if (UtilValidate.isNotEmpty(accessToken)) {
                    getMethod = new HttpGet(GitHubEvents.getApiEndPoint() + GitHubEvents.getUserApiUri());
                    user = getUserInfo(getMethod, accessToken, tokenType, Locale.getDefault());
                }
            }
        } catch (GenericEntityException | AuthenticatorException e) {
            throw new AuthenticatorException(e.getMessage(), e);
        }
        return user;
    }

    /**
     * Create user string.
     * @param userMap the user map
     * @return the string
     * @throws AuthenticatorException the authenticator exception
     */
    public String createUser(Map<String, Object> userMap) throws AuthenticatorException {
        GenericValue system;
        try {
            system = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", "system").cache().queryOne();
        } catch (GenericEntityException e) {
            throw new AuthenticatorException(e.getMessage(), e);
        }
        return createUser(userMap, system);
    }

    private String createUser(Map<String, Object> userMap, GenericValue system) throws AuthenticatorException {
        // create person + userLogin
        Map<String, Serializable> createPersonUlMap = new HashMap<>();
        String userLoginId = delegator.getNextSeqId("UserLogin");
        if (userMap.containsKey("name")) {
            // use github's name as OFBiz's lastName
            createPersonUlMap.put("lastName", (String) userMap.get("name"));
        }
        if (userMap.containsKey("login")) {
            createPersonUlMap.put("externalAuthId", (String) userMap.get("login"));
        }
        // createPersonUlMap.put("externalId", user.getUserId());
        createPersonUlMap.put("userLoginId", userLoginId);
        createPersonUlMap.put("currentPassword", "[EXTERNAL]");
        createPersonUlMap.put("currentPasswordVerify", "[EXTERNAL]");
        createPersonUlMap.put("userLogin", system);
        Map<String, Object> createPersonResult;
        try {
            createPersonResult = dispatcher.runSync("createPersonAndUserLogin", createPersonUlMap);
        } catch (GenericServiceException e) {
            throw new AuthenticatorException(e.getMessage(), e);
        }
        if (ServiceUtil.isError(createPersonResult)) {
            throw new AuthenticatorException(ServiceUtil.getErrorMessage(createPersonResult));
        }
        String partyId = (String) createPersonResult.get("partyId");

        // give this person a role of CUSTOMER
        GenericValue partyRole = delegator.makeValue("PartyRole", UtilMisc.toMap("partyId", partyId, "roleTypeId", "CUSTOMER"));
        try {
            delegator.create(partyRole);
        } catch (GenericEntityException e) {
            Debug.logError(e, MODULE);
            throw new AuthenticatorException(e.getMessage(), e);
        }

        // create email
        if (userMap.containsKey("email")) {
            Map<String, Serializable> createEmailMap = new HashMap<>();
            createEmailMap.put("emailAddress", (String) userMap.get("email"));
            createEmailMap.put("contactMechPurposeTypeId", "PRIMARY_EMAIL");
            createEmailMap.put("partyId", partyId);
            createEmailMap.put("userLogin", system);
            Map<String, Object> createEmailResult;
            try {
                createEmailResult = dispatcher.runSync("createPartyEmailAddress", createEmailMap);
            } catch (GenericServiceException e) {
                throw new AuthenticatorException(e.getMessage(), e);
            }
            if (ServiceUtil.isError(createEmailResult)) {
                throw new AuthenticatorException(ServiceUtil.getErrorMessage(createEmailResult));
            }
        }

        // create security group(s)
        Timestamp now = UtilDateTime.nowTimestamp();
        for (String securityGroup : (new GitHubUserGroupMapper(new String[] {(String) userMap.get("type")}).getSecurityGroups())) {
            // check and make sure the security group exists
            GenericValue secGroup = null;
            try {
                secGroup = EntityQuery.use(delegator).from("SecurityGroup").where("groupId", securityGroup).cache().queryOne();
            } catch (GenericEntityException e) {
                Debug.logError(e, e.getMessage(), MODULE);
            }

            // add it to the user if it exists
            if (secGroup != null) {
                Map<String, Serializable> createSecGrpMap = new HashMap<>();
                createSecGrpMap.put("userLoginId", userLoginId);
                createSecGrpMap.put("groupId", securityGroup);
                createSecGrpMap.put("fromDate", now);
                createSecGrpMap.put("userLogin", system);

                Map<String, Object> createSecGrpResult;
                try {
                    createSecGrpResult = dispatcher.runSync("addUserLoginToSecurityGroup", createSecGrpMap);
                } catch (GenericServiceException e) {
                    throw new AuthenticatorException(e.getMessage(), e);
                }
                if (ServiceUtil.isError(createSecGrpResult)) {
                    throw new AuthenticatorException(ServiceUtil.getErrorMessage(createSecGrpResult));
                }
            }
        }
        return userLoginId;
    }

    private void updateUser(Map<String, Object> userMap, GenericValue system, GenericValue userLogin) throws AuthenticatorException {
        // TODO implement me
    }

    /**
     * Updates a user's password.
     * @param username    User's username
     * @param password    User's current password
     * @param newPassword User's new password
     * @throws org.apache.ofbiz.common.authentication.api.AuthenticatorException
     *          when update password fails
     */
    @Override
    public void updatePassword(String username, String password, String newPassword) throws AuthenticatorException {
        Debug.logInfo("Calling GitHub:updatePassword() - ignored!!!", MODULE);
    }

    /**
     * Weight of this authenticator (lower weights are run first)
     * @return the weight of this Authenicator
     */
    @Override
    public float getWeight() {
        return 1;
    }

    /**
     * Is the user synchronzied back to OFBiz
     * @return true if the user record is copied to the OFB database
     */
    @Override
    public boolean isUserSynchronized() {
        return true;
    }

    /**
     * Is this expected to be the only authenticator, if so errors will be thrown when users cannot be found
     * @return true if this is expected to be the only Authenticator
     */
    @Override
    public boolean isSingleAuthenticator() {
        return false;
    }

    /**
     * Flag to test if this Authenticator is enabled
     * @return true if the Authenticator is enabled
     */
    @Override
    public boolean isEnabled() {
        return "true".equalsIgnoreCase(UtilProperties.getPropertyValue(PROPS, "github.authenticator.enabled", "true"));
    }

    public static Map<String, Object> getUserInfo(HttpGet httpGet, String accessToken, String tokenType, Locale locale)
            throws AuthenticatorException {
        JSON userInfo = null;
        httpGet.setConfig(PassportUtil.STANDARD_REQ_CONFIG);
        CloseableHttpClient jsonClient = HttpClients.custom().build();
        httpGet.setHeader(PassportUtil.AUTHORIZATION_HEADER, tokenType + " " + accessToken);
        httpGet.setHeader(PassportUtil.ACCEPT_HEADER, "application/json");
        CloseableHttpResponse getResponse = null;
        try {
            getResponse = jsonClient.execute(httpGet);
            String responseString = new BasicResponseHandler().handleResponse(getResponse);
            if (getResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                // Debug.logInfo("Json Response from GitHub: " + responseString, MODULE);
                userInfo = JSON.from(responseString);
            } else {
                String errMsg = UtilProperties.getMessage(RESOURCE, "GetOAuth2AccessTokenError", UtilMisc.toMap("error", responseString), locale);
                throw new AuthenticatorException(errMsg);
            }
        } catch (ClientProtocolException e) {
            throw new AuthenticatorException(e.getMessage());
        } catch (IOException e) {
            throw new AuthenticatorException(e.getMessage());
        } finally {
            if (getResponse != null) {
                try {
                    getResponse.close();
                } catch (IOException e) {
                    Debug.logError(e, MODULE);
                }
            }
        }
        JSONToMap jsonMap = new JSONToMap();
        Map<String, Object> userMap;
        try {
            userMap = jsonMap.convert(userInfo);
        } catch (ConversionException e) {
            throw new AuthenticatorException(e.getMessage());
        }
        return userMap;
    }
}
