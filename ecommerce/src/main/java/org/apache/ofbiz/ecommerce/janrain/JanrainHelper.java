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
package org.apache.ofbiz.ecommerce.janrain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.UtilXml;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.webapp.control.LoginWorker;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Java Helper Class for Janrain Engage
 */
public class JanrainHelper {

    private static final String MODULE = JanrainHelper.class.getName();
    private static String apiKey = UtilProperties.getPropertyValue("ecommerce", "janrain.apiKey");
    private static String baseUrl = UtilProperties.getPropertyValue("ecommerce", "janrain.baseUrl");
    public JanrainHelper(String apiKey, String baseUrl) {
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        JanrainHelper.apiKey = apiKey;
        JanrainHelper.baseUrl = baseUrl;
    }

    /**
     * Gets api key.
     * @return the api key
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Gets base url.
     * @return the base url
     */
    public String getBaseUrl() {
        return baseUrl;
    }
    public static Element authInfo(String token) {
        Map<String, Object> query = new HashMap<>();
        query.put("token", token);
        return apiCall("auth_info", query);
    }

    /**
     * All mappings hash map.
     * @return the hash map
     */
    public HashMap<String, List<String>> allMappings() {
        Element rsp = apiCall("all_mappings", null);
        rsp.getFirstChild();
        HashMap<String, List<String>> result = new HashMap<>();
        NodeList mappings = getNodeList("/rsp/mappings/mapping", rsp);
        for (int i = 0; i < mappings.getLength(); i++) {
            Element mapping = (Element) mappings.item(i);
            List<String> identifiers = new ArrayList<>();
            NodeList rkList = getNodeList("primaryKey", mapping);
            NodeList idList = getNodeList("identifiers/identifier", mapping);
            String remoteKey = ((Element) rkList.item(0)).getTextContent();
            for (int j = 0; j < idList.getLength(); j++) {
                Element ident = (Element) idList.item(j);
                identifiers.add(ident.getTextContent());
            }
            result.put(remoteKey, identifiers);
        }
        return result;
    }
    private static NodeList getNodeList(String xpathExpr, Element root) {
        XPathFactory factory = XPathFactory.newInstance();
        XPath xpath = factory.newXPath();
        try {
            return (NodeList) xpath.evaluate(xpathExpr, root, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            return null;
        }
    }

    /**
     * Mappings list.
     * @param primaryKey the primary key
     * @return the list
     */
    public List<String> mappings(Object primaryKey) {
        Map<String, Object> query = new HashMap<>();
        query.put("primaryKey", primaryKey);
        Element rsp = apiCall("mappings", query);
        Element oids = (Element) rsp.getFirstChild();
        List<String> result = new ArrayList<>();
        NodeList nl = oids.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Element e = (Element) nl.item(i);
            result.add(e.getTextContent());
        }
        return result;
    }

    /**
     * Map.
     * @param identifier the identifier
     * @param primaryKey the primary key
     */
    public void map(String identifier, Object primaryKey) {
        Map<String, Object> query = new HashMap<>();
        query.put("identifier", identifier);
        query.put("primaryKey", primaryKey);
        apiCall("map", query);
    }

