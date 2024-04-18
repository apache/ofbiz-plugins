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
package org.apache.ofbiz.projectmgr

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilValidate
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityConditionBuilder
import org.apache.ofbiz.project.Various

import java.sql.Timestamp

/**
 * Create a project
 * @return 'Success response containing the projectId, error response otherwise.
 */
Map createProject() {
    Map serviceResult
    if (parameters.templateId) {
        serviceResult = run service: 'copyProjet', with: [*: parameters,
                                                          projectId: parameters.templateId]
    } else {
        serviceResult = run service: 'createWorkEffort', with: [*: parameters,
                                                                currentStatusId: 'PRJ_ACTIVE']
    }
    Map serviceMap = [*: parameters,
                      workEffortId: serviceResult.workEffortId]

    // Add roles
    if (parameters.organizationPartyId) {
        run service: 'updateProjectRole', with: [*: serviceMap,
                                                 partyId: parameters.organizationPartyId,
                                                 roleTypeId: 'INTERNAL_ORGANIZATIO']
    }
    if (parameters.clientBillingPartyId) {
        run service: 'updateProjectRole', with: [*: serviceMap,
                                                 partyId: parameters.clientBillingPartyId,
                                                 roleTypeId: 'CLIENT_BILLING']
    }

    // create new work effort's e-mail address
    if (parameters.emailAddress) {
        if (!UtilValidate.isEmail(parameters.emailAddress)) {
            return error(label('PartyUiLabels', 'PartyEmailAddressNotFormattedCorrectly'))
        }
        run service: 'createWorkEffortEmailAddress', with: serviceMap
    }
    return success([projectId: serviceMap.workEffortId, workEffortId: serviceMap.workEffortId])
}

/**
 * Update a project
 * @return 'Success response after updating, error response otherwise.
 */
Map updateProject() {
    run service: 'updateWorkEffort', with: parameters

    // Add roles
    if (parameters.organizationPartyId) {
        run service: 'updateProjectRole', with: [*: parameters,
                                                 partyId: parameters.organizationPartyId,
                                                 roleTypeId: 'INTERNAL_ORGANIZATIO']
    }
    if (parameters.clientBillingPartyId) {
        run service: 'updateProjectRole', with: [*: parameters,
                                                 partyId: parameters.clientBillingPartyId,
                                                 roleTypeId: 'CLIENT_BILLING']
    }

    // update new work effort's e-mail address
    if (parameters.emailAddress) {
        if (!UtilValidate.isEmail(parameters.emailAddress)) {
            return error(label('PartyUiLabels', 'PartyEmailAddressNotFormattedCorrectly'))
        }
        GenericValue existEmailAddress = from('WorkEffortContactMechView')
                .where(workEffortId: parameters.workEffortId,
                        contactMechTypeId: 'EMAIL_ADDRESS')
                .filterByDate()
                .queryFirst()
        if (existEmailAddress) {
            run service: 'updateWorkEffortEmailAddress', with: [*: parameters,
                                                                oldContactMechId: existEmailAddress.contactMechId]
        } else {
            run service: 'createWorkEffortEmailAddress', with: parameters
        }
    }
    return success()
}

/**
 * update/create a specif role and type for a project
 * @return 'Success response after updating, error response otherwise.
 */
Map updateProjectRole() {
    GenericValue workEffortPartyAssignment = from('WorkEffortPartyAssignment')
            .where(workEffortId: parameters.workEffortId,
                    roleTypeId: parameters.roleTypeId)
            .filterByDate()
            .queryFirst()

    if (workEffortPartyAssignment) {
        if (parameters.partyId != workEffortPartyAssignment.partyId) {
            run service: 'expireWorkEffortPartyAssignment', with: workEffortPartyAssignment.getAllFields()
        } else {
            return success()
        }
    }
    run service: 'assignPartyToWorkEffort', with: parameters
    return success()
}

/**
 * Create a project task and optionally assign
 * @return 'Success response after creation, error response otherwise.
 */
Map createProjectTask() {
    // create task
    Map serviceResult = run service: 'createWorkEffort', with: parameters
    Map serviceMap = [*: parameters,
                       workEffortId: serviceResult.workEffortId]

    // optionally assign to party
    if (parameters.partyId) {
        run service: 'assignPartyToWorkEffort', with: serviceMap
    }

    // optionally enter estimated time and required skill -->
    if (parameters.estimatedHours) {
        run service: 'createWorkEffortSkillStandard', with: [*: serviceMap,
                                                             estimatedDuration: parameters.estimatedHours]
    }
    return success([workEffortId: serviceMap.workEffortId])
}

/**
 * Update the task and when info is provided update the related information too
 * @return 'Success response after updating, error response otherwise.
 */
Map updateTaskAndRelatedInfo() {
    run service: 'updateWorkEffort', with: parameters
    if (parameters.estimatedDuration) {
        Map serviceMap = [*: parameters]
        if (!parameters.skillTypeId) {
            GenericValue workEffortSkillStandard = from('WorkEffortSkillStandard')
                    .where(workEffortId: parameters.workEffortId)
                    .cache()
                    .queryFirst()
            serviceMap.skillTypeId = workEffortSkillStandard ? workEffortSkillStandard.skillTypeId : '_NA_'
        }
        String actionNeed = (from('WorkEffortSkillStandard')
                .where(workEffortId: parameters.workEffortId,
                        skillTypeId: serviceMap.skillTypeId)
                .queryCount() > 0) ? 'update' : 'create'
        run service: "${actionNeed}WorkEffortSkillStandard", with: serviceMap
    }
    return success()
}

