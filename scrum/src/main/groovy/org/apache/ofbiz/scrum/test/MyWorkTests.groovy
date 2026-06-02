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
import org.apache.ofbiz.service.testtools.OFBizTestCase

class MyWorkTests extends OFBizTestCase {

    MyWorkTests(String name) {
        super(name)
    }

    // Migrated from MyWorkTests.xml:testUpdateTimesheetEntryByWorkeffortNotComplete
    void testUpdateTimesheetEntryByWorkeffortNotComplete() {
        Map serviceCtx = [
                timesheetId: 'DEMO-TIMESHEET1',
                workEffortId: 'DEMO-TASK-1',
                planHours_o_0: 2.0d,
                hoursDay0_o_0: 1.0d,
                checkComplete: 'N',
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync(
                'updateTimesheetEntryByWorkeffort', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from MyWorkTests.xml:testUpdateTimesheetEntryByWorkeffortComplete
    void testUpdateTimesheetEntryByWorkeffortComplete() {
        Map serviceCtx = [
                timesheetId: 'DEMO-TIMESHEET1',
                workEffortId: 'DEMO-TASK-1',
                planHours_o_0: 2.0d,
                hoursDay0_o_0: 1.0d,
                checkComplete: 'Y',
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync(
                'updateTimesheetEntryByWorkeffort', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from MyWorkTests.xml:testUpdateTask
    // Original called updateTask event in ScrumEvents.xml which internally calls updateWorkEffort.
    void testUpdateTask() {
        Map serviceCtx = [
                workEffortId: 'DEMO-TASK-2',
                estimatedMilliSeconds: 3600000L,
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync('updateWorkEffort', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from MyWorkTests.xml:testRemoveTaskAssignToMe
    void testRemoveTaskAssignToMe() {
        List<GenericValue> listWorkAssignment = EntityQuery.use(delegator).from('WorkEffortPartyAssignment')
                .where('workEffortId', 'DEMO-TASK-2',
                        'partyId', 'SCRUMTEAM-2')
                .queryList()
        assert listWorkAssignment

        GenericValue assignment = listWorkAssignment[0]
        Map serviceCtx = [
                workEffortId: 'DEMO-TASK-2',
                partyId: 'SCRUMTEAM-2',
                roleTypeId: assignment.roleTypeId,
                fromDate: assignment.fromDate,
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync(
                'unassignPartyFromWorkEffort', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from MyWorkTests.xml:testAddNewTimesheet
    void testAddNewTimesheet() {
        Map serviceCtx = [
                partyId: 'SCRUMTEAM-2',
                requiredDate: java.sql.Timestamp.valueOf(
                        '2010-08-23 11:44:08.418'),
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync(
                'createTimesheetForThisWeek', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from MyWorkTests.xml:testSetTimeSheetStatusToComplete
    void testSetTimeSheetStatusToComplete() {
        Map serviceCtx = [
                timesheetId: 'DEMO-TIMESHEET2',
                statusId: 'TIMESHEET_COMPLETED',
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync('updateTimesheet', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

}
