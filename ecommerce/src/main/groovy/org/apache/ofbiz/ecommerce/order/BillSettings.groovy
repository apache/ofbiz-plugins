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

import org.apache.ofbiz.accounting.payment.BillingAccountWorker
import org.apache.ofbiz.base.util.UtilHttp
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityUtil

cart = session.getAttribute('shoppingCart')
currencyUomId = cart.getCurrency()
payType = parameters.paymentMethodType
if (!payType && parameters.useGc) {
    payType = 'GC'
}
context.cart = cart
context.paymentMethodType = payType

partyId = cart.getPartyId() ?: userLogin.partyId
context.partyId = partyId

// nuke the event messages
request.removeAttribute('_EVENT_MESSAGE_')

if (partyId && partyId != '_NA_') {
    GenericValue party = from('Party').where('partyId', partyId).cache().queryOne()
    context.party = party
    context.person = party.getRelatedOne('Person', true)
    if (party) {
        context.paymentMethodList = EntityUtil.filterByDate(party.getRelated('PaymentMethod', null, null, false))

        List billingAccountList = BillingAccountWorker.makePartyBillingAccountList(userLogin, currencyUomId, partyId, delegator, dispatcher)
        if (billingAccountList) {
            context.selectedBillingAccountId = cart.getBillingAccountId()
            context.billingAccountList = billingAccountList
        }
    }
}

if (parameters.useShipAddr && cart.getShippingContactMechId()) {
    GenericValue postalAddress = from('PostalAddress').where(contactMechId: cart.getShippingContactMechId()).queryOne()
    context.useEntityFields = 'Y'
    context.postalFields = postalAddress

    if (postalAddress && partyId) {
        context.partyContactMech = from('PartyContactMech')
                .where(partyId: partyId, contactMechId: postalAddress.contactMechId)
                .orderBy('-fromDate')
                .filterByDate()
                .queryFirst()
    }
} else {
    context.postalFields = UtilHttp.getParameterMap(request)
}

if (cart && !parameters.singleUsePayment) {
    if (cart.getPaymentMethodIds()) {
        String checkOutPaymentId = cart.getPaymentMethodIds().first()
        context.checkOutPaymentId = checkOutPaymentId
        GenericValue paymentMethod = from('PaymentMethod').where('paymentMethodId', checkOutPaymentId).queryOne()
        GenericValue account = null

        switch (paymentMethod.paymentMethodTypeId) {
            case 'CREDIT_CARD':
                account = paymentMethod.getRelatedOne('CreditCard', false)
                context.creditCard = account
                context.paymentMethodType = 'CC'
                break
            case 'EFT_ACCOUNT':
                account = paymentMethod.getRelatedOne('EftAccount', false)
                context.eftAccount = account
                context.paymentMethodType = 'EFT'
                break
            case 'GIFT_CARD':
                account = paymentMethod.getRelatedOne('GiftCard', false)
                context.giftCard = account
                context.paymentMethodType = 'GC'
                break
            default:
                context.paymentMethodType = 'offline'
                break
        }
        if (account && parameters.useShipAddr) {
            GenericValue address = account.getRelatedOne('PostalAddress', false)
            context.postalAddress = address
            context.postalFields = address
        }
    } else if (cart.getPaymentMethodTypeIds()) {
        context.checkOutPaymentId = cart.getPaymentMethodTypeIds().first()
    }
}

requestPaymentMethodType = parameters.paymentMethodType
if (requestPaymentMethodType) {
    context.paymentMethodType = requestPaymentMethodType
}
