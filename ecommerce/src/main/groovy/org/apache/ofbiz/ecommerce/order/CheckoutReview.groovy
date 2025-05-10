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
import org.apache.ofbiz.order.order.OrderReadHelper
import org.apache.ofbiz.order.shoppingcart.ShoppingCart
import org.apache.ofbiz.product.store.ProductStoreWorker

ShoppingCart cart = session.getAttribute('shoppingCart')
context.cart = cart

List orderItems = cart.makeOrderItems(dispatcher)
context.orderItems = orderItems

orderAdjustments = cart.makeAllAdjustments()

orderItemShipGroupInfo = cart.makeAllShipGroupInfos()
if (orderItemShipGroupInfo) {
    orderItemShipGroupInfo.each { valueObj ->
        if (valueObj.getEntityName() == 'OrderAdjustment') {
            // shipping / tax adjustment(s)
            orderAdjustments << valueObj
        }
    }
}
context.orderAdjustments = orderAdjustments

workEfforts = cart.makeWorkEfforts() // if required make workefforts for rental fixed assets too.
context.workEfforts = workEfforts

List orderHeaderAdjustments = OrderReadHelper.getOrderHeaderAdjustments(orderAdjustments, null)
context.orderHeaderAdjustments = orderHeaderAdjustments
context.orderItemShipGroups = cart.getShipGroups()
context.headerAdjustmentsToShow = OrderReadHelper.filterOrderAdjustments(orderHeaderAdjustments, true, false, false, false, false)

context.orderSubTotal = OrderReadHelper.getOrderItemsSubTotal(orderItems, orderAdjustments, workEfforts)
context.placingCustomerPerson = userLogin?.getRelatedOne('Person', true)
context.paymentMethods = cart.getPaymentMethods()

List paymentMethodTypeIds = cart.getPaymentMethodTypeIds()
if (paymentMethodTypeIds) {
    context.paymentMethodType = from('PaymentMethodType').where(paymentMethodTypeId: paymentMethodTypeIds.first()).queryOne()
}

GenericValue productStore = ProductStoreWorker.getProductStore(request)
context.productStore = productStore
context.isDemoStore = productStore.isDemoStore != 'N'

String payToPartyId = productStore.payToPartyId
GenericValue paymentAddress = PaymentWorker.getPaymentAddress(delegator, payToPartyId)
if (paymentAddress) {
    context.paymentAddress = paymentAddress
}

// TODO: FIXME!
/*
billingAccount = cart.getBillingAccountId() ? delegator.findOne('BillingAccount', [billingAccountId : cart.getBillingAccountId()], false) : null
if (billingAccount)
    context.billingAccount = billingAccount
*/

context.customerPoNumber = cart.getPoNumber()
context.carrierPartyId = cart.getCarrierPartyId()
context.shipmentMethodTypeId = cart.getShipmentMethodTypeId()
context.shippingInstructions = cart.getShippingInstructions()
context.maySplit = cart.getMaySplit()
context.giftMessage = cart.getGiftMessage()
context.isGift = cart.getIsGift()
context.currencyUomId = cart.getCurrency()

GenericValue shipmentMethodType = from('ShipmentMethodType').where(shipmentMethodTypeId: cart.getShipmentMethodTypeId()).queryOne()
if (shipmentMethodType) {
    context.shipMethDescription = shipmentMethodType.description
}

context.localOrderReadHelper = new OrderReadHelper(orderAdjustments, orderItems)
context.orderShippingTotal = cart.getTotalShipping()
context.orderTaxTotal = cart.getTotalSalesTax()
context.orderGrandTotal = cart.getGrandTotal()

// nuke the event messages
request.removeAttribute('_EVENT_MESSAGE_')
