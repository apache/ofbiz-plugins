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

import java.sql.Timestamp

import java.text.SimpleDateFormat
import java.sql.Date

import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilNumber
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.base.util.UtilValidate
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.util.EntityListIterator
import org.apache.ofbiz.order.order.OrderReadHelper
import org.apache.ofbiz.service.ModelService
import org.apache.ofbiz.service.ServiceUtil

/**
* Get Invoice data for DWH-SalesInvoiceItemFact
*/
def getDwhInvoiceData() {
    Map inMap
    serviceDef = "loadDwhInvoiceData"
    
    EntityCondition condition = EntityCondition.makeCondition(
        EntityCondition.makeCondition("statusId", "INVOICE_READY"),
        EntityCondition.makeCondition("statusDate", EntityOperator.GREATER_THAN_EQUAL_TO, parameters.fromDate),
        EntityCondition.makeCondition("statusDate", EntityOperator.LESS_THAN, parameters.thruDate)
    )
    List invoiceStatusList = from("InvoiceStatus").where(condition).queryList()
    if (invoiceStatusList) {
        for (GenericValue invoice : invoiceStatusList) {
            parameters.invoiceId = invoice.invoiceId
            inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
            serviceResult = run service: serviceDef, with: inMap
            if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
            inMap.clear()
        }
    }
}

def loadDwhInvoiceData(){
    Map inMap
    GenericValue invoice = from("Invoice").where(parameters).queryOne()
    if (!invoice) {
        String errorMessage = UtilProperties.getMessage("AccountingUiLabels", "AccountingInvoiceDoesNotExists", parameters.locale)
        logError(errorMessage)
        return error(errorMessage)
    }
    serviceDef = "updateInvoiceFact"
    inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
    serviceResult = run service: serviceDef, with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    inMap.clear()
}

def loadSalesOrderFact() {
    GenericValue orderHeader = from("OrderHeader").where(parameters).queryOne()
    if (!orderHeader) {
        String errorMessage = UtilProperties.getMessage("OrderErrorUiLabels", "OrderOrderIdDoesNotExists", parameters.locale)
        logError(errorMessage)
        return error(errorMessage)
    }
    if ("SALES_ORDER".equals(orderHeader.orderTypeId)) {
        if ("ORDER_APPROVED".equals(orderHeader.statusId)) {
            List orderItems = from("OrderItem")
                .where("orderId", orderHeader.orderId, "orderItemTypeId", "PRODUCT_ORDER_ITEM")
                .queryList()

            for (GenericValue orderItem : orderItems) {
                Map inMap = [:]
                inMap.orderHeader = orderHeader
                inMap.orderItem = orderItem
                inMap.orderAdjustment = null
                run service: "loadSalesOrderItemFact", with: inMap
            }
        }
    }
    return success()
}

