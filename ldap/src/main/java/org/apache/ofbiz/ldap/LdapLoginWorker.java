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
package org.apache.ofbiz.ldap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilXml;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.ldap.commons.InterfaceOFBizAuthenticationHandler;
import org.apache.ofbiz.webapp.control.LoginWorker;
import org.apache.ofbiz.webapp.control.RequestHandler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Common LDAP Login Workers
 */
public final class LdapLoginWorker {

    private static final String MODULE = LdapLoginWorker.class.getName();
    private static final String RESOURCE = "SecurityextUiLabels";
    private static final String LDAP_CONFIG = "plugins/ldap/config/ldap.xml";

    protected LdapLoginWorker() { }

    /**
     * An HTTP WebEvent handler that checks to see is a userLogin is logged in.
     * If not, the user is forwarded to the login page.
     *
     * @param request  The HTTP request object for the current JSP or Servlet request.
     * @param response The HTTP response object for the current JSP or Servlet request.
     * @return String
     */
    public static String checkLogin(HttpServletRequest request, HttpServletResponse response) {
        GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
        // anonymous shoppers are not logged in
        if (userLogin != null && "anonymous".equals(userLogin.getString("userLoginId"))) {
            userLogin = null;
        }

        // user is logged in; check to see if they have globally logged out if not
        // check if they have permission for this login attempt; if not log them out
        if (userLogin != null) {
            Element rootElement = getRootElement(request);
            boolean hasLdapLoggedOut = false;
            if (rootElement != null) {
                String className = UtilXml.childElementValue(rootElement, "AuthenticationHandler", "org.apache.ofbiz.ldap.openldap"
                        + ".OFBizLdapAuthenticationHandler");
                try {
                    Class<?> handlerClass = Class.forName(className);
                    InterfaceOFBizAuthenticationHandler authenticationHandler =
                            (InterfaceOFBizAuthenticationHandler) handlerClass.getDeclaredConstructor().newInstance();
                    hasLdapLoggedOut = authenticationHandler.hasLdapLoggedOut(request, response, rootElement);
                } catch (Exception e) {
                    Debug.logError(e, "Error calling checkLogin service", MODULE);
                    Map<String, String> messageMap = UtilMisc.toMap("errorMessage", e.getMessage());
                    String errMsg = UtilProperties.getMessage(RESOURCE, "loginevents.following_error_occurred_during_login", messageMap,
                            UtilHttp.getLocale(request));
                    request.setAttribute("_ERROR_MESSAGE_", errMsg);
                }
            }

            if (!LoginWorker.hasBasePermission(userLogin, request) || LoginWorker.isFlaggedLoggedOut(userLogin, userLogin.getDelegator()) || hasLdapLoggedOut) {
                Debug.logInfo("User does not have permission or is flagged as logged out", MODULE);
                LoginWorker.doBasicLogout(userLogin, request, response);
                userLogin = null;
            }
        }

        if (userLogin == null) {
            return login(request, response);
        }

        return "success";
    }

    /**
     * An HTTP WebEvent handler that logs in a userLogin. This should run before the security check.
     *
     * @param request  The HTTP request object for the current JSP or Servlet request.
     * @param response The HTTP response object for the current JSP or Servlet request.
     * @return Return a boolean which specifies whether or not the calling Servlet or
     * JSP should generate its own content. This allows an event to override the default content.
     */
    public static String login(HttpServletRequest request, HttpServletResponse response) {

        Element rootElement = getRootElement(request);
        String result = "error";
        if (rootElement != null) {
            String className = UtilXml.childElementValue(rootElement, "AuthenticationHandler", "org.apache.ofbiz.ldap.openldap"
                    + ".OFBizLdapAuthenticationHandler");
            try {
                Class<?> handlerClass = Class.forName(className);
                InterfaceOFBizAuthenticationHandler authenticationHandler =
                        (InterfaceOFBizAuthenticationHandler) handlerClass.getDeclaredConstructor().newInstance();
                result = authenticationHandler.login(request, response, rootElement);
            } catch (Exception e) {
                Debug.logError(e, "Error calling userLogin service", MODULE);
                Map<String, String> messageMap = UtilMisc.toMap("errorMessage", e.getMessage());
                String errMsg = UtilProperties.getMessage(RESOURCE, "loginevents.following_error_occurred_during_login", messageMap,
                        UtilHttp.getLocale(request));
                request.setAttribute("_ERROR_MESSAGE_", errMsg);
            }
        }

        if ("error".equals(result)) {
            boolean useOFBizLoginWhenFail = Boolean.getBoolean(UtilXml.childElementValue(rootElement, "UseOFBizLoginWhenLDAPFail", "false"));
            if (useOFBizLoginWhenFail) {
                return LoginWorker.login(request, response);
            }
        }
        return result;
    }

