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
package org.apache.ofbiz.ecommerce.order

import org.apache.ofbiz.accounting.payment.PaymentWorker
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.order.order.OrderReadHelper

String orderId = parameters.orderId
GenericValue orderHeader = from('OrderHeader').where(orderId: orderId).queryOne()

// we have a special case here where for an anonymous order the user will already be logged out,
// but the userLogin will be in the request so we can still do a security check here
GenericValue userLogin = userLogin ?: parameters.temporaryAnonymousUserLogin

// This is another special case, when Order is placed by anonymous user
// from ecommerce and then Order is completed by admin(or any user) from Order Manager
// then userLogin is not found when Order Complete Mail is send to user.
if (!userLogin && orderHeader) {
    List orderStatuses = orderHeader.getRelated('OrderStatus', null, null, false)

    // Handled the case of OFFLINE payment method. In case of OFFLINE payment 'ORDER_CREATED' status must be checked.
    List orderPaymentPreferences = orderHeader.getRelated('OrderPaymentPreference', null, ['orderPaymentPreferenceId'], false)
    List filteredOrderPaymentPreferences = EntityUtil.filterByCondition(orderPaymentPreferences,
            EntityCondition.makeCondition('paymentMethodTypeId', EntityOperator.IN, ['EXT_OFFLINE']))
    List filteredOrderStatusList =  EntityUtil.filterByCondition(orderStatuses, EntityCondition.makeCondition('statusId', EntityOperator.IN,
            filteredOrderPaymentPreferences
                    ? ['ORDER_COMPLETED', 'ORDER_APPROVED', 'ORDER_CREATED']
                    : ['ORDER_COMPLETED', 'ORDER_APPROVED']))
    if (filteredOrderStatusList) {
        String userLoginId = filteredOrderStatusList.size() < 2
                ? EntityUtil.getFirst(filteredOrderStatusList).statusUserLogin
                : filteredOrderStatusList.find { it.statusId == 'ORDER_COMPLETED' } ?.statusUserLogin
        userLogin = from('UserLogin').where(userLoginId: userLoginId).queryOne()
    }
    if (!userLogin && delegator.getDelegatorName().startsWith('test')) {
        userLogin = from('UserLogin').where(userLoginId: 'system').cache().queryOne()
    }
    context.userLogin = userLogin
}

partyId = context.partyId ?: userLogin?.partyId

// can anybody view an anonymous order?  this is set in the screen widget and should only be turned on by an email confirmation screen
allowAnonymousView = context.allowAnonymousView

isDemoStore = true
String roleTypeId = 'PLACING_CUSTOMER'
if (orderId) {
    if (orderHeader?.orderTypeId == 'PURCHASE_ORDER') {
        roleTypeId = 'SUPPLIER_AGENT' //drop shipper or supplier
    }
    context.roleTypeId = roleTypeId
    // check OrderRole to make sure the user can view this order.  This check must be done for any order which is not anonymously placed and
    // any anonymous order when the allowAnonymousView security flag (see above) is not set to Y, to prevent peeking
    if (orderHeader && (orderHeader.createdBy != 'anonymous' || (orderHeader.createdBy == 'anonymous' && allowAnonymousView != 'Y'))) {
        orderRole = from('OrderRole').where(orderId: orderId, partyId: partyId, roleTypeId: roleTypeId).queryFirst()

        if (!userLogin || !orderRole) {
            context.remove('orderHeader')
            orderHeader = null
            logWarning('Warning: in OrderStatus.groovy before getting order detail info: role not found or user not logged in; partyId=['
                    + partyId + '], userLoginId=[' + (userLogin ? userLogin.userLoginId : 'null' ) + ']')
        }
    }
}

