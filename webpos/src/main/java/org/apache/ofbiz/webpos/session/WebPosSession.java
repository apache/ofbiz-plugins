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
package org.apache.ofbiz.webpos.session;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.DelegatorFactory;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.order.shoppingcart.ShoppingCart;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.ofbiz.webapp.control.LoginWorker;
import org.apache.ofbiz.webpos.transaction.WebPosTransaction;

public class WebPosSession {

    private static final String MODULE = WebPosSession.class.getName();

    private String id = null;
    private Map<String, Object> attributes = new HashMap<>();
    private GenericValue userLogin = null;
    private Locale locale = null;
    private String productStoreId = null;
    private String facilityId = null;
    private String currencyUomId = null;
    private transient Delegator delegator = null;
    private String delegatorName = null;
    private LocalDispatcher dispatcher = null;
    private Boolean mgrLoggedIn = null;
    private WebPosTransaction webPosTransaction = null;
    private ShoppingCart cart = null;

    public WebPosSession(String id, Map<String, Object> attributes, GenericValue userLogin, Locale locale, String productStoreId, String facilityId, String currencyUomId, Delegator delegator, LocalDispatcher dispatcher, ShoppingCart cart) {
        this.id = id;
        this.attributes = attributes;
        this.userLogin = userLogin;
        this.locale = locale;
        this.productStoreId = productStoreId;
        this.facilityId = facilityId;
        this.currencyUomId = currencyUomId;

        if (UtilValidate.isNotEmpty(delegator)) {
            this.delegator = delegator;
            this.delegatorName = delegator.getDelegatorName();
        } else {
            this.delegator = this.getDelegator();
            this.delegatorName = delegator.getDelegatorName();
        }

        this.dispatcher = dispatcher;
        this.cart = cart;
        Debug.logInfo("Created WebPosSession [" + id + "]", MODULE);
    }

    /**
     * Gets user login.
     * @return the user login
     */
    public GenericValue getUserLogin() {
        return this.userLogin;
    }

    /**
     * Sets user login.
     * @param userLogin the user login
     */
    public void setUserLogin(GenericValue userLogin) {
        this.userLogin = userLogin;
    }

    /**
     * Sets attribute.
     * @param name  the name
     * @param value the value
     */
    public void setAttribute(String name, Object value) {
        this.attributes.put(name, value);
    }

    /**
     * Gets attribute.
     * @param name the name
     * @return the attribute
     */
    public Object getAttribute(String name) {
        return this.attributes.get(name);
    }

    /**
     * Gets id.
     * @return the id
     */
    public String getId() {
        return this.id;
    }

    /**
     * Gets user login id.
     * @return the user login id
     */
    public String getUserLoginId() {
        if (UtilValidate.isEmpty(getUserLogin())) {
            return null;
        } else {
            return this.getUserLogin().getString("userLoginId");
        }
    }

    /**
     * Gets user party id.
     * @return the user party id
     */
    public String getUserPartyId() {
        if (UtilValidate.isEmpty(getUserLogin())) {
            return null;
        } else {
            return this.getUserLogin().getString("partyId");
        }
    }

    /**
     * Gets locale.
     * @return the locale
     */
    public Locale getLocale() {
        return this.locale;
    }

    /**
     * Sets locale.
     * @param locale the locale
     */
    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    /**
     * Gets product store id.
     * @return the product store id
     */
    public String getProductStoreId() {
        return this.productStoreId;
    }

    /**
     * Sets product store id.
     * @param productStoreId the product store id
     */
    public void setProductStoreId(String productStoreId) {
        this.productStoreId = productStoreId;
    }

    /**
     * Gets facility id.
     * @return the facility id
     */
    public String getFacilityId() {
        return this.facilityId;
    }

    /**
     * Sets facility id.
     * @param facilityId the facility id
     */
    public void setFacilityId(String facilityId) {
        this.facilityId = facilityId;
    }

    /**
     * Gets currency uom id.
     * @return the currency uom id
     */
    public String getCurrencyUomId() {
        return this.currencyUomId;
    }

    /**
     * Sets currency uom id.
     * @param currencyUomId the currency uom id
     */
    public void setCurrencyUomId(String currencyUomId) {
        this.currencyUomId = currencyUomId;
    }

    /**
     * Gets delegator.
     * @return the delegator
     */
    public Delegator getDelegator() {
        if (UtilValidate.isEmpty(delegator)) {
            delegator = DelegatorFactory.getDelegator(delegatorName);
        }
        return delegator;
    }

    /**
     * Gets dispatcher.
     * @return the dispatcher
     */
    public LocalDispatcher getDispatcher() {
        return dispatcher;
    }

