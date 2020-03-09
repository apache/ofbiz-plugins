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
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityListIterator
import org.apache.ofbiz.service.ModelService
import org.apache.ofbiz.service.ServiceUtil

def quickInitDataWarehouse() {
    Map inMap = dispatcher.getDispatchContext().makeValidContext("loadDateDimension", ModelService.IN_PARAM, parameters)
    serviceResult = run service: "loadDateDimension", with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    inMap.clear()
    serviceResult = run service: "loadCurrencyDimension", with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    // loads all products in the ProductDimension
    serviceResult = run service: "loadAllProductsInProductDimension", with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    // loads the invoice items in the SalesInvoiceItemFact fact entity
    entryExprs = EntityCondition.makeCondition([
            EntityCondition.makeCondition("invoiceTypeId", EntityOperator.EQUALS, "SALES_INVOICE"),
            EntityCondition.makeCondition("invoiceDate", EntityOperator.GREATER_THAN_EQUAL_TO, parameters.fromDate),
            EntityCondition.makeCondition("invoiceDate", EntityOperator.LESS_THAN_EQUAL_TO, parameters.thruDate)
    ], EntityOperator.AND)
    EntityListIterator listIterator = from("Invoice").where(entryExprs).queryIterator()
    GenericValue iterator
    while (iterator = listIterator.next()) {
        inMap.invoiceId = iterator.invoiceId
        serviceResult = run service: "loadSalesInvoiceFact", with: inMap
        if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    }

    // loads the order items in the SalesOrderItemFact fact entity
    entryExprs = EntityCondition.makeCondition([
            EntityCondition.makeCondition("orderTypeId", EntityOperator.EQUALS, "SALES_ORDER"),
            EntityCondition.makeCondition("orderDate", EntityOperator.GREATER_THAN_EQUAL_TO, parameters.fromDate),
            EntityCondition.makeCondition("orderDate", EntityOperator.LESS_THAN_EQUAL_TO, parameters.thruDate)
    ], EntityOperator.AND)
    listIterator = from("OrderHeader").where(entryExprs).queryIterator()
    inMap.clear()
    while (iterator = listIterator.next()) {
        inMap.orderId = iterator.orderId
        serviceResult = run service: "loadSalesOrderFact", with: inMap
        if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    }

    // loads the inventory items in the InventoryItemFact fact entity
    listIterator = from("InventoryItem").where("inventoryItemTypeId", "NON_SERIAL_INV_ITEM").queryIterator()
    inMap.clear()
    while (iterator = listIterator.next()) {
        inMap.inventoryItemId = iterator.inventoryItemId
        serviceResult = run service: "loadInventoryFact", with: inMap
        if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    }
}

def loadCurrencyDimension() {
    // Initialize the CurrencyDimension using the update strategy of 'type 1
    EntityListIterator listIterator = from("Uom").where("uomTypeId", "CURRENCY_MEASURE").queryIterator()
    GenericValue currency
    while (currency = listIterator.next()) {
        currencyDims = from("CurrencyDimension").where("currencyId", currency.uomId).queryList()
        if (currencyDims) {
            for (GenericValue currencyDim: currencyDims) {
                currencyDim.description = currency.description
                currencyDim.store()
            }
        } else {
            currencyDim = delegator.makeValue("CurrencyDimension")
            currencyDim.currencyId = currency.uomId
            currencyDim.description = currency.description
            currencyDim.create()
        }
    }
}

def prepareProductDimensionData() {
    GenericValue product = from("Product").where("productId", parameters.productId).queryOne()
    if (product == null) {
        return error(UtilProperties.getMessage('ProductUiLabels', 'ProductProductNotFoundWithProduct', locale))
    }
    productDimension = delegator.makeValue("ProductDimension")
    productDimension.setNonPKFields(parameters)
    GenericValue productType = select("description").from("Product").where("productId", parameters.productId).queryOne()
    productDimension.productType = productType.description
    Map result = success()
    result.productDimension = productDimension
    return result
}

def loadProductInProductDimension() {
    Map inMap = dispatcher.getDispatchContext().makeValidContext("prepareProductDimensionData", ModelService.IN_PARAM, parameters)
    serviceResult = run service: "prepareProductDimensionData", with: inMap
    GenericValue productDimension
    if (ServiceUtil.isSuccess(serviceResult)) {
        productDimension  = serviceResult.productDimension
    }
    inMap.clear()
    inMap = dispatcher.getDispatchContext().makeValidContext("storeGenericDimension", ModelService.IN_PARAM, parameters)
    inMap.naturalKeyFields = "productId"
    inMap.dimensionValue = productDimension
    run service: "storeGenericDimension", with: inMap
}

def loadAllProductsInProductDimension() {
    EntityListIterator listIterator = from("Product").queryIterator()
    GenericValue product
    Map inMap
    while (product = listIterator.next()) {
        inMap = dispatcher.getDispatchContext().makeValidContext("loadProductInProductDimension", ModelService.IN_PARAM, parameters)
        inMap.productId = product.productId
        run service: "loadProductInProductDimension", with: inMap
    }
}