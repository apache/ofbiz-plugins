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
package org.apache.ofbiz.cmssite.multisite;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;

import org.apache.ofbiz.base.util.UtilHttp;


public class MultiSiteRequestWrapper implements HttpServletRequest {

    private final HttpServletRequest request;

    public MultiSiteRequestWrapper(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public String changeSessionId() {
        return request.changeSessionId();
    }

    @Override
    public String getAuthType() {
        return request.getAuthType();
    }

    @Override
    public String getContextPath() {
        return request.getContextPath();
    }

    @Override
    public Cookie[] getCookies() {
        return request.getCookies();
    }

    @Override
    public long getDateHeader(String arg0) {
        return request.getDateHeader(arg0);
    }

    @Override
    public String getHeader(String arg0) {
        return request.getHeader(arg0);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return request.getHeaderNames();
    }

    @Override
    public Enumeration<String> getHeaders(String arg0) {
        return request.getHeaders(arg0);
    }

    @Override
    public int getIntHeader(String arg0) {
        return request.getIntHeader(arg0);
    }

    @Override
    public String getMethod() {
        return request.getMethod();
    }

    @Override
    public String getPathInfo() {
        boolean removePathAlias = (Boolean) request.getAttribute("removePathAlias");
        String pathInfo = request.getPathInfo();
        if (removePathAlias && pathInfo != null) {
            int nextPathSegmentStart = pathInfo.indexOf('/', 1);
            if (nextPathSegmentStart == -1) {
                nextPathSegmentStart = pathInfo.indexOf('?', 1);
                if (nextPathSegmentStart == -1) {
                    return "/";
                }
            }
            return pathInfo.substring(nextPathSegmentStart);
        } else {
            return pathInfo;
        }
    }

    @Override
    public String getPathTranslated() {
        return request.getPathTranslated();
    }

    @Override
    public String getQueryString() {
        return request.getQueryString();
    }

    @Override
    public String getRemoteUser() {
        return request.getRemoteUser();
    }

    @Override
    public String getRequestURI() {
        return request.getRequestURI();
    }

    @Override
    public StringBuffer getRequestURL() {
        return request.getRequestURL();
    }

    @Override
    public String getRequestedSessionId() {
        return request.getRequestedSessionId();
    }

    @Override
    public String getServletPath() {
        return request.getServletPath();
    }

    @Override
    public HttpSession getSession() {
        return request.getSession();
    }

    @Override
    public HttpSession getSession(boolean arg0) {
        return request.getSession(arg0);
    }

    @Override
    public Principal getUserPrincipal() {
        return request.getUserPrincipal();
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return request.isRequestedSessionIdFromCookie();
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return request.isRequestedSessionIdFromURL();
    }

    @Deprecated @Override
    public boolean isRequestedSessionIdFromUrl() {
        return request.isRequestedSessionIdFromURL();
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        return request.isRequestedSessionIdValid();
    }

    @Override
    public boolean isUserInRole(String arg0) {
        return request.isUserInRole(arg0);
    }

    @Override
    public Object getAttribute(String arg0) {
        return request.getAttribute(arg0);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return request.getAttributeNames();
    }

    @Override
    public String getCharacterEncoding() {
        return request.getCharacterEncoding();
    }

    @Override
    public int getContentLength() {
        return request.getContentLength();
    }

    @Override
    public long getContentLengthLong() {
        return request.getContentLengthLong();
    }

    @Override
    public String getContentType() {
        return request.getContentType();
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return request.getInputStream();
    }

    @Override
    public String getLocalAddr() {
        return request.getLocalAddr();
    }

    @Override
    public String getLocalName() {
        return request.getLocalName();
    }

    @Override
    public int getLocalPort() {
        return request.getLocalPort();
    }

    @Override
    public Locale getLocale() {
        return UtilHttp.getLocale(request);
    }

    @Override
    public Enumeration<Locale> getLocales() {
        return request.getLocales();
    }

    @Override
    public String getParameter(String arg0) {
        return request.getParameter(arg0);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return request.getParameterMap();
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return request.getParameterNames();
    }

    @Override
    public String[] getParameterValues(String arg0) {
        return request.getParameterValues(arg0);
    }

    @Override
    public String getProtocol() {
        return request.getProtocol();
    }
    /** get reader */
    @Override
    public BufferedReader getReader() throws IOException {
        return request.getReader();
    }
    /** get real path */
    @Override @Deprecated
    public String getRealPath(String arg0) {
        return request.getServletContext().getRealPath(arg0);
    }
    /** get remote addr */
    @Override
    public String getRemoteAddr() {
        return request.getRemoteAddr();
    }
    /** get remote host */
    @Override
    public String getRemoteHost() {
        return request.getRemoteHost();
    }
    /** get remote port */
    @Override
    public int getRemotePort() {
        return request.getRemotePort();
    }
    /** get request dispatcher */
    @Override
    public RequestDispatcher getRequestDispatcher(String arg0) {
        return request.getRequestDispatcher(arg0);
    }
    /** get scheme */
    @Override
    public String getScheme() {
        return request.getScheme();
    }
    /** get server name */
    @Override
    public String getServerName() {
        return request.getServerName();
    }
    /** get server port */
    @Override
    public int getServerPort() {
        return request.getServerPort();
    }
    /** is secure */
    @Override
    public boolean isSecure() {
        return request.isSecure();
    }
    /** remove attribute */
    @Override
    public void removeAttribute(String arg0) {
        request.removeAttribute(arg0);
    }
    /** set attribute */
    @Override
    public void setAttribute(String arg0, Object arg1) {
        request.setAttribute(arg0, arg1);
    }
    /** set character encoding */
    @Override
    public void setCharacterEncoding(String arg0) throws UnsupportedEncodingException {
        request.setCharacterEncoding(arg0);
    }
    /** authenticate */
    @Override
    public boolean authenticate(HttpServletResponse arg0) throws IOException, ServletException {
        return request.authenticate(arg0);
    }
    /** get part */
    @Override
    public Part getPart(String arg0) throws IOException, IllegalStateException, ServletException {
        return request.getPart(arg0);
    }
    /** get parts */
    @Override
    public Collection<Part> getParts() throws IOException, IllegalStateException, ServletException {
        return request.getParts();
    }
    /** login */
    @Override
    public void login(String arg0, String arg1) throws ServletException {
        request.login(arg0, arg1);
    }
    /** logout */
    @Override
    public void logout() throws ServletException {
        request.logout();
    }
    /** get async context */
    @Override
    public AsyncContext getAsyncContext() {
        return request.getAsyncContext();
    }
    /** get dispatcher type */
    @Override
    public DispatcherType getDispatcherType() {
        return request.getDispatcherType();
    }
    /** get servlet context */
    @Override
    public ServletContext getServletContext() {
        return request.getServletContext();
    }
    /** is async started */
    @Override
    public boolean isAsyncStarted() {
        return request.isAsyncStarted();
    }
    /** is async supported */
    @Override
    public boolean isAsyncSupported() {
        return request.isAsyncSupported();
    }
    /** start async */
    @Override
    public AsyncContext startAsync() {
        return request.startAsync();
    }
    /** start async */
    @Override
    public AsyncContext startAsync(ServletRequest arg0, ServletResponse arg1) {
        return request.startAsync(arg0, arg1);
    }
    /** upgrade */
    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException {
        return request.upgrade(handlerClass);
    }
}
