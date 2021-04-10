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
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericDelegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SpellCheckResponse.Suggestion;
import org.apache.solr.common.SolrInputDocument;

/**
 * Base class for OFBiz Test Tools test case implementations.
 */
public abstract class SolrProductSearch {

    private static final String MODULE = SolrProductSearch.class.getName();
    private static final String RESOURCE = "SolrUiLabels";

    /**
     * Adds product to solr, with product denoted by productId field in instance attribute
     * - intended for use with ECAs/SECAs.
     */
    public static Map<String, Object> addToSolr(DispatchContext dctx, Map<String, Object> context) throws GenericEntityException {
        Map<String, Object> result;
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        GenericValue productInstance = (GenericValue) context.get("instance");
        String productId = (String) productInstance.get("productId");
        String solrIndexName = (String) context.get("indexName");

        if (SolrUtil.isSolrEcaEnabled()) {
            // Debug.logVerbose("Solr: addToSolr: Running indexing for productId '" + productId + "'", MODULE);
            try {
                GenericValue product = EntityQuery.use(delegator).from("Product").where("productId", productId).queryOne();
                Map<String, Object> dispatchContext = ProductUtil.getProductContent(product, dctx, context);
                dispatchContext.put("treatConnectErrorNonFatal", SolrUtil.isEcaTreatConnectErrorNonFatal());
                dispatchContext.put("indexName", solrIndexName);
                Map<String, Object> runResult = dispatcher.runSync("addToSolrIndex", dispatchContext);
                String runMsg = ServiceUtil.getErrorMessage(runResult);
                if (UtilValidate.isEmpty(runMsg)) {
                    runMsg = null;
                }
                if (ServiceUtil.isError(runResult)) {
                    result = ServiceUtil.returnError(runMsg);
                } else if (ServiceUtil.isFailure(runResult)) {
                    result = ServiceUtil.returnFailure(runMsg);
                } else {
                    result = ServiceUtil.returnSuccess();
                }
            } catch (GenericEntityException | GenericServiceException gse) {
                Debug.logError(gse, gse.getMessage(), MODULE);
                result = ServiceUtil.returnError(gse.toString());
            }
        } else {
            final String statusMsg = "Solr ECA indexing disabled; skipping indexing for productId '" + productId + "'";
            if (Debug.verboseOn()) {
                Debug.logVerbose("Solr: addToSolr: " + statusMsg, MODULE);
            }
            result = ServiceUtil.returnSuccess();
        }
        return result;
    }

