package org.apache.ofbiz.picking

import org.apache.ofbiz.base.util.UtilMisc
import org.apache.ofbiz.service.ServiceUtil

/**
 * Retrieves a list of orders available to be picked for a given facility.
 * Wraps existing findOrdersToPickMove service.
 */
def getOrdersToPick() {
    Map result = ServiceUtil.returnSuccess()
    
    Map findResult = runService("findOrdersToPickMove", [facilityId: context.facilityId, userLogin: context.userLogin])
    if (ServiceUtil.isError(findResult)) {
        return findResult
    }
    
    // Transform orderHeaders to a simpler list for the PWA
    List orderList = []
    if (findResult.orderHeaders) {
        findResult.orderHeaders.each { orderHeader ->
            orderList << [
                orderId: orderHeader.orderId,
                orderDate: orderHeader.orderDate,
                statusId: orderHeader.statusId,
                grandTotal: orderHeader.grandTotal
            ]
        }
    }
    
    result.orderList = orderList
    return result
}

/**
 * Creates a picklist from a list of orderIds.
 * Wraps existing createPicklistFromOrders service.
 */
def createPicklist() {
    Map serviceResult = runService("createPicklistFromOrders", [
        orderIds: context.orderIds, 
        facilityId: context.facilityId, 
        userLogin: context.userLogin
    ])
    
    if (ServiceUtil.isError(serviceResult)) {
        return serviceResult
    }
    
    return ServiceUtil.returnSuccess(null, [picklistId: serviceResult.picklistId])
}

/**
 * Retrieves details of a specific picklist, including items sorted by location.
 * Wraps existing getPickAndPackReportInfo service.
 */
def getPicklistDetails() {
    Map result = ServiceUtil.returnSuccess()
    
    Map reportResult = runService("getPickAndPackReportInfo", [
        picklistId: context.picklistId, 
        userLogin: context.userLogin
    ])
    
    if (ServiceUtil.isError(reportResult)) {
        return reportResult
    }
    
    // The reportResult contains a complex structure of picklistBins and their items.
    // We will extract and flatten this for the PWA.
    Map picklistDetails = [:]
    picklistDetails.picklistId = context.picklistId
    
    List items = []
    if (reportResult.picklistBinInfoList) {
        reportResult.picklistBinInfoList.each { binInfo ->
            if (binInfo.picklistBinItemInfoList) {
                binInfo.picklistBinItemInfoList.each { itemInfo ->
                    items << [
                        picklistBinId: binInfo.picklistBin.picklistBinId,
                        orderId: itemInfo.orderHeader.orderId,
                        orderItemSeqId: itemInfo.orderItem.orderItemSeqId,
                        productId: itemInfo.product.productId,
                        productName: itemInfo.product.internalName,
                        quantity: itemInfo.picklistItem.quantity,
                        locationSeqId: itemInfo.inventoryItem?.locationSeqId,
                        area: itemInfo.facilityLocation?.areaId,
                        aisle: itemInfo.facilityLocation?.aisleId,
                        section: itemInfo.facilityLocation?.sectionId,
                        level: itemInfo.facilityLocation?.levelId
                    ]
                }
            }
        }
    }
    
    result.picklistDetails = picklistDetails
    result.picklistDetails.items = items
    return result
}

/**
 * Records a pick for a specific picklist item.
 * Wraps setPicklistItemToComplete which internally calls updatePicklistItem.
 */
def recordPick() {
    Map serviceCtx = context.subMap(["picklistBinId", "orderItemSeqId", "orderId", "inventoryItemId", "quantity"])
    serviceCtx.userLogin = context.userLogin
    
    Map serviceResult = runService("setPicklistItemToComplete", serviceCtx)
    
    if (ServiceUtil.isError(serviceResult)) {
        return serviceResult
    }
    
    return ServiceUtil.returnSuccess()
}
