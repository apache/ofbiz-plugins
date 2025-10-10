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

import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator

taskId = parameters.taskId
taskName = parameters.taskName
sprintId = parameters.sprintId
sprintName = parameters.sprintName
taskTypeId = parameters.taskTypeId
projectId = parameters.projectId
projectName = parameters.projectName
backlogTypeId = parameters.unplannedFlag
statusId = parameters.statusId
partyId = parameters.partyId

unplannedTaskList = []
plannedTaskList = []
resultList = []
taskList = []
implementTaskList = []
testTaskList = []
errorTaskList = []
installTaskList = []

// get Unplaned task list
if (taskId || taskName || taskTypeId  || sprintId || sprintName
        || projectId || projectName || backlogTypeId || statusId
        || partyId || viewIndex_1 || viewIndex_2 || viewIndex_3  || viewIndex_4
        || viewIndexNo_1 || viewIndexNo_2 || viewIndexNo_3 || viewIndexNo_4) {
    if (taskId || taskName || taskTypeId) {
        exprBldr = []
        if (taskId) {
            exprBldr.add(EntityCondition.makeCondition('workEffortId', EntityOperator.EQUALS, taskId))
        }
        if (taskName) {
            exprBldr.add(EntityCondition.makeCondition('workEffortName', EntityOperator.LIKE, '%' + taskName + '%'))
        }
        if (taskTypeId) {
            exprBldr.add(EntityCondition.makeCondition('workEffortTypeId', EntityOperator.EQUALS, taskTypeId))
        }
        if (statusId) {
            exprBldr.add(EntityCondition.makeCondition('currentStatusId', EntityOperator.EQUALS, statusId))
        }
        unplannedTaskList = from('UnPlannedBacklogsAndTasks').where(exprBldr).orderBy('-createdDate').queryList()
    } else {
        unplannedTaskList = from('UnPlannedBacklogsAndTasks').orderBy('-createdDate').queryList()
    }

    exprBldr2 = []
    if (taskId) {
        exprBldr2.add(EntityCondition.makeCondition('taskId', EntityOperator.EQUALS, taskId))
    }
    if (taskName) {
        exprBldr2.add(EntityCondition.makeCondition('taskName', EntityOperator.LIKE, '%' + taskName + '%'))
    }
    if (taskTypeId) {
        exprBldr2.add(EntityCondition.makeCondition('taskTypeId', EntityOperator.EQUALS, taskTypeId))
    }
    if (statusId) {
        exprBldr2.add(EntityCondition.makeCondition('taskCurrentStatusId', EntityOperator.EQUALS, statusId))
    }
    if (sprintId) {
        exprBldr2.add(EntityCondition.makeCondition('sprintId', EntityOperator.EQUALS, sprintId))
    }
    if (sprintName) {
        exprBldr2.add(EntityCondition.makeCondition('sprintName', EntityOperator.LIKE, '%' + sprintName + '%'))
    }
    if (projectId) {
        exprBldr2.add(EntityCondition.makeCondition('projectId', EntityOperator.EQUALS, projectId))
    }
    if (projectName) {
        exprBldr2.add(EntityCondition.makeCondition('projectName', EntityOperator.LIKE, '%' + projectName + '%'))
    }
    exprBldr2.add(EntityCondition.makeCondition('sprintTypeId', EntityOperator.EQUALS, 'SCRUM_SPRINT'))
    plannedTaskList = from('ProjectSprintBacklogAndTask').where(exprBldr2).orderBy('-taskCreatedDate').queryList()

    unplannedTaskList.each {
        taskList << [taskId: it.workEffortId,
                     sprintId: null,
                     projectId: null,
                     productId: it.productId,
                     taskName: it.workEffortName,
                     taskTypeId: it.workEffortTypeId,
                     taskCurrentStatusId: it.currentStatusId,
                     taskEstimatedMilliSeconds: it.estimatedMilliSeconds,
                     taskCreatedDate: it.createdDate,
                     custRequestId: it.custRequestId,
                     description: it.description,
                     custRequestTypeId: it.custRequestTypeId,
                     taskActualMilliSeconds: it.actualMilliSeconds,
                     taskEstimatedStartDate: it.estimatedStartDate]
    }

    plannedTaskList.each {
        taskList << [taskId: it.taskId,
                     taskName: it.taskName,
                     taskTypeId: it.taskTypeId,
                     taskCurrentStatusId: it.taskCurrentStatusId,
                     taskEstimatedMilliSeconds: it.taskEstimatedMilliSeconds,
                     taskCreatedDate: it.taskCreatedDate,
                     sprintId: it.sprintId,
                     sprintName: it.sprintName,
                     projectId: it.projectId,
                     projectName: it.projectName,
                     custRequestId: it.custRequestId,
                     description: it.description,
                     custRequestTypeId: it.custRequestTypeId,
                     taskActualMilliSeconds: it.taskActualMilliSeconds,
                     taskEstimatedStartDate: it.taskEstimatedStartDate]
    }
    //Check the backlog
    if (backlogTypeId) {
        if (backlogTypeId == 'Y') {
            taskList.each { taskMap ->
                if (taskMap.custRequestTypeId == 'RF_UNPLAN_BACKLOG') {
                    resultList.add(taskMap)
                }
            }
        }
        if (backlogTypeId == 'N') {
            taskList.each { taskMap ->
                if (taskMap.custRequestTypeId == 'RF_PROD_BACKLOG') {
                    resultList.add(taskMap)
                }
            }
        }
    } else {
        taskList.each { taskMap ->
            resultList.add(taskMap)
        }
    }
    // Check party assigned
    if (partyId) {
        assignedList = resultList
        resultList = []
        assignedList.each { assignedMap ->
            workEffortId = assignedMap.taskId
            assignToList = from('WorkEffortPartyAssignment').where('workEffortId', workEffortId, 'partyId', partyId).queryList()
            if (assignToList) {
                assignedMap.partyId = assignToList[0].partyId
                resultList.add(assignedMap)
            }
        }
    } else {
        assignedList = resultList
        resultList = []
        assignedList.each { assignedMap ->
            workEffortId = assignedMap.taskId
            assignToList = from('WorkEffortPartyAssignment').where('workEffortId', workEffortId).queryList()
            if (assignToList) {
                assignedMap.partyId = assignToList[0].partyId
                resultList.add(assignedMap)
            } else {
                resultList.add(assignedMap)
            }
        }
    }

    resultList.each {
        switch (it.taskTypeId) {
            case 'SCRUM_TASK_IMPL' -> implementTaskList << it
            case 'SCRUM_TASK_INST' -> installTaskList << it
            case 'SCRUM_TASK_TEST' -> testTaskList << it
            case 'SCRUM_TASK_ERROR' -> errorTaskList << it
        }
    }

    if (implementTaskList) {
        context.implementTaskList = implementTaskList
    }
    if (installTaskList) {
        context.installTaskList = installTaskList
    }
    if (testTaskList) {
        context.testTaskList = testTaskList
    }
    if (errorTaskList) {
        context.errorTaskList = errorTaskList
    }
}
