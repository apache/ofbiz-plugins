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
package org.apache.ofbiz.projectmgr

/**
* service to create project
*/
Map createProject() {
    Map result = success()
    wepaInMap = [:]
    emailInMap = [:]
    if (parameters.templateId) {
        parameters.projectId = parameters.templateId
        pc = run service: 'copyProject', with: parameters
        workEffortId = pc.workEffortId
    } else {
        parameters.currentStatusId = 'PRJ_ACTIVE'
        we = run service: 'createWorkEffort', with: parameters
        workEffortId = we.workEffortId
    }
    wepaInMap.workEffortId = workEffortId
    // create project role for the internal organisation
    if (parameters.organizationPartyId) {
        wepaInMap.partyId = parameters.organizationPartyId
        wepaInMap.roleTypeId = 'INTERNAL_ORGANIZATIO'
        wepa = run service: 'createWorkEffortPartyAssignment', with: wepaInMap
    }
    // create project role for the client
    if (parameters.clientBillingPartyId) {
        wepaInMap.partyId = parameters.clientBillingPartyId
        wepaInMap.roleTypeId = 'CLIENT_BILLING'
        wepa = run service: 'createWorkEffortPartyAssignment', with: wepaInMap
    }
    // create email address for the project
    emailInMap.contactMechTypeId = 'EMAIL_ADDRESS'
    emailAddress = parameters.emailAddress
    if (emailAddress) {
        emailInMap.emailAddress = emailAddress
    } else {
        emailAddress = 'project.' + workEffortId + '@example.com'
        emailInMap.emailAddress = emailAddress
    }
    email = run service: 'createEmailAddress', with: emailInMap
    // create the contact mech regarding the project's email address
    wecmInMap = [:]
    wecmInMap.workEffortId = workEffortId
    wecmInMap.contactMechId = email.contactMechId
    wecmInMap.contactMechTypeId = 'EMAIL_ADDRESS'
    wecmInMap.infoString = emailAddress
    weContactMech = run service: 'createWorkEffortContactMech', with: wecmInMap
    result.put('projectId', workEffortId)
    return result
}