def loadSalesOrderItemFact() {
    Map inMap
    Map naturalKeyFields
    Map serviceResult
    GenericValue orderHeader = parameters.orderHeader
    GenericValue orderItem = parameters.orderItem
    GenericValue orderAdjustment = parameters.orderAdjustment
    SimpleDateFormat dateDimensionIdFormat = new SimpleDateFormat("yyyyMMdd")

    List orderAdjustments
    GenericValue orderStatus

    if (!orderHeader) {
        orderHeader = from("OrderHeader").where(parameters).queryOne()
    }
    orderId = orderHeader.orderId
    if (!orderItem) {
        orderItem = from("OrderItem").where(parameters).queryOne()
    }
    if (!orderAdjustment) {
        orderAdjustments = from("OrderAdjustment").where("orderId": orderItem.orderId).queryList()
    }
    if (!orderHeader) {
        String errorMessage = UtilProperties.getMessage("OrderErrorUiLabels", "OrderOrderIdDoesNotExists", parameters.locale)
        logError(errorMessage)
        return error(errorMessage)
    }
    if (!orderItem) {
        String errorMessage = UtilProperties.getMessage("OrderErrorUiLabels", "OrderOrderItemIdDoesNotExists", parameters.locale)
        logError(errorMessage)
        return error(errorMessage)
    }
    Map partyAccountingPreferencesCallMap = [:]
    OrderReadHelper orderReadHelper = new OrderReadHelper(orderHeader)
    Map billFromParty = orderReadHelper.getBillFromParty()
    billFromVendor = billFromParty.partyId
    Map billToParty = orderReadHelper.getBillToParty()
    billToCustomer = billToParty.partyId
    partyAccountingPreferencesCallMap.organizationPartyId = billFromVendor
    Map accountResult = run service:"getPartyAccountingPreferences", with: partyAccountingPreferencesCallMap
    GenericValue accPref = accountResult.partyAccountingPreference

    if ("ORDER_APPROVED".equals(orderHeader.statusId)) {
        GenericValue fact = from("SalesOrderItemFact").where(orderId: orderId, orderItemSeqId: orderItem.orderItemSeqId).queryOne()
        // key handling
        if (!fact) {
            fact = makeValue("SalesOrderItemFact")
            fact.orderId = orderId
            fact.orderItemSeqId = orderItem.orderItemSeqId
            fact.statusId = orderItem.statusId
            
            // Convert billFromVendor
            if(billFromVendor) {
                naturalKeyFields = [:]
                naturalKeyFields.partyId = billFromVendor
                inMap = [:]
                inMap.dimensionEntityName = "OrganisationDimension"
                inMap.naturalKeyFields = naturalKeyFields
                serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
                fact.organisationDimId = serviceResult.dimensionId
                if (!fact.organisationDimId) {
                    fact.organisationDimId = "0"
                }
            } else {
                fact.organisationDimId = "1"
            }
            // Convert billToCustomer
            if(billToCustomer) {
                naturalKeyFields = [:]
                naturalKeyFields.partyId = billToCustomer
                inMap = [:]
                inMap.dimensionEntityName = "CustomerDimension"
                inMap.naturalKeyFields = naturalKeyFields
                serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
                fact.customerDimId = serviceResult.dimensionId
                if (!fact.customerDimId) {
                    fact.customerDimId = "0"
                }
            } else {
                fact.customerDimId = "1"
            }
            // Convert storeId
            if (orderHeader.productStoreId) {
                naturalKeyFields = [:]
                naturalKeyFields.productStoreId = orderHeader.productStoreId
                inMap = [:]
                inMap.dimensionEntityName = "StoreDimension"
                inMap.naturalKeyFields = naturalKeyFields
                serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
                fact.storeDimId = serviceResult.dimensionId
                if (!fact.storeDimId) {
                    fact.storeDimId = "0"
                }
            } else {
                fact.storeDimId = "1"
            }
            // Convert salesChannelId
            if (orderHeader.salesChannelEnumId) {
                naturalKeyFields = [:]
                naturalKeyFields.salesChannelId = orderHeader.salesChannelEnumId
                inMap = [:]
                inMap.dimensionEntityName = "SalesChannelDimension"
                inMap.naturalKeyFields = naturalKeyFields
                serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
                fact.salesChannelDimId = serviceResult.dimensionId
                if (!fact.salesChannelDimId) {
                    fact.salesChannelDimId = "0"
                }
            } else {
                fact.salesChannelDimId = "1"
            }
            // pod
            if ("EUR".equals(orderHeader.currencyUom)) {
                fact.pod = "Latin"
            } else {
                fact.pod = "Engish"
            }

            // brand
            if (orderHeader.salesChannelEnumId) {
                GenericValue brand = from("Enumeration").where(enumId: orderHeader.salesChannelEnumId).queryOne()
                fact.brand = brand.description
            }

            // conversion of the order date
            orderStatus = from("OrderStatus")
                .where(orderId: orderHeader.orderId, statusId: "ORDER_APPROVED")
                .orderBy("-statusDatetime")
                .queryFirst()
            if (orderStatus.statusDatetime) {
                Date statusDatetime = new Date(orderStatus.statusDatetime.getTime())
                fact.orderDateDimId = Integer.parseInt(dateDimensionIdFormat.format(statusDatetime));
            }

            // conversion of the product id
            if (UtilValidate.isNotEmpty(orderItem.productId)) {
                inMap = [:]
                naturalKeyFields = [:]
                inMap.dimensionEntityName = "ProductDimension"
                naturalKeyFields.productId = orderItem.productId
                inMap.naturalKeyFields = naturalKeyFields
                serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
                fact.productDimId = serviceResult.dimensionId
                if (!fact.productDimId) {
                    fact.productDimId = "0"
                }
            } else {
                fact.productDimId = "1"
            }
            // conversion of the order currency
            if (orderHeader.currencyUom) {
                inMap = [:]
                naturalKeyFields = [:]
                inMap.dimensionEntityName = "CurrencyDimension"
                naturalKeyFields.currencyId = orderHeader.currencyUom
                inMap.naturalKeyFields = naturalKeyFields
                serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
                fact.origCurrencyDimId = serviceResult.dimensionId
                if (!fact.origCurrencyDimId) {
                    fact.origCurrencyDimId = "_NF_"
                }
            } else {
                fact.origCurrencyDimId = "_NA_"
            }

            // productCategoryId
            GenericValue productCategoryMember = from("ProductCategoryMember").where(productId: orderItem.productId, thruDate: null).queryFirst()
            if (productCategoryMember) {
                fact.productCategoryId = productCategoryMember.productCategoryId
            }

            // TODO
            fact.create()
        }
        /*
         * facts handling
         */

        fact.quantity = (BigDecimal) orderItem.quantity
        fact.extGrossAmount = (BigDecimal) 0
        fact.extGrossCost = (BigDecimal) 0
        fact.extDiscountAmount = (BigDecimal) 0
        fact.extNetAmount = (BigDecimal) 0
        fact.extShippingAmount = (BigDecimal) 0
        fact.extTaxAmount = (BigDecimal) 0

        fact.GS = (BigDecimal) 0
        fact.GMS = (BigDecimal) 0
        fact.GMP = (BigDecimal) 0
        fact.GSS = (BigDecimal) 0
        fact.GSC = (BigDecimal) 0
        fact.GSP = (BigDecimal) 0
        fact.GP = (BigDecimal) 0
        fact.countOrder = (BigDecimal) 0

        // extGrossAmount
        Map convertUomCurrencyMap = [:]
        convertUomCurrencyMap.uomId = orderHeader.currencyUom
        convertUomCurrencyMap.uomIdTo = accPref.baseCurrencyUomId
        if (UtilValidate.isNotEmpty(orderStatus)) {
        convertUomCurrencyMap.nowDate = orderStatus.statusDatetime
        }
        Map convertResult = run service: "convertUomCurrency", with: convertUomCurrencyMap
        BigDecimal exchangeRate = convertResult.conversionFactor

        if (exchangeRate) {
            BigDecimal unitPrice = orderItem.unitPrice * exchangeRate
            fact.extGrossAmount = fact.quantity * unitPrice
        }

        // extGrossCost
        GenericValue cost = from("SupplierProduct")
            .where(productId: orderItem.productId, availableThruDate: null, minimumOrderQuantity: (BigDecimal) 0)
            .queryFirst()
        if (cost) {
            convertUomCurrencyMap.uomId = cost.currencyUomId
            convertUomCurrencyMap.uomIdTo = accPref.baseCurrencyUomId
            if (orderStatus) {
                convertUomCurrencyMap.nowDate = orderStatus.statusDatetime
            }
            Map grossCostResult = run service: "convertUomCurrency", with: convertUomCurrencyMap
            exchangeRate = grossCostResult.conversionFactor

            if (exchangeRate) {
                BigDecimal costPrice = cost.lastPrice * exchangeRate
                fact.extGrossCost = fact.quantity * costPrice
            }
        }

        // extShippingAmount
        for (GenericValue shipping : orderAdjustments) {
            if ("SHIPPING_CHARGES".equals(shipping.orderAdjustmentTypeId)) {
                fact.extShippingAmount = fact.extShippingAmount + shipping.amount
            }
        }

        // extTaxAmount
        for (GenericValue tax : orderAdjustments) {
            if ("SALES_TAX".equals(tax.orderAdjustmentTypeId)) {
                fact.extTaxAmount = fact.extTaxAmount + tax.amount
            }
        }

        // extDiscountAmount
        for (GenericValue discount : orderAdjustments) {
            if ("PROMOTION_ADJUSTMENT".equals(discount.orderAdjustmentTypeId)) {
                fact.extDiscountAmount = fact.extDiscountAmount + discount.amount
                // product promo code
                GenericValue productPromoCode = from("ProductPromoCode").where(productPromoId: discount.productPromoId).queryFirst()
                if (productPromoCode) {
                    fact.productPromoCode = productPromoCode.productPromoCodeId
                } else {
                    fact.productPromoCode = "Not require code"
                }
            }
        }

        // extNetAmount
        fact.extNetAmount = fact.extGrossAmount - fact.extDiscountAmount

        // GS
        BigDecimal countGS = (BigDecimal) 0
        List checkGSList = from("SalesOrderItemFact").where(orderId: orderHeader.orderId).queryList()
        for (GenericValue checkGS : checkGSList) {
            if (checkGS.GS) {
                if (0 != checkGS.GS) {
                    countGS = 1
                }
            }
        }
        if (countGS == 0) {
            convertUomCurrencyMap.uomId = orderHeader.currencyUom
            convertUomCurrencyMap.uomIdTo = accPref.baseCurrencyUomId
            if (orderStatus) {
                convertUomCurrencyMap.nowDate = orderStatus.statusDatetime
            }
            Map GSResult = run service: "convertUomCurrency", with: convertUomCurrencyMap
            exchangeRate = GSResult.conversionFactor

            if (exchangeRate) {
                fact.GS = orderHeader.grandTotal * exchangeRate
            }
        }

        // GMS
        fact.GMS = fact.GMS + fact.extGrossAmount

        // GMP
        fact.GMP = fact.GMS - fact.extGrossCost

        // GSP
        BigDecimal countGSP = (BigDecimal) 0
        List checkGSPList = from("SalesOrderItemFact").where(orderId: orderHeader.orderId).queryList()
        for (GenericValue checkGSP : checkGSPList) {
            if (checkGSP.GSP) {
                if (checkGSP.GSP != 0) {
                    countGSP = 1
                }
            }
        }
        if (countGSP == 0) {
            List orderItemList = from("OrderItem").where(orderId: orderHeader.orderId).queryList()

            BigDecimal warrantyPrice = (BigDecimal) 0
            for (GenericValue warranty : orderAdjustments) {
                if ("WARRANTY_ADJUSTMENT".equals(warranty.orderAdjustmentTypeId)) {
                    warrantyPrice = warrantyPrice + warranty.amount
                }
            }
            BigDecimal GSS = fact.extShippingAmount + warrantyPrice

            convertUomCurrencyMap.uomId = orderHeader.currencyUom
            convertUomCurrencyMap.uomIdTo = accPref.baseCurrencyUomId
            if (orderStatus) {
                convertUomCurrencyMap.nowDate = orderStatus.statusDatetime
            }
            Map GSPResult = run service: "convertUomCurrency", with: convertUomCurrencyMap
            exchangeRate = GSPResult.conversionFactor

            if (exchangeRate) {
                GSS = GSS * exchangeRate
            }
            fact.GSS = GSS
            fact.GSP = (BigDecimal) GSS
        }

        // GP
        fact.GP = fact.GMP + fact.GSP

        // countOrder
        BigDecimal countOrder = (BigDecimal) 0
        List checkCountOrderList = from("SalesOrderItemFact").where(orderId: orderHeader.orderId).queryList()
        for (GenericValue checkCountOrder : checkCountOrderList) {
            if (checkCountOrder.countOrder) {
                if (checkCountOrder.countOrder != 0) {
                    countOrder = 1
                }
            }
        }
        if (countOrder == 0) {
            fact.countOrder = (BigDecimal) 1
        }
        fact.store()
    }
    return success()
}

