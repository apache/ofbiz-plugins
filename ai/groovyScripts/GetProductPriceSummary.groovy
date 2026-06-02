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

def getProductPriceSummary() {
    String productId = parameters.productId
    if (!productId) return error("productId is required")

    GenericValue product = from("Product").where("productId", productId).queryOne()
    if (!product) return error("Product not found: ${productId}")

    List prices = from("ProductPrice")
            .where("productId", productId,
                   "productPricePurposeId", "PURCHASE",
                   "currencyUomId", "USD",
                   "productStoreGroupId", "_NA_")
            .filterByDate()
            .queryList()

    Map byType = [:]
    for (GenericValue p : prices) {
        byType[p.productPriceTypeId] = p.getBigDecimal("price")
    }

    Map result = success()
    result.productName      = product.getString("productName") ?: product.getString("internalName") ?: productId
    result.defaultPrice     = byType["DEFAULT_PRICE"]
    result.listPrice        = byType["LIST_PRICE"]
    result.averageCost      = byType["AVERAGE_COST"]
    result.competitivePrice = byType["COMPETITIVE_PRICE"]
    result.activePromoPrice = byType["SPECIAL_PROMO_PRICE"]
    result.currencyUomId    = "USD"
    return result
}
