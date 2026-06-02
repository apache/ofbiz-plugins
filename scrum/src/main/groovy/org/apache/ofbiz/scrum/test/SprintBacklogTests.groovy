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

class SprintBacklogTests extends OFBizTestCase {

    SprintBacklogTests(String name) {
        super(name)
    }

    // Migrated from SprintBacklogTests.xml:testcreateSprintBacklogByAdmin
    // Original called createSprintBacklog event which internally calls createWorkEffortRequest service.
    void testCreateSprintBacklogByAdmin() {
        Map serviceCtx = [
                custRequestId: 'TEST5',
                workEffortId: 'DEMO-SPRINT-1',
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync(
                'createWorkEffortRequest', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from SprintBacklogTests.xml:testcreateSprintBacklogByScrummaster
    void testCreateSprintBacklogByScrummaster() {
        Map serviceCtx = [
                custRequestId: 'TEST6',
                workEffortId: 'DEMO-SPRINT-1',
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync(
                'createWorkEffortRequest', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from SprintBacklogTests.xml:testdeleteSprintBacklogByAdmin
    void testDeleteSprintBacklogByAdmin() {
        Map serviceCtx = [
                custRequestId: 'TEST1',
                workEffortId: 'DEMO-SPRINT-1',
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync(
                'deleteWorkEffortRequest', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    // Migrated from SprintBacklogTests.xml:testdeleteSprintBacklogByScurmmaster
    void testDeleteSprintBacklogByScrummaster() {
        Map serviceCtx = [
                custRequestId: 'TEST2',
                workEffortId: 'DEMO-SPRINT-1',
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync(
                'deleteWorkEffortRequest', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

}