    /**
     * Adds product to solr index.
     */
    public static Map<String, Object> addToSolrIndex(DispatchContext dctx, Map<String, Object> context) throws GenericEntityException {
        HttpSolrClient client = null;
        Locale locale = (Locale) context.get("locale");
        Map<String, Object> result;
        String productId = (String) context.get("productId");
        String solrIndexName = (String) context.get("indexName");
        // connectErrorNonFatal is a necessary option because in some cases it may be considered normal that solr server is unavailable;
        // don't want to return error and abort transactions in these cases.
        Boolean treatConnectErrorNonFatal = (Boolean) context.get("treatConnectErrorNonFatal");
        try {
            Debug.logInfo("Solr: Generating and indexing document for productId '" + productId + "'", MODULE);

            SolrUtil.getInstance();
            client = SolrUtil.getHttpSolrClient(solrIndexName);

            // Construct Documents
            SolrInputDocument doc1 = SolrUtil.generateSolrDocument(context);
            Collection<SolrInputDocument> docs = new ArrayList<>();

            if (Debug.verboseOn()) {
                Debug.logVerbose("Solr: Indexing document: " + doc1.toString(), MODULE);
            }

            docs.add(doc1);

            // push Documents to server
            client.add(docs);
            client.commit();

            final String statusStr = UtilProperties.getMessage(RESOURCE, "SolrDocumentForProductIdAddedToSolrIndex",
                    UtilMisc.toMap("productId", context.get("productId")), locale);
            Debug.logInfo("Solr: " + statusStr, MODULE);
            result = ServiceUtil.returnSuccess(statusStr);
        } catch (MalformedURLException e) {
            Debug.logError(e, e.getMessage(), MODULE);
            result = ServiceUtil.returnError(e.toString());
            result.put("errorType", "urlError");
        } catch (SolrServerException e) {
            if (e.getCause() != null && e.getCause() instanceof ConnectException) {
                final String statusStr = UtilProperties.getMessage(RESOURCE, "SolrFailureConnectingToSolrServerToCommitProductId",
                        UtilMisc.toMap("productId", context.get("productId")), locale);
                if (Boolean.TRUE.equals(treatConnectErrorNonFatal)) {
                    Debug.logWarning(e, "Solr: " + statusStr, MODULE);
                    result = ServiceUtil.returnFailure(statusStr);
                } else {
                    Debug.logError(e, "Solr: " + statusStr, MODULE);
                    result = ServiceUtil.returnError(statusStr);
                }
                result.put("errorType", "connectError");
            } else {
                Debug.logError(e, e.getMessage(), MODULE);
                result = ServiceUtil.returnError(e.toString());
                result.put("errorType", "solrServerError");
            }
        } catch (IOException e) {
            Debug.logError(e, e.getMessage(), MODULE);
            result = ServiceUtil.returnError(e.toString());
            result.put("errorType", "ioError");
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (IOException e) {
                    Debug.logError(e, MODULE);
                }
            }
        }
        return result;
    }

    /**
     * Adds a List of products to the solr index.
     * <p>
     * This is faster than reflushing the index each time.
     */
    public static Map<String, Object> addListToSolrIndex(DispatchContext dctx, Map<String, Object> context) throws GenericEntityException {
        String solrIndexName = (String) context.get("indexName");
        Locale locale = (Locale) context.get("locale");
        HttpSolrClient client = null;
        Map<String, Object> result;
        Boolean treatConnectErrorNonFatal = (Boolean) context.get("treatConnectErrorNonFatal");
        try {
            Collection<SolrInputDocument> docs = new ArrayList<>();

            // Construct Documents
            List<Map<String, Object>> fieldList = UtilGenerics.cast(context.get("fieldList"));

            Debug.logInfo("Solr: Generating and adding " + fieldList.size() + " documents to solr index", MODULE);

            for (Iterator<Map<String, Object>> fieldListIterator = fieldList.iterator(); fieldListIterator.hasNext();) {
                SolrInputDocument doc1 = SolrUtil.generateSolrDocument(fieldListIterator.next());
                if (Debug.verboseOn()) {
                    Debug.logVerbose("Solr: Indexing document: " + doc1.toString(), MODULE);
                }
                docs.add(doc1);
            }
            SolrUtil.getInstance();
            // push Documents to server
            client = SolrUtil.getHttpSolrClient(solrIndexName);
            client.add(docs);
            client.commit();

            final String statusStr = UtilProperties.getMessage(RESOURCE, "SolrAddedDocumentsToSolrIndex",
                    UtilMisc.toMap("fieldList", fieldList.size()), locale);
            Debug.logInfo("Solr: " + statusStr, MODULE);
            result = ServiceUtil.returnSuccess(statusStr);
        } catch (MalformedURLException e) {
            Debug.logError(e, e.getMessage(), MODULE);
            result = ServiceUtil.returnError(e.toString());
            result.put("errorType", "urlError");
        } catch (SolrServerException e) {
            if (e.getCause() != null && e.getCause() instanceof ConnectException) {
                final String statusStr = UtilProperties.getMessage(RESOURCE, "SolrFailureConnectingToSolrServerToCommitProductList",
                        UtilMisc.toMap("productId", context.get("productId")), locale);
                if (Boolean.TRUE.equals(treatConnectErrorNonFatal)) {
                    Debug.logWarning(e, "Solr: " + statusStr, MODULE);
                    result = ServiceUtil.returnFailure(statusStr);
                } else {
                    Debug.logError(e, "Solr: " + statusStr, MODULE);
                    result = ServiceUtil.returnError(statusStr);
                }
                result.put("errorType", "connectError");
            } else {
                Debug.logError(e, e.getMessage(), MODULE);
                result = ServiceUtil.returnError(e.toString());
                result.put("errorType", "solrServerError");
            }
        } catch (IOException e) {
            Debug.logError(e, e.getMessage(), MODULE);
            result = ServiceUtil.returnError(e.toString());
            result.put("errorType", "ioError");
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (IOException e) {
                    Debug.logError(e, MODULE);
                }
            }
        }
        return result;
    }

    /**
     * Runs a query on the Solr Search Engine and returns the results.
     * <p>
     * This function only returns an object of type QueryResponse, so it is probably not a good idea to call it directly from within the
     * groovy files (As a decent example on how to use it, however, use keywordSearch instead).
     */
    public static Map<String, Object> runSolrQuery(DispatchContext dctx, Map<String, Object> context) {
        // get Connection
        HttpSolrClient client = null;
        String solrIndexName = (String) context.get("indexName");
        Map<String, Object> result;
        try {
            SolrUtil.getInstance();
            client = SolrUtil.getHttpSolrClient(solrIndexName);
            // create Query Object
            SolrQuery solrQuery = new SolrQuery();
            solrQuery.setQuery((String) context.get("query"));
            boolean faceted = (Boolean) context.get("facet");
            if (faceted) {
                solrQuery.setFacet(faceted);
                solrQuery.addFacetField("manu");
                solrQuery.addFacetField("cat");
                solrQuery.setFacetMinCount(1);
                solrQuery.setFacetLimit(8);

                solrQuery.addFacetQuery("listPrice:[0 TO 50]");
                solrQuery.addFacetQuery("listPrice:[50 TO 100]");
                solrQuery.addFacetQuery("listPrice:[100 TO 250]");
                solrQuery.addFacetQuery("listPrice:[250 TO 500]");
                solrQuery.addFacetQuery("listPrice:[500 TO 1000]");
                solrQuery.addFacetQuery("listPrice:[1000 TO 2500]");
                solrQuery.addFacetQuery("listPrice:[2500 TO 5000]");
                solrQuery.addFacetQuery("listPrice:[5000 TO 10000]");
                solrQuery.addFacetQuery("listPrice:[10000 TO 50000]");
                solrQuery.addFacetQuery("listPrice:[50000 TO *]");
            }

            boolean spellCheck = (Boolean) context.get("spellcheck");
            if (spellCheck) {
                solrQuery.setParam("spellcheck", spellCheck);
            }

            boolean highLight = (Boolean) context.get("highlight");
            if (highLight) {
                solrQuery.setHighlight(highLight);
                solrQuery.setHighlightSimplePre("<span class=\"highlight\">");
                solrQuery.addHighlightField("description");
                solrQuery.setHighlightSimplePost("</span>");
                solrQuery.setHighlightSnippets(2);
            }

            // Set additional Parameter
            // SolrQuery.ORDER order = SolrQuery.ORDER.desc;

            if (context.get("viewIndex") != null && (Integer) context.get("viewIndex") > 0) {
                solrQuery.setStart((Integer) context.get("viewIndex"));
            }
            if (context.get("viewSize") != null && (Integer) context.get("viewSize") > 0) {
                solrQuery.setRows((Integer) context.get("viewSize"));
            }

            // if ((List) context.get("queryFilter") != null && ((ArrayList<SolrDocument>) context.get("queryFilter")).size() > 0) {
            // List filter = (List) context.get("queryFilter");
            // String[] tn = new String[filter.size()];
            // Iterator it = filter.iterator();
            // for (int i = 0; i < filter.size(); i++) {
            // tn[i] = (String) filter.get(i);
            // }
            // solrQuery.setFilterQueries(tn);
            // }
            String queryFilter = (String) context.get("queryFilter");
            if (UtilValidate.isNotEmpty(queryFilter)) {
                solrQuery.setFilterQueries(queryFilter.split(" "));
            }
            if ((String) context.get("returnFields") != null) {
                solrQuery.setFields((String) context.get("returnFields"));
            }

            // if ((Boolean) context.get("sortByReverse"))order.reverse();
            if ((String) context.get("sortBy") != null && !((String) context.get("sortBy")).isEmpty()) {
                SolrQuery.ORDER order;
                if (!((Boolean) context.get("sortByReverse"))) {
                    order = SolrQuery.ORDER.asc;
                } else {
                    order = SolrQuery.ORDER.desc;
                }
                solrQuery.setSort(((String) context.get("sortBy")).replaceFirst("-", ""), order);
            }

            if ((String) context.get("facetQuery") != null) {
                solrQuery.addFacetQuery((String) context.get("facetQuery"));
            }

            QueryResponse rsp = client.query(solrQuery);
            result = ServiceUtil.returnSuccess();
            result.put("queryResult", rsp);
        } catch (Exception e) {
            Debug.logError(e, e.getMessage(), MODULE);
            result = ServiceUtil.returnError(e.toString());
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (IOException e) {
                    Debug.logError(e, MODULE);
                }
            }
        }
        return result;
    }

    /**
     * Performs solr products search.
     */
    public static Map<String, Object> productsSearch(DispatchContext dctx, Map<String, Object> context) {
        Map<String, Object> result;
        Locale locale = (Locale) context.get("locale");
        LocalDispatcher dispatcher = dctx.getDispatcher();
        String solrIndexName = (String) context.get("indexName");

        try {
            Map<String, Object> dispatchMap = new HashMap<>();
            if (UtilValidate.isNotEmpty(context.get("productCategoryId"))) {
                String productCategoryId = (String) context.get("productCategoryId");
                dispatchMap.put("query", "cat:*" + productCategoryId + "*");
            } else {
                return ServiceUtil.returnError(UtilProperties.getMessage(RESOURCE, "SolrMissingProductCategoryId", locale));
            }
            if (context.get("viewSize") != null) {
                dispatchMap.put("viewSize", Integer.parseInt(((String) context.get("viewSize"))));
            }
            if (context.get("viewIndex") != null) {
                dispatchMap.put("viewIndex", Integer.parseInt((String) context.get("viewIndex")));
            }
            if (context.get("queryFilter") != null) {
                dispatchMap.put("queryFilter", context.get("queryFilter"));
            }
            dispatchMap.put("facet", false);
            dispatchMap.put("spellcheck", true);
            dispatchMap.put("highlight", true);
            dispatchMap.put("indexName", solrIndexName);

            Map<String, Object> searchResult = dispatcher.runSync("runSolrQuery", dispatchMap);
            if (ServiceUtil.isError(searchResult)) {
                return ServiceUtil.returnError(ServiceUtil.getErrorMessage(searchResult));
            }

            QueryResponse queryResult = (QueryResponse) searchResult.get("queryResult");

            if (queryResult != null) {
                result = ServiceUtil.returnSuccess();
                result.put("results", queryResult.getResults());
                result.put("listSize", queryResult.getResults().getNumFound());
                result.put("viewIndex", queryResult.getResults().getStart());
                result.put("viewSize", queryResult.getResults().size());
            } else {
                result = ServiceUtil.returnFailure();
            }
        } catch (Exception e) {
            Debug.logError(e, e.getMessage(), MODULE);
            result = ServiceUtil.returnError(e.toString());
        }
        return result;
    }

    /**
     * Performs keyword search.
     * <p>
     * The search form requires the result to be in a specific layout, so this will generate the proper results.
     */
    public static Map<String, Object> keywordSearch(DispatchContext dctx, Map<String, Object> context) {
        Map<String, Object> result;
        LocalDispatcher dispatcher = dctx.getDispatcher();
        String solrIndexName = (String) context.get("indexName");

        try {
            if (context.get("query") == null || context.get("query").equals("")) {
                context.put("query", "*:*");
            }

            Map<String, Object> dispatchMap = new HashMap<>();
            if (context.get("viewSize") != null) {
                dispatchMap.put("viewSize", Integer.parseInt(((String) context.get("viewSize"))));
            }
            if (context.get("viewIndex") != null) {
                dispatchMap.put("viewIndex", Integer.parseInt((String) context.get("viewIndex")));
            }
            if (context.get("query") != null) {
                dispatchMap.put("query", context.get("query"));
            }
            if (context.get("queryFilter") != null) {
                dispatchMap.put("queryFilter", context.get("queryFilter"));
            }
            dispatchMap.put("spellcheck", true);
            dispatchMap.put("indexName", solrIndexName);

            Map<String, Object> searchResult = dispatcher.runSync("runSolrQuery", dispatchMap);
            if (ServiceUtil.isError(searchResult)) {
                return ServiceUtil.returnError(ServiceUtil.getErrorMessage(searchResult));
            }
            QueryResponse queryResult = (QueryResponse) searchResult.get("queryResult");

            List<List<String>> suggestions = new ArrayList<>();
            if (queryResult.getSpellCheckResponse() != null && queryResult.getSpellCheckResponse().getSuggestions() != null) {
                Iterator<Suggestion> iter = queryResult.getSpellCheckResponse().getSuggestions().iterator();
                while (iter.hasNext()) {
                    Suggestion resultDoc = iter.next();
                    Debug.logInfo("Suggestion " + resultDoc.getAlternatives(), MODULE);
                    suggestions.add(resultDoc.getAlternatives());
                }
            }

            boolean isCorrectlySpelled = true;
            if (queryResult.getSpellCheckResponse() != null) {
                isCorrectlySpelled = queryResult.getSpellCheckResponse().isCorrectlySpelled();
            }

            result = ServiceUtil.returnSuccess();
            result.put("isCorrectlySpelled", isCorrectlySpelled);

            Map<String, Integer> facetQuery = queryResult.getFacetQuery();
            Map<String, String> facetQueries = new HashMap<>();
            for (String fq : facetQuery.keySet()) {
                if (facetQuery.get(fq) > 0) {
                    facetQueries.put(fq, fq.replaceAll("^.*\\u005B(.*)\\u005D", "$1") + " (" + facetQuery.get(fq) + ")");
                }
            }

            Map<String, Map<String, Long>> facetFields = new HashMap<>();
            List<FacetField> facets = queryResult.getFacetFields();
            for (FacetField facet : facets) {
                Map<String, Long> facetEntry = new HashMap<>();
                List<FacetField.Count> facetEntries = facet.getValues();
                if (UtilValidate.isNotEmpty(facetEntries)) {
                    for (FacetField.Count fcount : facetEntries) {
                        facetEntry.put(fcount.getName(), fcount.getCount());
                    }
                    facetFields.put(facet.getName(), facetEntry);
                }
            }

            result.put("results", queryResult.getResults());
            result.put("facetFields", facetFields);
            result.put("facetQueries", facetQueries);
            result.put("queryTime", queryResult.getElapsedTime());
            result.put("listSize", queryResult.getResults().getNumFound());
            result.put("viewIndex", queryResult.getResults().getStart());
            result.put("viewSize", queryResult.getResults().size());
            result.put("suggestions", suggestions);

        } catch (Exception e) {
            Debug.logError(e, e.getMessage(), MODULE);
            result = ServiceUtil.returnError(e.toString());
        }
        return result;
    }

    /**
     * Returns a map of the categories currently available under the root element.
     */
    public static Map<String, Object> getAvailableCategories(DispatchContext dctx, Map<String, Object> context) {
        Map<String, Object> result;
        String solrIndexName = (String) context.get("indexName");
        try {
            boolean displayProducts = false;
            if (UtilValidate.isNotEmpty(context.get("displayProducts"))) {
                displayProducts = (Boolean) context.get("displayProducts");
            }

            int viewIndex = 0;
            int viewSize = 9;
            if (displayProducts) {
                viewIndex = (Integer) context.get("viewIndex");
                viewSize = (Integer) context.get("viewSize");
            }
            String catalogId = null;
            if (UtilValidate.isNotEmpty(context.get("catalogId"))) {
                catalogId = (String) context.get("catalogId");
            }

            String productCategoryId = (String) context.get("productCategoryId") != null
                    ? CategoryUtil.getCategoryNameWithTrail((String) context.get("productCategoryId"), dctx) : null;
            Debug.logInfo("productCategoryId " + productCategoryId, MODULE);
            Map<String, Object> query = SolrUtil.categoriesAvailable(catalogId, productCategoryId, (String) context.get("productId"),
                    displayProducts, viewIndex, viewSize, solrIndexName);

            QueryResponse cat = (QueryResponse) query.get("rows");
            result = ServiceUtil.returnSuccess();
            result.put("numFound", (long) 0);
            Map<String, Object> categories = new HashMap<>();
            List<FacetField> catList = cat.getFacetFields();
            for (Iterator<FacetField> catIterator = catList.iterator(); catIterator.hasNext();) {
                FacetField field = catIterator.next();
                List<Count> catL = field.getValues();
                if (catL != null) {
                    // Debug.logInfo("FacetFields = " + catL, MODULE);
                    for (Iterator<Count> catIter = catL.iterator(); catIter.hasNext();) {
                        FacetField.Count f = catIter.next();
                        if (f.getCount() > 0) {
                            categories.put(f.getName(), Long.toString(f.getCount()));
                        }
                    }
                    result.put("categories", categories);
                    result.put("numFound", cat.getResults().getNumFound());
                    // Debug.logInfo("The returned map is this:" + result, MODULE);
                }
            }
        } catch (Exception e) {
            result = ServiceUtil.returnError(e.toString());
            result.put("numFound", (long) 0);
        }
        return result;
    }

    /**
     * Return a map of the side deep categories.
     */
    public static Map<String, Object> getSideDeepCategories(DispatchContext dctx, Map<String, Object> context) {
        Map<String, Object> result;
        String solrIndexName = (String) context.get("indexName");
        try {
            String catalogId = null;
            if (UtilValidate.isNotEmpty(context.get("catalogId"))) {
                catalogId = (String) context.get("catalogId");
            }

            String productCategoryId = (String) context.get("productCategoryId") != null
                    ? CategoryUtil.getCategoryNameWithTrail((String) context.get("productCategoryId"), dctx) : null;
            result = ServiceUtil.returnSuccess();
            Map<String, List<Map<String, Object>>> catLevel = new HashMap<>();
            Debug.logInfo("productCategoryId: " + productCategoryId, MODULE);

            //Add toplevel categories
            String[] trailElements = productCategoryId.split("/");

            //iterate over actual results
            for (String elements : trailElements) {
                //catIds must be greater than 3 chars
                if (elements.length() > 3) {
                    Debug.logInfo("elements: " + elements, MODULE);
                    String categoryPath = CategoryUtil.getCategoryNameWithTrail(elements, dctx);
                    String[] categoryPathArray = categoryPath.split("/");
                    int level = Integer.parseInt(categoryPathArray[0]);
                    String facetQuery = CategoryUtil.getFacetFilterForCategory(categoryPath, dctx);
                    Map<String, Object> query = SolrUtil.categoriesAvailable(catalogId, categoryPath, null, facetQuery, false, 0, 0, solrIndexName);
                    QueryResponse cat = (QueryResponse) query.get("rows");
                    List<Map<String, Object>> categories = new ArrayList<>();

                    List<FacetField> catList = cat.getFacetFields();
                    for (Iterator<FacetField> catIterator = catList.iterator(); catIterator.hasNext();) {
                        FacetField field = catIterator.next();
                        List<Count> catL = field.getValues();
                        if (catL != null) {
                            for (Iterator<Count> catIter = catL.iterator(); catIter.hasNext();) {
                                FacetField.Count f = catIter.next();
                                if (f.getCount() > 0) {
                                    Map<String, Object> catMap = new HashMap<>();
                                    LinkedList<String> iName = new LinkedList<>();
                                    iName.addAll(Arrays.asList(f.getName().split("/")));
                                    catMap.put("catId", iName.getLast());
                                    iName.removeFirst();
                                    String path = f.getName();
                                    catMap.put("path", path);
                                    if (level > 0) {
                                        iName.removeLast();
                                        catMap.put("parentCategory", StringUtils.join(iName, "/"));
                                    } else {
                                        catMap.put("parentCategory", null);
                                    }
                                    catMap.put("count", Long.toString(f.getCount()));
                                    categories.add(catMap);
                                }
                            }
                        }
                    }
                    catLevel.put("menu-" + level, categories);
                }
            }
            result.put("categories", catLevel);
            result.put("numFound", (long) 0);

        } catch (Exception e) {
            result = ServiceUtil.returnError(e.toString());
            result.put("numFound", (long) 0);
        }
        return result;
    }

    /**
     * Rebuilds the solr index.
     */
    public static Map<String, Object> rebuildSolrIndex(DispatchContext dctx, Map<String, Object> context) throws GenericEntityException {
        HttpSolrClient client = null;
        Map<String, Object> result;
        GenericDelegator delegator = (GenericDelegator) dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Locale locale = (Locale) context.get("locale");
        String solrIndexName = (String) context.get("indexName");

        Boolean treatConnectErrorNonFatal = (Boolean) context.get("treatConnectErrorNonFatal");

        try {
            SolrUtil.getInstance();
            client = SolrUtil.getHttpSolrClient(solrIndexName);

            // now lets fetch all products
            List<Map<String, Object>> solrDocs = new ArrayList<>();
            List<GenericValue> products = delegator.findList("Product", null, null, null, null, true);
            int numDocs = 0;
            if (products != null) {
                numDocs = products.size();
            }

            Debug.logInfo("Solr: Clearing solr index and rebuilding with " + numDocs + " found products", MODULE);

            Iterator<GenericValue> productIterator = products.iterator();
            while (productIterator.hasNext()) {
                GenericValue product = productIterator.next();
                Map<String, Object> dispatchContext = ProductUtil.getProductContent(product, dctx, context);
                solrDocs.add(dispatchContext);
            }

            // this removes everything from the index
            client.deleteByQuery("*:*");
            client.commit();

            // THis adds all products to the Index (instantly)
            Map<String, Object> runResult = dispatcher.runSync("addListToSolrIndex",
                    UtilMisc.toMap("fieldList", solrDocs, "userLogin", userLogin, "locale", locale, "indexName",
                    solrIndexName, "treatConnectErrorNonFatal", treatConnectErrorNonFatal));
            if (ServiceUtil.isError(runResult)) {
                return ServiceUtil.returnError(ServiceUtil.getErrorMessage(runResult));
            }
            String runMsg = ServiceUtil.getErrorMessage(runResult);
            if (UtilValidate.isEmpty(runMsg)) {
                runMsg = null;
            }
            if (ServiceUtil.isError(runResult)) {
                result = ServiceUtil.returnError(runMsg);
            } else if (ServiceUtil.isFailure(runResult)) {
                result = ServiceUtil.returnFailure(runMsg);
            } else {
                final String statusMsg = UtilProperties.getMessage(RESOURCE, "SolrClearedSolrIndexAndReindexedDocuments",
                        UtilMisc.toMap("numDocs", numDocs), locale);
                result = ServiceUtil.returnSuccess(statusMsg);
            }
        } catch (IOException | GenericServiceException e) {
            Debug.logError(e, e.getMessage(), MODULE);
            result = ServiceUtil.returnError(e.toString());
        } catch (SolrServerException e) {
            if (e.getCause() != null && e.getCause() instanceof ConnectException) {
                final String statusStr = UtilProperties.getMessage(RESOURCE, "SolrFailureConnectingToSolrServerToRebuildIndex", locale);
                if (Boolean.TRUE.equals(treatConnectErrorNonFatal)) {
                    Debug.logWarning(e, "Solr: " + statusStr, MODULE);
                    result = ServiceUtil.returnFailure(statusStr);
                } else {
                    Debug.logError(e, "Solr: " + statusStr, MODULE);
                    result = ServiceUtil.returnError(statusStr);
                }
            } else {
                Debug.logError(e, e.getMessage(), MODULE);
                result = ServiceUtil.returnError(e.toString());
            }
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (IOException e) {
                    Debug.logError(e, e.getMessage(), MODULE);
                }
            }
        }
        return result;
    }
}
