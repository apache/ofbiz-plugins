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

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilMisc
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityComparisonOperator
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityConditionBuilder
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.util.EntityFindOptions
import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.webapp.website.WebSiteWorker

uiLabelMap = UtilProperties.getResourceBundleMap('scrumUiLabels', locale)
partyId = parameters.partyId ?: parameters.userLogin.partyId

// show the requested timesheet, otherwise the current , if not exist create
timesheet = null
timesheetId = parameters.timesheetId
if (timesheetId) {
    timesheet = from('Timesheet').where('timesheetId', timesheetId).queryOne()
    partyId = timesheet.partyId // use the party from this timesheet
} else {
    // make sure because of timezone changes, not a duplicate timesheet is created
    midweek = UtilDateTime.addDaysToTimestamp(UtilDateTime.getWeekStart(UtilDateTime.nowTimestamp()), 3)
    entryExprs = EntityCondition.makeCondition([
            EntityCondition.makeCondition('fromDate', EntityComparisonOperator.LESS_THAN, midweek),
            EntityCondition.makeCondition('thruDate', EntityComparisonOperator.GREATER_THAN, midweek),
            EntityCondition.makeCondition('partyId', EntityComparisonOperator.EQUALS, partyId)
    ], EntityOperator.AND)
    entryIterator = from('Timesheet').where(entryExprs).queryIterator()
    timesheet = entryIterator.next()
    entryIterator.close()
    if (timesheet == null) {
        result = runService('createProjectTimesheet', ['userLogin': parameters.userLogin, 'partyId': partyId])
        if (result && result.timesheetId) {
            timesheet = from('Timesheet').where('timesheetId', result.timesheetId).queryOne()
        }
    }
}
if (!timesheet) {
    return
}
context.timesheet = timesheet
context.weekNumber = UtilDateTime.weekNumber(timesheet.fromDate)

// get the user names
context.partyNameView = from('PartyNameView').where('partyId', partyId).queryOne()
// get the default rate for this person
rateTypes = from('PartyRate').where('partyId', partyId, 'defaultRate', 'Y').filterByDate().queryList()
if (rateTypes) {
    context.defaultRateTypeId = rateTypes[0].rateTypeId
}

entries = []
Map entry = [timesheetId: timesheet.timesheetId]
Map leaveEntry = [timesheetId: timesheet.timesheetId]
taskTotal = 0.00
planTotal = 0.00
leaveTaskTotal = 0.00
leavePlanTotal = 0.00
day0Total = 0.00; day1Total = 0.00; day2Total = 0.00; day3Total = 0.00; day4Total = 0.00; day5Total = 0.00; day6Total = 0.00
pDay0Total = 0.00; pDay1Total = 0.00; pDay2Total = 0.00; pDay3Total = 0.00; pDay4Total = 0.00; pDay5Total = 0.00; pDay6Total = 0.00
pHours = 0.00
timeEntry = null
lastTimeEntry = null
emplLeaveEntry = null
lastEmplLeaveEntry = null

