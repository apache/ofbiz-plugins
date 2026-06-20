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
    
    // Transform pickMoveInfoList to a simpler list for the PWA
    List orderList = []
    if (findResult.pickMoveInfoList) {
        findResult.pickMoveInfoList.each { pickMoveInfo ->
            if (pickMoveInfo.orderReadyToPickInfoList) {
                pickMoveInfo.orderReadyToPickInfoList.each { orderReadyInfo ->
                    def orderHeader = orderReadyInfo.orderHeader
                    if (orderHeader) {
                        if (!orderList.any { it.orderId == orderHeader.orderId }) {
                            orderList << [
                                orderId: orderHeader.orderId,
                                orderDate: orderHeader.orderDate,
                                statusId: orderHeader.statusId,
                                grandTotal: orderHeader.grandTotal
                            ]
                        }
                    }
                }
            }
        }
    }
    
    result.orderList = orderList
    return result
}

/**
 * Creates a picklist from a list of orderIds.
 * Wraps existing createPicklistFromOrders service.
 */
def createPickingPicklist() {
    Map serviceResult = runService("createPicklistFromOrders", [
        orderIdList: context.orderIds, 
        facilityId: context.facilityId, 
        userLogin: context.userLogin
    ])
    
    if (ServiceUtil.isError(serviceResult)) {
        return serviceResult
    }
    
    Map result = ServiceUtil.returnSuccess()
    result.picklistId = serviceResult.picklistId
    return result
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
    if (reportResult.picklistInfo && reportResult.picklistInfo.picklistBinInfoList) {
        reportResult.picklistInfo.picklistBinInfoList.each { binInfo ->
            if (binInfo.picklistItemInfoList) {
                binInfo.picklistItemInfoList.each { itemInfo ->
                    items << [
                        picklistBinId: binInfo.picklistBin?.picklistBinId,
                        orderId: itemInfo.orderItem?.orderId,
                        orderItemSeqId: itemInfo.orderItem?.orderItemSeqId,
                        shipGroupSeqId: itemInfo.picklistItem?.shipGroupSeqId,
                        inventoryItemId: itemInfo.picklistItem?.inventoryItemId,
                        itemStatusId: itemInfo.picklistItem?.itemStatusId,
                        productId: itemInfo.product?.productId,
                        productName: itemInfo.product?.internalName,
                        quantity: itemInfo.picklistItem?.quantity,
                        locationSeqId: itemInfo.inventoryItemAndLocation?.locationSeqId,
                        area: itemInfo.inventoryItemAndLocation?.areaId,
                        aisle: itemInfo.inventoryItemAndLocation?.aisleId,
                        section: itemInfo.inventoryItemAndLocation?.sectionId,
                        level: itemInfo.inventoryItemAndLocation?.levelId
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
    Map serviceCtx = context.subMap(["picklistBinId", "orderItemSeqId", "orderId", "inventoryItemId", "shipGroupSeqId", "quantity"])
    serviceCtx.itemStatusId = "PICKITEM_COMPLETED"
    serviceCtx.userLogin = context.userLogin
    
    Map serviceResult = runService("setPicklistItemToComplete", serviceCtx)
    
    if (ServiceUtil.isError(serviceResult)) {
        return serviceResult
    }
    
    return ServiceUtil.returnSuccess()
}