/**
 * Update task to resource assignment, if required create a new one by re-assigment
 * @return 'Success response after updating, error response otherwise.
 */
Map updateTaskAssigment() {
    // check if a change in partyId Or roleTypeId: need to delete and create new
    Timestamp fromDate = parameters.fromDate
    if ((parameters.newPartyId && parameters.partyId != parameters.newPartyId)
            || (parameters.newRoleTypeId && parameters.roleTypeId != parameters.newRoleTypeId)) {
        // roleType and/or partyId changed: end old and create new assign
        run service: 'expireWorkEffortPartyAssignment', with: parameters
        Map serviceResult = run service: 'createWorkEffortPartyAssignment', with: [*: parameters,
                                                                                   partyId: parameters.newPartyId,
                                                                                   statusId: 'PAS_ASSIGNED',
                                                                                   roleTypeId: parameters.newRoleTypeId]
        fromDate = serviceResult.fromDate
    } else {
        GenericValue partyAssignment = from('WorkEffortAndPartyAssign').where(parameters).queryOne()
        if (partyAssignment) {
            if (parameters.statusId == 'PAS_ENDED') {
                run service: 'expireWorkEffortPartyAssignment', with: parameters
            }
            if (parameters.statusId == 'PAS_COMPLETED') {
                return updateTaskStatusToComplete(parameters.workEffortId)
            }
        }
        String serviceName = partyAssignment ? 'updateWorkEffortPartyAssignment' : 'assignPartyToWorkEffort'
        run service: serviceName, with: parameters
    }
    return success([workEffortId: parameters.workEffortId, fromDate: fromDate])
}

/**
 * Check partyAssignments on a task, if all completes set task status to completed and set actual completionDate to now
 * @return Success response after updating, error response otherwise.
 */
Map updateTaskStatusToComplete(String workEffortId) {
    EntityCondition condition = new EntityConditionBuilder().AND {
        EQUALS(workEffortId: workEffortId)
        NOT_EQUAL(statusId: 'PAS_COMPLETED')
    }
    boolean canComplete = from('WorkEffortPartyAssignment')
            .where(condition)
            .filterByDate()
            .queryOne() == 0
    if (canComplete) {
        run service: 'updateWorkEffort', with: [
                workEffortId: workEffortId,
                currentStatusId: 'PTS_COMPLETED',
                actualCompletionDate: UtilDateTime.nowTimestamp()]

        // check for related customer request, set these too to completed
        from('CustRequestWorkEffort')
                .where(workEffortId: workEffortId)
                .queryList()
                .each {
                    run service: 'updateCustRequest', with: [custRequestId: it.custRequestId,
                                                             statusId: 'CRQ_COMPLETED']
                }
    }
    return success([workEffortId: workEffortId])
}

/**
 * Project Scheduler sets the planning dates according task requirements and available resources
 * theory behind the program
 * - - - - - - - - - - - - -
 * (program under development)
 * Assumptions for tasks and resources
 * 1. a workday has 8 hours.
 * 2. a workweek has 40 hours and 5 days.
 * 3. The order of the execution of the tasks is set by the workeffort association.
 * 4. The default start of the project is today
 * 5. default length of a task is 3 day if not planned hours entered
 *
 * The steps of the program are:
 *    1. read all tasks  and check if there are predesessors, when not set he estimated dates
 *    for critical path processing:
 *      * ES - Earliest Start time
 *      * EF - Earliest Finish time
 *      * LS - Latest Start time
 *      * LF - Latest Finish time
 *
 * EF = LF task is on the critical path
 *
 * 2. call a recursive java function to set all the dependant tasks.
 */
Map scheduleProject() {
    // find a starting point being either the estimated start date of a project or the earliest actual start date.
    EntityCondition condition = new EntityConditionBuilder().AND {
        NOT_EQUAL(actualStartDate: null)
    }
    List tasks = from('ProjectAndPhaseAndTask')
            .where(condition)
            .orderBy('-actualStartDate')
            .queryList()

    Timestamp generalStartDate = null
    Timestamp startDate = null
    String taskId = null
    // remove all estimated dates
    if (tasks) {
        startDate = tasks[0].actualStartDate
        taskId = tasks[0].workEffortId
        tasks.each {
            tasks.estimatedStartDate = null
            tasks.estimatedCompletionDate = null
        }
    } else {
        generalStartDate = UtilDateTime.nowTimestamp()
    }

    while (!generalStartDate) {
        BigDecimal highestHours
        List assocs = from('WorkEffortAssoc')
                .where(workEffortId: taskId)
                .queryList()
        if (assocs) {
            assocs.each {
                BigDecimal hours = 0
                Map task = run service: 'getProjectTask', with: [taskId: it.workEffortIdFrom]
                if (task.estimatedHours && task.actualHours) {
                    hours = (task.estimatedHours < task.actualHours) ? task.actualHours : task.estimatedHours
                } else {
                    hours = task.actualHours ?: 16
                }
                if (!highestHours || highestHours < hours) {
                    highestHours = hours
                    preDesessorId = task.taskId
                }
            }
            BigDecimal taskDays = -(highestHours / 8)
            startDate = UtilDateTime.addDaysToTimestamp(startDate, taskDays)
        } else {
            GenericValue workEffort = from('WorkEffort').where(workEffortId: taskId).queryOne()
            if (workEffort.parentWorkEffortId) {
                taskId = workEffort.parentWorkEffortId
            } else {
                generalStartDate = startDate
            }
        }
    }

    // create the tasklist
    Timestamp now = UtilDateTime.nowTimestamp()
    GenericValue project = from('WorkEffort').where(workEffortId: parameters.projectId).queryOne()
    project.getRelated('ChildWorkEffort', null, null, false)
            .each {
                it.getRelated('ChildWorkEffort', null, null, false)
                        .each { task ->
                            if (from('WorkEffortAssoc').where(workEffortIdFrom: task.workEffortId).queryOne()) {
                                // no predecessors so i can set the dates
                                run service: 'updateWorkEffort', with: [workEffortId: task.workEffortId,
                                                                        estimatedStartDate: now,
                                                                        estimatedCompletionDate: Various.calculateCompletionDate(task, now)]
                                Various.setDatesFollowingTasks(from('WorkEffort').where(workEffortId: task.workEffortId).queryOne())
                            }
                        }
            }
    return success()
}