// retrieve work effort data when the workeffortId has changed.
void retrieveWorkEffortData() {
    // get the planned number of hours
    entryWorkEffort = lastTimeEntry.getRelatedOne('WorkEffort', false)
    if (entryWorkEffort) {
        plannedHours = entryWorkEffort.getRelated('WorkEffortSkillStandard', null, null, false)
        pHours = 0.00
        plannedHours.each { plannedHour ->
            if (plannedHour.estimatedDuration) {
                pHours += plannedHour.estimatedDuration
            }
        }
        estimatedHour = 0.00

        estimatedMilliSeconds = entryWorkEffort.estimatedMilliSeconds
        if (estimatedMilliSeconds > 0) {
            estimatedHour = estimatedMilliSeconds / 3600000
        }
        entry.plannedHours = estimatedHour
        //entry.plannedHours = pHours
        planHours = 0.0
        planHours = lastTimeEntry.planHours
        lastTimeEntry = from('TimeEntry')
                .where(workEffortId: lastTimeEntry.workEffortId, partyId: partyId)
                .orderBy('-fromDate')
                .queryFirst()
        if (planHours < 1) {
            planHours = estimatedHour
        }
        entry.planHours = lastTimeEntry.planHours
        actualHours = entryWorkEffort.getRelated('TimeEntry', null, null, false)
        aHours = 0.00
        actualHours.each { actualHour ->
            if (actualHour.hours) {
                aHours += actualHour.hours
            }
        }
        entry.actualHours = aHours
        // get party assignment data to be able to set the task to complete
        workEffortPartyAssigns = EntityUtil.filterByDate(entryWorkEffort.getRelated('WorkEffortPartyAssignment', [partyId: partyId], null, false))
        if (workEffortPartyAssigns) {
            workEffortPartyAssign = workEffortPartyAssigns[0]
            entry.fromDate = workEffortPartyAssign.getTimestamp('fromDate')
            entry.roleTypeId = workEffortPartyAssign.roleTypeId
            if (workEffortPartyAssign.statusId == 'SCAS_COMPLETED') {
                entry.checkComplete = 'Y'
            }
        } else {
            if (entryWorkEffort.currentStatusId == 'STS_COMPLETED') {
                entry.checkComplete = 'Y'
            }
        }

        // get project/phase information
        entry.workEffortId = entryWorkEffort.workEffortId
        entry.workEffortName = entryWorkEffort.workEffortName
        result = run service: 'getProjectInfoFromTask', with: [taskId: entryWorkEffort.workEffortId]
        entry.phaseId = result.phaseId
        entry.phaseName = result.phaseName
        entry.projectId = result.projectId
        entry.projectName = result.projectName
        entry.taskWbsId = result.taskWbsId
    }
    entry.acualTotal = taskTotal
    entry.planTotal = planTotal
    //Drop Down Lists
    if ('Y' != entry.checkComplete) {
        if (aHours > 0.00) {
            entries.add(entry)
        }
    } else {
        entries.add(entry)
    }
    // start new entry
    taskTotal = 0.00
    planTotal = 0.00
    entry = [timesheetId: timesheet.timesheetId]
}

timesheet
        .getRelated('TimeEntry', null, ['workEffortId', 'rateTypeId', 'fromDate'], false)
        .each { timeEntry ->
            if (lastTimeEntry &&
                    (lastTimeEntry.workEffortId != timeEntry.workEffortId ||
                            lastTimeEntry.rateTypeId != timeEntry.rateTypeId)) {
                retrieveWorkEffortData()
            }
            if (timeEntry.hours) {
                String dayNumber = 'd' + (timeEntry.fromDate.getTime() - timesheet.fromDate.getTime()) / (24 * 60 * 60 * 1000)
                BigDecimal hours = timeEntry.hours
                entry.(dayNumber) = hours
                switch (dayNumber) {
                    case 'd0' -> day0Total += hours
                    case 'd1' -> day1Total += hours
                    case 'd2' -> day2Total += hours
                    case 'd3' -> day3Total += hours
                    case 'd4' -> day4Total += hours
                    case 'd5' -> day5Total += hours
                    case 'd6' -> day6Total += hours
                }
                taskTotal += hours
            }
            if (timeEntry.planHours) {
                String dayNumber = 'pd' + (timeEntry.fromDate.getTime() - timesheet.fromDate.getTime()) / (24 * 60 * 60 * 1000)
                BigDecimal planHours = timeEntry.planHours
                entry.(dayNumber) = planHours
                switch (dayNumber) {
                    case 'pd0' -> pDay0Total += planHours
                    case 'pd1' -> pDay1Total += planHours
                    case 'pd2' -> pDay2Total += planHours
                    case 'pd3' -> pDay3Total += planHours
                    case 'pd4' -> pDay4Total += planHours
                    case 'pd5' -> pDay5Total += planHours
                    case 'pd6' -> pDay6Total += planHours
                }
                planTotal += planHours
            }
            lastTimeEntry = timeEntry
            entry.rateTypeId = timeEntry.rateTypeId
        }

