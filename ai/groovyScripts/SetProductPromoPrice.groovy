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

import org.apache.ofbiz.base.util.UtilDateTime
import java.sql.Timestamp

def setProductPromoPrice() {
    String productId      = parameters.productId
    BigDecimal promoPrice = parameters.promoPrice
    int durationDays      = parameters.durationDays ? parameters.durationDays as int : 7

    if (!productId) return error("productId is required")
    if (promoPrice == null || promoPrice <= BigDecimal.ZERO) {
        return error("promoPrice must be a positive value")
    }

    GenericValue product = from("Product").where("productId", productId).queryOne()
    if (!product) return error("Product not found: ${productId}")

    Timestamp fromDate = UtilDateTime.nowTimestamp()
    Timestamp thruDate = new Timestamp(fromDate.time + (durationDays * 24L * 60 * 60 * 1000))
    String loginId = userLogin?.getString("userLoginId") ?: "system"

    GenericValue priceRecord = delegator.makeValue("ProductPrice")
    priceRecord.set("productId",               productId)
    priceRecord.set("productPriceTypeId",      "SPECIAL_PROMO_PRICE")
    priceRecord.set("productPricePurposeId",   "PURCHASE")
    priceRecord.set("currencyUomId",           "USD")
    priceRecord.set("productStoreGroupId",     "_NA_")
    priceRecord.set("fromDate",                fromDate)
    priceRecord.set("thruDate",                thruDate)
    priceRecord.set("price",                   promoPrice)
    priceRecord.set("createdDate",             fromDate)
    priceRecord.set("createdByUserLogin",      loginId)
    priceRecord.set("lastModifiedDate",        fromDate)
    priceRecord.set("lastModifiedByUserLogin", loginId)
    delegator.create(priceRecord)

    return success([confirmedProductId: productId,
                    confirmedPrice:     promoPrice,
                    confirmedFromDate:  fromDate.toString(),
                    confirmedThruDate:  thruDate.toString()])
}