/**
 *  Update workeffort by workEffortId and timesheetId
 * @return 'Success response after updating, error response otherwise.
 */
Map updateTimeEntryByWorkEffort() {
    if (!parameters.workEffortId || parameters.workEffortId == 'Totals') {
        return success([timesheetId: parameters.timesheetId])
    }
    GenericValue timesheet = from('Timesheet').where(parameters).queryOne()
    // check if party assigned to task, when not add with roletype of project, if assigned check status

    List assigns = from('WorkEffortPartyAssignment')
            .where(workEffortId: parameters.workEffortId,
                    partyId: timesheet.partyId)
            .filterByDate()
            .queryList()

    if (!assigns) {
        Map serviceResult = run service: 'getProjectIdAndNameFromTask', with: [taskId: parameters.workEffortId]
        GenericValue projectAssign = from('WorkEffortPartyAssignment')
                .where(workEffortId: serviceResult.projectId,
                        partyId: timesheet.partyId)
                .queryFirst()
        if (projectAssign) {
            run service: 'assignPartyToWorkEffort', with: [*: parameters,
                                                           partyId:  timesheet.partyId,
                                                           roleTypeId: projectAssign.roleTypeId,
                                                           statusId: 'PAS_ASSIGNED']
        }

        // check if the actual start date is set, when not set it to todays date
        if (!project.actualStartDate) {
            run service: 'updateWorkEffort', with: [workEffortId: parameters.workEffortId,
                                                    actualStartDate: UtilDateTime.nowTimestamp()]
        }
    }
    List timeEntries = timesheet.getRelated('TimeEntry', null, null, false)

    // update existing entries
    BigDecimal hours = 0
    timeEntries.findAll {
        it.workEffortId == parameters.workEffortId &&
        it.rateTypeId == parameters.rateTypeId
    }.each {
        // translate the date into the day number
        int dayNumber = UtilDateTime.getIntervalInDays(timesheet.fromDate, it.fromDate)
        hours = parameters["hoursDay${dayNumber}"] != null ? parameters["hoursDay${dayNumber}"] : -1
        updateTimeEntry(parameters, it, hours, userLogin.partyId, null)
        parameters["hoursDay${dayNumber}"] = -1
    }

    // process not yet done fields
    for (int dayNr = 0; dayNr < 7; dayNr++) {
        if (parameters["hoursDay${dayNr}"]) {
            updateTimeEntry(parameters, null, parameters["hoursDay${dayNr}"], userLogin.partyId,
                    UtilDateTime.addDaysToTimestamp(timesheet.fromDate, dayNr))
        }
    }

    // update the assignment status if required
    if (parameters.checkComplete == 'Y') {
        GenericValue alreadyAssign = from('WorkEffortPartyAssignment')
                .where(workEffortId: parameters.workEffortId,
                        partyId: timesheet.partyId)
                .filterByDate()
                .queryFirst()
        if (alreadyAssign.statusId != 'PAS_COMPLETED') {
            run service: 'updateTaskAssigment', with: [partyId: timesheet.partyId,
                                                       statusId: 'PAS_COMPLETED',
                                                       roleTypeId: alreadyAssign.roleTypeId,
                                                       fromDate: alreadyAssign.fromDate,
                                                       workEffortId: parameters.workEffortId]
        }
    }
    return success([timesheetId: timesheet.timesheetId])
}

/**
 * Get the projectId when a phase or task is provided.
 * @return 'Success response resolve project info, failure response otherwise.
 */
Map getProjectIdAndNameFromTask() {
    if (!parameters.taskId && !parameters.phaseId) {
        return failure(label('ProjectMgrUiLabels', 'ProjectMgrErrorProjectNotFound'))
    }
    GenericValue task = null
    String phaseId = parameters.phaseId
    if (!phaseId) {
        task = from('WorkEffort').where(workEffortId: parameters.taskId).queryOne()
        phaseId = task ? task.workEffortParentId : null
    }
    GenericValue phase = from('WorkEffort').where(workEffortId: phaseId).queryOne()
    if (phase) {
        GenericValue project = phase.getRelatedOne('ParentWorkEffort', true)

        return success([projectId: project ? project.workEffortId : '',
                        projectName: project ? project.workEffortName : '',
                        phaseId: phase ? phase.workEffortId : '',
                        phaseName: phase ? phase.workEffortName : '',
                        taskId: task ? task.workEffortId : '',
                        taskName: task ? task.workEffortName : '',
                        taskWbsId: project?.workEffortId + '.' + phase?.sequenceNum + '.' + task?.sequenceNum])
    }
    return failure(label('ProjectMgrUiLabels', 'ProjectMgrErrorProjectNotFound'))
}

