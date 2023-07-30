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

package org.apache.ofbiz.passport.login

import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.product.store.ProductStoreWorker

final String module = "GetThirdPartyLogins.groovy"

adminErrorInfo = context.adminErrorInfo
productStoreId = context.productStoreId
if (!productStoreId) {
    productStore = ProductStoreWorker.getProductStore(request)
    productStoreId = productStore.productStoreId
}

if (!adminErrorInfo || !adminErrorInfo.hasError()) {
    storePassportLoginMethList = null
    // Get lists of passport login methods
    if (productStoreId) {
        storePassportLoginMethList = from("ThirdPartyLogin").where("productStoreId", productStoreId).orderBy("sequenceNum ASC").queryList();
        storePassportLoginMethList = EntityUtil.filterByDate(storePassportLoginMethList)
    }
        
    // Extra data preparation for each login method.
    if (storePassportLoginMethList) {
        storeLoginMethList = []
        for (storeLoginMeth in storePassportLoginMethList) {
            storeLoginMethDetail = from(storeLoginMeth.loginMethTypeId + storeLoginMeth.loginProviderId).where("productStoreId", productStoreId).filterByDate().queryFirst();
            storeLoginMethList.add(storeLoginMethDetail)
        }
        context.storeLoginMethList = storeLoginMethList
    }
}
