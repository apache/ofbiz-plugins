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
package org.apache.ofbiz.scrum

import org.apache.ofbiz.entity.GenericValue

GenericValue custRequest = from('CustRequest').where(custRequestId: custRequestId).queryOne()
GenericValue person = from('PartyNameView').where(partyId: partyIdTo).queryOne()
Map informationMap = [internalName: null,
                  productId: null,
                  workEffortName: null,
                  workEffortId: null]

//check in sprint
List<GenericValue> backlogList = select('productId', 'workEffortId', 'custRequestId')
        .from('ProductBacklog')
        .where('workEffortTypeId', 'SCRUM_SPRINT', 'custRequestId', custRequestId)
        .queryList()
if (backlogList) {
    GenericValue sprint = from('WorkEffort').where(workEffortId: backlogList.first().workEffortId).cache().queryOne()
    informationMap.workEffortName = sprint.workEffortName
    informationMap.workEffortId = sprint.workEffortId
} else {
    backlogList = select('productId', 'workEffortId', 'custRequestId')
            .from('ProductBacklog')
            .where(custRequestId: custRequestId).queryList()
}
if (backlogList) {
    GenericValue product = from('Product').where(productId: backlogList.first().productId).cache().queryOne()
    informationMap.internalName = product.internalName
    informationMap.productId = product.productId
}

// check backlog removed from sprint.
boolean removedFromSprint = false
if (custRequest.statusId == 'CRQ_ACCEPTED') {
    List custStatusList = custRequest.getRelated('CustRequestStatus', null, ['-custRequestStatusId'], false)
    if (custStatusList.size() > 2 && custStatusList[1].statusId == 'CRQ_REVIEWED') {
        removedFromSprint = true
    }
}

context.custRequest = custRequest
context.person = person
context.informationMap = informationMap
context.removedFromSprint = removedFromSprint
