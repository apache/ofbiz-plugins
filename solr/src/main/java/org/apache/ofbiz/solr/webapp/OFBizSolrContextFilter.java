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
package org.apache.ofbiz.solr.webapp;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.ofbiz.base.conversion.ConversionException;
import org.apache.ofbiz.base.conversion.JSONConverters.MapToJSON;
import org.apache.ofbiz.base.lang.JSON;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilTimer;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.webapp.control.LoginWorker;
import org.apache.solr.common.SolrException;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.NodeConfig;
import org.apache.solr.servlet.SolrDispatchFilter;

/**
 * OFBizSolrContextFilter - Restricts access to solr urls.
 */
public class OFBizSolrContextFilter extends SolrDispatchFilter {

    private static final String MODULE = OFBizSolrContextFilter.class.getName();
    private static final String RESOURCE = "SolrUiLabels";

    private static void sendJsonHeaderMessage(HttpServletRequest httpRequest, HttpServletResponse httpResponse,
                                              GenericValue userLogin, String notLoginMessage, String noPermissionMessage, Locale locale)
            throws IOException {
        httpResponse.setContentType("application/json");
        MapToJSON mapToJson = new MapToJSON();
        Map<String, Object> responseHeader = new HashMap<>();
        JSON json;
        String message = "";

        try (OutputStream os = httpResponse.getOutputStream()) {
            if (UtilValidate.isEmpty(userLogin)) {
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                responseHeader.put("status", HttpServletResponse.SC_UNAUTHORIZED);
                message = UtilProperties.getMessage(RESOURCE, notLoginMessage, locale);
                responseHeader.put("message", message);
            } else {
                httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                responseHeader.put("status", HttpServletResponse.SC_FORBIDDEN);
                message = UtilProperties.getMessage(RESOURCE, noPermissionMessage, locale);
                responseHeader.put("message", message);
            }
            json = mapToJson.convert(UtilMisc.toMap("responseHeader", (Object) responseHeader));
            os.write(json.toString().getBytes());
            os.flush();
            Debug.logInfo("[" + httpRequest.getRequestURI().substring(1) + "(Domain:" + httpRequest.getScheme() + "://" + httpRequest.getServerName()
                    + ")] Request error: " + message, MODULE);
        } catch (ConversionException e) {
            Debug.logError("Error while converting responseHeader map to JSON.", MODULE);
        }
    }
    /** Init */
    @Override
    public void init(FilterConfig config) throws ServletException {
        Properties props = System.getProperties();
        props.setProperty("solr.log.dir", UtilProperties.getPropertyValue("solrconfig", "solr.log.dir", "runtime/logs/solr"));
        props.setProperty("solr.log.level", UtilProperties.getPropertyValue("solrconfig", "solr.log.level", "INFO"));
        props.setProperty("solr/home", UtilProperties.getPropertyValue("solrconfig", "solr/home"));
        super.init(config);
    }
    /** Do filter */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        Locale locale = UtilHttp.getLocale(httpRequest);