/**
 * Load Sales Order Data Daily
 */
def loadSalesOrderDataDaily() {
    Timestamp nowTimestamp = UtilDateTime.nowTimestamp()
    Date nowDate = new Date(nowTimestamp.getTime())
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd 07:00:00.000")
    def today = sdf.format(nowDate)
    Date yesterdayDate = new Date(nowTimestamp.getTime()-86400000)
    def yesterday = sdf.format(yesterdayDate)

    Map inMap = [:]
    inMap.fromDate = yesterday
    inMap.thruDate = today

    run service: "importSalesOrderData", with: inMap
    return success()
}

/**
 * Get SalesOrder data for DWH-SalesOrderItemFact
 */
def getDwhOrderData() {
    Map inMap = [fromDate: parameters.fromDate, thruDate: parameters.thruDate]
    Debug.logInfo("in getDwhOrderData - parameters = " + parameters, "FactServices.groovy")
    serviceDef = "loadDwhOrderData"
    EntityCondition condition = EntityCondition.makeCondition(
        EntityCondition.makeCondition("statusId", "ORDER_APPROVED"),
        EntityCondition.makeCondition("statusDatetime", EntityOperator.GREATER_THAN_EQUAL_TO, parameters.fromDate),
        EntityCondition.makeCondition("statusDatetime", EntityOperator.LESS_THAN, parameters.thruDate)
        )
    List orderStatusList = from("OrderStatus").where(condition).queryList()
    if (orderStatusList) {
        for (GenericValue order : orderStatusList) {
            Debug.logInfo("in getDwhOrderData - orderId = " + order.orderId, "FactServices.groovy")
            parameters.orderId = order.orderId
            inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
            serviceResult = run service: serviceDef, with: inMap
            if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
                inMap.clear()
            }
        }
    return success()
}

def loadDwhOrderData(){
    Map inMap
    GenericValue order = from("OrderHeader").where(parameters).queryOne()
    if (!order) {
        String errorMessage = UtilProperties.getMessage("AccountingUiLabels", "AccountingInvoiceDoesNotExists", parameters.locale)
        logError(errorMessage)
        return error(errorMessage)
    }
    switch (order.orderTypeId) {
    case "PURCHASE_ORDER":
        serviceDef = "loadOrderFact"
        inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
        serviceResult = run service: serviceDef, with: inMap
        if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
            inMap.clear()
        break
    case "SALES_ORDER":
        serviceDef = "loadOrderFact"
        inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
        serviceResult = run service: serviceDef, with: inMap
        if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
            inMap.clear()
        break
    }
}

/**
 * Import Sales Order Data
 */
def importSalesOrderData() {
    Map inMap = [fromDate: parameters.fromDate, thruDate: parameters.thruDate]
    EntityCondition condition = EntityCondition.makeCondition(
        EntityCondition.makeCondition("statusId", "ORDER_APPROVED"),
        EntityCondition.makeCondition("statusDatetime", EntityOperator.GREATER_THAN_EQUAL_TO, parameters.fromDate),
        EntityCondition.makeCondition("statusDatetime", EntityOperator.LESS_THAN, parameters.thruDate)
        )
    List orderStatusList = from("OrderStatus").where(condition).queryList()
    for (GenericValue orderHeader : orderStatusList) {
        inMap =[:]
        inMap.orderId = orderHeader.orderId
        res = run service:"loadSalesOrderFact", with: inMap
        if (!ServiceUtil.isSuccess(res)) {
            return res
        }
    }
    return success()
}

/**
 * Convert Uom Currency from UomConversionDated entity
 */
