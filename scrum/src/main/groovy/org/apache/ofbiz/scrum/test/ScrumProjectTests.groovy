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

class ScrumProjectTests extends OFBizTestCase {

    ScrumProjectTests(String name) {
        super(name)
    }

    // Migrated from ScrumProjectTests.xml:testCreateScrumProjectByProductOwner
    // Original called createScrumProject event which internally calls createWorkEffort service.
    void testCreateScrumProjectByProductOwner() {
        Map serviceCtx = [
                workEffortId: 'TEST_WE_006',
                workEffortName: 'TEST_WEN06',
                workEffortTypeId: 'SCRUM_PROJECT',
                currentStatusId: 'SPJ_ACTIVE',
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync('createWorkEffort', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from ScrumProjectTests.xml:testUpdateScrumProjectByProductOwner
    void testUpdateScrumProjectByProductOwner() {
        Map createCtx = [
                workEffortId: 'TEST_WE_007',
                workEffortName: 'TEST_WEN07',
                workEffortTypeId: 'SCRUM_PROJECT',
                currentStatusId: 'SPJ_ACTIVE',
                userLogin: userLogin
        ]
        dispatcher.runSync('createWorkEffort', createCtx)

        Map serviceCtx = [
                workEffortId: 'TEST_WE_007',
                workEffortName: 'TEST_WEN07_UPDATE',
                workEffortTypeId: 'SCRUM_PROJECT',
                currentStatusId: 'SPJ_ACTIVE',
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync('updateWorkEffort', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from ScrumProjectTests.xml:testUpdateScrumProjectByScrumMaster
    void testUpdateScrumProjectByScrumMaster() {
        Map createCtx = [
                workEffortId: 'TEST_WE_008',
                workEffortName: 'TEST_WEN08',
                workEffortTypeId: 'SCRUM_PROJECT',
                currentStatusId: 'SPJ_ACTIVE',
                userLogin: userLogin
        ]
        dispatcher.runSync('createWorkEffort', createCtx)

        Map serviceCtx = [
                workEffortId: 'TEST_WE_008',
                workEffortName: 'TEST_WEN08_UPDATE',
                workEffortTypeId: 'SCRUM_PROJECT',
                currentStatusId: 'SPJ_ACTIVE',
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync('updateWorkEffort', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from ScrumProjectTests.xml:testCloseScrumProject
    void testCloseScrumProject() {
        Map serviceCtx = [
                workEffortId: 'DEMO-PROJECT-1',
                currentStatusId: 'SPJ_CLOSED',
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync('updateWorkEffort', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

}
