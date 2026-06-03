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

import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import java.sql.Timestamp

def getRecentOrderActivity() {
    String productId = parameters.productId
    if (!productId) return error("productId is required")

    int days = parameters.days ? parameters.days as int : 30
    Timestamp fromDate = new Timestamp(System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000))

    List headers = from("OrderHeader")
            .where(EntityCondition.makeCondition([
                EntityCondition.makeCondition("orderTypeId", EntityOperator.EQUALS, "SALES_ORDER"),
                EntityCondition.makeCondition("orderDate", EntityOperator.GREATER_THAN_EQUAL_TO, fromDate),
                EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, "ORDER_CANCELLED")
            ], EntityOperator.AND))
            .select("orderId")
            .queryList()

    if (!headers) {
        return success([orderCount: 0, quantitySold: BigDecimal.ZERO,
                        totalRevenue: BigDecimal.ZERO, periodDays: days])
    }

    List orderIds = headers.collect { it.getString("orderId") }

    List items = from("OrderItem")
            .where(EntityCondition.makeCondition([
                EntityCondition.makeCondition("productId", EntityOperator.EQUALS, productId),
                EntityCondition.makeCondition("orderId", EntityOperator.IN, orderIds)
            ], EntityOperator.AND))
            .select("orderId", "quantity", "unitPrice")
            .queryList()

    BigDecimal qtyTotal = BigDecimal.ZERO
    BigDecimal revTotal = BigDecimal.ZERO
    Set seenOrders = []
    for (def item : items) {
        BigDecimal qty   = item.getBigDecimal("quantity")  ?: BigDecimal.ZERO
        BigDecimal price = item.getBigDecimal("unitPrice") ?: BigDecimal.ZERO
        qtyTotal = qtyTotal.add(qty)
        revTotal = revTotal.add(qty.multiply(price))
        seenOrders.add(item.getString("orderId"))
    }

    return success([orderCount:   seenOrders.size(),
                    quantitySold: qtyTotal,
                    totalRevenue: revTotal,
                    periodDays:   days])
}