def convertUomCurrency() {
    if (!parameters.nowDate) {
        Timestamp now = UtilDateTime.nowTimestamp()
        parameters.nowDate = now
    }
    Map result = success()
    EntityCondition condition = EntityCondition.makeCondition(
        EntityCondition.makeCondition("uomId", parameters.uomId),
        EntityCondition.makeCondition("uomIdTo", parameters.uomIdTo),
        EntityCondition.makeCondition("fromDate", EntityOperator.LESS_THAN_EQUAL_TO, parameters.nowDate),
        EntityCondition.makeCondition("thruDate", EntityOperator.GREATER_THAN, parameters.nowDate)
        )
    GenericValue UomConversion = from("UomConversionDated").where(condition).orderBy("-fromDate").queryFirst()
    if (UomConversion) {
        result.conversionFactor = UomConversion.conversionFactor
    } else {
        GenericValue UomConversionLastest = from("UomConversionDated")
            .where(uomId: parameters.uomId, uomIdTo: parameters.uomIdTo, thruDate: null)
            .queryFirst()
        if (UomConversionLastest) {
            result.conversionFactor = UomConversionLastest.conversionFactor
        }
    }
    return result
}

def loadInventoryItemFact() {
    GenericValue inventory = from("InventoryItem").where(inventoryItemId: parameters.inventoryItemId).queryOne()
    GenericValue fact = from("InventoryItemFact").where(inventoryItemId: parameters.inventoryItemId).queryOne()

    Map inMap = [:]
    Map naturalKeyFields = [:]
    Map serviceResult
    SimpleDateFormat dateDimensionIdFormat = new SimpleDateFormat("yyyyMMdd")
    if (!fact) {
        fact = makeValue("InventoryItemFact")
        fact.inventoryItemId = inventory.inventoryItemId
        // conversion of the inventory date
        if (inventory?.createdStamp) {
            Date createdStampDatetime = new Date(inventory.createdStamp.getTime())
            fact.inventoryDateDimId = Integer.parseInt(dateDimensionIdFormat.format(createdStampDatetime));
        }
        // conversion of the facilityId
        naturalKeyFields = [:]
        naturalKeyFields.facilityId = inventory.facilityId
        inMap = [:]
        inMap.dimensionEntityName = "FacilityDimension"
        inMap.naturalKeyFields = naturalKeyFields
        serviceResult = run service:"getDimensionIdFromNaturalKey", with: inMap
        if(serviceResult) {
            fact.facilityDimId = serviceResult.dimensionId
        } else {
            fact.facilityDimId = "0"
        }
        // conversion of the organisationId
        naturalKeyFields = [:]
        naturalKeyFields.partyId = inventory.ownerPartyId
        inMap = [:]
        inMap.dimensionEntityName = "OrganisationDimension"
        inMap.naturalKeyFields = naturalKeyFields
        serviceResult = run service:"getDimensionIdFromNaturalKey", with: inMap
        if(serviceResult) {
            fact.organisationDimId = serviceResult.dimensionId
        } else {
            fact.organisationDimId = "0"
        }
        // conversion of the productId
        if (inventory?.productId) {
            naturalKeyFields = [:]
            naturalKeyFields.productId = inventory.productId
            inMap = [:]
            inMap.dimensionEntityName = "ProductDimension"
            inMap.naturalKeyFields = naturalKeyFields
            serviceResult = run service:"getDimensionIdFromNaturalKey", with: inMap
            fact.productDimId = serviceResult.dimensionId
            if (!fact.productDimId) {
                fact.productDimId = "0"
            }
        } else {
            fact.productDimId = "1"
        }
        // conversion of the order currency
        if (inventory?.currencyUomId) {
            inMap =[:]
            naturalKeyFields = [:]
            inMap.dimensionEntityName = "CurrencyDimension"
            naturalKeyFields.currencyId = inventory.currencyUomId
            inMap.naturalKeyFields = naturalKeyFields
            serviceResult = run service:"getDimensionIdFromNaturalKey", with: inMap
            fact.origCurrencyDimId = serviceResult.dimensionId
            if (!fact.origCurrencyDimId) {
                fact.origCurrencyDimId = "_NF_"
            }
        } else {
            fact.origCurrencyDimId = "_NA_"
        }
        fact.create()
    }

    fact.inventoryItemId = inventory.inventoryItemId
    fact.quantityOnHandTotal = inventory.quantityOnHandTotal
    fact.availableToPromiseTotal = inventory.availableToPromiseTotal
    fact.unitCost = inventory.unitCost

    // calculate sold out amount
    fact.soldoutAmount = inventory.quantityOnHandTotal - inventory.availableToPromiseTotal

    fact.store()
    return success()
}

def updateOfbizFact() {
    Map inMap
    switch(parameters.factEntityName) {
    case "InventoryItemFact":
        serviceDef = "loadInventoryItemFact"
        inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
        sourceEntity = "InventoryItem"
        entryExprs = EntityCondition.makeCondition( [
            EntityCondition.makeCondition("inventoryItemTypeId", EntityOperator.EQUALS, "NON_SERIAL_INV_ITEM"),
            EntityCondition.makeCondition("lastUpdatedStamp", EntityOperator.GREATER_THAN_EQUAL_TO, parameters.fromDate),
            EntityCondition.makeCondition("lastUpdatedStamp", EntityOperator.LESS_THAN_EQUAL_TO, parameters.thruDate)
        ], EntityOperator.AND)
        queryListIterator = from(sourceEntity).where(entryExprs).queryIterator()
        while(sourceRecord = queryListIterator.next()) {
            inMap.inventoryItemId = sourceRecord.inventoryItemId
            serviceResult = run service: serviceDef, with: inMap
            if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
        }
        queryListIterator.close()
        inMap.clear()
        break
    case "PurchaseInvoiceItemFact":
        serviceDef = "getDwhInvoiceData"
        inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
        Debug.logInfo("in updateOfbizFact " + parameters.factEntityName + " - inMap = " + inMap, "FactServices.groovy")
        serviceResult = run service: serviceDef, with: inMap
        if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
        inMap.clear()
        break
    case "PurchaseOrderItemFact":
        serviceDef = "getDwhOrderData"
        inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
        Debug.logInfo("in updateOfbizFact " + parameters.factEntityName + " - inMap = " + inMap, "FactServices.groovy")
        serviceResult = run service: serviceDef, with: inMap
        if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
        inMap.clear()
        break
    case "SalesInvoiceItemFact":
        serviceDef = "getDwhInvoiceData"
        inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
        Debug.logInfo("in updateOfbizFact " + parameters.factEntityName + " - inMap = " + inMap, "FactServices.groovy")
        serviceResult = run service: serviceDef, with: inMap
        if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
        inMap.clear()
        break
    case "SalesOrderItemFact":
        serviceDef = "getDwhOrderData"
        inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
        Debug.logInfo("in updateOfbizFact " + parameters.factEntityName + " - inMap = " + inMap, "FactServices.groovy")
        serviceResult = run service: serviceDef, with: inMap
        if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
        inMap.clear()
        break
    }
}