    /**
     * Unmap.
     * @param identifier the identifier
     * @param primaryKey the primary key
     */
    public void unmap(String identifier, Object primaryKey) {
        Map<String, Object> query = new HashMap<>();
        query.put("identifier", identifier);
        query.put("primaryKey", primaryKey);
        apiCall("unmap", query);
    }
    private static Element apiCall(String methodName, Map<String, Object> partialQuery) {
        Map<String, Object> query = null;
        if (partialQuery == null) {
            query = new HashMap<>();
        } else {
            query = new HashMap<>(partialQuery);
        }
        query.put("format", "xml");
        query.put("apiKey", apiKey);
        StringBuffer sb = new StringBuffer();
        for (Iterator<Map.Entry<String, Object>> it = query.entrySet().iterator(); it.hasNext();) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            try {
                Map.Entry<String, Object> e = it.next();
                sb.append(URLEncoder.encode(e.getKey().toString(), "UTF-8"));
                sb.append('=');
                sb.append(URLEncoder.encode(e.getValue().toString(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("Unexpected encoding error", e);
            }
        }
        String data = sb.toString();
        try {
            URL url = new URL(baseUrl + "/api/v2/" + methodName);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.connect();
            OutputStreamWriter osw = new OutputStreamWriter(
                    conn.getOutputStream(), "UTF-8");
            osw.write(data);
            osw.close();

            BufferedReader post = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line = "";
            StringBuilder buf = new StringBuilder();
            while ((line = post.readLine()) != null) {
                buf.append(line);
            }
            post.close();
            Document tagXml = UtilXml.readXmlDocument(buf.toString());
            Element response = tagXml.getDocumentElement();
            if (!"ok".equals(response.getAttribute("stat"))) {
                throw new RuntimeException("Unexpected API error");
            }
            return response;
        } catch (MalformedURLException e) {
            throw new RuntimeException("Unexpected URL error", e);
        } catch (IOException e) {
            throw new RuntimeException("Unexpected IO error", e);
        } catch (ParserConfigurationException | SAXException e) {
            throw new RuntimeException("Unexpected XML error", e);
        }
    }

    public static String janrainCheckLogin(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        String token = request.getParameter("token");
        String errMsg = "";
        if (UtilValidate.isNotEmpty(token)) {
            Element authInfo = JanrainHelper.authInfo(token);
            Element profileElement = UtilXml.firstChildElement(authInfo, "profile");
            Element nameElement = UtilXml.firstChildElement(profileElement, "name");

            // profile element
            String displayName = UtilXml.elementValue(UtilXml.firstChildElement(profileElement, "displayName"));
            String email = UtilXml.elementValue(UtilXml.firstChildElement(profileElement, "email"));
            String identifier = UtilXml.elementValue(UtilXml.firstChildElement(profileElement, "identifier"));
            String preferredUsername = UtilXml.elementValue(UtilXml.firstChildElement(profileElement, "preferredUsername"));
            String providerName = UtilXml.elementValue(UtilXml.firstChildElement(profileElement, "providerName"));
            String url = UtilXml.elementValue(UtilXml.firstChildElement(profileElement, "url"));

            // name element
            String givenName = UtilXml.elementValue(UtilXml.firstChildElement(nameElement, "givenName"));
            String familyName = UtilXml.elementValue(UtilXml.firstChildElement(nameElement, "familyName"));
            String formatted = UtilXml.elementValue(UtilXml.firstChildElement(nameElement, "formatted"));

            if (UtilValidate.isEmpty("preferredUsername")) {
                errMsg = UtilProperties.getMessage("SecurityextUiLabels", "loginevents.username_not_found_reenter", UtilHttp.getLocale(request));
                request.setAttribute("_ERROR_MESSAGE_", errMsg);
                return "error";
            }

            Map<String, String> result = new HashMap<>();
            result.put("displayName", displayName);
            result.put("email", email);
            result.put("identifier", identifier);
            result.put("preferredUsername", preferredUsername);
            result.put("providerName", providerName);
            result.put("url", url);
            result.put("givenName", givenName);
            result.put("familyName", familyName);
            result.put("formatted", formatted);
            request.setAttribute("userInfoMap", result);
            try {
                GenericValue userLogin = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", preferredUsername).cache().queryOne();
                if (userLogin != null) {
                    LoginWorker.doBasicLogin(userLogin, request);
                    LoginWorker.createSecuredLoginIdCookie(request, response);
                    LoginWorker.autoLoginSet(request, response);
                    return "success";
                } else {
                    return "userLoginMissing";
                }
            } catch (GenericEntityException e) {
                Debug.logError(e, "Error finding the userLogin for distributed cache clear", MODULE);
            }
        }
        return "success";
    }
}
