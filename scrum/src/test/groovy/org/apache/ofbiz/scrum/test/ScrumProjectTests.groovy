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
import org.apache.ofbiz.testtools.JunitJupiterTest
import org.apache.ofbiz.testtools.JupiterTestHelper
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

@JunitJupiterTest
class ScrumProjectTests implements JupiterTestHelper {

    // Migrated from ScrumProjectTests.xml:testCreateScrumProjectByProductOwner
    // Original called createScrumProject event which internally calls createWorkEffort service.
    @Test
    @Order(1)
    void testCreateScrumProjectByProductOwner() {
        String workEffortId = testParams.workEffortId ?: 'TEST_WE_006'
        String workEffortName = testParams.workEffortName ?: 'TEST_WEN06'
        String workEffortTypeId = testParams.workEffortTypeId ?: 'SCRUM_PROJECT'
        String currentStatusId = testParams.currentStatusId ?: 'SPJ_ACTIVE'
        Map serviceCtx = [
                workEffortId: workEffortId,
                workEffortName: workEffortName,
                workEffortTypeId: workEffortTypeId,
                currentStatusId: currentStatusId,
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync('createWorkEffort', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from ScrumProjectTests.xml:testUpdateScrumProjectByProductOwner
    @Test
    @Order(2)
    void testUpdateScrumProjectByProductOwner() {
        String workEffortId = testParams.workEffortId ?: 'TEST_WE_007'
        String workEffortName = testParams.workEffortName ?: 'TEST_WEN07'
        String workEffortTypeId = testParams.workEffortTypeId ?: 'SCRUM_PROJECT'
        String currentStatusId = testParams.currentStatusId ?: 'SPJ_ACTIVE'
        String updatedWorkEffortName = testParams.updatedWorkEffortName ?: 'TEST_WEN07_UPDATE'
        Map createCtx = [
                workEffortId: workEffortId,
                workEffortName: workEffortName,
                workEffortTypeId: workEffortTypeId,
                currentStatusId: currentStatusId,
                userLogin: userLogin
        ]
        dispatcher.runSync('createWorkEffort', createCtx)

        Map serviceCtx = [
                workEffortId: workEffortId,
                workEffortName: updatedWorkEffortName,
                workEffortTypeId: workEffortTypeId,
                currentStatusId: currentStatusId,
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync('updateWorkEffort', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from ScrumProjectTests.xml:testUpdateScrumProjectByScrumMaster
    @Test
    @Order(3)
    void testUpdateScrumProjectByScrumMaster() {
        String workEffortId = testParams.workEffortId ?: 'TEST_WE_008'
        String workEffortName = testParams.workEffortName ?: 'TEST_WEN08'
        String workEffortTypeId = testParams.workEffortTypeId ?: 'SCRUM_PROJECT'
        String currentStatusId = testParams.currentStatusId ?: 'SPJ_ACTIVE'
        String updatedWorkEffortName = testParams.updatedWorkEffortName ?: 'TEST_WEN08_UPDATE'
        Map createCtx = [
                workEffortId: workEffortId,
                workEffortName: workEffortName,
                workEffortTypeId: workEffortTypeId,
                currentStatusId: currentStatusId,
                userLogin: userLogin
        ]
        dispatcher.runSync('createWorkEffort', createCtx)

        Map serviceCtx = [
                workEffortId: workEffortId,
                workEffortName: updatedWorkEffortName,
                workEffortTypeId: workEffortTypeId,
                currentStatusId: currentStatusId,
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync('updateWorkEffort', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from ScrumProjectTests.xml:testCloseScrumProject
    @Test
    @Order(4)
    void testCloseScrumProject() {
        String workEffortId = testParams.workEffortId ?: 'DEMO-PROJECT-1'
        String currentStatusId = testParams.currentStatusId ?: 'SPJ_CLOSED'
        Map serviceCtx = [
                workEffortId: workEffortId,
                currentStatusId: currentStatusId,
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync('updateWorkEffort', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

}