/**
 * copy a project with related phases and tasks however no actual data"
 * @return 'Success response containing the newProjectId in workEffortId and projectId
 */
Map copyProject() {
    GenericValue project = from('WorkEffort').where(workEffortId: parameters.projectId).queryOne()
    if (!project) {
        return error(label('ProjectMgrUiLabels', 'ProjectMgrErrorProjectNotFound'))
    }
    parameters.workEffortName = parameters.workEffortName ?: project.workEffortName
    parameters.description = parameters.description ?: project.description
    parameters.workEffortTypeId = parameters.toTemplate == 'Y' ? 'PROJECT_TEMPLATE' : 'PROJECT'
    parameters.currentStatusId = 'PRJ_ACTIVE'
    Map serviceResult = run service: 'createWorkEffort', with: [*: parameters,
                                                                workEffortId: null]
    String newProjectId = serviceResult.workEffortId

    // copy assigned parties
    from('WorkEffortAndPartyAssign')
            .where(workEffortId: project.workEffortId)
            .filterByDate()
            .queryList()
            .each {
                run service: 'createWorkEffortPartyAssignment', with: [*: it.getAllFields(),
                                                                       workEffortId: newProjectId,
                                                                       fromDate: null,
                                                                       statusId: 'PAS_ASSIGNED']
            }

    // copy phase
    project.getRelated('ChildWorkEffort', null, null, false).each {
        parameters.workEffortTypeId = parameters.toTemplate ? 'PHASE_TEMPLATE' : 'PHASE'
        serviceResult = run service: 'createWorkEffort', with: [*: parameters,
                                                                workEffortName: it.workEffortName,
                                                                workEffortParentId: newProjectId,
                                                                currentStatusId: '_NA_',
                                                                workEffortId: null]
        String newPhaseId = serviceResult.workEffortId
        it.getRelated('ChildWorkEffort', null, null, false).each { task ->
            task.workEffortTypeId = parameters.toTemplate ? 'TASK_TEMPLATE' : 'TASK'
            task.workEffortParentId = newPhaseId
            task.currentStatusId = 'PTS_CREATED'
            run service: 'createWorkEffort', with: [*: task.getAllFields(),
                                                    workEffortId: null]
        }
    }
    return success([workEffortId: newProjectId, projectId: newProjectId])
}

/**
 * get Project information
 * @return 'Success response containing the projectInfo and projectId
 */
Map getProject() {
    GenericValue project = from('WorkEffort').where(workEffortId: parameters.projectId).cache().queryOne()
    if (!project) {
        return success()
    }
    Map highInfo = [projectId: project.workEffortId,
                    projectName: project.workEffortName,
                    projectDescription: project.description,
                    parentProjectId: project.workEffortParentId]
    ['estimatedStartDate', 'estimatedCompletionDate', 'actualStartDate', 'currentStatusId',
     'actualCompletionDate', 'scopeEnumId', 'createdStamp', 'createdDate'].each {
        highInfo.(it) = project.(it)
    }

    //loop through the related phases and tasks
    highInfo = combineInfo(highInfo, highInfo, parameters.partyId)

    // get e-mail address
    GenericValue emailAddress = from('WorkEffortContactMechView')
            .where(workEffortId: project.workEffortId,
                    contactMechTypeId: 'EMAIL_ADDRESS')
            .filterByDate()
            .queryFirst()
    if (emailAddress) {
        highInfo.emailAddress = emailAddress.infoString
    }

    highInfo = createDates(highInfo)

    return success([projectInfo: highInfo,
                    projectId: project.workEffortId])
}

/**
 * get Project Phase information
 * @return 'Success response containing the phaseList and projectId
 */
Map getProjectPhaseList() {
    GenericValue project = from('WorkEffort').where(workEffortId: parameters.projectId).cache().queryOne()
    if (!project) {
        return error(label('ProjectMgrUiLabels', 'ProjectMgrErrorProjectNotFound'))
    }

    List phaseList = []
    from('WorkEffort')
            .where(workEffortTypeId: 'PHASE', workEffortParentId: parameters.projectId)
            .orderBy('sequenceNum', 'workEffortName')
            .queryList()
            .each {
                Map highInfo = [sequenceNum: it.sequenceNum,
                                phaseId: it.workEffortId,
                                phaseSeqNum: it.sequenceNum,
                                phaseName: it.workEffortName,
                                phaseDescription: it.description,
                                scopeEnumId: it.scopeEnumId]
                highInfo = combineInfo(highInfo, it, parameters.partyId)
                highInfo = createDates(highInfo)
                phaseList << highInfo
            }
    return success([phaseList: phaseList, projectId: parameters.projectId])
}

/**
 * get Project Phase/task information
 * @return 'Success response containing the taskList and projectId
 */
