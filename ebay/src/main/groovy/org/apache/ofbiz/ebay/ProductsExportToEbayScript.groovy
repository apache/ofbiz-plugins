/*
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
*/
package org.apache.ofbiz.ebay

import org.apache.ofbiz.entity.util.EntityUtil

webSiteList = []
webSite = null
if (parameters.productStoreId) {
    productStoreId = parameters.productStoreId
    webSiteList = from('WebSite').where(productStoreId: productStoreId).queryList()
    if (parameters.webSiteId) {
        webSite = from('WebSite').where(webSiteId: parameters.webSiteId).cache().queryOne()
        context.selectedWebSiteId = parameters.webSiteId
    } else if (webSiteList) {
        webSite = EntityUtil.getFirst(webSiteList)
        context.selectedWebSiteId = webSite.webSiteId
    }
    context.productStoreId = productStoreId
    context.webSiteList = webSiteList
    context.countryCode = parameters.country ?: 'US'
    if (webSite) {
        eBayConfig = from('EbayConfig').where(productStoreId: productStoreId).queryOne()
        context.customXml = eBayConfig.customXml
        context.webSiteUrl = webSite.standardContentPrefix
        categoryCode = parameters.categoryCode

        if (productStoreId) {
            results = run service: 'getEbayCategories', with:
                    [categoryCode: categoryCode,
                     productStoreId: productStoreId]
        }
        if (results.categories) {
            context.categories = results.categories
        }
        context.hideExportOptions = categoryCode && categoryCode.substring(0, 1) != 'Y'
                ? 'Y'
                : 'N'
    }
}
