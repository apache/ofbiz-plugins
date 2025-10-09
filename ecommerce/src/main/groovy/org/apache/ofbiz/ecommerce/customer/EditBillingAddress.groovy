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
package org.apache.ofbiz.ecommerce.customer

import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.party.contact.ContactHelper

if (userLogin) {
    GenericValue party = userLogin.getRelatedOne('Party', true)
    GenericValue contactMech = EntityUtil.getFirst(ContactHelper.getContactMech(party, 'BILLING_LOCATION', 'POSTAL_ADDRESS', false))
    if (contactMech) {
        GenericValue postalAddress = contactMech.getRelatedOne('PostalAddress', false)
        String billToContactMechId = postalAddress.contactMechId
        context.with {
            billToContactMechId = postalAddress.billToContactMechId
            billToName = postalAddress.toName
            billToAttnName = postalAddress.attnName
            billToAddress1 = postalAddress.address1
            billToAddress2 = postalAddress.address2
            billToCity = postalAddress.city
            billToPostalCode = postalAddress.postalCode
            billToStateProvinceGeoId = postalAddress.stateProvinceGeoId
            billToCountryGeoId = postalAddress.countryGeoId
        }
        GenericValue billToStateProvinceGeo = from('Geo').where('geoId', postalAddress.stateProvinceGeoId).cache().queryOne()
        if (billToStateProvinceGeo) {
            context.billToStateProvinceGeo = billToStateProvinceGeo.geoName
        }
        GenericValue billToCountryProvinceGeo = from('Geo').where('geoId', postalAddress.countryGeoId).cache().queryOne()
        if (billToCountryProvinceGeo) {
            context.billToCountryProvinceGeo = billToCountryProvinceGeo.geoName
        }

        GenericValue paymentMethod = from('PaymentMethod')
                .where(partyId: party.partyId, paymentMethodTypeId: 'CREDIT_CARD')
                .orderBy('fromDate')
                .filterByDate()
                .queryFirst()
        if (paymentMethod) {
            GenericValue creditCard = paymentMethod.getRelatedOne('CreditCard', false)
            context.with {
                paymentMethodTypeId = 'CREDIT_CARD'
                cardNumber = creditCard.cardNumber
                cardType = creditCard.cardType
                paymentMethodId = creditCard.paymentMethodId
                firstNameOnCard = creditCard.firstNameOnCard
                lastNameOnCard = creditCard.lastNameOnCard
                expMonth = (creditCard.expireDate).substring(0, 2)
                expYear = (creditCard.expireDate).substring(3)
            }
        }
        if (shipToContactMechId) {
            if (billToContactMechId && billToContactMechId == shipToContactMechId) {
                context.useShippingAddressForBilling = 'Y'
            }
        }
    }

    List billToContactMechList = ContactHelper.getContactMech(party, 'PHONE_BILLING', 'TELECOM_NUMBER', false)
    if (billToContactMechList) {
        billToTelecomNumber = (EntityUtil.getFirst(billToContactMechList)).getRelatedOne('TelecomNumber', false)
        pcm = EntityUtil.getFirst(billToTelecomNumber.getRelated('PartyContactMech', null, null, false))
        context.billToTelecomNumber = billToTelecomNumber
        context.billToExtension = pcm.extension
    }

    List billToFaxNumberList = ContactHelper.getContactMech(party, 'FAX_BILLING', 'TELECOM_NUMBER', false)
    if (billToFaxNumberList) {
        billToFaxNumber = (EntityUtil.getFirst(billToFaxNumberList)).getRelatedOne('TelecomNumber', false)
        faxPartyContactMech = EntityUtil.getFirst(billToFaxNumber.getRelated('PartyContactMech', null, null, false))
        context.billToFaxNumber = billToFaxNumber
        context.billToFaxExtension = faxPartyContactMech.extension
    }
}