//retrieve Empl Leave data.
void retrieveEmplLeaveData(Map leaveEntry) {
    if (lastEmplLeaveEntry) {
        //service get Hours
        Map result = run service: 'getPartyLeaveHoursForDate', with:
                [partyId: lastEmplLeaveEntry.partyId,
                 leaveTypeId: lastEmplLeaveEntry.leaveTypeId,
                 fromDate: lastEmplLeaveEntry.fromDate]
        if (result.hours) {
            leaveEntry.plannedHours = result.hours
            leaveEntry.planHours = result.hours
        }
        if (lastEmplLeaveEntry.leaveStatus == 'LEAVE_APPROVED') {
            leaveEntry.checkComplete = 'Y'
        }
        leaveEntry.partyId = lastEmplLeaveEntry.partyId
        leaveEntry.leaveTypeId = lastEmplLeaveEntry.leaveTypeId
        leaveEntry.leavefromDate = lastEmplLeaveEntry.fromDate
        leaveEntry.leavethruDate = lastEmplLeaveEntry.thruDate
        leaveEntry.description = lastEmplLeaveEntry.description
    }
    leaveEntry.acualTotal = leaveTaskTotal
    leaveEntry.planHours = leavePlanTotal
    leaveEntry.actualHours = leaveTaskTotal
    //Drop Down Lists
    entries << leaveEntry
    // start new leaveEntry
    leaveTaskTotal = 0.00
    leavePlanTotal = 0.00
    leaveEntry = [timesheetId: timesheet.timesheetId]
}

// define condition
from('EmplLeave')
        .where(new EntityConditionBuilder()
                .AND {
                    GREATER_THAN_EQUAL_TO(fromDate: timesheet.fromDate)
                    LESS_THAN_EQUAL_TO(thruDate: timesheet.fromDate)
                    EQUALS(partyId: partyId)
                })
        .cursorScrollInsensitive()
        .distinct()
        .queryIterator()
        .each {
            if (emplLeaveEntry != void) {
                lastEmplLeaveEntry = emplLeaveEntry
            }
            emplLeaveEntry = it
            if (lastEmplLeaveEntry
                    && (lastEmplLeaveEntry.leaveTypeId != emplLeaveEntry.leaveTypeId
                    || lastEmplLeaveEntry.partyId != emplLeaveEntry.partyId)) {
                retrieveEmplLeaveData(leaveEntry)
            }
            Map resultHours = run service: 'getPartyLeaveHoursForDate', with:
                    [partyId: emplLeaveEntry.partyId, leaveTypeId: emplLeaveEntry.leaveTypeId, fromDate: emplLeaveEntry.fromDate]

            if (resultHours.hours) {
                String leaveDayNumber = 'd' + (emplLeaveEntry.fromDate.getTime() - timesheet.fromDate.getTime()) / (24 * 60 * 60 * 1000)
                resultHours = run service: 'getPartyLeaveHoursForDate', with:
                        [partyId: emplLeaveEntry.partyId, leaveTypeId: emplLeaveEntry.leaveTypeId, fromDate: emplLeaveEntry.fromDate]
                BigDecimal leaveHours = resultHours.hours
                switch (leaveDayNumber) {
                    case 'd0' -> day0Total += leaveHours
                    case 'd1' -> day1Total += leaveHours
                    case 'd2' -> day2Total += leaveHours
                    case 'd3' -> day3Total += leaveHours
                    case 'd4' -> day4Total += leaveHours
                    case 'd5' -> day5Total += leaveHours
                    case 'd6' -> day6Total += leaveHours
                }
                leaveEntry.(leaveDayNumber) = leaveHours
                leaveTaskTotal += leaveHours
            }
            if (resultHours.hours) {
                String leavePlanDay = 'pd' + (emplLeaveEntry.fromDate.getTime() - timesheet.fromDate.getTime()) / (24 * 60 * 60 * 1000)
                Map resultPlanHours = run service: 'getPartyLeaveHoursForDate', with:
                        [partyId: emplLeaveEntry.partyId, leaveTypeId: emplLeaveEntry.leaveTypeId, fromDate: emplLeaveEntry.fromDate]
                BigDecimal leavePlanHours = resultPlanHours.hours
                switch (leavePlanDay) {
                    case 'pd0' -> pDay0Total += leavePlanHours
                    case 'pd1' -> pDay1Total += leavePlanHours
                    case 'pd2' -> pDay2Total += leavePlanHours
                    case 'pd3' -> pDay3Total += leavePlanHours
                    case 'pd4' -> pDay4Total += leavePlanHours
                    case 'pd5' -> pDay5Total += leavePlanHours
                    case 'pd6' -> pDay6Total += leavePlanHours
                }
                leaveEntry.(leavePlanDay) = leavePlanHours
                leavePlanTotal += leavePlanHours
            }
            leaveEntry.rateTypeId = 'STANDARD'
        }

