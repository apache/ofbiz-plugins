/*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* 'License'); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.apache.ofbiz.scrum

import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator

partyId = userLogin.partyId
taskPartyList = []
taskListDropdown = []

taskUnplanList = from('ProjectSprintBacklogTaskAndParty')
        .where(partyId: partyId,
                taskCurrentStatusId: 'STS_CREATED',
                custRequestTypeId: 'RF_UNPLAN_BACKLOG')
        .orderBy('taskTypeId')
        .queryList()
taskUnplanList.each { taskUnplanMap ->
    unplanMap = [:]
    custRequestId = taskUnplanMap.custRequestId
    productlist = from('CustRequestItem').where('custRequestId', custRequestId).orderBy('productId').queryList()
    productlist.each {
        product = from('Product').where(productId: it.productId).cache().queryOne()
        unplanMap.with {
            taskId = taskUnplanMap.taskId
            taskName = taskUnplanMap.taskName
            projectId = taskUnplanMap.projectId
            projectName = taskUnplanMap.projectName
            sprintId = taskUnplanMap.sprintId
            sprintName = taskUnplanMap.sprintName
            description = taskUnplanMap.description
            custRequestId = taskUnplanMap.custRequestId
            productId = product.productId
            productName = product.internalName
        }
    }
    taskPartyList << taskUnplanMap
    taskListDropdown << unplanMap
}

exprBldr = []
exprBldr << EntityCondition.makeCondition('custRequestTypeId', EntityOperator.EQUALS, 'RF_PROD_BACKLOG')
exprBldr << EntityCondition.makeCondition('custRequestTypeId', EntityOperator.EQUALS, 'RF_SCRUM_MEETINGS')
andExprs = []
andExprs << EntityCondition.makeCondition('taskCurrentStatusId', EntityOperator.EQUALS, 'STS_CREATED')
andExprs << EntityCondition.makeCondition('partyId', EntityOperator.EQUALS, partyId)
andExprs << EntityCondition.makeCondition(exprBldr, EntityOperator.OR)
custRequestTypeCond = EntityCondition.makeCondition(andExprs, EntityOperator.AND)

taskPlanList = from('ProjectSprintBacklogTaskAndParty').where(custRequestTypeCond).orderBy('taskTypeId', 'projectId', 'sprintId').queryList()
taskPlanList.each { taskPlanMap ->
    planMap = [:]
    if (taskPlanMap.custRequestTypeId == 'RF_SCRUM_MEETINGS') {
        workEffPartyAssignedList = from('WorkEffortPartyAssignment').where('partyId', partyId, 'workEffortId', taskPlanMap.taskId).queryList()
        workEffPartyAssignedMap = workEffPartyAssignedList[0]
        if (workEffPartyAssignedMap.statusId != 'SCAS_COMPLETED') {
            taskPartyList << taskPlanMap
            taskListDropdown << taskPlanMap
        }
    } else {
        if (taskPlanMap.projectId) {
            taskPartyList << taskPlanMap
            taskListDropdown << taskPlanMap
        } else {
            custRequestId = taskPlanMap.custRequestId
            custProduct = from('CustRequestItem').where('custRequestId', custRequestId).orderBy('productId').queryFirst()
            product = from('Product').where('productId', custProduct.productId).cache().queryOne()
            planMap.with {
                taskId = taskPlanMap.taskId
                taskTypeId = taskPlanMap.taskTypeId
                taskName = taskPlanMap.taskName
                projectId = taskPlanMap.projectId
                projectName = taskPlanMap.projectName
                sprintId = taskPlanMap.sprintId
                sprintName = taskPlanMap.sprintName
                custRequestId = taskPlanMap.custRequestId
                description = taskPlanMap.description
                productId = product.productId
                productName = product.internalName
            }
            taskPartyList << planMap
            taskListDropdown << planMap
        }
    }
}
if (taskPartyList) {
    context.taskPartyList = taskPartyList
}
if (taskListDropdown) {
    context.taskListDropdown = taskListDropdown
}

