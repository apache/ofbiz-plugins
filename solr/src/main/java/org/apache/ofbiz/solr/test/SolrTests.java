
/*
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
 */

package org.apache.ofbiz.solr.test;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.ofbiz.service.testtools.OFBizTestCase;

public class SolrTests extends OFBizTestCase {

    private Map<String, Object> context;
    private Map<String, Object> response;
    private String validTestProductId = "GZ-1006";
    private String validTestProductId2 = "GZ-1005";
    private String invalidTestProductId = validTestProductId + validTestProductId;

    public SolrTests(String name) {
        super(name);
    }

    @Override
    protected void tearDown() throws Exception {
    }

    /**
     * Test add product to index.
     * @throws Exception the exception
     */
    public void testAddProductToIndex() throws Exception {

        GenericValue product = EntityQuery.use(delegator).from("Product").where("productId", validTestProductId).queryOne();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("instance", product);

        Map<String, Object> resp = getDispatcher().runSync("addToSolr", ctx);
        if (ServiceUtil.isError(resp)) {
            String errorMessage = ServiceUtil.getErrorMessage(resp);
            throw new Exception(errorMessage);
        }
        assertTrue("Could not init search index", ServiceUtil.isSuccess(resp));

        Map<String, Object> sctx = new HashMap<>();
        sctx.put("productCategoryId", "102");

        Map<String, Object> sresp = getDispatcher().runSync("solrProductsSearch", sctx);
        if (ServiceUtil.isError(sresp)) {
            String errorMessage = ServiceUtil.getErrorMessage(sresp);
            throw new Exception(errorMessage);
        }
        assertTrue("Could not query search index", ServiceUtil.isSuccess(sresp));


    }

    /**
     * Test add to solr index.
     * @throws Exception the exception
     */
    public void testAddToSolrIndex() throws Exception {
        context = new HashMap<>();
        context.put("productId", validTestProductId);
        response = getDispatcher().runSync("addToSolrIndex", context);
        if (ServiceUtil.isError(response)) {
            String errorMessage = ServiceUtil.getErrorMessage(response);
            throw new Exception(errorMessage);
        }
        assertTrue("Could not add Product to Index", ServiceUtil.isSuccess(
                response));
    }

    /**
     * Test add to solr index invalid product.
     * @throws Exception the exception
     */
    public void testAddToSolrIndexInvalidProduct() throws Exception {
        context = new HashMap<>();
        context.put("productId", invalidTestProductId);
        response = getDispatcher().runSync("addToSolrIndex", context);
        if (ServiceUtil.isError(response)) {
            String errorMessage = ServiceUtil.getErrorMessage(response);
            throw new Exception(errorMessage);
        }
        assertTrue("Could not test the addition of an invalid product to the Solr index", ServiceUtil.isSuccess(
                response));
    }

    /**
     * Test add list to solr index.
     * @throws Exception the exception
     */
    public void testAddListToSolrIndex() throws Exception {
        List<Map<String, Object>> products = new ArrayList<>();
        Map<String, Object> product1 = new HashMap<>();
        Map<String, Object> product2 = new HashMap<>();
        GenericValue validTestProduct = EntityQuery.use(delegator).from("Product").where("productId", validTestProductId).queryOne();
        GenericValue validTestProduct2 = EntityQuery.use(delegator).from("Product").where("productId", validTestProductId2).queryOne();

        product1.put("productId", validTestProduct);
        product2.put("productId", validTestProduct2);

        products.add(product1);
        products.add(product2);
        context = new HashMap<>();
        context.put("fieldList", products);

        response = getDispatcher().runSync("addListToSolrIndex", context);
        if (ServiceUtil.isError(response)) {
            String errorMessage = ServiceUtil.getErrorMessage(response);
            throw new Exception(errorMessage);
        }
        assertTrue("Could not add products to index", ServiceUtil.isSuccess(response));

    }

    /**
     * Test add list to solr index invalid products.
     * @throws Exception the exception
     */
    public void testAddListToSolrIndexInvalidProducts() throws Exception {
        List<Map<String, Object>> products = new ArrayList<>();
        Map<String, Object> product1 = new HashMap<>();
        Map<String, Object> product2 = new HashMap<>();
        GenericValue testProduct = EntityQuery.use(delegator).from("Product").where("productId", validTestProductId).queryOne();
        GenericValue testProduct2 = EntityQuery.use(delegator).from("Product").where("productId", validTestProductId2).queryOne();

        testProduct.replace("productId", invalidTestProductId);
        testProduct.replace("productId", invalidTestProductId);

        product1.put("productId", testProduct);
        product2.put("productId", testProduct2);

        products.add(product1);
        products.add(product2);
        context = new HashMap<>();
        context.put("fieldList", products);

        response = getDispatcher().runSync("addListToSolrIndex", context);
        if (ServiceUtil.isError(response)) {
            String errorMessage = ServiceUtil.getErrorMessage(response);
            throw new Exception(errorMessage);
        }
        assertTrue("Could not test adding invalid products to index", ServiceUtil.isSuccess(response));

    }
}

