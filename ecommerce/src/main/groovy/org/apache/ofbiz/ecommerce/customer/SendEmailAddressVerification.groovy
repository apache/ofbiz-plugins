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

import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityQuery
import org.apache.ofbiz.product.store.ProductStoreWorker
import org.apache.ofbiz.service.GenericServiceException
import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.webapp.website.WebSiteWorker

// After an email address was added or changed, or when the customer asks to resend: mail a verification link
// for that address unless it is already verified. A failure is logged and never fails the request.
String contactMechId = request.getAttribute('contactMechId') ?: request.getParameter('contactMechId')
if (userLogin && contactMechId) {
    GenericValue partyContactMech = EntityQuery.use(delegator).from('PartyContactMech')
            .where('partyId', userLogin.partyId, 'contactMechId', contactMechId)
            .filterByDate()
            .queryFirst()
    GenericValue contactMech = EntityQuery.use(delegator).from('ContactMech')
            .where('contactMechId', contactMechId)
            .queryOne()
    if (partyContactMech && partyContactMech.verified != 'Y' && contactMech?.contactMechTypeId == 'EMAIL_ADDRESS') {
        try {
            Map result = dispatcher.runSync('sendEmailAddressVerification', [userLogin: userLogin,
                    partyId: userLogin.partyId, contactMechId: contactMechId,
                    productStoreId: ProductStoreWorker.getProductStoreId(request),
                    webSiteId: WebSiteWorker.getWebSiteId(request)])
            if (ServiceUtil.isError(result)) {
                Debug.logWarning("Could not send the email address verification: ${ServiceUtil.getErrorMessage(result)}",
                        'SendEmailAddressVerification')
            }
        } catch (GenericServiceException e) {
            Debug.logWarning(e, 'Could not send the email address verification', 'SendEmailAddressVerification')
        }
    }
}
return 'success'