if (timeEntry) {
    lastTimeEntry = timeEntry
    retrieveWorkEffortData()
}
if (emplLeaveEntry) {
    lastEmplLeaveEntry = emplLeaveEntry
    retrieveEmplLeaveData(leaveEntry)
}

// add empty lines if timesheet not completed
if (timesheet.statusId != 'TIMESHEET_COMPLETED') {
    for (c = 0; c < 3; c++) { // add empty lines
        entries << [timesheetId: timesheet.timesheetId]
    }
}

// add the totals line if at least one entry
if (timeEntry || emplLeaveEntry) {
    entry = [timesheetId: timesheet.timesheetId]
    entry.with {
        d0 = day0Total
        d1 = day1Total
        d2 = day2Total
        d3 = day3Total
        d4 = day4Total
        d5 = day5Total
        d6 = day6Total
        pd0 = pDay0Total
        pd1 = pDay1Total
        pd2 = pDay2Total
        pd3 = pDay3Total
        pd4 = pDay4Total
        pd5 = pDay5Total
        pd6 = pDay6Total
        phaseName = uiLabelMap.ScrumTotals
        workEffortId = 'Totals'
        total = day0Total + day1Total + day2Total + day3Total + day4Total + day5Total + day6Total
    }
    entries << entry
}
context.timeEntries = entries
// get all timesheets of this user, including the planned hours
timesheetsDb = from('Timesheet').where(partyId: partyId).orderBy('-fromDate').queryList()
timesheets = []
timesheetsDb.each { timesheetDb ->
    //get hours from EmplLeave
    findOpts = new EntityFindOptions(true, EntityFindOptions.TYPE_SCROLL_INSENSITIVE, EntityFindOptions.CONCUR_READ_ONLY, true)
    leaveExprsList = [
            EntityCondition.makeCondition('fromDate', EntityOperator.GREATER_THAN_EQUAL_TO, timesheetDb.fromDate),
            EntityCondition.makeCondition('fromDate', EntityOperator.LESS_THAN_EQUAL_TO, timesheetDb.thruDate),
            EntityCondition.makeCondition('partyId', EntityOperator.EQUALS, partyId)]
    leaveHours = 0.00

    from('EmplLeave').where(leaveExprsList).cursorScrollInsensitive().distinct().queryIterator().each {
        emplLeaveEntry = it
        Map resultHour = run service: 'getPartyLeaveHoursForDate', with:
                [partyId: emplLeaveEntry.partyId, leaveTypeId: emplLeaveEntry.leaveTypeId, fromDate: emplLeaveEntry.fromDate]
        if (resultHour.hours) {
            leaveHours += resultHour.hours.doubleValue()
        }
    }
    //get hours from TimeEntry
    timesheet = [*: timesheetDb]
    entries = timesheetDb.getRelated('TimeEntry', null, null, false)
    hours = 0.00
    entries.each { timeEntry ->
        if (timeEntry.hours) {
            hours += timeEntry.hours.doubleValue()
        }
    }
    timesheet.weekNumber = UtilDateTime.weekNumber(timesheetDb.fromDate)
    timesheet.hours = hours + leaveHours
    timesheets << timesheet
}
context.timesheets = timesheets

