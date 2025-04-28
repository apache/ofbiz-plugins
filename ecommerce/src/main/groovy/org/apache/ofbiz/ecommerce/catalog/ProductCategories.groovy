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
package org.apache.ofbiz.ecommerce.catalog

import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.product.catalog.CatalogWorker
import org.apache.ofbiz.product.category.CategoryContentWrapper
import org.apache.ofbiz.product.category.CategoryWorker

/*
 * This script is also referenced by the ecommerce's screens and
 * should not contain order component's specific code.
 */

List fillTree(List rootCat, int catLvl, String parentCategoryId) {
    if (rootCat) {
        rootCat.sort { it.productCategoryId }
        List listTree = []
        for (root in rootCat) {
            preCatChilds = from('ProductCategoryRollup').where('parentProductCategoryId', root.productCategoryId).queryList()
            catChilds = EntityUtil.getRelated('CurrentProductCategory', null, preCatChilds, false)
            List childList = []

            // CatLvl uses for identify the Category level for display different css class
            if (catChilds) {
                if (catLvl == 2) {
                    childList = fillTree(catChilds, catLvl + 1, parentCategoryId.replace('/', '') + '/' + root.productCategoryId)
                } else if (catLvl == 1) {
                    childList = fillTree(catChilds, catLvl + 1, parentCategoryId.replace('/', '') + root.productCategoryId)
                } else {
                    childList = fillTree(catChilds, catLvl + 1, parentCategoryId + '/' + root.productCategoryId)
                }
            }

            productsInCat = from('ProductCategoryAndMember').where('productCategoryId', root.productCategoryId).queryList()

            // Display the category if this category containing products or contain the category that's containing products
            if (productsInCat || childList) {
                Map rootMap = [:]
                category = from('ProductCategory').where('productCategoryId', root.productCategoryId).queryOne()
                categoryContentWrapper = new CategoryContentWrapper(category, request)
                context.title = categoryContentWrapper.get('CATEGORY_NAME', 'html')
                categoryDescription = categoryContentWrapper.get('DESCRIPTION', 'html')

                if (categoryContentWrapper.get('CATEGORY_NAME', 'html').toString()) {
                    rootMap.categoryName = categoryContentWrapper.get('CATEGORY_NAME', 'html')
                } else {
                    rootMap.categoryName = root.categoryName
                }

                if (categoryContentWrapper.get('DESCRIPTION', 'html').toString()) {
                    rootMap.categoryDescription = categoryContentWrapper.get('DESCRIPTION', 'html')
                } else {
                    rootMap.categoryDescription = root.description
                }

                rootMap.productCategoryId = root.productCategoryId
                rootMap.parentCategoryId = parentCategoryId
                rootMap.child = childList

                listTree << rootMap
            }
        }
        return listTree
    }
}

CategoryWorker.getRelatedCategories(request, 'topLevelList',
        CatalogWorker.getCatalogTopCategoryId(request, CatalogWorker.getCurrentCatalogId(request)), true)
curCategoryId = parameters.category_id ?: parameters.CATEGORY_ID ?: ''
request.setAttribute('curCategoryId', curCategoryId)
CategoryWorker.setTrail(request, curCategoryId)

categoryList = request.getAttribute('topLevelList')
if (categoryList) {
    catContentWrappers = [:]
    CategoryWorker.getCategoryContentWrappers(catContentWrappers, categoryList, request)
    context.catContentWrappers = catContentWrappers
    completedTree = fillTree(categoryList, 1, '')
    context.completedTree = completedTree
}