Map getProjectTaskList() {
    GenericValue project = from('WorkEffort').where(workEffortId: parameters.projectId).cache().queryOne()
    if (!project) {
        return error(label('ProjectMgrUiLabels', 'ProjectMgrErrorProjectNotFound'))
    }

    List taskList = []
    from('ProjectAndPhaseAndTask')
            .where(workEffortTypeId: 'PHASE', workEffortParentId: parameters.projectId)
            .orderBy('phaseName', 'phaseSeqNum', 'sequenceNum', 'workEffortName')
            .queryList()
            .each {
                Map highInfo = [phaseName: it.phaseName,
                        phaseSeqNum: it.phaseSeqNum,
                        taskId: it.workEffortId]
                highInfo = combineInfo(highInfo, it, parameters.partyId)
                highInfo = createDates(highInfo)
                highInfo.workEffortId = it.workEffortId
                highInfo.workEffortName = it.workEffortName
                highInfo.sequenceNum = it.sequenceNum
                taskList << highInfo
            }
    return success([taskList: taskList, projectId: parameters.projectId])
}

/**
 * Resolve information on task
 * @return 'Success response containing the taskInfo
 */
Map getProjectTask() {
    GenericValue task = from('WorkEffort').where(workEffortId: parameters.taskId).cache().queryOne()
    Map highInfo = [taskId: task.workEffortId,
            taskSeqNum: task.sequenceNum,
            taskName: task.workEffortName,
            taskDescription: task.description,
            scopeEnumId: task.scopeEnumId,
            workEffortParentId: task.workEffortParentId]
    return success([taskInfo: combineInfo(highInfo, task, parameters.partyId)])
}

/**
 * get Project information by party member
 * @return 'Success response containing the projectParties
 */
Map getProjectsByParties() {
    EntityCondition condition = new EntityConditionBuilder().AND {
        if (parameters.projectId) {
            EQUALS(projectId: parameters.projectId)
        }
        if (parameters.partyId) {
            EQUALS(partyId: parameters.partyId)
        } else {
            NOT_EQUAL(partyId: null)
        }
    }
    List tasks = from('ProjectAndPhaseAndTaskParty')
            .where(condition)
            .orderBy('projectId', 'partyId')
            .queryList()
    List projectParties = []
    Map projectParty = [:]
    for (GenericValue task: tasks) {
        if (projectParty && task.partyId != projectParty.partyId) {
            projectParties << projectParty
            projectParty = [:]
        }
        if (!projectParty) {
            projectParty.partyId = task.partyId
            GenericValue partyNameView = from('PartyNameView').where(partyId: task.partyId).cache().queryOne()
            if (partyNameView) {
                projectParty.partyName = "${partyNameView.lastName ?: ''},${partyNameView.firstName ?: ''}${partyNameView.groupName ?: ''}"
            }
            projectParty.roleTypeId = task.roleTypeId
            projectParty.fromDate = task.fromDate
            projectParty.thruDate = task.thruDate
        }
        // get the planned/actual hours
        projectParty.putAll(getHours(task, task, task.partyId))
        if (!projectParty) {
            projectParties << projectParty
        }
    }

    return success([projectParties: projectParties])
}

/**
 * get task information by party member
 * @return 'Success response containing the taskParties
 */
Map getTasksByParties() {
    // get the list of tasks optionaly selected for a party
    EntityCondition condition = new EntityConditionBuilder().AND {
        if (parameters.partyId) {
            EQUALS(partyId: parameters.partyId)
        }
        if (parameters.workEffortId) {
            EQUALS(workEffortId: parameters.workEffortId)
        }
    }
    Map taskParty = [:]
    List taskParties = []
    from('WorkEffortPartyAssignment')
            .where(condition)
            .filterByDate()
            .orderBy('workEffortId', 'partyId')
            .queryList()
            .each {
                if (taskParty && taskParty.partyId != it.partyId) {
                    taskParties << taskParty
                    taskParty = [:]
                }
                if (!taskParty) {
                    taskParty.partyId = it.partyId
                    GenericValue partyNameView = from('PartyNameView').where(partyId: it.partyId).cache().queryOne()
                    if (partyNameView) {
                        taskParty.partyName = "${partyNameView.lastName ?: ''},${partyNameView.firstName ?: ''}${partyNameView.groupName ?: ''}"
                    }
                    taskParty.roleTypeId = it.roleTypeId
                    taskParty.statusId = it.statusId
                    taskParty.fromDate = it.fromDate
                    taskParty.thruDate = it.thruDate
                }

                // get the planned hours
                taskParty.putAll(getHours(taskParty, it.getRelatedOne('WorkEffort', true), it.partyId))
            }
    if (taskParties) {
        taskParties << taskParty
    }
    return success([taskParties: taskParties])
}

/**
 * Creates TimeEntry and searches for a timesheetId if not provided
 * @return 'Success response containing the timesheetId and fromDate, error response otherwise.
 */
