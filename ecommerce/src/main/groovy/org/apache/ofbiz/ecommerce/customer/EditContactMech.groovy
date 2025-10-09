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
import org.apache.ofbiz.party.contact.ContactMechWorker

/* puts the following in the context: 'contactMech', 'contactMechId',
        'partyContactMech', 'partyContactMechPurposes', 'contactMechTypeId',
        'contactMechType', 'purposeTypes', 'postalAddress', 'telecomNumber',
        'requestName', 'donePage', 'tryEntity', 'contactMechTypes'
 */
target = [:]
ContactMechWorker.getContactMechAndRelated(request, userLogin.partyId, target)
context.putAll(target)

context.canNotView = !security.hasEntityPermission('PARTYMGR', '_VIEW', session) && !context.partyContactMech && context.contactMech

preContactMechTypeId = parameters.preContactMechTypeId
if (preContactMechTypeId) {
    context.preContactMechTypeId = preContactMechTypeId
}

paymentMethodId = parameters.paymentMethodId
if (paymentMethodId) {
    context.paymentMethodId = paymentMethodId
}

cmNewPurposeTypeId = parameters.contactMechPurposeTypeId
if (cmNewPurposeTypeId) {
    GenericValue contactMechPurposeType = from('ContactMechPurposeType').where(contactMechPurposeTypeId: cmNewPurposeTypeId).queryOne()
    if (contactMechPurposeType) {
        context.contactMechPurposeType = contactMechPurposeType
    } else {
        cmNewPurposeTypeId = null
    }
    context.cmNewPurposeTypeId = cmNewPurposeTypeId
}

tryEntity = context.tryEntity

contactMechData = context.contactMech
if (!tryEntity) {
    contactMechData = parameters
}
context.contactMechData = contactMechData ?: [:]

partyContactMechData = context.partyContactMech
if (!tryEntity) {
    partyContactMechData = parameters
}
context.partyContactMechData = partyContactMechData ?: [:]

postalAddressData = context.postalAddress
if (!tryEntity) {
    postalAddressData = parameters
}
context.postalAddressData = postalAddressData ?: [:]

telecomNumberData = context.telecomNumber
if (!tryEntity) {
    telecomNumberData = parameters
}
context.telecomNumberData = telecomNumberData ?: [:]

// load the geo names for selected countries and states/regions
String geoId = parameters.countryGeoId ?: postalAddressData?.countryGeoId
if (geoId) {
    context.selectedCountryName = from('Geo').where(geoId: geoId).cache().queryOne()?.geoName
}

geoId = parameters.stateProvinceGeoId ?: postalAddressData?.stateProvinceGeoId
if (geoId) {
    context.selectedStateName = from('Geo').where(geoId: geoId).cache().queryOne()?.geoName
}