def updateInvoiceFact() {
    //Debug.logInfo("in updateInvoiceFact","FactServices")
    GenericValue invoice = from("Invoice").where(parameters).queryOne()
    if (!invoice) {
        String errorMessage = UtilProperties.getMessage("AccountingUiLabels", "AccountingInvoiceDoesNotExists", parameters.locale)
        logError(errorMessage)
        return error(errorMessage)
    }

    //Debug.logInfo("in updateInvoiceFact - invoiceTypeId = " + invoice.invoiceTypeId,"FactServices")
    //Debug.logInfo("in updateInvoiceFact - invoiceId = " + invoice.invoiceId,"FactServices")
    Map andConditions = ["invoiceItemTypeId": "INV_FPROD_ITEM"]
    List invoiceItems = delegator.getRelated("InvoiceItem", null, null, invoice, false)
    for (GenericValue invoiceItem : invoiceItems) {
        //Debug.logInfo("in updateInvoiceFact - invoiceItemSeqId = " + invoiceItem.invoiceItemSeqId,"FactServices")
        Map inMap = [invoice: invoice, invoiceItem: invoiceItem]
        //Debug.logInfo("in updateInvoiceFact - calling loadInvoiceItemFact","FactServices")
        run service: "loadInvoiceItemFact", with: inMap
        //if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    }
    //Debug.logInfo("in updateInvoiceFact - Exiting","FactServices")
    return success()
}

def loadInvoiceItemFact() {
    //Debug.logInfo("in loadInvoiceItemFact","FactServices")
    GenericValue invoice = parameters.invoice
    GenericValue invoiceItem = parameters.invoiceItem
    SimpleDateFormat dateDimensionIdFormat = new SimpleDateFormat("yyyyMMdd")
    if (!invoice) {
        invoice = from("Invoice").where(parameters).queryOne()
    }
    if (!invoice) {
        String errorMessage = UtilProperties.getMessage("AccountingUiLabels", "AccountingInvoiceDoesNotExists", parameters.locale)
        logError(errorMessage)
        return error(errorMessage)
    }
    invoiceTypeId = invoice.invoiceTypeId
    //Debug.logInfo("in loadInvoiceItemFact - invoiceTypeId = " + invoiceTypeId,"FactServices")
    //Debug.logInfo("in loadInvoiceItemFact - invoiceId = " + invoice.invoiceId,"FactServices")
    if (UtilValidate.isEmpty(invoiceItem)) {
        invoiceItem = from("InvoiceItem").where(parameters).queryOne()
    }
    if (!invoiceItem) {
        String errorMessage = UtilProperties.getMessage("AccountingUiLabels", "AccountingInvoiceItemDoesNotExists", parameters.locale)
        logError(errorMessage)
        return error(errorMessage)
    }
    switch(invoiceTypeId) {
        case "PURCHASE_INVOICE":
            factEntity = "PurchaseInvoiceItemFact"
            fromPartyDimension = "SupplierDimension"
            toPartyDimension = "OrganisationDimension"
            break
        case "SALES_INVOICE":
            factEntity = "SalesInvoiceItemFact"
            fromPartyDimension = "OrganisationDimension"
            toPartyDimension = "CustomerDimension"
            break
    }
    GenericValue fact = from(factEntity).where(invoiceId: invoiceItem.invoiceId, invoiceItemSeqId: invoiceItem.invoiceItemSeqId).queryOne()
    if(!fact) {
        Map inMap
        Map naturalKeyFields
        Map serviceResult
        String dimensionId
        fact = makeValue(factEntity)
        fact.invoiceId = invoice.invoiceId
        fact.invoiceItemSeqId = invoiceItem.invoiceItemSeqId
        invoiceDate = invoice.invoiceDate
        // conversion of the invoice date
        if (invoiceDate) {
            Date invoiceDateValue = new Date(invoiceDate.getTime())
            fact.invoiceDateDimId = Integer.parseInt(dateDimensionIdFormat.format(invoiceDateValue));
        }
        // conversion of the invoice currency
        if (invoice.currencyUomId) {
            fact.origCurrencyDimId = invoice.currencyUomId
        } else {
            fact.origCurrencyDimId = "_NA_"
        }
        // conversion of the product id
        if (invoiceItem.productId) {
            naturalKeyFields = [:]
            naturalKeyFields.productId = invoiceItem.productId
            inMap = [:]
            inMap.dimensionEntityName = "ProductDimension"
            inMap.naturalKeyFields = naturalKeyFields
            serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
            fact.productDimId = serviceResult.dimensionId
            if (!fact.productDimId) {
                fact.productDimId = "0"
            }
        } else {
            fact.productDimId = "1"
        }
        
        switch(invoiceTypeId) {
            case "PURCHASE_INVOICE":
                // conversion of the internal organisation id
                if (invoice.partyId) {
                    naturalKeyFields = [:]
                    naturalKeyFields.partyId = invoice.partyId
                    inMap = [:]
                    inMap.dimensionEntityName = toPartyDimension
                    inMap.naturalKeyFields = naturalKeyFields
                    serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
                    fact.organisationDimId = serviceResult.dimensionId
                    if (!fact.organisationDimId) {
                        fact.organisationDimId = "0"
                    }
                } else {
                    fact.organisationDimId = "1"
                }
                // conversion of the supplier id
                if (invoice.partyIdFrom) {
                    naturalKeyFields = [:]
                    naturalKeyFields.partyId = invoice.partyIdFrom
                    inMap = [:]
                    inMap.dimensionEntityName = fromPartyDimension
                    inMap.naturalKeyFields = naturalKeyFields
                    serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
                    fact.supplierDimId = serviceResult.dimensionId
                    if (!fact.supplierDimId) {
                        fact.supplierDimId = "0"
                    }
                } else {
                    fact.supplierDimId = "1"
                }
                break
            case "SALES_INVOICE":
                // conversion of the internal organisation id
                if (invoice.partyIdFrom) {
                    naturalKeyFields = [:]
                    naturalKeyFields.partyId = invoice.partyIdFrom
                    inMap = [:]
                    inMap.dimensionEntityName = fromPartyDimension
                    inMap.naturalKeyFields = naturalKeyFields
                    serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
                    fact.organisationDimId = serviceResult.dimensionId
                    if (!fact.organisationDimId) {
                        fact.organisationDimId = "0"
                    }
                } else {
                    fact.organisationDimId = "1"
                }
                // conversion of the customer id
                if (invoice.partyId) {
                    naturalKeyFields = [:]
                    naturalKeyFields.partyId = invoice.partyId
                    inMap = [:]
                    inMap.dimensionEntityName = toPartyDimension
                    inMap.naturalKeyFields = naturalKeyFields
                    serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
                    fact.customerDimId = serviceResult.dimensionId
                    if (!fact.customerDimId) {
                        fact.customerDimId = "0"
                    }
                } else {
                    fact.customerDimId = "1"
                }
                break
        }
        fact.orderId = "_NA_"
        fact.create()
    }/*
     * facts handling
     */
    fact.quantity = (BigDecimal) invoiceItem.quantity
    fact.amount = (BigDecimal) invoiceItem.amount
    fact.extGrossAmount = (BigDecimal) 0
    fact.extDiscountAmount = (BigDecimal) 0
    fact.extTaxAmount = (BigDecimal) 0
    fact.extNetAmount = (BigDecimal) 0

    if (invoiceItem.quantity && invoiceItem.amount) {
        fact.extGrossAmount = invoiceItem.quantity * invoiceItem.amount
    }

    Map andConditions
    // taxes
    andConditions = [invoiceItemTypeId: "ITM_SALES_TAX"]
    List taxes = delegator.getRelated("ChildrenInvoiceItem", null, null, invoiceItem, false)
    for (GenericValue tax : taxes) {
        if (tax.amount) {
        fact.extTaxAmount = fact.extTaxAmount + tax.amount
        }
    }
    // discounts
    andConditions = [invoiceItemTypeId: "ITM_PROMOTION_ADJ"]
    List discounts = delegator.getRelated("ChildrenInvoiceItem", null, null, invoiceItem, false)
    for (GenericValue discount : discounts) {
        if (discount.amount) {
        fact.extDiscountAmount = fact.extDiscountAmount - discount.amount
        }
    }
    fact.extNetAmount = fact.extGrossAmount - fact.extDiscountAmount
    // TODO: prorate invoice header discounts and shipping charges
    // TODO: costs
    fact.extManFixedCost = (BigDecimal) 0
    fact.extManVarCost = (BigDecimal) 0
    fact.extStorageCost = (BigDecimal) 0
    fact.extDistributionCost = (BigDecimal) 0

    BigDecimal costs = fact.extManFixedCost + fact.extManVarCost + fact.extStorageCost + fact.extDistributionCost
    fact.contributionAmount = fact.extNetAmount - costs

    fact.store()
    //Debug.logInfo("in loadInvoiceItemFact - factEntity = " + factEntity,"FactServices")
    //Debug.logInfo("in loadInvoiceItemFact - fact = " + fact,"FactServices")
    //return success()
    //Debug.logInfo("in loadInvoiceItemFact - exiting = " + fact,"FactServices")
}

def loadOrderFact() {
    Debug.logInfo("in loadOrderFact ","FactServices")
    GenericValue orderHeader = from("OrderHeader").where(parameters).queryOne()
    if (!orderHeader) {
        String errorMessage = UtilProperties.getMessage("OrderErrorUiLabels", "OrderOrderIdDoesNotExists", parameters.locale)
        logError(errorMessage)
        return error(errorMessage)
    }
    Debug.logInfo("in loadOrderFact - orderId = " + orderHeader.orderId,"FactServices")
    List orderItems = from("OrderItem")
    	.where("orderId", orderHeader.orderId, "orderItemTypeId", "PRODUCT_ORDER_ITEM")
    	.queryList()
    for (GenericValue orderItem : orderItems) {
    	Map inMap = [:]
    	inMap.orderHeader = orderHeader
    	inMap.orderItem = orderItem
    	inMap.orderAdjustment = null
    	run service: "loadOrderItemFact", with: inMap
    }
    Debug.logInfo("in loadOrderFact - exiting","FactServices")
    return success()
}

def loadOrderItemFact() {
    Debug.logInfo("in loadOrderItemFact","FactServices")
    Map inMap
    Map naturalKeyFields
    Map serviceResult
    GenericValue fact
    GenericValue orderHeader = parameters.orderHeader
    GenericValue orderItem = parameters.orderItem
    GenericValue orderAdjustment = parameters.orderAdjustment
    SimpleDateFormat dateDimensionIdFormat = new SimpleDateFormat("yyyyMMdd")

    List orderAdjustments
    GenericValue orderStatus

    if (!orderHeader) {
        orderHeader = from("OrderHeader").where(parameters).queryOne()
    }
    if (!orderHeader) {
        String errorMessage = UtilProperties.getMessage("OrderErrorUiLabels", "OrderOrderIdDoesNotExists", parameters.locale)
        logError(errorMessage)
        return error(errorMessage)
    }
    orderId = orderHeader.orderId
    orderTypeId = orderHeader.orderTypeId
    if (!orderItem) {
        orderItem = from("OrderItem").where(parameters).queryOne()
    }
    if (!orderItem) {
        String errorMessage = UtilProperties.getMessage("OrderErrorUiLabels", "OrderOrderItemIdDoesNotExists", parameters.locale)
        logError(errorMessage)
        return error(errorMessage)
    }
    if (!orderAdjustment) {
        orderAdjustments = from("OrderAdjustment").where("orderId": orderItem.orderId).queryList()
    }

    Debug.logInfo("in loadOrderItemFact - order = " + orderId,"FactServices")
    Debug.logInfo("in loadOrderItemFact - order type = " + orderTypeId,"FactServices")
    Map partyAccountingPreferencesCallMap = [:]
    OrderReadHelper orderReadHelper = new OrderReadHelper(orderHeader)
    Map billFromParty = orderReadHelper.getBillFromParty()
    billFromVendor = billFromParty.partyId
    Debug.logInfo("in LoadOrderItemFact - billFromParty = " + billFromVendor, "FactServices")
    Map billToParty = orderReadHelper.getBillToParty()
    billToCustomer = billToParty.partyId
    Debug.logInfo("in LoadOrderItemFact - billToPartyId = " + billToCustomer, "FactServices")
    switch(orderTypeId) {
    		case "PURCHASE_ORDER":
    			factEntity = "PurchaseOrderItemFact"
    			fromPartyDimension = "SupplierDimension"
    			toPartyDimension = "OrganisationDimension"
    			partyAccountingPreferencesCallMap.organizationPartyId = billToCustomer
    			break
    		case "SALES_ORDER":
    			factEntity = "SalesOrderItemFact"
    			fromPartyDimension = "OrganisationDimension"
    			toPartyDimension = "CustomerDimension"
    			partyAccountingPreferencesCallMap.organizationPartyId = billFromVendor
    			break
    }
    
    Map accountResult = run service:"getPartyAccountingPreferences", with: partyAccountingPreferencesCallMap
    GenericValue accPref = accountResult.partyAccountingPreference
    
    //if ("ORDER_APPROVED".equals(orderHeader.statusId)) {
        fact = from(factEntity).where(orderId: orderId, orderItemSeqId: orderItem.orderItemSeqId).queryOne()
        // key handling
        if (!fact) {
            fact = makeValue(factEntity)
            fact.orderId = orderId
            fact.orderItemSeqId = orderItem.orderItemSeqId
            fact.statusId = orderItem.statusId
            switch(orderTypeId) {
            	case "PURCHASE_ORDER":
            		// Convert billFromVendor
            	    if(billFromVendor) {
            	    	naturalKeyFields = [:]
            	    	naturalKeyFields.partyId = billFromVendor
                        inMap = [:]
                        inMap.dimensionEntityName = fromPartyDimension
                        inMap.naturalKeyFields = naturalKeyFields
                        serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
                        fact.supplierDimId = serviceResult.dimensionId
                        if (!fact.supplierDimId) {
                            fact.supplierDimId = "0"
                        }
                    } else {
                        fact.supplierDimId = "1"
                    }
            		// Convert billToCustomer
            	    if(billFromVendor) {
            	    	naturalKeyFields = [:]
            	    	naturalKeyFields.partyId = billToCustomer
                        inMap = [:]
                        inMap.dimensionEntityName = toPartyDimension
                        inMap.naturalKeyFields = naturalKeyFields
                        serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
                        fact.organisationDimId = serviceResult.dimensionId
                        if (!fact.organisationDimId) {
                            fact.organisationDimId = "0"
                        }
                    } else {
                        fact.organisationDimId = "1"
                    }
            		break
            	case "SALES_ORDER":
            		// Convert billFromVendor
            	    if(billFromVendor) {
            	    	naturalKeyFields = [:]
            	    	naturalKeyFields.partyId = billFromVendor
                        inMap = [:]
                        Debug.logInfo("in LoadOrderItemFact - billFromParty = " + naturalKeyFields.partyId, "FactServices")
                        inMap.dimensionEntityName = fromPartyDimension
                        inMap.naturalKeyFields = naturalKeyFields
                        serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
                        fact.organisationDimId = serviceResult.dimensionId
                        if (!fact.organisationDimId) {
                            fact.organisationDimId = "0"
                        }
                    } else {
                        fact.organisationDimId = "1"
                    }
            	    // Convert billToCustomer
                    if(billToCustomer) {
                        naturalKeyFields = [:]
                        naturalKeyFields.partyId = billToCustomer
                        inMap = [:]
                        Debug.logInfo("in LoadOrderItemFact - billToParty = " + naturalKeyFields.partyId, "FactServices")
                        
                        inMap.dimensionEntityName = toPartyDimension
                        inMap.naturalKeyFields = naturalKeyFields
                        serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
                        fact.customerDimId = serviceResult.dimensionId
                        if (!fact.customerDimId) {
                            fact.customerDimId = "0"
                        }
                    } else {
                        fact.customerDimId = "1"
                    }
            		break
            }
            // Convert storeId
            if (orderHeader.productStoreId) {
                naturalKeyFields = [:]
                naturalKeyFields.productStoreId = orderHeader.productStoreId
                inMap = [:]
                inMap.dimensionEntityName = "StoreDimension"
                inMap.naturalKeyFields = naturalKeyFields
                serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
                fact.storeDimId = serviceResult.dimensionId
                if (!fact.storeDimId) {
                    fact.storeDimId = "0"
                }
            } else {
                fact.storeDimId = "1"
            }
            // Convert salesChannelId
            if (orderHeader.salesChannelEnumId) {
                naturalKeyFields = [:]
                naturalKeyFields.salesChannelId = orderHeader.salesChannelEnumId
                inMap = [:]
                inMap.dimensionEntityName = "SalesChannelDimension"
                inMap.naturalKeyFields = naturalKeyFields
                serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
                fact.salesChannelDimId = serviceResult.dimensionId
                if (!fact.salesChannelDimId) {
                    fact.salesChannelDimId = "0"
                }
            } else {
                fact.salesChannelDimId = "1"
            }
            // pod
            if ("EUR".equals(orderHeader.currencyUom)) {
                fact.pod = "Latin"
            } else {
                fact.pod = "Engish"
            }

            // brand
            if (orderHeader.salesChannelEnumId) {
                GenericValue brand = from("Enumeration").where(enumId: orderHeader.salesChannelEnumId).queryOne()
                fact.brand = brand.description
            }

            // conversion of the order date
            orderStatus = from("OrderStatus")
                .where(orderId: orderHeader.orderId, statusId: "ORDER_APPROVED")
                .orderBy("-statusDatetime")
                .queryFirst()
            if (orderStatus.statusDatetime) {
                Date statusDatetime = new Date(orderStatus.statusDatetime.getTime())
                fact.orderDateDimId = Integer.parseInt(dateDimensionIdFormat.format(statusDatetime));
            }

            // conversion of the product id
            if (UtilValidate.isNotEmpty(orderItem.productId)) {
                inMap = [:]
                naturalKeyFields = [:]
                inMap.dimensionEntityName = "ProductDimension"
                naturalKeyFields.productId = orderItem.productId
                inMap.naturalKeyFields = naturalKeyFields
                serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
                fact.productDimId = serviceResult.dimensionId
                if (!fact.productDimId) {
                    fact.productDimId = "0"
                }
            } else {
                fact.productDimId = "1"
            }
            // conversion of the order currency
            if (orderHeader.currencyUom) {
                inMap = [:]
                naturalKeyFields = [:]
                inMap.dimensionEntityName = "CurrencyDimension"
                naturalKeyFields.currencyId = orderHeader.currencyUom
                inMap.naturalKeyFields = naturalKeyFields
                serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
                fact.origCurrencyDimId = serviceResult.dimensionId
                if (!fact.origCurrencyDimId) {
                    fact.origCurrencyDimId = "_NF_"
                }
            } else {
                fact.origCurrencyDimId = "_NA_"
            }

            // productCategoryId
            GenericValue productCategoryMember = from("ProductCategoryMember").where(productId: orderItem.productId, thruDate: null).queryFirst()
            if (productCategoryMember) {
                fact.productCategoryId = productCategoryMember.productCategoryId
            }

            // TODO
            fact.create()
        }
        /*
         * facts handling
         */

        fact.quantity = (BigDecimal) orderItem.quantity
        fact.extGrossAmount = (BigDecimal) 0
        fact.extGrossCost = (BigDecimal) 0
        fact.extDiscountAmount = (BigDecimal) 0
        fact.extNetAmount = (BigDecimal) 0
        fact.extShippingAmount = (BigDecimal) 0
        fact.extTaxAmount = (BigDecimal) 0

        fact.GS = (BigDecimal) 0
        fact.GMS = (BigDecimal) 0
        fact.GMP = (BigDecimal) 0
        fact.GSS = (BigDecimal) 0
        fact.GSC = (BigDecimal) 0
        fact.GSP = (BigDecimal) 0
        fact.GP = (BigDecimal) 0
        fact.countOrder = (BigDecimal) 0

        // extGrossAmount
        Map convertUomCurrencyMap = [:]
        convertUomCurrencyMap.uomId = orderHeader.currencyUom
        convertUomCurrencyMap.uomIdTo = accPref.baseCurrencyUomId
        if (UtilValidate.isNotEmpty(orderStatus)) {
        convertUomCurrencyMap.nowDate = orderStatus.statusDatetime
        }
        Map convertResult = run service: "convertUomCurrency", with: convertUomCurrencyMap
        BigDecimal exchangeRate = convertResult.conversionFactor

        if (exchangeRate) {
            BigDecimal unitPrice = orderItem.unitPrice * exchangeRate
            fact.extGrossAmount = fact.quantity * unitPrice
        }

        // extGrossCost
        GenericValue cost = from("SupplierProduct")
            .where(productId: orderItem.productId, availableThruDate: null, minimumOrderQuantity: (BigDecimal) 0)
            .queryFirst()
        if (cost) {
            convertUomCurrencyMap.uomId = cost.currencyUomId
            convertUomCurrencyMap.uomIdTo = accPref.baseCurrencyUomId
            if (orderStatus) {
                convertUomCurrencyMap.nowDate = orderStatus.statusDatetime
            }
            Map grossCostResult = run service: "convertUomCurrency", with: convertUomCurrencyMap
            exchangeRate = grossCostResult.conversionFactor

            if (exchangeRate) {
                BigDecimal costPrice = cost.lastPrice * exchangeRate
                fact.extGrossCost = fact.quantity * costPrice
            }
        }

        // extShippingAmount
        for (GenericValue shipping : orderAdjustments) {
            if ("SHIPPING_CHARGES".equals(shipping.orderAdjustmentTypeId)) {
                fact.extShippingAmount = fact.extShippingAmount + shipping.amount
            }
        }

        // extTaxAmount
        for (GenericValue tax : orderAdjustments) {
            if ("SALES_TAX".equals(tax.orderAdjustmentTypeId)) {
                fact.extTaxAmount = fact.extTaxAmount + tax.amount
            }
        }

        // extDiscountAmount
        for (GenericValue discount : orderAdjustments) {
            if ("PROMOTION_ADJUSTMENT".equals(discount.orderAdjustmentTypeId)) {
                fact.extDiscountAmount = fact.extDiscountAmount + discount.amount
                // product promo code
                GenericValue productPromoCode = from("ProductPromoCode").where(productPromoId: discount.productPromoId).queryFirst()
                if (productPromoCode) {
                    fact.productPromoCode = productPromoCode.productPromoCodeId
                } else {
                    fact.productPromoCode = "Not require code"
                }
            }
        }

        // extNetAmount
        fact.extNetAmount = fact.extGrossAmount - fact.extDiscountAmount

        // GS
        BigDecimal countGS = (BigDecimal) 0
        List checkGSList = from("SalesOrderItemFact").where(orderId: orderHeader.orderId).queryList()
        for (GenericValue checkGS : checkGSList) {
            if (checkGS.GS) {
                if (0 != checkGS.GS) {
                    countGS = 1
                }
            }
        }
        if (countGS == 0) {
            convertUomCurrencyMap.uomId = orderHeader.currencyUom
            convertUomCurrencyMap.uomIdTo = accPref.baseCurrencyUomId
            if (orderStatus) {
                convertUomCurrencyMap.nowDate = orderStatus.statusDatetime
            }
            Map GSResult = run service: "convertUomCurrency", with: convertUomCurrencyMap
            exchangeRate = GSResult.conversionFactor

            if (exchangeRate) {
                fact.GS = orderHeader.grandTotal * exchangeRate
            }
        }

        // GMS
        fact.GMS = fact.GMS + fact.extGrossAmount

        // GMP
        fact.GMP = fact.GMS - fact.extGrossCost

        // GSP
        BigDecimal countGSP = (BigDecimal) 0
        List checkGSPList = from("SalesOrderItemFact").where(orderId: orderHeader.orderId).queryList()
        for (GenericValue checkGSP : checkGSPList) {
            if (checkGSP.GSP) {
                if (checkGSP.GSP != 0) {
                    countGSP = 1
                }
            }
        }
        if (countGSP == 0) {
            List orderItemList = from("OrderItem").where(orderId: orderHeader.orderId).queryList()

            BigDecimal warrantyPrice = (BigDecimal) 0
            for (GenericValue warranty : orderAdjustments) {
                if ("WARRANTY_ADJUSTMENT".equals(warranty.orderAdjustmentTypeId)) {
                    warrantyPrice = warrantyPrice + warranty.amount
                }
            }
            BigDecimal GSS = fact.extShippingAmount + warrantyPrice

            convertUomCurrencyMap.uomId = orderHeader.currencyUom
            convertUomCurrencyMap.uomIdTo = accPref.baseCurrencyUomId
            if (orderStatus) {
                convertUomCurrencyMap.nowDate = orderStatus.statusDatetime
            }
            Map GSPResult = run service: "convertUomCurrency", with: convertUomCurrencyMap
            exchangeRate = GSPResult.conversionFactor

            if (exchangeRate) {
                GSS = GSS * exchangeRate
            }
            fact.GSS = GSS
            fact.GSP = (BigDecimal) GSS
        }

        // GP
        fact.GP = fact.GMP + fact.GSP

        // countOrder
        BigDecimal countOrder = (BigDecimal) 0
        List checkCountOrderList = from("SalesOrderItemFact").where(orderId: orderHeader.orderId).queryList()
        for (GenericValue checkCountOrder : checkCountOrderList) {
            if (checkCountOrder.countOrder) {
                if (checkCountOrder.countOrder != 0) {
                    countOrder = 1
                }
            }
        }
        if (countOrder == 0) {
            fact.countOrder = (BigDecimal) 1
        }
        fact.store()
    //}
    Debug.logInfo("in loadOrderItemFact - factEntity = " + factEntity,"FactServices")
    Debug.logInfo("in loadOrderItemFact - fact = " + fact,"FactServices")
    //return success()
    Debug.logInfo("in loadOrderItemFact - exiting = " + fact,"FactServices")
    return success()
}