Map createTimeEntryInTimesheet() {
    String timesheetId = parameters.timesheetId
    if (parameters.fromDate && !parameters.timesheetId) {
        GenericValue timesheet = from('Timesheet').where(partyId: parameters.partyId).filterByDate().queryFirst()
        if (timesheet) {
            if (timesheet.statusId != 'TIMESHEET_IN_PROCESS') {
                return error(label('ProjectMgrUiLabels', 'ProjectMgrCannotAddToTimesheet'))
            }
            timesheetId = timesheet.timesheetId
        } else {
            // create new timesheet
            Map serviceReturn = run service: 'createTimesheetForThisWeek', with: [*: parameters,
                                                                                  requiredDate: parameters.fromDate]
            timesheetId = serviceReturn.timesheetId
        }
    }
    // get role for this party in this project
    if (parameters.roleTypeId) {
        GenericValue taskRole = from('ProjectPartyAndPhaseAndTask')
                .where(partyId: parameters.partyId,
                        workEffortId: parameters.workEffortId)
                .queryFirst()
        run service: 'assignPartyToWorkEffort', with: [*: taskRole.getAllFields(),
                                                       statusId: 'PAS_ASSIGNED']
    }
    Map serviceResult = run service: 'createTimeEntry', with: [*: parameters,
                                                               timesheetId: timesheetId]
    return success([fromDate: serviceResult.fromDate, timesheetId: timesheetId])
}

/**
 * Add all reported time on all completed timeSheets from all workEfforts for a project
 * @return 'Success response containing the invoiceId, error response otherwise.
 */
Map addProjectTimeToInvoice() {
    // Recreate the invoice if still in preparation in order to correct errors.
    boolean createInvoice = !parameters.reCreate
    if (parameters.reCreate == 'Y') {
        GenericValue invoice = from('Invoice').where(parameters).queryOne()
        if (!invoice) {
            return error(label('WorkEffortUiLabels', 'WorkEffortTimesheetCannotFindInvoice'))
        }
        //FIXME <call-simple-method method-name="checkInvoiceStatusInProgress"
        // xml-resource="component://accounting/minilang/invoice/InvoiceServices.xml"/>
        EntityCondition removeCond = EntityCondition.makeCondition('invoiceId', parameters.invoiceId)
        delegator.storeByCondition('TimeEntry',
                [invoiceId: null, invoiceItemSeqId: null],
                removeCond)
        delegator.removeByCondition('InvoiceItem', removeCond)
        createInvoice = true //do not create, only add
    }
    EntityCondition condition = new EntityConditionBuilder().AND {
        EQUALS(projectId: parameters.projectId)
        EQUALS(invoiceId: null)
        EQUALS(timesheetStatusId: 'TIMESHEET_COMPLETED')
        if (parameters.thruDate) {
            LESS_THAN(fromDate: parameters.thruDate)
        }
    }
    List tasks = from('ProjectPhaseTaskAndTimeEntryTimeSheet')
            .where(condition)
            .orderBy('workEffortId')
            .queryList()
    if (!tasks) {
        return error(label('WorkEffortUiLabels', 'ProjectMgrNoTimeentryItemsFound'))
    }
    String invoiceId = parameters.invoiceId
    if (createInvoice) {
        Map serviceResult = run service: 'addWorkEffortTimeToNewInvoice', with: [*: parameters,
                                                                                 workEffortId: parameters.projectId,
                                                                                 combineInvoiceItem: 'Y']
        invoiceId = serviceResult.invoiceId
    }
    tasks*.workEffortId.unique().each {
        run service: 'addWorkEffortTimeToInvoice', with: [*: parameters,
                                                          invoiceId: invoiceId,
                                                          combineInvoiceItem: 'Y',
                                                          taskId: it]
    }
    return success([invoiceId: invoiceId])
}

/**
 * SECA to add either project-testing or -approval parties to a task when a task is set to complete
 * @return 'Success response after adding, error response otherwise.
 */
Map addValidationPartiesToTask() {
    // check if this is the last party which completed his task
    EntityCondition condition = new EntityConditionBuilder().AND {
        EQUALS(workEffortId: parameters.workEffortId)
        NOT_EQUAL(statusId: 'PAS_COMPLETED')
        NOT_EQUAL(partyId: parameters.partyId)
    }
    if (from('addValidationPartiesToTask')
            .where(condition)
            .queryCount() == 0) {
        Map serviceResult = run service: 'getProjectIdAndNameFromTask', with: [taskId: parameters.workEffortId]
        String projectId = serviceResult.projectId

        // see who is responsible for testing/validation in this project
        condition = new EntityConditionBuilder().AND {
            EQUALS(workEffortId: projectId)
            NOT_EQUAL(partyId: parameters.partyId)
            IN(roleTypeId: ['PROVIDER_VALIDATOR', 'PROVIDER_TESTER'])
        }
        List assigns = from('WorkEffortPartyAssignment')
                .where(condition)
                .filterByDate()
                .queryList()
        if (assigns) {
            assigns.each {
                run service: 'createWorkEffortPartyAssignment', with: [*: it.getAllFields(),
                                                                       workEffortId: parameters.workEffortId,
                                                                       statusId: 'PAS_ASSIGNED']
            }
        } else {
            logInfo('No validation parties defined in this project: no validation parties added....')
        }
    } else {
        logInfo('Not the last party who completes his task: validation parties not added....')
    }
    return success()
}

// Internal functions
/**
 * combine lower level Actual hours info.
 * @return Map highInfo with actual hours
 */
