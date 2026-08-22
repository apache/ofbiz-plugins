/*******************************************************************************
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
 *******************************************************************************/
package org.apache.ofbiz.scrum.test

import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityQuery
import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.testtools.JunitJupiterTest
import org.apache.ofbiz.testtools.JupiterTestHelper
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

@JunitJupiterTest
class MyWorkTests implements JupiterTestHelper {

    // Migrated from MyWorkTests.xml:testUpdateTimesheetEntryByWorkeffortNotComplete
    @Test
    @Order(1)
    void testUpdateTimesheetEntryByWorkeffortNotComplete() {
        String timesheetId = testParams.timesheetId ?: 'DEMO-TIMESHEET1'
        String workEffortId = testParams.workEffortId ?: 'DEMO-TASK-1'
        Double planHours = (testParams.planHours ?: 2.0d) as Double
        Double hoursDay0 = (testParams.hoursDay0 ?: 1.0d) as Double
        String checkComplete = testParams.checkComplete ?: 'N'
        Map serviceCtx = [
                timesheetId: timesheetId,
                workEffortId: workEffortId,
                planHours_o_0: planHours,
                hoursDay0_o_0: hoursDay0,
                checkComplete: checkComplete,
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync(
                'updateTimesheetEntryByWorkeffort', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from MyWorkTests.xml:testUpdateTimesheetEntryByWorkeffortComplete
    @Test
    @Order(2)
    void testUpdateTimesheetEntryByWorkeffortComplete() {
        String timesheetId = testParams.timesheetId ?: 'DEMO-TIMESHEET1'
        String workEffortId = testParams.workEffortId ?: 'DEMO-TASK-1'
        Double planHours = (testParams.planHours ?: 2.0d) as Double
        Double hoursDay0 = (testParams.hoursDay0 ?: 1.0d) as Double
        String checkComplete = testParams.checkComplete ?: 'Y'
        Map serviceCtx = [
                timesheetId: timesheetId,
                workEffortId: workEffortId,
                planHours_o_0: planHours,
                hoursDay0_o_0: hoursDay0,
                checkComplete: checkComplete,
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync(
                'updateTimesheetEntryByWorkeffort', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from MyWorkTests.xml:testUpdateTask
    // Original called updateTask event in ScrumEvents.xml which internally calls updateWorkEffort.
    @Test
    @Order(3)
    void testUpdateTask() {
        String workEffortId = testParams.workEffortId ?: 'DEMO-TASK-2'
        Long estimatedMilliSeconds = (testParams.estimatedMilliSeconds ?: 3600000L) as Long
        Map serviceCtx = [
                workEffortId: workEffortId,
                estimatedMilliSeconds: estimatedMilliSeconds,
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync('updateWorkEffort', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from MyWorkTests.xml:testRemoveTaskAssignToMe
    @Test
    @Order(4)
    void testRemoveTaskAssignToMe() {
        String workEffortId = testParams.workEffortId ?: 'DEMO-TASK-2'
        String partyId = testParams.partyId ?: 'SCRUMTEAM-2'
        List<GenericValue> listWorkAssignment = EntityQuery.use(delegator).from('WorkEffortPartyAssignment')
                .where('workEffortId', workEffortId,
                        'partyId', partyId)
                .queryList()
        assert listWorkAssignment

        GenericValue assignment = listWorkAssignment[0]
        Map serviceCtx = [
                workEffortId: workEffortId,
                partyId: partyId,
                roleTypeId: assignment.roleTypeId,
                fromDate: assignment.fromDate,
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync(
                'unassignPartyFromWorkEffort', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from MyWorkTests.xml:testAddNewTimesheet
    @Test
    @Order(5)
    void testAddNewTimesheet() {
        String partyId = testParams.partyId ?: 'SCRUMTEAM-2'
        java.sql.Timestamp requiredDate = java.sql.Timestamp.valueOf(
                testParams.requiredDate ?: '2010-08-23 11:44:08.418')
        Map serviceCtx = [
                partyId: partyId,
                requiredDate: requiredDate,
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync(
                'createTimesheetForThisWeek', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from MyWorkTests.xml:testSetTimeSheetStatusToComplete
    @Test
    @Order(6)
    void testSetTimeSheetStatusToComplete() {
        String timesheetId = testParams.timesheetId ?: 'DEMO-TIMESHEET2'
        String statusId = testParams.statusId ?: 'TIMESHEET_COMPLETED'
        Map serviceCtx = [
                timesheetId: timesheetId,
                statusId: statusId,
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync('updateTimesheet', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

}
