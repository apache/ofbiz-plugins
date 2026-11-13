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

def getProductInventorySummary() {
    String productId = parameters.productId
    if (!productId) return error("productId is required")

    List facilities = from("ProductFacility")
            .where("productId", productId)
            .queryList()

    if (!facilities) {
        return success([totalAtpQuantity:  BigDecimal.ZERO,
                        facilityBreakdown: "No facility records found for ${productId}"])
    }

    BigDecimal total = BigDecimal.ZERO
    List lines = []
    for (def pf : facilities) {
        BigDecimal atp = pf.getBigDecimal("lastInventoryCount") ?: BigDecimal.ZERO
        total = total.add(atp)
        lines << "${pf.facilityId}: ${atp}"
    }

    return success([totalAtpQuantity:  total,
                    facilityBreakdown: lines.join(", ")])
}