    /**
     * An HTTP WebEvent handler that logs out a userLogin by clearing the session.
     *
     * @param request  The HTTP request object for the current request.
     * @param response The HTTP response object for the current request.
     * @return Return a boolean which specifies whether or not the calling request
     * should generate its own content. This allows an event to override the default content.
     */
    public static String logout(HttpServletRequest request, HttpServletResponse response) {
        // run the before-logout events
        RequestHandler rh = RequestHandler.getRequestHandler(request.getSession().getServletContext());
        rh.runBeforeLogoutEvents(request, response);

        // invalidate the security group list cache
        GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");

        LoginWorker.doBasicLogout(userLogin, request, response);

        Element rootElement = getRootElement(request);

        String result = "error";
        if (rootElement != null) {
            String className = UtilXml.childElementValue(rootElement, "AuthenticationHandler", "org.apache.ofbiz.ldap.openldap"
                    + ".OFBizLdapAuthenticationHandler");
            try {
                Class<?> handlerClass = Class.forName(className);
                InterfaceOFBizAuthenticationHandler authenticationHandler =
                        (InterfaceOFBizAuthenticationHandler) handlerClass.getDeclaredConstructor().newInstance();
                result = authenticationHandler.logout(request, response, rootElement);
            } catch (Exception e) {
                Debug.logError(e, "Error calling userLogin service", MODULE);
                Map<String, String> messageMap = UtilMisc.toMap("errorMessage", e.getMessage());
                String errMsg = UtilProperties.getMessage(RESOURCE, "loginevents.following_error_occurred_during_login", messageMap,
                        UtilHttp.getLocale(request));
                request.setAttribute("_ERROR_MESSAGE_", errMsg);
            }
        }

        if (request.getAttribute("_AUTO_LOGIN_LOGOUT_") == null) {
            return LoginWorker.autoLoginCheck(request, response);
        }
        return result;
    }

    protected static Element getRootElement(HttpServletRequest request) {
        if (Debug.infoOn()) {
            Debug.logInfo("LDAP config file: " + LDAP_CONFIG, MODULE);
        }
        File configFile = new File(LDAP_CONFIG);
        Element rootElement = null;
        try (FileInputStream configFileIS = new FileInputStream(configFile)) {
            Document configDoc = UtilXml.readXmlDocument(configFileIS, "LDAP configuration file " + LDAP_CONFIG);
            rootElement = configDoc.getDocumentElement();
        } catch (FileNotFoundException e) {
            Debug.logError(e, "Error calling userLogin service", MODULE);
            Map<String, String> messageMap = UtilMisc.toMap("errorMessage", e.getMessage());
            String errMsg = UtilProperties.getMessage(RESOURCE, "loginevents.following_error_occurred_during_login", messageMap,
                    UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
        } catch (SAXException e) {
            Debug.logError(e, "Error calling userLogin service", MODULE);
            Map<String, String> messageMap = UtilMisc.toMap("errorMessage", e.getMessage());
            String errMsg = UtilProperties.getMessage(RESOURCE, "loginevents.following_error_occurred_during_login", messageMap,
                    UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
        } catch (ParserConfigurationException e) {
            Debug.logError(e, "Error calling userLogin service", MODULE);
            Map<String, String> messageMap = UtilMisc.toMap("errorMessage", e.getMessage());
            String errMsg = UtilProperties.getMessage(RESOURCE, "loginevents.following_error_occurred_during_login", messageMap,
                    UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
        } catch (IOException e) {
            Debug.logError(e, "Error calling userLogin service", MODULE);
            Map<String, String> messageMap = UtilMisc.toMap("errorMessage", e.getMessage());
            String errMsg = UtilProperties.getMessage(RESOURCE, "loginevents.following_error_occurred_during_login", messageMap,
                    UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
        }

        return rootElement;
    }
}
