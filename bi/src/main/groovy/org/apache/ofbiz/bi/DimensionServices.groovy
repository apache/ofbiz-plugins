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
package org.apache.ofbiz.bi

import org.apache.ofbiz.entity.GenericEntity
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityConditionBuilder

Map quickInitDataWarehouse() {
    run service: 'loadDateDimension', with: parameters
    run service: 'loadCurrencyDimension', with: [:]

    // loads all products in the ProductDimension
    run service: 'loadAllProductsInProductDimension', with: [:]

    // loads the invoice items in the SalesInvoiceItemFact fact entity
    EntityCondition entryExprs = new EntityConditionBuilder().AND {
        EQUALS(invoiceTypeId: 'SALES_INVOICE')
        GREATER_THAN_EQUAL_TO(invoiceDate: parameters.fromDate)
        LESS_THAN_EQUAL_TO(invoiceDate: parameters.thruDate)
    }
    from('Invoice').where(entryExprs).queryIterator().each {
        run service: 'loadSalesInvoiceFact', with: it
    }

    // loads the order items in the SalesOrderItemFact fact entity
    entryExprs = new EntityConditionBuilder().AND {
        EQUALS(orderTypeId: 'SALES_ORDER')
        GREATER_THAN_EQUAL_TO(orderDate: parameters.fromDate)
        LESS_THAN_EQUAL_TO(orderDate: parameters.thruDate)
    }
    from('OrderHeader').where(entryExprs).queryIterator().each {
        run service: 'loadSalesOrderFact', with: [orderId: it.orderId]
    }

    // loads the inventory items in the InventoryItemFact fact entity
    from('InventoryItem').where('inventoryItemTypeId', 'NON_SERIAL_INV_ITEM').queryIterator().each {
        run service: 'loadInventoryFact', with: [inventoryItemId: it.inventoryItemId]
    }
    return success()
}

Map loadCurrencyDimension() {
    // Initialize the CurrencyDimension using the update strategy of 'type 1
    from('Uom').where(uomTypeId: 'CURRENCY_MEASURE').queryIterator().each {
        List currencyDims = from('CurrencyDimension').where(currencyId: it.uomId).queryList()
        if (currencyDims) {
            for (GenericValue currencyDim : currencyDims) {
                currencyDim.description = it.description
                currencyDim.store()
            }
        } else {
            makeValue('CurrencyDimension', [dimensionId: it.uomId,
                                            currencyId: it.uomId,
                                            description: it.description])
                    .create()
        }
    }
    return success()
}

Map prepareProductDimensionData() {
    GenericValue product = from('Product').where(parameters).queryOne()
    if (!product) {
        return error(label('ProductUiLabels', 'ProductProductNotFoundWithProduct'))
    }
    GenericValue productDimension = makeValue('ProductDimension')
    productDimension.setNonPKFields(product)
    GenericValue productType = select('description').from('ProductType')
            .where('productTypeId', product.productTypeId).cache().queryOne()
    productDimension.productType = productType.description
    return success(productDimension: productDimension)
}

Map loadProductInProductDimension() {
    Map serviceResult = run service: 'prepareProductDimensionData', with: parameters
    GenericEntity productDimension = serviceResult.productDimension
    run service: 'storeGenericDimension', with: [*: parameters,
                                                 naturalKeyFields: 'productId',
                                                 dimensionValue: productDimension]
    return success()
}

Map loadAllProductsInProductDimension() {
    from('Product').queryIterator().each {
        run service: 'loadProductInProductDimension', with: [*: parameters,
                                                             productId: it.productId]
    }
    return success()
}
