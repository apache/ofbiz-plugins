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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.io.IOException;
import java.io.Serializable;
import java.sql.Timestamp;

import javax.transaction.Transaction;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.ofbiz.passport.event.LinkedInEvents;
import org.apache.ofbiz.passport.util.PassportUtil;
import org.apache.ofbiz.common.authentication.api.Authenticator;
import org.apache.ofbiz.common.authentication.api.AuthenticatorException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.transaction.TransactionUtil;
import org.apache.ofbiz.entity.transaction.GenericTransactionException;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.UtilXml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * LinkedIn OFBiz Authenticator
 */
public class LinkedInAuthenticator implements Authenticator {

    private static final String MODULE = LinkedInAuthenticator.class.getName();

    public static String getPROPS() {
        return PROPS;
    }

    private static final String PROPS = "linkedInAuth.properties";
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
     * For LinkedIn users, we only check if the username(userLoginId) exists an
     * externalAuthId, and the externalAuthId has a valid accessToken in
     * LinkedInUser entity.
     * @param userLoginId   User's login id
     * @param password      User's password
     * @param isServiceAuth true if authentication is for a service call
     * @return true if the user is authenticated
     * @throws org.apache.ofbiz.common.authentication.api.AuthenticatorException
     *          when a fatal error occurs during authentication
     */
    @Override
    public boolean authenticate(String userLoginId, String password, boolean isServiceAuth) throws AuthenticatorException {
        Document user = null;
        HttpGet getMethod = null;
        try {
            GenericValue userLogin = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", userLoginId).queryOne();
            String externalAuthId = userLogin.getString("externalAuthId");
            GenericValue linkedInUser = EntityQuery.use(delegator).from("LinkedInUser").where("linkedInUserId", externalAuthId).queryOne();
            if (linkedInUser != null) {
                String accessToken = linkedInUser.getString("accessToken");
                if (UtilValidate.isNotEmpty(accessToken)) {
                    getMethod = new HttpGet(LinkedInEvents.TOKEN_END_POINT + LinkedInEvents.USER_API_URI + "?oauth2_access_token=" + accessToken);
                    user = LinkedInAuthenticator.getUserInfo(getMethod, Locale.getDefault());
                }
            }
        } catch (GenericEntityException | ParserConfigurationException | AuthenticatorException | IOException e) {
            throw new AuthenticatorException(e.getMessage(), e);
        } catch (SAXException e) {
            throw new AuthenticatorException(e.getMessage(), e);
        } finally {
            if (getMethod != null) {
                getMethod.releaseConnection();
            }
        }

        Debug.logInfo("LinkedIn auth called; returned user info: " + user, MODULE);
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
        Document user = getLinkedInUserinfo(userLoginId);

        GenericValue system;
        try {
            system = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", "system").cache().queryOne();
        } catch (GenericEntityException e) {
            throw new AuthenticatorException(e.getMessage(), e);
        }

        GenericValue userLogin;
        try {
            userLogin = EntityQuery.use(delegator).from("UserLogin").where("externalAuthId", getLinkedInUserId(user)).queryFirst();
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
                    createUser(user, system);
                } else {
                    // update the user information
                    updateUser(user, system, userLogin);
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

    private Document getLinkedInUserinfo(String userLoginId) throws AuthenticatorException {
        Document user = null;
        HttpGet getMethod = null;
        try {
            GenericValue userLogin = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", userLoginId).queryOne();
            String externalAuthId = userLogin.getString("externalAuthId");
            GenericValue linkedInUser = EntityQuery.use(delegator).from("LinkedInUser").where("linkedInUserId", externalAuthId).queryOne();
            if (linkedInUser != null) {
                String accessToken = linkedInUser.getString("accessToken");
                if (UtilValidate.isNotEmpty(accessToken)) {
                    getMethod = new HttpGet(LinkedInEvents.TOKEN_END_POINT + LinkedInEvents.USER_API_URI + "?oauth2_access_token=" + accessToken);
                    user = getUserInfo(getMethod, Locale.getDefault());
                }
            }
        } catch (GenericEntityException | ParserConfigurationException | AuthenticatorException | IOException e) {
            throw new AuthenticatorException(e.getMessage(), e);
        } catch (SAXException e) {
            throw new AuthenticatorException(e.getMessage(), e);
        } finally {
            if (getMethod != null) {
                getMethod.releaseConnection();
            }
        }
        return user;
    }

    /**
     * Create user string.
     * @param user the user
     * @return the string
     * @throws AuthenticatorException the authenticator exception
     */
    public String createUser(Document user) throws AuthenticatorException {
        GenericValue system;
        try {
            system = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", "system").cache().queryOne();
        } catch (GenericEntityException e) {
            throw new AuthenticatorException(e.getMessage(), e);
        }
        return createUser(user, system);
    }

    private String createUser(Document user, GenericValue system) throws AuthenticatorException {
        Map<String, String> userInfo = parseLinkedInUserInfo(user);

        // create person + userLogin
        Map<String, Serializable> createPersonUlMap = new HashMap<>();
        String userLoginId = delegator.getNextSeqId("UserLogin");
        if (userInfo.containsKey("firstName")) {
            createPersonUlMap.put("firstName", userInfo.get("firstName"));
        }
        if (userInfo.containsKey("lastName")) {
            createPersonUlMap.put("lastName", userInfo.get("lastName"));
        }
        if (userInfo.containsKey("userId")) {
            createPersonUlMap.put("externalAuthId", userInfo.get("userId"));
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
        if (userInfo.containsKey("emailAddress")) {
            Map<String, Serializable> createEmailMap = new HashMap<>();
            createEmailMap.put("emailAddress", userInfo.get("emailAddress"));
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
        for (String securityGroup : (new LinkedInUserGroupMapper(new String[] {"person"}).getSecurityGroups())) {
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

    private void updateUser(Document user, GenericValue system, GenericValue userLogin) throws AuthenticatorException {
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
        Debug.logInfo("Calling LinkedIn:updatePassword() - ignored!!!", MODULE);
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
        return "true".equalsIgnoreCase(UtilProperties.getPropertyValue(PROPS, "linked.authenticator.enabled", "true"));
    }

    public static Document getUserInfo(HttpGet httpGet, Locale locale)
            throws IOException, AuthenticatorException, SAXException, ParserConfigurationException {
        Document userInfo = null;
        httpGet.setConfig(PassportUtil.STANDARD_REQ_CONFIG);
        CloseableHttpClient jsonClient = HttpClients.custom().build();
        CloseableHttpResponse getResponse = jsonClient.execute(httpGet);
        String responseString = new BasicResponseHandler().handleResponse(getResponse);
        if (getResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
            // Debug.logInfo("Json Response from LinkedIn: " + responseString, MODULE);
            userInfo = UtilXml.readXmlDocument(responseString);
        } else {
            String errMsg = UtilProperties.getMessage(RESOURCE, "GetOAuth2AccessTokenError", UtilMisc.toMap("error", responseString), locale);
            throw new AuthenticatorException(errMsg);
        }
        return userInfo;
    }

    public static String getLinkedInUserId(Document userInfo) {
        NodeList persons = userInfo.getElementsByTagName("person");
        if (UtilValidate.isEmpty(persons) || persons.getLength() <= 0) {
            return null;
        }
        Element standardProfileRequest = UtilXml.firstChildElement((Element) persons.item(0), "site-standard-profile-request");
        Element url = UtilXml.firstChildElement(standardProfileRequest, "url");
        if (UtilValidate.isNotEmpty(url)) {
            String urlContent = url.getTextContent();
            if (UtilValidate.isNotEmpty(urlContent)) {
                String id = urlContent.substring(urlContent.indexOf("?id="));
                id = id.substring(0, id.indexOf("&"));
                Debug.logInfo("LinkedIn user id: " + id, MODULE);
                return id;
            }
        }
        return null;
    }

    public static Map<String, String> parseLinkedInUserInfo(Document userInfo) {
        Map<String, String> results = new HashMap<>();
        NodeList persons = userInfo.getElementsByTagName("person");
        if (UtilValidate.isEmpty(persons) || persons.getLength() <= 0) {
            return results;
        }
        Element person = (Element) persons.item(0);
        Element standardProfileRequest = UtilXml.firstChildElement(person, "site-standard-profile-request");
        Element url = UtilXml.firstChildElement(standardProfileRequest, "url");
        if (UtilValidate.isNotEmpty(url)) {
            String urlContent = url.getTextContent();
            if (UtilValidate.isNotEmpty(urlContent)) {
                String id = urlContent.substring(urlContent.indexOf("?id="));
                id = id.substring(0, id.indexOf("&"));
                Debug.logInfo("LinkedIn user id: " + id, MODULE);
                results.put("userId", id);
            }
        }
        Element firstNameElement = UtilXml.firstChildElement(person, "first-name");
        if (UtilValidate.isNotEmpty(firstNameElement) && UtilValidate.isNotEmpty(firstNameElement.getTextContent())) {
            results.put("firstName", firstNameElement.getTextContent());
        }
        Element lastNameElement = UtilXml.firstChildElement(person, "last-name");
        if (UtilValidate.isNotEmpty(lastNameElement) && UtilValidate.isNotEmpty(lastNameElement.getTextContent())) {
            results.put("lastName", lastNameElement.getTextContent());
        }
        Element emailElement = UtilXml.firstChildElement(person, "email-address");
        if (UtilValidate.isNotEmpty(emailElement) && UtilValidate.isNotEmpty(emailElement.getTextContent())) {
            results.put("emailAddress", emailElement.getTextContent());
        }
        return results;
    }
}