private Map combineActualHours(Map highInfo, String partyId) {
    /* to calculate actual hours : the declared number of hours in time entry should be multiplied by the
     *    max percentage declared in PartyRate if a valid party rate can be found for the party associated to a
     *    the timesheet associated to this time entry and has the same rateType as this timeEntry
     * actualHoursOriginal is the total of hours in time entries without application of percentage declared in partyRate
     */
    EntityCondition condition = new EntityConditionBuilder().AND {
        if (highInfo.projectId) {
            EQUALS(projectId: highInfo.projectId)
        }
        if (highInfo.phaseId) {
            EQUALS(phaseId: highInfo.phaseId)
        }
        if (highInfo.taskId) {
            EQUALS(taskId: highInfo.taskId)
        }
        if (partyId) {
            EQUALS(hoursPartyId: partyId)
        }
    }
    Map notRatedValue = from('ProjectPhaseTaskActualNotRatedHoursView')
            .where(condition)
            .select('totalOriginalHours')
            .queryFirst()
    BigDecimal originalHours = notRatedValue.totalOriginalHours

    /* II- get total for timeEntries having a partyRate that should be applied
       before applying rate (totalOriginalHours)
       after applying rate (totalRatedHours)
     */
    Map ratedValue = from('ProjectPhaseTaskActualRatedHoursView')
            .where(condition)
            .select('totalOriginalHours', 'totalRatedHours')
            .queryFirst()
    // not used ratedValue.totalRatedHours  because not works, reason seem to be totalRatedHours is a calculated field ???
    highInfo.actualHours = (ratedValue.totalRatedHours ?: 0) + (originalHours ?: 0)
    highInfo.originalActualHours = originalHours ?: 0 + (ratedValue.totalOriginalHours ?: 0)

    // do the same but for non-billed hours
    // first get not rated hours
    condition = new EntityConditionBuilder().AND {
        if (highInfo.projectId) {
            EQUALS(projectId: highInfo.projectId)
        }
        if (highInfo.phaseId) {
            EQUALS(phaseId: highInfo.phaseId)
        }
        if (highInfo.taskId) {
            EQUALS(taskId: highInfo.taskId)
        }
        if (partyId) {
            EQUALS(hoursPartyId: partyId)
        }
        EQUALS(invoiceId: null)
    }
    notRatedValue = from('ProjectPhaseTaskActualNotRatedHoursView')
            .where(condition)
            .select('totalOriginalHours')
            .queryFirst()
    BigDecimal actualNonBilledHours = 0
    if (notRatedValue.totalOriginalHours) {
        // second get non billed for entries having an invoiceId
        ratedValue = from('ProjectPhaseTaskActualRatedHoursView')
                .where(condition)
                .select('totalOriginalHours', 'totalRatedHours')
                .queryFirst()
        actualNonBilledHours = notRatedValue.totalOriginalHours + (ratedValue.totalOriginalHours ?: 0)
    }
    highInfo.actualNonBilledHours = actualNonBilledHours
    return highInfo
}

/**
 * combine lower level status, dates of tasks.
 * @return Map highInfo
 */
private Map combineInfo(Map highInfo, Map lowInfo, String partyId) {
    highInfo.currentStatusId = combineStatusInfo(highInfo, lowInfo)
    highInfo = combineDatesAndPlannedHoursInfo(highInfo)
    return combineActualHours(highInfo, partyId)
}

/**
 * Merge the estimated and actual dates
 * @return Map highInfo
 */
private Map createDates(Map highInfo) {
    // input/output is 'highInfo map
    // create dates taking the last known one to save space on the list
    highInfo.startDate = highInfo.actualStartDate ?: highInfo.estimatedStartDate
    highInfo.completionDate = highInfo.actualCompletionDate ?: highInfo.estimatedCompletionDate
    return highInfo
}

/**
 * Get the planned and estimated hours for a task and add to the highInfo map
 * @return Map highInfo with hours
 */
private Map getHours(Map highInfo, Map lowInfo, String partyId) {
    // input is 'lowInfo' map output is 'highInfo' map
    // PartyId: if provided only the hours of that party
    // add the planned hours together
    from('WorkEffortSkillStandard')
            .where(workEffortId: lowInfo.workEffortId)
            .queryList()
            .each {
                if (it.estimatedDuration) {
                    highInfo.plannedHours = (highInfo.plannedHours ?: 0) + it.estimatedDuration
                }
            }

    // get the actual billed / non billed hours
    from('TimeEntry')
            .where(workEffortId: lowInfo.workEffortId)
            .queryList()
            .each {
                if (it.hours) {
                    GenericValue timesheet = it.getRelatedOne('Timesheet', true)

                    // check if only a part of the registered hours need to be taken into account
                    BigDecimal originalActualHours = it.hours
                    GenericValue partyRate = from('PartyRate')
                            .where(partyId: timesheet.partyId,
                                    rateTypeId: it.rateTypeId)
                            .filterByDate(it.fromDate)
                            .queryFirst()
                    if (partyRate.percentageUsed) {
                        it.actualHours = (it.actualHours * partyRate.percentageUsed) / 100
                    }
                    if (partyId && timesheet.partyId == partyId) {
                        highInfo.originalActualHours = originalActualHours + (highInfo.originalActualHours ?: 0)
                        highInfo.actualHours = it.actualHours + (highInfo.actualHours ?: 0)
                        if (!it.invoiceId) {
                            highInfo.actualNonBilledHours = it.hours + (highInfo.actualNonBilledHours ?: 0)
                        }
                    }

                    // keep also a general total for the actual hours of all participants
                    highInfo.actualTotalHours = it.hours + highInfo.actualTotalHours ?: 0
                    if (!it.invoiceId) {
                        highInfo.actualNonBilledTotalHours = it.hours + (highInfo.actualNonBilledTotalHours ?: 0)
                    }
                }
            }
    return highInfo
}

