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

import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.service.testtools.OFBizTestCase

class SprintTests extends OFBizTestCase {

    SprintTests(String name) {
        super(name)
    }

    // Migrated from SprintTests.xml:testUpdateSprintBacklog
    void testUpdateSprintBacklog() {
        Map serviceCtx = [
                custRequestId: 'TEST9',
                estimatedMilliSeconds: 36000000L,
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync('updateCustRequest', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from SprintTests.xml:testCreateSprintByScrummaster
    void testCreateSprintByScrummaster() {
        String sprintId = createSprint()
        assert sprintId
    }

    // Migrated from SprintTests.xml:testUpdateSprintByScrummaster
    void testUpdateSprintByScrummaster() {
        String sprintId = createSprint()
        Map serviceCtx = [
                workEffortId: sprintId,
                workEffortName: 'SprintTest',
                currentStatusId: 'SPRINT_ACTIVE',
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync('updateWorkEffort', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from SprintTests.xml:testCreateSprintByAdmin
    void testCreateSprintByAdmin() {
        String sprintId = createSprint()
        assert sprintId
    }

    // Migrated from SprintTests.xml:testUpdateSprintByAdmin
    void testUpdateSprintByAdmin() {
        String sprintId = createSprint()
        Map serviceCtx = [
                workEffortId: sprintId,
                workEffortName: 'SprintTest',
                currentStatusId: 'SPRINT_ACTIVE',
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync('updateWorkEffort', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from SprintTests.xml:testAddSprintMember
    void testAddSprintMember() {
        Map serviceCtx = [
                workEffortId: 'DEMO-SPRINT-1',
                roleTypeId: 'SCRUM_TEAM',
                statusId: 'PRTYASGN_ASSIGNED',
                partyId: 'DemoCustomer-1',
                fromDate: java.sql.Timestamp.valueOf(
                        '2010-07-30 00:00:00.000'),
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync(
                'assignPartyToWorkEffort', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from SprintTests.xml:testRemoveSprintMember
    void testRemoveSprintMember() {
        Map serviceCtx = [
                workEffortId: 'DEMO-SPRINT-1',
                roleTypeId: 'SCRUM_TEAM',
                statusId: 'PRTYASGN_ASSIGNED',
                partyId: 'DemoCustomer-1',
                fromDate: java.sql.Timestamp.valueOf('2010-07-31 00:00:00.000'),
                userLogin: userLogin
        ]
        // Assign first so we can safely remove it regardless of test execution order
        dispatcher.runSync('assignPartyToWorkEffort', serviceCtx)

        Map removeCtx = [
                workEffortId: 'DEMO-SPRINT-1',
                roleTypeId: 'SCRUM_TEAM',
                partyId: 'DemoCustomer-1',
                fromDate: java.sql.Timestamp.valueOf('2010-07-31 00:00:00.000'),
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync(
                'unassignPartyFromWorkEffort', removeCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    /**
     * Helper: creates a sprint via createWorkEffort service.
     * The original XML called createSprint event in ScrumEvents.xml,
     * which internally creates a WorkEffort of type SCRUM_SPRINT.
     */
    protected String createSprint() {
        Map serviceCtx = [
                workEffortName: 'SprintTest',
                description: 'Test Create Sprint',
                workEffortTypeId: 'SCRUM_SPRINT',
                currentStatusId: 'SPRINT_ACTIVE',
                workEffortParentId: 'DEMO-PROJECT-1',
                estimatedMilliSeconds: 1440000000L,
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync('createWorkEffort', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
        return serviceResult.workEffortId
    }

}