    /**
     * Gets cart.
     * @return the cart
     */
    public ShoppingCart getCart() {
        return this.cart;
    }

    /**
     * Logout.
     */
    public void logout() {
        if (UtilValidate.isNotEmpty(webPosTransaction)) {
            webPosTransaction.closeTx();
            webPosTransaction = null;
        }

        if (UtilValidate.isNotEmpty(getUserLogin())) {
            LoginWorker.setLoggedOut(this.getUserLogin().getString("userLoginId"), this.getDelegator());
        }
    }

    /**
     * Login.
     * @param username   the username
     * @param password   the password
     * @param dispatcher the dispatcher
     * @throws UserLoginFailure the user login failure
     */
    public void login(String username, String password, LocalDispatcher dispatcher) throws UserLoginFailure {
        this.checkLogin(username, password, dispatcher);
    }

    /**
     * Check login generic value.
     * @param username   the username
     * @param password   the password
     * @param dispatcher the dispatcher
     * @return the generic value
     * @throws UserLoginFailure the user login failure
     */
    public GenericValue checkLogin(String username, String password, LocalDispatcher dispatcher) throws UserLoginFailure {
        // check the required parameters and objects
        if (UtilValidate.isEmpty(dispatcher)) {
            throw new UserLoginFailure(UtilProperties.getMessage("WebPosUiLabels", "WebPosUnableToLogIn", getLocale()));
        }
        if (UtilValidate.isEmpty(username)) {
            throw new UserLoginFailure(UtilProperties.getMessage("PartyUiLabels", "PartyUserNameMissing", getLocale()));
        }
        if (UtilValidate.isEmpty(password)) {
            throw new UserLoginFailure(UtilProperties.getMessage("PartyUiLabels", "PartyPasswordMissing", getLocale()));
        }

        // call the login service
        Map<String, Object> result = null;
        try {
            result = dispatcher.runSync("userLogin", UtilMisc.toMap("login.username", username, "login.password", password));
        } catch (GenericServiceException e) {
            Debug.logError(e, MODULE);
            throw new UserLoginFailure(e);
        } catch (Throwable t) {
            Debug.logError(t, "Throwable caught!", MODULE);
        }

        // check for errors
        if (ServiceUtil.isError(result)) {
            throw new UserLoginFailure(ServiceUtil.getErrorMessage(result));
        } else {
            GenericValue ul = (GenericValue) result.get("userLogin");
            if (ul == null) {
                throw new UserLoginFailure(UtilProperties.getMessage("WebPosUiLabels", "WebPosUserLoginNotValid", getLocale()));
            }
            return ul;
        }
    }

    /**
     * Has role boolean.
     * @param userLogin  the user login
     * @param roleTypeId the role type id
     * @return the boolean
     */
    public boolean hasRole(GenericValue userLogin, String roleTypeId) {
        if (UtilValidate.isEmpty(userLogin) || UtilValidate.isEmpty(roleTypeId)) {
            return false;
        }
        String partyId = userLogin.getString("partyId");
        GenericValue partyRole = null;
        try {
            partyRole = getDelegator().findOne("PartyRole", false, "partyId", partyId, "roleTypeId", roleTypeId);
        } catch (GenericEntityException e) {
            Debug.logError(e, MODULE);
            return false;
        }

        if (UtilValidate.isEmpty(partyRole)) {
            return false;
        }

        return true;
    }

    /**
     * Is manager logged in boolean.
     * @return the boolean
     */
    public boolean isManagerLoggedIn() {
        if (UtilValidate.isEmpty(mgrLoggedIn)) {
            mgrLoggedIn = hasRole(getUserLogin(), "MANAGER");
        }
        return mgrLoggedIn;
    }

    /**
     * Gets current transaction.
     * @return the current transaction
     */
    public WebPosTransaction getCurrentTransaction() {
        if (UtilValidate.isEmpty(webPosTransaction)) {
            webPosTransaction = new WebPosTransaction(this);
        }
        return webPosTransaction;
    }

    /**
     * Sets current transaction.
     * @param webPosTransaction the web pos transaction
     */
    public void setCurrentTransaction(WebPosTransaction webPosTransaction) {
        this.webPosTransaction = webPosTransaction;
    }

    @SuppressWarnings("serial")
    public class UserLoginFailure extends GeneralException {
        public UserLoginFailure() {
            super();
        }

        public UserLoginFailure(String str) {
            super(str);
        }

        public UserLoginFailure(String str, Throwable nested) {
            super(str, nested);
        }

        public UserLoginFailure(Throwable nested) {
            super(nested);
        }
    }
}