// get existing task that no assign
taskList = []
projectSprintBacklogAndTaskList = []
backlogIndexList = []
from('ProjectSprintBacklogAndTask')
        .where(sprintTypeId: 'SCRUM_SPRINT', taskCurrentStatusId: 'STS_CREATED')
        .orderBy('projectName ASC', 'taskActualStartDate DESC')
        .queryList()
        .each { projectAndTaskMap ->
            userLoginId = userLogin.partyId
            sprintId = projectAndTaskMap.sprintId
            backlogIndexList << from('WorkEffortAndProduct').where(workEffortId: projectAndTaskMap.projectId).queryFirst()?.productId

            GenericValue partyAssignmentSprintMap = from('WorkEffortPartyAssignment')
                    .where(workEffortId: sprintId, partyId: userLoginId).queryFirst()
            // if this userLoginId is a member of sprint
            if (partyAssignmentSprintMap) {
                GenericValue partyAssignmentTaskMap = from('WorkEffortPartyAssignment')
                        .where(workEffortId: projectAndTaskMap.taskId)
                        .queryFirst()
                // if the task do not assigned
                if (partyAssignmentTaskMap) {
                    if (projectAndTaskMap.custRequestTypeId == 'RF_SCRUM_MEETINGS'
                            && projectAndTaskMap.backlogStatusId == 'CRQ_REVIEWED') {
                        projectSprintBacklogAndTaskList.add(projectAndTaskMap)
                    }
                } else {
                    projectSprintBacklogAndTaskList.add(0, projectAndTaskMap)
                }
            }
        }

// for unplanned taks.
unplanList = []
if (backlogIndexList) {
    backlogIndex = new HashSet(backlogIndexList)
    custRequestList = from('CustRequest')
            .where(custRequestTypeId: 'RF_UNPLAN_BACKLOG', statusId: 'CRQ_REVIEWED')
            .orderBy('-custRequestDate')
            .queryList()
    if (custRequestList) {
        custRequestList.each { custRequestMap ->
            GenericValue custRequestItem = custRequestMap.getRelated('CustRequestItem', null, null, false).first()
            productOut = custRequestItem.productId
            GenericValue product = from('Product').where(productId: productOut).cache().queryOne()
            backlogIndex.each { backlogProduct ->
                productId = backlogProduct
                if (productId == productOut) {
                    from('CustRequestWorkEffort')
                            .where(custRequestId: custRequestItem.custRequestId)
                            .queryList()
                            .each { custRequestWorkEffortMap ->
                                GenericValue partyAssignmentTaskMap = from('WorkEffortPartyAssignment')
                                        .where(workEffortId: custRequestWorkEffortMap.workEffortId)
                                        .queryFirst()
                                // if the task do not assigned
                                if (!partyAssignmentTaskMap) {
                                    GenericValue workEffortMap = from('WorkEffort')
                                            .where(workEffortId: custRequestWorkEffortMap.workEffortId)
                                            .queryOne()
                                    unplanList << [description: custRequestMap.description,
                                                   productName: product.internalName,
                                                   taskId: workEffortMap.workEffortId,
                                                   taskName: workEffortMap.workEffortName,
                                                   custRequestTypeId: custRequestMap.custRequestTypeId,
                                                   taskTypeId: workEffortMap.workEffortTypeId]
                                }
                            }
                }
            }
        }
    }
}
projectSprintBacklogAndTaskList = UtilMisc.sortMaps(projectSprintBacklogAndTaskList, ['projectName', 'sprintName', '-taskTypeId', 'custRequestId'])
projectSprintBacklogAndTaskList.each { projectSprintBacklogAndTaskMap ->
    String blTypeId = projectSprintBacklogAndTaskMap.custRequestTypeId
    if (blTypeId == 'RF_SCRUM_MEETINGS') {
        taskList.add(projectSprintBacklogAndTaskMap)
    }
}
projectSprintBacklogAndTaskList = UtilMisc.sortMaps(projectSprintBacklogAndTaskList, ['-projectName', 'sprintName', '-taskTypeId', 'custRequestId'])
projectSprintBacklogAndTaskList.each { projectSprintBacklogAndTaskMap ->
    if (projectSprintBacklogAndTaskMap.custRequestTypeId == 'RF_PROD_BACKLOG') {
        taskList.add(0, projectSprintBacklogAndTaskMap)
    }
}
unplanList = UtilMisc.sortMaps(unplanList, ['-productName', '-taskTypeId', 'custRequestId'])
unplanList.each { unplanMap ->
    taskList.add(0, unplanMap)
}
context.taskList = taskList

// notification context
context.webSiteId = WebSiteWorker.getWebSiteId(request)
