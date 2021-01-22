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
package org.apache.ofbiz.solr;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.ofbiz.base.component.ComponentConfig;
import org.apache.ofbiz.base.component.ComponentConfig.WebappInfo;
import org.apache.ofbiz.base.component.ComponentException;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.GenericEntityException;

/**
 * Solr utility class.
 */
public final class SolrUtil {

    private SolrUtil() { }
    private static final String MODULE = SolrUtil.class.getName();
    private static final String[] SOLR_PRODUCT_ATTRIBUTE = {"productId", "internalName", "manu", "size", "smallImage", "mediumImage", "largeImage",
            "listPrice", "defaultPrice", "inStock", "isVirtual" };

    private static final String SOLR_CONFIG_NAME = "solrconfig.properties";
    private static final String SOLR_URL = makeSolrWebappUrl();

    private static final String SOCKET_TIMEOUT_STRING = UtilProperties.getPropertyValue(SOLR_CONFIG_NAME, "solr.client.socket.timeout");

    private static final String CON_TIMEOUT_STRING = UtilProperties.getPropertyValue(SOLR_CONFIG_NAME, "solr.client.connection.timeout");

    private static final String CLIENT_USER_NAME = UtilProperties.getPropertyValue(SOLR_CONFIG_NAME, "solr.client.username");

    private static final String CLIENT_PASSWORD = UtilProperties.getPropertyValue(SOLR_CONFIG_NAME, "solr.client.password");

    private static final Integer SOCKET_TIMEOUT = getSocketTimeout();

    private static final Integer CON_TIMEOUT = getConnectionTimeout();

    private static final String TRUST_SELF_SIGN_CERT_STRING = UtilProperties.getPropertyValue(SOLR_CONFIG_NAME,
            "solr.client.trust.selfsigned.cert", "false");

    private static final boolean TRUST_SELF_SIGNED_CERT = getTrustSelfSignedCert();

    public static String makeSolrWebappUrl() {
        final String solrWebappProtocol = UtilProperties.getPropertyValue(SOLR_CONFIG_NAME, "solr.webapp.protocol");
        final String solrWebappDomainName = UtilProperties.getPropertyValue(SOLR_CONFIG_NAME, "solr.webapp.domainName");
        final String solrWebappPath = UtilProperties.getPropertyValue(SOLR_CONFIG_NAME, "solr.webapp.path");
        final String solrWebappPortOverride = UtilProperties.getPropertyValue(SOLR_CONFIG_NAME, "solr.webapp.portOverride");

        String solrPort;
        if (UtilValidate.isNotEmpty(solrWebappPortOverride)) {
            solrPort = solrWebappPortOverride;
        } else {
            solrPort = UtilProperties.getPropertyValue("url", ("https".equals(solrWebappProtocol)
                    ? "port.https" : "port.http"), ("https".equals(solrWebappProtocol) ? "8443" : "8080"));
        }

        return solrWebappProtocol + "://" + solrWebappDomainName + ":" + solrPort + solrWebappPath;
    }

