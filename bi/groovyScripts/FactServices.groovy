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

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilNumber
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.base.util.UtilValidate
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.order.order.OrderReadHelper
import org.apache.ofbiz.service.ServiceUtil


def loadSalesInvoiceFact() {
    GenericValue invoice = from("Invoice").where(parameters).queryOne()
    if (!invoice) {
        String errorMessage = UtilProperties.getMessage("AccountingUiLabels", "AccountingInvoiceDoesNotExists", parameters.locale)
        logError(errorMessage)
        return error(errorMessage)
    }
    if ("SALES_INVOICE".equals(invoice.invoiceTypeId)) {
        Map andConditions = ["invoiceItemTypeId": "INV_FPROD_ITEM"]
        List invoiceItems = delegator.getRelated("InvoiceItem", null, null, invoice, false)
        for (GenericValue invoiceItem : invoiceItems) {
            Map inMap = [invoice: invoice, invoiceItem: invoiceItem]
            run service: "loadSalesInvoiceItemFact", with: inMap
        }
    }
    return success()
}

def loadSalesInvoiceItemFact() {
    GenericValue invoice = parameters.invoice
    GenericValue invoiceItem = parameters.invoiceItem
    if (!invoice) {
        invoice = from("Invoice").where(parameters).queryOne()
    }
    if (UtilValidate.isEmpty(invoiceItem)) {
        invoiceItem = from("InvoiceItem").where(parameters).queryOne()
    }
    if (!invoice) {
        String errorMessage = UtilProperties.getMessage("AccountingUiLabels", "AccountingInvoiceDoesNotExists", parameters.locale)
        logError(errorMessage)
        return error(errorMessage)
    }
    if (!invoiceItem) {
        String errorMessage = UtilProperties.getMessage("AccountingUiLabels", "AccountingInvoiceItemDoesNotExists", parameters.locale)
        logError(errorMessage)
        return error(errorMessage)
    }

    if ("SALES_INVOICE".equals(invoice.invoiceTypeId)) {
        GenericValue fact = from("SalesInvoiceItemFact").where(invoiceId: invoiceItem.invoiceId, invoiceItemSeqId: invoiceItem.invoiceItemSeqId).queryOne()
        // key handling
        if (!fact) {
            Map inMap
            Map naturalKeyFields
            Map serviceResult
            String dimensionId
            fact = makeValue("SalesInvoiceItemFact")
            fact.invoiceId = invoice.invoiceId
            fact.invoiceItemSeqId = invoiceItem.invoiceItemSeqId
            // conversion of the invoice date
            if (invoice.invoiceDate) {
                inMap = [:]
                naturalKeyFields = [:]
                inMap.dimensionEntityName = "DateDimension"
                Date invoiceDate = new Date(invoice.invoiceDate.getTime())
                naturalKeyFields.dateValue = invoiceDate
                inMap.naturalKeyFields = naturalKeyFields
                serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
                fact.invoiceDateDimId = serviceResult.dimensionId
                if (!fact.invoiceDateDimId) {
                    fact.invoiceDateDimId = "_NF_"
                }
            } else {
                fact.invoiceDateDimId = "_NA_"
            }

            // conversion of the product id
            if (invoiceItem.productId) {
                inMap = [:]
                naturalKeyFields = [:]
                inMap.dimensionEntityName = "ProductDimension"
                naturalKeyFields.productId = invoiceItem.productId
                inMap.naturalKeyFields = naturalKeyFields
                serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
                fact.productDimId = serviceResult.dimensionId
                if (!act.productDimId) {
                    fact.productDimId = "_NF_"
                }
            } else {
                fact.productDimId = "_NA_"
            }

            // conversion of the invoice currency
            if (invoice.currencyUomId) {
                inMap = [:]
                naturalKeyFields = [:]
                inMap.dimensionEntityName = "CurrencyDimension"
                naturalKeyFields.currencyId = invoice.currencyUomId
                inMap.naturalKeyFields = naturalKeyFields
                serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
                fact.origCurrencyDimId = serviceResult.dimensionId
                if (!fact.origCurrencyDimId) {
                    fact.origCurrencyDimId = "_NF_"
                }
            } else {
                fact.origCurrencyDimId = "_NA_"
            }

            // TODO
            fact.orderId = "_NA_"
            fact.billToCustomerDimId = "_NA_"
            fact.create()
        }
        /*
         * facts handling
         */
        fact.quantity = (BigDecimal) invoiceItem.quantity
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
    }
    return success()
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

    List orderAdjustments
    GenericValue orderStatus


    if (!orderHeader) {
        orderHeader = from("OrderHeader").where(parameters).queryOne()
    }
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

    if ("ORDER_APPROVED".equals(orderHeader.statusId)) {
        GenericValue fact = from("SalesOrderItemFact").where(orderId: orderItem.orderId, orderItemSeqId: orderItem.orderItemSeqId).queryOne()
        // key handling
        if (!fact) {
            fact = makeValue("SalesOrderItemFact")
            fact.orderId = orderHeader.orderId
            fact.orderItemSeqId = orderItem.orderItemSeqId
            fact.productStoreId = orderHeader.productStoreId
            fact.salesChannelEnumId = orderHeader.salesChannelEnumId
            fact.statusId = orderItem.statusId

            // account
            if (orderHeader.productStoreId) {
                GenericValue account =  from("ProductStore").where(productStoreId: orderHeader.productStoreId).queryOne()
                fact.account = account.storeName
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
                inMap = [:]
                naturalKeyFields = [:]
                inMap.dimensionEntityName = "DateDimension"
                Date statusDatetime = new Date(orderStatus.statusDatetime.getTime())
                naturalKeyFields.dateValue = statusDatetime
                inMap.naturalKeyFields = naturalKeyFields
                serviceResult = run service: "getDimensionIdFromNaturalKey", with: inMap
                fact.orderDateDimId = serviceResult.dimensionId
                if (!fact.orderDateDimId) {
                    fact.orderDateDimId = "_NF_"
                }
            } else {
                fact.orderDateDimId = "_NA_"
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
                    fact.productDimId = "_NF_"
                }
            } else {
                fact.productDimId = "_NA_"
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
            fact.billToCustomerDimId = "_NA_"

            fact.create()
        }
        /*
         * facts handling
         */
        Map partyAccountingPreferencesCallMap = [:]

        OrderReadHelper orderReadHelper = new OrderReadHelper(orderHeader)
        Map billFromParty = orderReadHelper.getBillFromParty()
        partyAccountingPreferencesCallMap.organizationPartyId = billFromParty.partyId
        Map accountResult = run service:"getPartyAccountingPreferences", with: partyAccountingPreferencesCallMap
        GenericValue accPref = accountResult.partyAccountingPreference

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
 * Import Sales Order Data
 */
def importSalesOrderData() {
    Map inMap = [fromDate: parameters.fromDate, thruDate: parameters.thruDate]

    Map res = run service:"loadDateDimension", with: inMap
    if (!ServiceUtil.isSuccess(res)) {
        return res
    }
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
            Integer factDateDimensionId = Integer.parseInt(dateDimensionIdFormat.format(createdStampDatetime));
            fact.inventoryDateDimId = factDateDimensionId
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
            fact.facilityDimId = "_NF_"
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
            fact.organisationDimId = "_NF_"
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
                fact.productDimId = "_NF_"
            }
        } else {
            fact.productDimId = "_NA_"
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