if (orderHeader) {
    productStore = orderHeader.getRelatedOne('ProductStore', true)
    if (productStore) {
        isDemoStore = productStore.isDemoStore != 'N'
    }

    OrderReadHelper orderReadHelper = new OrderReadHelper(orderHeader)
    List orderItems = orderReadHelper.getOrderItems()
    List orderAdjustments = orderReadHelper.getAdjustments()
    List orderHeaderAdjustments = orderReadHelper.getOrderHeaderAdjustments()
    BigDecimal orderSubTotal = orderReadHelper.getOrderItemsSubTotal()
    List orderItemShipGroups = orderReadHelper.getOrderItemShipGroups()
    List headerAdjustmentsToShow = orderReadHelper.getOrderHeaderAdjustmentsToShow()

    BigDecimal orderShippingTotal = OrderReadHelper.getAllOrderItemsAdjustmentsTotal(orderItems, orderAdjustments, false, false, true)
    orderShippingTotal = orderShippingTotal.add(OrderReadHelper.calcOrderAdjustments(orderHeaderAdjustments, orderSubTotal, false, false, true))

    orderTaxTotal = OrderReadHelper.getAllOrderItemsAdjustmentsTotal(orderItems, orderAdjustments, false, true, false)
    orderTaxTotal = orderTaxTotal.add(OrderReadHelper.calcOrderAdjustments(orderHeaderAdjustments, orderSubTotal, false, true, false))

    GenericValue placingCustomerOrderRole = from('OrderRole').where(orderId: orderId, roleTypeId: roleTypeId).queryFirst()
    placingCustomerPerson = placingCustomerOrderRole
            ? from('Person').where(partyId: placingCustomerOrderRole.partyId).queryOne()
            : null

    billingAccount = orderHeader.getRelatedOne('BillingAccount', false)

    orderPaymentPreferences = EntityUtil.filterByAnd(orderHeader.getRelated('OrderPaymentPreference', null, null, false),
            [EntityCondition.makeCondition('statusId', EntityOperator.NOT_EQUAL, 'PAYMENT_CANCELLED')])
    paymentMethods = []
    orderPaymentPreferences.each { opp ->
        paymentMethod = opp.getRelatedOne('PaymentMethod', false)
        if (paymentMethod) {
            paymentMethods << paymentMethod
        } else {
            paymentMethodType = opp.getRelatedOne('PaymentMethodType', false)
            if (paymentMethodType) {
                context.paymentMethodType = paymentMethodType
            }
        }
    }

    payToPartyId = productStore.payToPartyId
    paymentAddress = PaymentWorker.getPaymentAddress(delegator, payToPartyId)
    if (paymentAddress) {
        context.paymentAddress = paymentAddress
    }

    // get Shipment tracking info
    orderShipmentInfoSummaryList = select('shipmentId', 'shipmentRouteSegmentId', 'carrierPartyId',
            'shipmentMethodTypeId', 'shipmentPackageSeqId', 'trackingCode', 'boxNumber')
            .from('OrderShipmentInfoSummary')
            .where('orderId', orderId)
            .orderBy('shipmentId', 'shipmentRouteSegmentId', 'shipmentPackageSeqId')
            .distinct()
            .queryList()

    Set customerPoNumberSet = []
    orderItems.each { orderItemPo ->
        String correspondingPoId = orderItemPo.correspondingPoId
        if (correspondingPoId && correspondingPoId != '(none)') {
            customerPoNumberSet << correspondingPoId
        }
    }

    // check if there are returnable items
    returned = 0.00
    totalItems = 0.00
    orderItems.each { oitem ->
        totalItems += oitem.quantity
        ritems = oitem.getRelated('ReturnItem', null, null, false)
        ritems.each { ritem ->
            rh = ritem.getRelatedOne('ReturnHeader', false)
            if (rh.statusId != 'RETURN_CANCELLED') {
                returned += ritem.returnQuantity
            }
        }
    }

    if (totalItems > returned) {
        context.returnLink = 'Y'
    }

    context.putAll([orderId: orderId,
                    orderHeader: orderHeader,
                    localOrderReadHelper: orderReadHelper,
                    orderItems: orderItems,
                    orderAdjustments: orderAdjustments,
                    orderHeaderAdjustments: orderHeaderAdjustments,
                    orderSubTotal: orderSubTotal,
                    orderItemShipGroups: orderItemShipGroups,
                    headerAdjustmentsToShow: headerAdjustmentsToShow,
                    currencyUomId: orderReadHelper.getCurrency(),
                    orderShippingTotal: orderShippingTotal,
                    orderTaxTotal: orderTaxTotal,
                    orderGrandTotal: OrderReadHelper.getOrderGrandTotal(orderItems, orderAdjustments),
                    placingCustomerPerson: placingCustomerPerson,
                    billingAccount: billingAccount,
                    paymentMethods: paymentMethods,
                    productStore: productStore,
                    isDemoStore: isDemoStore,
                    orderShipmentInfoSummaryList: orderShipmentInfoSummaryList,
                    customerPoNumberSet: customerPoNumberSet,
                    orderItemChangeReasons: from('Enumeration').where(enumTypeId: 'ODR_ITM_CH_REASON').cache().queryList()])
}