/**
 * update a timeEntry in silence
 * @return nothing
 */
private void updateTimeEntry(Map parameters, GenericValue timeEntry, BigDecimal hours, String partyId, Timestamp fromDate) {
    if (hours == -1 ) {
        return
    }
    if (timeEntry && timeEntry.timeEntryId) {
        if (hours == 0) {
            run service: 'deleteTimeEntry', with: [timeEntryId: timeEntry.timeEntryId]
        } else {
            run service: 'updateTimeEntry', with: [hours: hours,
                                                   timeEntryId: timeEntry.timeEntryId,
                                                   rateTypeId: timeEntry.rateTypeId,
                                                   partyId: timeEntry.partyId]
        }
    } else {
        run service: 'createTimeEntry', with: [*: parameters,
                                               hours: hours,
                                               fromDate: fromDate,
                                               partyId: partyId]
    }
}

/**
 * combine lower level status
 * The status for a project or phase is
 * * IN_PROGRESS if at least one task still in progress
 * * COMPLETED if all task are either completed or cancelled
 * * CREATED if other conditions does not apply
 * For a task the status is
 * * IN_PROGRESS if it has at least one resource and at least a time entry
 * * ASSIGNED if it has at least one resource but no time entry associated
 * @return statusId after analyse
 */
private String combineStatusInfo(Map highInfo, Map lowInfo = null) {
    if (lowInfo && lowInfo.workEffortTypeId == 'TASK') {
        if (lowInfo.currentStatusId == 'PTS_CREATED' &&
                from('WorkEffortPartyAssignment')
                        .where(workEffortId: lowInfo.workEffortId)
                        .filterByDate()
                        .queryCount() > 0) {
            return (from('TimeEntry')
                    .where(workEffortId: lowInfo.workEffortId)
                    .queryCount() > 0) ? 'PTS_CREATED_AS' : 'PTS_CREATED_IP'
        }
        return lowInfo.currentStatusId
    }
    EntityCondition condition = new EntityConditionBuilder().AND {
        if (highInfo.projectId) {
            EQUALS(projectId: highInfo.projectId)
        }
        if (highInfo.phaseId) {
            EQUALS(phaseId: highInfo.phaseId)
        }
        if (highInfo.taskId) {
            EQUALS(taskId: highInfo.taskId)
        }
    }
    long tasksCount = from('ProjectPhaseTaskAssignmentView').where(condition).queryCount()
    EntityCondition completedCond = new EntityConditionBuilder().AND {
        IN(taskStatusId: ['PTS_CANCELLED', 'PTS_COMPLETED'])
    }
    long completedTasks = from('ProjectPhaseTaskAssignmentView').where(condition).having(completedCond).queryCount()
    if (completedTasks == tasksCount) {
        return 'PTS_COMPLETED'
    }

    EntityCondition assignedCond = new EntityConditionBuilder().AND {
        EQUALS(entriesCount: 0L)
        GREATER_THAN_EQUAL_TO(resourceCount: 1L)
        NOT_IN(taskStatusId: ['PTS_CANCELLED', 'PTS_COMPLETED'])
    }
    long assignedTasks = from('ProjectPhaseTaskAssignmentView').where(condition).having(assignedCond).queryCount()
    EntityCondition relatedCond = new EntityConditionBuilder().AND {
        GREATER_THAN_EQUAL_TO(entriesCount: 1L)
        GREATER_THAN_EQUAL_TO(resourceCount: 1L)
        NOT_IN(taskStatusId: ['PTS_CANCELLED', 'PTS_COMPLETED'])
    }
    long progressTasks = from('ProjectPhaseTaskAssignmentView').where(condition).having(relatedCond).queryCount()
    return (progressTasks > 0 || assignedTasks > 0) ? 'PTS_CREATED_IP' : 'PTS_CREATED'
}

/**
 * combine lower level start end dates and planned hours for a project, phase or task
 * @return Map containning date info
 */
private Map combineDatesAndPlannedHoursInfo(Map highInfo) {
    EntityCondition condition = new EntityConditionBuilder().AND {
        if (highInfo.projectId) {
            EQUALS(projectId: highInfo.projectId)
        }
        if (highInfo.phaseId) {
            EQUALS(phaseId: highInfo.phaseId)
        }
        if (highInfo.taskId) {
            EQUALS(taskId: highInfo.taskId)
        }
    }

    // Now used TimeEntries to update (or not) actual start and end Date
    Map summaryInfo = from('ProjectPhaseTaskSklSumView')
            .where(condition)
            .select('projectId', 'estimatedStartDate', 'actualStartDate',
                    'estimatedCompletionDate', 'actualCompletionDate', 'plannedHours', 'priority')
            .queryFirst()
    if (summaryInfo) {
        highInfo.putAll(summaryInfo.getAllFields())
    }

    // update actual start date by the min date form sub tasks associated TimeEntries
    // (if before actualStartDate field)
    Map timeEntriesInfo = from('ProjectPhaseTaskActualEntrySumView')
            .where(condition)
            .select('actualEntryStartDate')
            .queryFirst()
    if (timeEntriesInfo && highInfo.actualStartDate > timeEntriesInfo.actualEntryStartDate) {
        highInfo.actualStartDate = timeEntriesInfo.actualEntryStartDate
    }
    return highInfo
}
