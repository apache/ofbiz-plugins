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
package org.apache.ofbiz.webpos.search

import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.order.shoppingcart.ShoppingCart
import org.apache.ofbiz.webpos.WebPosEvents
import org.apache.ofbiz.webpos.session.WebPosSession

WebPosSession webPosSession = WebPosEvents.getWebPosSession(request, null)
if (webPosSession) {
    ShoppingCart shoppingCart = webPosSession.getCart()
    String shipToCustomerPartyId = shoppingCart.getShipToCustomerPartyId()
    if (shipToCustomerPartyId) {
        context.personShipTo = from('Person').where(partyId: shipToCustomerPartyId).cache().queryOne()
    }
    String shippingContactMechId = shoppingCart.getContactMechId('SHIPPING_LOCATION')
    if (shippingContactMechId) {
        GenericValue contactMech = from('ContactMech').where(contactMechId: shippingContactMechId).queryOne()
        if (contactMech && contactMech.contactMechTypeId == 'POSTAL_ADDRESS') {
            context.shippingPostalAddress = contactMech.getRelatedOne('PostalAddress', false)
        }
    }
    String billToCustomerPartyId = shoppingCart.getBillToCustomerPartyId()
    if (billToCustomerPartyId) {
        context.personBillTo = from('Person').where('partyId', billToCustomerPartyId).cache().queryOne()
    }
    String billingContactMechId = shoppingCart.getContactMechId('BILLING_LOCATION')
    if (billingContactMechId) {
        GenericValue contactMech = from('ContactMech').where('contactMechId', billingContactMechId).queryOne()
        if (contactMech && contactMech.contactMechTypeId == 'POSTAL_ADDRESS') {
            context.billingPostalAddress = contactMech.getRelatedOne('PostalAddress', false)
        }
    }
}