        // check if the request is from an authorized user
        String servletPath = httpRequest.getServletPath();
        if (UtilValidate.isNotEmpty(servletPath) && (servletPath.startsWith("/admin/") || servletPath.endsWith("/update")
                || servletPath.endsWith("/update/json") || servletPath.endsWith("/update/csv") || servletPath.endsWith("/update/extract")
                || servletPath.endsWith("/replication") || servletPath.endsWith("/file") || servletPath.endsWith("/file/"))) {
            HttpSession session = httpRequest.getSession();
            GenericValue userLogin = (GenericValue) session.getAttribute("userLogin");
            if (servletPath.startsWith("/admin/") && (UtilValidate.isEmpty(userLogin) || !LoginWorker.hasBasePermission(userLogin, httpRequest))) {
                response.setContentType("application/json");
                MapToJSON mapToJson = new MapToJSON();
                JSON json;
                OutputStream os = null;
                try {
                    json = mapToJson.convert(UtilMisc.toMap("ofbizLogin", (Object) "true"));
                    os = response.getOutputStream();
                    os.write(json.toString().getBytes());
                    os.flush();
                    String message = "";
                    if (UtilValidate.isEmpty(userLogin)) {
                        message = UtilProperties.getMessage(RESOURCE, "SolrErrorManageLoginFirst", locale);
                    } else {
                        message = UtilProperties.getMessage(RESOURCE, "SolrErrorNoManagePermission", locale);
                    }
                    Debug.logInfo("[" + httpRequest.getRequestURI().substring(1) + "(Domain:" + request.getScheme() + "://" + request.getServerName()
                            + ")] Request error: " + message, MODULE);
                } catch (ConversionException e) {
                    Debug.logError("Error while converting Solr ofbizLogin map to JSON.", MODULE);
                } finally {
                    if (os != null) {
                        os.close();
                    }
                }
                return;
            } else if (servletPath.endsWith("/update") || servletPath.endsWith("/update/json") || servletPath.endsWith("/update/csv")
                    || servletPath.endsWith("/update/extract")) {
                // NOTE: the update requests are defined in an index's solrconfig.xml
                // get the Solr index name from the request
                if (UtilValidate.isEmpty(userLogin) || !LoginWorker.hasBasePermission(userLogin, httpRequest)) {
                    sendJsonHeaderMessage(httpRequest, httpResponse, userLogin, "SolrErrorUpdateLoginFirst", "SolrErrorNoUpdatePermission", locale);
                    return;
                }
            } else if (servletPath.endsWith("/replication")) {
                // get the Solr index name from the request
                if (UtilValidate.isEmpty(userLogin) || !LoginWorker.hasBasePermission(userLogin, httpRequest)) {
                    sendJsonHeaderMessage(httpRequest, httpResponse, userLogin, "SolrErrorReplicateLoginFirst", "SolrErrorNoReplicatePermission",
                            locale);
                    return;
                }
            } else if (servletPath.endsWith("/file") || servletPath.endsWith("/file/")) {
                // get the Solr index name from the request
                if (UtilValidate.isEmpty(userLogin) || !LoginWorker.hasBasePermission(userLogin, httpRequest)) {
                    sendJsonHeaderMessage(httpRequest, httpResponse, userLogin, "SolrErrorViewFileLoginFirst", "SolrErrorNoViewFilePermission",
                            locale);
                    return;
                }
            }
        }

        String charset = request.getCharacterEncoding();
        String rname = null;
        if (httpRequest.getRequestURI() != null) {
            rname = httpRequest.getRequestURI().substring(1);
        }
        if (rname != null && (rname.endsWith(".css") || rname.endsWith(".js") || rname.endsWith(".ico") || rname.endsWith(".html")
                || rname.endsWith(".png") || rname.endsWith(".jpg") || rname.endsWith(".gif"))) {
            rname = null;
        }
        UtilTimer timer = null;
        if (Debug.timingOn() && rname != null) {
            timer = new UtilTimer();
            timer.setLog(true);
            timer.timerString("[" + rname + "(Domain:" + request.getScheme() + "://" + request.getServerName() + ")] Request Begun, encoding=["
                    + charset + "]", MODULE);
        }
        // NOTE: there's a chain.doFilter in SolrDispatchFilter's doFilter
        super.doFilter(request, response, chain);
        if (Debug.timingOn() && rname != null)
            timer.timerString("[" + rname + "(Domain:" + request.getScheme() + "://" + request.getServerName() + ")] Request Done", MODULE);
    }
    /** Destroy */
    @Override
    public void destroy() {
        super.destroy();
    }

    /**
     * Override this to change CoreContainer initialization
     * @return a CoreContainer to hold this server's cores
     */
    @Override
    protected CoreContainer createCoreContainer(Path solrHome, Properties extraProperties) {
        NodeConfig nodeConfig = null;
        try {
            nodeConfig = loadNodeConfig(solrHome, extraProperties);
        } catch (SolrException e) {
            //            nodeConfig = loadNodeConfig("plugins/solr/home", extraProperties);
            Path path = Paths.get("plugins/solr/home");
            nodeConfig = loadNodeConfig(path, extraProperties);
        }
        cores = new CoreContainer(nodeConfig, extraProperties, true);
        cores.load();
        return cores;
    }
}

