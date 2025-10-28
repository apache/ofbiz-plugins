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
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityComparisonOperator
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.util.EntityUtil

uiLabelMap = UtilProperties.getResourceBundleMap('scrumUiLabels', locale)

partyId = parameters.partyId ?: parameters.userLogin.partyId

// show the requested timesheet, otherwise the current , if not exist create
timesheet = null
timesheetId = parameters.timesheetId
if (timesheetId) {
    timesheet = from('Timesheet').where(timesheetId: timesheetId).queryOne()
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
    if (!timesheet) {
        result = run servic: 'createProjectTimesheet', with: [partyId: partyId]
        if (result && result.timesheetId) {
            timesheet = from('Timesheet').where(timesheetId: result.timesheetId).queryOne()
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
entry = [timesheetId: timesheet.timesheetId]
taskTotal = 0.00
day0Total = 0.00
day1Total = 0.00
day2Total = 0.00
day3Total = 0.00
day4Total = 0.00
day5Total = 0.00
day6Total = 0.00
pHours = 0.00
timeEntry = null
lastTimeEntry = null

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
        entry.plannedHours = pHours
        actualHours = entryWorkEffort.getRelated('TimeEntry', null, null, false)
        aHours = 0.00
        actualHours.each { actualHour ->
            if (actualHour.hours) {
                aHours += actualHour.hours
            }
        }
        entry.actualHours = aHours
        // get party assignment data to be able to set the task to complete
        GenericValue workEffortPartyAssign = EntityUtil.filterByDate(entryWorkEffort.getRelated('WorkEffortPartyAssignment',
                [partyId: partyId], null, false))?.first()
        if (workEffortPartyAssign) {
            entry.fromDate = workEffortPartyAssign.fromDate
            entry.roleTypeId = workEffortPartyAssign.roleTypeId
            if (workEffortPartyAssign.statusId == 'PAS_COMPLETED') {
                entry.checkComplete = 'Y'
            }
        }

        // get project/phase information
        entry.workEffortId = entryWorkEffort.workEffortId
        entry.workEffortName = entryWorkEffort.workEffortName
        Map result = run service: 'getProjectIdAndNameFromTask', with: [taskId: entryWorkEffort.workEffortId]
        entry.phaseId = result.phaseId
        entry.phaseName = result.phaseName
        entry.projectId = result.projectId
        entry.projectName = result.projectName
        entry.taskWbsId = result.taskWbsId
    }
    entry.total = taskTotal
    //Drop Down Lists
    entries << entry
    // start new entry
    taskTotal = 0.00
    entry = [timesheetId: timesheet.timesheetId]
}

timesheet
        .getRelated('TimeEntry', null, ['workEffortId', 'rateTypeId', 'fromDate'], false)
        .each { timeEntry ->
            if (lastTimeEntry &&
                    (timeEntry.workEffortId != lastTimeEntry.workEffortId ||
                            timeEntry.rateTypeId != lastTimeEntry.rateTypeId)) {
                retrieveWorkEffortData()
            }
            if (timeEntry.hours) {
                dayNumber = 'd' + (timeEntry.fromDate.getTime() - timesheet.fromDate.getTime()) / (24 * 60 * 60 * 1000)
                hours = timeEntry.hours.doubleValue()
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
            entry.rateTypeId = timeEntry.rateTypeId
            lastTimeEntry = timeEntry
        }

if (timeEntry) {
    lastTimeEntry = timeEntry
    retrieveWorkEffortData()
}

// add empty lines if timesheet not completed
if (timesheet.statusId != 'TIMESHEET_COMPLETED') {
    for (c = 0; c < 3; c++) { // add empty lines
        entries << [timesheetId: timesheet.timesheetId]
    }
}

// add the totals line if at least one entry
if (timeEntry) {
    entry = [timesheetId: timesheet.timesheetId]
    entry.with {
        d0 = day0Total
        d1 = day1Total
        d2 = day2Total
        d3 = day3Total
        d4 = day4Total
        d5 = day5Total
        d6 = day6Total
        phaseName = uiLabelMap.ProjectMgrTotals
        workEffortId = 'Totals'
        total = day0Total + day1Total + day2Total + day3Total + day4Total + day5Total + day6Total
    }
    entries << entry
}
context.timeEntries = entries
// get all timesheets of this user, including the planned hours
timesheetsDb = from('Timesheet').where('partyId', partyId).orderBy('fromDate DESC').queryList()
timesheets = []
timesheetsDb.each { timesheetDb ->
    timesheet = [*: timesheetDb]
    entries = timesheetDb.getRelated('TimeEntry', null, null, false)
    hours = 0.00
    entries.each { timeEntry ->
        if (timeEntry.hours) {
            hours += timeEntry.hours.doubleValue()
        }
    }
    timesheet.weekNumber = UtilDateTime.weekNumber(timesheetDb.fromDate)
    timesheet.hours = hours
    timesheets << timesheet
}
context.timesheets = timesheets
