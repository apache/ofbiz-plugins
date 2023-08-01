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

import groovy.json.JsonOutput
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityConditionList
import org.apache.ofbiz.entity.condition.EntityExpr
import org.apache.ofbiz.entity.condition.EntityOperator

import javax.servlet.http.HttpSession
import java.math.RoundingMode
import java.sql.Timestamp

final String taskStatusCompleted = 'PTS_COMPLETED'
final int defaultTaskDurationDays = 3
final String milestoneWorkEffortTypeId = 'MILESTONE'
final String permissionCheckEntity = 'PROJECTMGR'
final BigDecimal hundredPercent = new BigDecimal(100)

String projectId = parameters.projectId
GenericValue userLogin = parameters.userLogin as GenericValue

Map<String, Object> result = runService('getProject', [projectId: projectId, userLogin: userLogin])
Map<String, Object> project = result.projectInfo as Map<String, Object>
if (project == null) {
    return
}

context.chartStart = project.startDate ?: UtilDateTime.nowTimestamp() // default today's date
context.chartEnd = project.completionDate ?: UtilDateTime.addDaysToTimestamp(UtilDateTime.nowTimestamp(), 14)

List ganttList = []
List<Map<String, Object>> ganttItems = []

result = runService('getProjectPhaseList', [userLogin: userLogin, projectId: projectId])
List<Map<String, Object>> phases = result.phaseList as List<Map<String, Object>>
if (phases) {
    phases.each { phase ->
        Map<String, Object> newPhase = phase
        newPhase.phaseNr = phase.phaseId
        if (!newPhase.estimatedStartDate && newPhase.actualStartDate) {
            newPhase.estimatedStartDate = newPhase.actualStartDate
        }
        newPhase.estimatedStartDate = newPhase.estimatedStartDate ?: context.chartStart

        if (!newPhase.estimatedCompletionDate && newPhase.actualCompletionDate) {
            newPhase.estimatedCompletionDate = newPhase.actualCompletionDateDate
        }
        newPhase.estimatedCompletionDate = newPhase.estimatedCompletionDate
                ?: UtilDateTime.addDaysToTimestamp(newPhase.estimatedStartDate as Timestamp, defaultTaskDurationDays)

        newPhase.workEffortTypeId = 'PHASE'
        ganttList.add(newPhase)

        ganttItems << [
                pID: phase.phaseId as Integer,
                pName: phase.phaseSeqNum ? "${phase.phaseSeqNum}. ${phase.phaseName}" : phase.phaseName,
                pStart: '',
                pEnd: '',
                pPlanStart: '',
                pPlanEnd: '',
                pClass: 'ggroupblack',
                pLink: '',
                pMile: 0,
                pRes: '',
                pComp: 0,
                pGroup: 1,
                pParent: 0,
                pOpen: 1,
                pDepend: '',
        ]

        EntityConditionList<EntityExpr> cond = EntityCondition.makeCondition([
                EntityCondition.makeCondition('currentStatusId', EntityOperator.NOT_EQUAL, 'PTS_CANCELLED'),
                EntityCondition.makeCondition('workEffortParentId', EntityOperator.EQUALS, phase.phaseId)
        ], EntityOperator.AND)
        List<GenericValue> tasks = from('WorkEffort').where(cond).orderBy('sequenceNum', 'workEffortName').queryList()

        if (tasks) {
            tasks.each { task ->
                Map<String, Object> resultTaskInfo = runService('getProjectTask', [userLogin: userLogin, taskId: task.workEffortId])
                Map<String, Object> taskInfo = resultTaskInfo.taskInfo as Map<String, Object>
                taskInfo.taskNr = task.workEffortId
                taskInfo.phaseNr = phase.phaseId

                taskInfo.resource = ((taskInfo.plannedHours &&
                        taskStatusCompleted != taskInfo.currentStatusId &&
                        taskInfo.plannedHours > taskInfo.actualHours) ? taskInfo.plannedHours : taskInfo.actualHours) +
                        ' Hrs'

                BigDecimal durationHours = resultTaskInfo.plannedHours as BigDecimal
                if (taskStatusCompleted == taskInfo.currentStatusId) {
                    taskInfo.completion = hundredPercent
                } else {
                    BigDecimal actualHours = taskInfo.actualHours as BigDecimal
                    BigDecimal plannedHours = taskInfo.plannedHours as BigDecimal
                    taskInfo.completion = (actualHours && plannedHours)
                            ? (actualHours * hundredPercent).divide(plannedHours, 0, RoundingMode.UP)
                            : 0
                }
                if (!taskInfo.estimatedStartDate && taskInfo.actualStartDate) {
                    taskInfo.estimatedStartDate = taskInfo.actualStartDate
                }
                taskInfo.estimatedStartDate = taskInfo.estimatedStartDate ?: newPhase.estimatedStartDate

                if (!taskInfo.estimatedCompletionDate && taskInfo.actualCompletionDate) {
                    taskInfo.estimatedCompletionDate = taskInfo.actualCompletionDate
                }
                if (!taskInfo.estimatedCompletionDate && !durationHours) {
                    taskInfo.estimatedCompletionDate =
                            UtilDateTime.addDaysToTimestamp(newPhase.estimatedStartDate as Timestamp,
                                    defaultTaskDurationDays)
                } else if (!taskInfo.estimatedCompletionDate && durationHours) {
                    taskInfo.estimatedCompletionDate =
                            UtilDateTime.addDaysToTimestamp(newPhase.estimatedStartDate as Timestamp,
                                    durationHours / 8)
                }

                taskInfo.workEffortTypeId = task.workEffortTypeId
                if (security.hasEntityPermission(permissionCheckEntity, '_READ', session as HttpSession) ||
                        security.hasEntityPermission(permissionCheckEntity, '_ADMIN', session as HttpSession)) {
                    taskInfo.url = '/projectmgr/control/taskView?workEffortId=' + task.workEffortId
                } else {
                    taskInfo.url = ''
                }

                List<GenericValue> preTaskAssociations = from('WorkEffortAssoc')
                        .where('workEffortIdTo', task.workEffortId)
                        .orderBy('workEffortIdFrom')
                        .queryList()

                taskInfo.preDecessor = preTaskAssociations*.getRelatedOne('FromWorkEffort', false)
                        .collect { we -> we.workEffortId as String }
                        .join(',')

                ganttItems << [
                        pID: taskInfo.taskNr as Integer,
                        pName: taskInfo.taskSeqNum ? "${taskInfo.taskSeqNum}. ${taskInfo.taskName}" : taskInfo.taskName,
                        pStart: (taskInfo.estimatedStartDate as Timestamp).toLocalDateTime().dateString,
                        pEnd: (taskInfo.estimatedCompletionDate as Timestamp).toLocalDateTime().dateString,
                        pPlanStart: (taskInfo.estimatedStartDate as Timestamp).toLocalDateTime().dateString,
                        pPlanEnd: (taskInfo.estimatedCompletionDate as Timestamp).toLocalDateTime().dateString,
                        pClass: taskInfo.workEffortTypeId == milestoneWorkEffortTypeId ? 'gmilestone' : 'gtaskgreen',
                        pLink: taskInfo.url,
                        pMile: taskInfo.workEffortTypeId == milestoneWorkEffortTypeId ? 1 : 0,
                        pRes: taskInfo.resource,
                        pComp: taskInfo.completion,
                        pGroup: 0,
                        pParent: taskInfo.phaseNr,
                        pOpen: taskInfo.workEffortTypeId == milestoneWorkEffortTypeId ? 0 : 1,
                        pDepend: taskInfo.preDecessor ?: '',
                ]
            }
        }
    }
}

context.phaseTaskListJson = JsonOutput.toJson(ganttItems)
