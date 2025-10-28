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

import org.apache.ofbiz.base.util.UtilMisc
import org.apache.ofbiz.entity.GenericEntityException
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityConditionBuilder

// find cust request and items
List<GenericValue> custRequestAndItems = []

if (parameters.statusId == 'Any') {
    parameters.statusId = ''
}
Map performFindResults = run service: 'performFind', with: [entityName: 'CustRequestAndCustRequestItem',
                                                               inputFields: [*: parameters,
                                                                             custRequestTypeId: 'RF_PROD_BACKLOG'],
                                                               orderBy: 'custSequenceNum']
try {
    custRequestAndItems = performFindResults.listIt.getCompleteList()
} catch (GenericEntityException e) {
    logError(e.toString())
} finally {
    if (performFindResults.listIt) {
        try {
            performFindResults.listIt.close()
        } catch (GenericEntityException e) {
            logError(e.toString())
        }
    }
}

// prepare cust request item list [cust request and item Map]
int countSequence = 1
List custRequestAndCustRequestItems = []
custRequestAndItems.each { custRequestAndItem ->
    Map tempCustRequestAndItem = [*: custRequestAndItem,
                                  custSequenceNum: countSequence,
                                  realSequenceNum: custRequestAndItem.custSequenceNum]
    // if custRequest has task then get Actual Hours
    List<GenericValue> custWorkEffortList = from('CustRequestWorkEffort')
            .where(custRequestId: custRequestAndItem.custRequestId)
            .queryList()
    BigDecimal actualHours = 0.00
    if (custWorkEffortList) {
        custWorkEffortList.each {
            Map result = run service: 'getScrumActualHour', with: [taskId: it.workEffortId, partyId: null]
            actualHours += result.actualHours
        }
        tempCustRequestAndItem.actualHours = actualHours ?: null
    }
    tempCustRequestAndItem.actualHours = null
    custRequestAndCustRequestItems << tempCustRequestAndItem
    countSequence++
}

if (parameters.sequence == 'N') { // re-order category list item
    custRequestAndCustRequestItems = UtilMisc.sortMaps(custRequestAndCustRequestItems, ['parentCustRequestId'])
}
//set status back for display in Find screen
parameters.statusId = parameters.statusId ?: 'Any'
context.custRequestAndCustRequestItems = custRequestAndCustRequestItems

// unplanned backlog item list
String productId = parameters.productId
EntityCondition condition = new EntityConditionBuilder().AND {
    EQUALS(custRequestTypeId: 'RF_UNPLAN_BACKLOG')
    EQUALS(productId: productId)
    IN(statusId: ['CRQ_ACCEPTED', 'CRQ_REOPENED'])
}
List<GenericValue> unplannedList = select('custRequestId', 'custSequenceNum',
        'statusId', 'description', 'custEstimatedMilliSeconds', 'custRequestName', 'parentCustRequestId')
        .from('CustRequestAndCustRequestItem')
        .where(condition)
        .orderBy('custSequenceNum')
        .queryList()

int countSequenceUnplanned = 1
List unplanBacklogItems = []
unplannedList.each { unplannedItem ->
    Map tempUnplanned = [*: unplannedItem,
                         custSequenceNum: countSequenceUnplanned,
                         realSequenceNum: unplannedItem.custSequenceNum]
    // if custRequest has task then get Actual Hours
    List unplanCustWorkEffortList = from('CustRequestWorkEffort')
            .where(custRequestId: unplannedItem.custRequestId)
            .queryList()
    BigDecimal actualHours = 0.00
    if (unplanCustWorkEffortList) {
        unplanCustWorkEffortList.each {
            Map result = run service: 'getScrumActualHour', with: [taskId: it.workEffortId, partyId: null]
            actualHours += result.actualHours
        }
    }
    tempUnplanned.actualHours = actualHours ?: null
    unplanBacklogItems << tempUnplanned
    countSequenceUnplanned++
}
if (parameters.UnplannedSequence == 'N') { // re-order category list item
    unplanBacklogItems = UtilMisc.sortMaps(unplanBacklogItems, ['parentCustRequestId'])
}
context.unplanBacklogItems = unplanBacklogItems