    private static Integer getSocketTimeout() {
        if (UtilValidate.isNotEmpty(SOCKET_TIMEOUT_STRING)) {
            try {
                return Integer.parseInt(SOCKET_TIMEOUT_STRING);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static Integer getConnectionTimeout() {
        if (UtilValidate.isNotEmpty(CON_TIMEOUT_STRING)) {
            try {
                return Integer.parseInt(CON_TIMEOUT_STRING);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static boolean getTrustSelfSignedCert() {
        return "true".equals(TRUST_SELF_SIGN_CERT_STRING);
    }

    public static boolean isSolrEcaEnabled() {
        Boolean ecaEnabled = null;
        String sysProp = System.getProperty("ofbiz.solr.eca.enabled");
        if (UtilValidate.isNotEmpty(sysProp)) {
            if ("true".equalsIgnoreCase(sysProp)) {
                ecaEnabled = Boolean.TRUE;
            } else if ("false".equalsIgnoreCase(sysProp)) {
                ecaEnabled = Boolean.FALSE;
            }
        }
        if (ecaEnabled == null) {
            ecaEnabled = UtilProperties.getPropertyAsBoolean(SolrUtil.SOLR_CONFIG_NAME, "solr.eca.enabled", false);
        }
        return Boolean.TRUE.equals(ecaEnabled);
    }

    public static WebappInfo getSolrWebappInfo() {
        WebappInfo solrApp = null;
        try {
            ComponentConfig cc = ComponentConfig.getComponentConfig("solr");
            for (WebappInfo currApp : cc.getWebappInfos()) {
                if ("solr".equals(currApp.getName())) {
                    solrApp = currApp;
                    break;
                }
            }
        } catch (ComponentException e) {
            throw new IllegalStateException(e);
        }
        return solrApp;
    }
    public static boolean isEcaTreatConnectErrorNonFatal() {
        Boolean treatConnectErrorNonFatal = UtilProperties.getPropertyAsBoolean(SOLR_CONFIG_NAME, "solr.eca.treatConnectErrorNonFatal", true);
        return Boolean.TRUE.equals(treatConnectErrorNonFatal);
    }
    public static SolrInputDocument generateSolrDocument(Map<String, Object> context) throws GenericEntityException {
        SolrInputDocument doc1 = new SolrInputDocument();

        // add defined attributes
        for (int i = 0; i < SOLR_PRODUCT_ATTRIBUTE.length; i++) {
            if (context.get(SOLR_PRODUCT_ATTRIBUTE[i]) != null) {
                doc1.addField(SOLR_PRODUCT_ATTRIBUTE[i], context.get(SOLR_PRODUCT_ATTRIBUTE[i]).toString());
            }
        }

        // add catalog
        if (context.get("catalog") != null) {
            List<String> catalog = UtilGenerics.cast(context.get("catalog"));
            for (String c : catalog) {
                doc1.addField("catalog", c);
            }
        }

        // add categories
        if (context.get("category") != null) {
            List<String> category = UtilGenerics.cast(context.get("category"));
            Iterator<String> catIter = category.iterator();
            while (catIter.hasNext()) {
                String cat = catIter.next();
                doc1.addField("cat", cat);
            }
        }

        // add features
        if (context.get("features") != null) {
            Set<String> features = UtilGenerics.cast(context.get("features"));
            Iterator<String> featIter = features.iterator();
            while (featIter.hasNext()) {
                String feat = featIter.next();
                doc1.addField("features", feat);
            }
        }

        // add attributes
        if (context.get("attributes") != null) {
            List<String> attributes = UtilGenerics.cast(context.get("attributes"));
            Iterator<String> attrIter = attributes.iterator();
            while (attrIter.hasNext()) {
                String attr = attrIter.next();
                doc1.addField("attributes", attr);
            }
        }

        // add title
        if (context.get("title") != null) {
            Map<String, String> title = UtilGenerics.cast(context.get("title"));
            for (Map.Entry<String, String> entry : title.entrySet()) {
                doc1.addField("title_i18n_" + entry.getKey(), entry.getValue());
            }
        }

        // add short_description
        if (context.get("description") != null) {
            Map<String, String> description = UtilGenerics.cast(context.get("description"));
            for (Map.Entry<String, String> entry : description.entrySet()) {
                doc1.addField("description_i18n_" + entry.getKey(), entry.getValue());
            }
        }

        // add short_description
        if (context.get("longDescription") != null) {
            Map<String, String> longDescription = UtilGenerics.cast(context.get("longDescription"));
            for (Map.Entry<String, String> entry : longDescription.entrySet()) {
                doc1.addField("longdescription_i18n_" + entry.getKey(), entry.getValue());
            }
        }

        return doc1;
    }
    public static Map<String, Object> categoriesAvailable(String catalogId, String categoryId, String productId,
                                                          boolean displayproducts, int viewIndex, int viewSize, String solrIndexName) {
        return categoriesAvailable(catalogId, categoryId, productId, null, displayproducts, viewIndex, viewSize, solrIndexName);
    }

    public static Map<String, Object> categoriesAvailable(String catalogId, String categoryId, String productId, String facetPrefix,
                                                          boolean displayproducts, int viewIndex, int viewSize, String solrIndexName) {
        // create the data model
        Map<String, Object> result = new HashMap<>();
        HttpSolrClient client = null;
        QueryResponse returnMap = new QueryResponse();
        try {
            // do the basic query
            client = getHttpSolrClient(solrIndexName);
            // create Query Object
            String query = "inStock[1 TO *]";
            if (categoryId != null) {
                query += " +cat:" + categoryId;
            } else if (productId != null) {
                query += " +productId:" + productId;
            }
            SolrQuery solrQuery = new SolrQuery();
            solrQuery.setQuery(query);

            if (catalogId != null) {
                solrQuery.setFilterQueries("catalog:" + catalogId);
            }
            if (displayproducts) {
                if (viewSize > -1) {
                    solrQuery.setRows(viewSize);
                } else {
                    solrQuery.setRows(50000);
                }
                if (viewIndex > -1) {
                    solrQuery.setStart(viewIndex);
                }
            } else {
                solrQuery.setFields("cat");
                solrQuery.setRows(0);
            }

            if (UtilValidate.isNotEmpty(facetPrefix)) {
                solrQuery.setFacetPrefix(facetPrefix);
            }

            solrQuery.setFacetMinCount(0);
            solrQuery.setFacet(true);
            solrQuery.addFacetField("cat");
            solrQuery.setFacetLimit(-1);
            if (Debug.verboseOn()) {
                Debug.logVerbose("solr: solrQuery: " + solrQuery, MODULE);
            }
            returnMap = client.query(solrQuery, METHOD.POST);
            result.put("rows", returnMap);
            result.put("numFound", returnMap.getResults().getNumFound());
        } catch (Exception e) {
            Debug.logError(e.getMessage(), MODULE);
        }
        return result;
    }

    public static SolrUtil getInstance() {
        return new SolrUtil();
    }

    public static HttpSolrClient getHttpSolrClient(String solrIndexName) throws ClientProtocolException, IOException {
        HttpClientContext httpContext = HttpClientContext.create();

        CloseableHttpClient httpClient = null;
        if (TRUST_SELF_SIGNED_CERT) {
            httpClient = UtilHttp.getAllowAllHttpClient();
        } else {
            httpClient = HttpClients.createDefault();
        }

        RequestConfig requestConfig = null;
        if (UtilValidate.isNotEmpty(SOCKET_TIMEOUT) && UtilValidate.isNotEmpty(CON_TIMEOUT)) {
            requestConfig = RequestConfig.custom()
                  .setSocketTimeout(SOCKET_TIMEOUT)
                  .setConnectTimeout(CON_TIMEOUT)
                  .setRedirectsEnabled(true)
                  .build();
        } else if (UtilValidate.isNotEmpty(SOCKET_TIMEOUT)) {
            requestConfig = RequestConfig.custom()
                    .setSocketTimeout(SOCKET_TIMEOUT)
                    .setRedirectsEnabled(true)
                    .build();
        } else if (UtilValidate.isNotEmpty(CON_TIMEOUT)) {
            requestConfig = RequestConfig.custom()
                    .setConnectTimeout(CON_TIMEOUT)
                    .setRedirectsEnabled(true)
                    .build();
        } else {
            requestConfig = RequestConfig.custom()
                    .setRedirectsEnabled(true)
                    .build();
        }

        HttpGet httpLogin = new HttpGet(SOLR_URL + "/control/login?USERNAME=" + CLIENT_USER_NAME + "&PASSWORD=" + CLIENT_PASSWORD);
        httpLogin.setConfig(requestConfig);
        CloseableHttpResponse loginResponse = httpClient.execute(httpLogin, httpContext);
        loginResponse.close();
        return new HttpSolrClient.Builder(SOLR_URL + "/" + solrIndexName).withHttpClient(httpClient).build();
    }
}
