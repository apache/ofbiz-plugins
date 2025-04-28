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

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.entity.GenericEntityException
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.util.EntityFindOptions

// get all timesheets of all user, including the planned hours
timesheets = []
inputFields = [:]

parameters.noConditionFind = parameters.noConditionFind ?: 'N'
inputFields.putAll(parameters)
performFindResults = run service: 'performFind', with: [entityName: 'Timesheet',
                                                        inputFields: inputFields,
                                                        orderBy: 'fromDate DESC']
if (performFindResults.listSize > 0) {
    try {
        timesheetsDb = performFindResults.listIt.getCompleteList()
    } catch (GenericEntityException e) {
        logError(e, 'Failure in ' + module)
    } finally {
        if (performFindResults.listIt != null) {
            try {
                performFindResults.listIt.close()
            } catch (GenericEntityException e) {
                logError(e)
            }
        }
    }

    timesheetsDb.each { timesheetDb ->
        //get hours from EmplLeave
        leaveHours = 0.00
        findOpts = new EntityFindOptions(true, EntityFindOptions.TYPE_SCROLL_INSENSITIVE, EntityFindOptions.CONCUR_READ_ONLY, true)
        leaveExprsList = []
        leaveExprsList.add(EntityCondition.makeCondition('fromDate', EntityOperator.GREATER_THAN_EQUAL_TO, timesheetDb.fromDate))
        leaveExprsList.add(EntityCondition.makeCondition('fromDate', EntityOperator.LESS_THAN_EQUAL_TO, timesheetDb.thruDate))
        leaveExprsList.add(EntityCondition.makeCondition('partyId', EntityOperator.EQUALS, timesheetDb.partyId))
        from('EmplLeave')
                .where(leaveExprsList)
                .select('partyId', 'leaveTypeId', 'fromDate')
                .cursorScrollInsensitive()
                .distinct()
                .queryList()
                .each {
                    Map resultHour = run service: 'getPartyLeaveHoursForDate', with: it.getAllFields()
                    if (resultHour?.hours) {
                        leaveHours += resultHour.hours
                    }
                }
        //get hours from TimeEntry
        timesheet = [:]
        timesheet.putAll(timesheetDb)
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
}
