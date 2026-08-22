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
import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.testtools.JunitJupiterTest
import org.apache.ofbiz.testtools.JupiterTestHelper
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

@JunitJupiterTest
@SuppressWarnings(['PublicMethodsBeforeNonPublicMethods', 'JUnitTestMethodWithoutAssert'])
class ProductBacklogTests implements JupiterTestHelper {

    @Test
    @Order(1)
    void testAdminOperations() {
        GenericValue adminUserLogin = from('UserLogin').where('userLoginId', 'admin').queryOne()
        assert adminUserLogin

        String custRequestId = testParams.custRequestId ?: 'TEST5'
        String custRequestName = testParams.custRequestName ?: 'TEST Product Backlog'
        String productId = testParams.productId ?: 'DEMO-PRODUCT-1'
        String description = testParams.description ?: 'TEST Product Backlog'
        String custRequestTypeId = testParams.custRequestTypeId ?: 'RF_PROD_BACKLOG'
        String statusId = testParams.statusId ?: 'CRQ_ACCEPTED'
        String cancelledStatusId = testParams.cancelledStatusId ?: 'CRQ_CANCELLED'

        // Create
        Map createCtx = [
                custRequestName: custRequestName,
                productId: productId,
                description: description,
                custRequestTypeId: custRequestTypeId,
                statusId: statusId,
                fromPartyId: adminUserLogin.partyId,
                userLogin: adminUserLogin
        ]
        Map createResult = dispatcher.runSync('createCustRequest', createCtx)
        assert ServiceUtil.isSuccess(createResult)

        // Update
        Map updateCtx = [
                custRequestId: custRequestId,
                custRequestName: custRequestName,
                description: description,
                statusId: statusId,
                userLogin: adminUserLogin
        ]
        Map updateResult = dispatcher.runSync('updateCustRequest', updateCtx)
        assert ServiceUtil.isSuccess(updateResult)

        // Delete
        GenericValue result = from('CustRequest').where('custRequestId', custRequestId).queryOne()
        if (result && result.statusId == statusId) {
            Map deleteCtx = [
                    custRequestId: custRequestId,
                    statusId: cancelledStatusId,
                    userLogin: adminUserLogin
            ]
            Map deleteResult = dispatcher.runSync('updateCustRequest', deleteCtx)
            assert ServiceUtil.isSuccess(deleteResult)
        }
    }

    @Test
    @Order(2)
    void testProductOwnerOperations() {
        GenericValue poUserLogin = from('UserLogin').where('userLoginId', 'productowner').queryOne()
        assert poUserLogin

        String custRequestId = testParams.custRequestId ?: 'TEST6'
        String custRequestName = testParams.custRequestName ?: 'TEST Product Backlog'
        String productId = testParams.productId ?: 'DEMO-PRODUCT-1'
        String description = testParams.description ?: 'TEST Product Backlog'
        String custRequestTypeId = testParams.custRequestTypeId ?: 'RF_PROD_BACKLOG'
        String statusId = testParams.statusId ?: 'CRQ_ACCEPTED'
        String cancelledStatusId = testParams.cancelledStatusId ?: 'CRQ_CANCELLED'

        // Create
        Map createCtx = [
                custRequestName: custRequestName,
                productId: productId,
                description: description,
                custRequestTypeId: custRequestTypeId,
                statusId: statusId,
                fromPartyId: poUserLogin.partyId,
                userLogin: poUserLogin
        ]
        Map createResult = dispatcher.runSync('createCustRequest', createCtx)
        assert ServiceUtil.isSuccess(createResult)

        // Update
        Map updateCtx = [
                custRequestId: custRequestId,
                custRequestName: custRequestName,
                description: description,
                statusId: statusId,
                userLogin: poUserLogin
        ]
        Map updateResult = dispatcher.runSync('updateCustRequest', updateCtx)
        assert ServiceUtil.isSuccess(updateResult)

        // Delete
        GenericValue result = from('CustRequest').where('custRequestId', custRequestId).queryOne()
        if (result && result.statusId == statusId) {
            Map deleteCtx = [
                    custRequestId: custRequestId,
                    statusId: cancelledStatusId,
                    userLogin: poUserLogin
            ]
            Map deleteResult = dispatcher.runSync('updateCustRequest', deleteCtx)
            assert ServiceUtil.isSuccess(deleteResult)
        }
    }

    @Test
    @Order(3)
    void testScrumMasterOperations() {
        GenericValue smUserLogin = from('UserLogin').where('userLoginId', 'scrummaster').queryOne()
        assert smUserLogin

        String custRequestName = testParams.custRequestName ?: 'TEST Product Backlog'
        String productId = testParams.productId ?: 'DEMO-PRODUCT-1'
        String description = testParams.description ?: 'TEST Product Backlog'
        String custRequestTypeId = testParams.custRequestTypeId ?: 'RF_PROD_BACKLOG'
        String statusId = testParams.statusId ?: 'CRQ_ACCEPTED'
        String custRequestId = testParams.custRequestId ?: 'TEST7'
        String cancelledStatusId = testParams.cancelledStatusId ?: 'CRQ_CANCELLED'

        // Create
        Map createCtx = [
                custRequestName: custRequestName,
                productId: productId,
                description: description,
                custRequestTypeId: custRequestTypeId,
                statusId: statusId,
                fromPartyId: smUserLogin.partyId,
                userLogin: smUserLogin
        ]
        Map createResult = dispatcher.runSync('createCustRequest', createCtx)
        assert ServiceUtil.isSuccess(createResult)

        // Delete
        Map deleteCtx = [
                custRequestId: custRequestId,
                statusId: cancelledStatusId,
                userLogin: smUserLogin
        ]
        Map deleteResult = dispatcher.runSync('updateCustRequest', deleteCtx)
        assert ServiceUtil.isSuccess(deleteResult)
    }

    @Test
    @Order(4)
    void testCreateBacklogSetStatus() {
        GenericValue poUserLogin = from('UserLogin').where('userLoginId', 'productowner').queryOne()
        String custRequestName = testParams.custRequestName ?: 'TEST Product Backlog'
        String productId = testParams.productId ?: 'DEMO-PRODUCT-1'
        String description = testParams.description ?: 'TEST Product Backlog'
        String custRequestTypeId = testParams.custRequestTypeId ?: 'RF_PROD_BACKLOG'
        String statusId = testParams.statusId ?: 'CRQ_ACCEPTED'
        Map serviceCtx = [
                custRequestName: custRequestName,
                productId: productId,
                description: description,
                custRequestTypeId: custRequestTypeId,
                statusId: statusId,
                fromPartyId: poUserLogin.partyId,
                userLogin: poUserLogin
        ]
        Map serviceResult = dispatcher.runSync('createCustRequest', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    @Test
    @Order(5)
    void testCreateDefaultBacklogs() {
        GenericValue adminUserLogin = from('UserLogin').where('userLoginId', 'admin').queryOne()
        String workEffortName = testParams.workEffortName ?: 'Test Default Task'
        String description = testParams.description ?: 'Test Project'
        String workEffortTypeId = testParams.workEffortTypeId ?: 'SCRUM_TASK_IMPL'
        String workEffortPurposeTypeId = testParams.workEffortPurposeTypeId ?: 'SCRUM_DEFAULT_TASK'
        String currentStatusId = testParams.currentStatusId ?: 'STS_CREATED'
        Map serviceCtx = [
                workEffortName: workEffortName,
                description: description,
                workEffortTypeId: workEffortTypeId,
                workEffortPurposeTypeId: workEffortPurposeTypeId,
                currentStatusId: currentStatusId,
                userLogin: adminUserLogin
        ]
        Map serviceResult = dispatcher.runSync('createWorkEffort', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    @Test
    @Order(6)
    void testProductBacklogCategoryOperations() {
        GenericValue adminUserLogin = from('UserLogin').where('userLoginId', 'admin').queryOne()
        String custRequestName = testParams.custRequestName ?: 'Backlog'
        String custRequestTypeId = testParams.custRequestTypeId ?: 'RF_PROD_BACKLOG'
        String statusId = testParams.statusId ?: 'CRQ_ACCEPTED'
        String productId = testParams.productId ?: 'DEMO-PRODUCT-1'
        String updateCustRequestId = testParams.updateCustRequestId ?: 'TEST10'

        // Create
        Map createCtx = [
                custRequestName: custRequestName,
                custRequestTypeId: custRequestTypeId,
                statusId: statusId,
                fromPartyId: adminUserLogin.partyId,
                userLogin: adminUserLogin
        ]
        Map createResult = dispatcher.runSync('createCustRequest', createCtx)
        assert ServiceUtil.isSuccess(createResult)

        String custRequestId = createResult.custRequestId
        Map itemCtx = [
                custRequestId: custRequestId,
                productId: productId,
                userLogin: adminUserLogin
        ]
        Map itemResult = dispatcher.runSync('createCustRequestItem', itemCtx)
        assert ServiceUtil.isSuccess(itemResult)

        // Update
        Map updateCtx = [
                custRequestId: updateCustRequestId,
                custRequestName: custRequestName,
                userLogin: adminUserLogin
        ]
        Map updateResult = dispatcher.runSync('updateCustRequest', updateCtx)
        assert ServiceUtil.isSuccess(updateResult)
    }

    @Test
    @Order(7)
    void testProductBacklogEmailOperations() {
        GenericValue systemUserLogin = from('UserLogin').where('userLoginId', 'system').queryOne()
        String productId = testParams.productId ?: 'DEMO-PRODUCT-1'
        String custRequestId = testParams.custRequestId ?: 'TEST10'
        String communicationEventTypeId = testParams.communicationEventTypeId ?: 'EMAIL_COMMUNICATION'
        String partyIdFrom = testParams.partyIdFrom ?: 'DemoCustomer-1'
        String partyIdTo = testParams.partyIdTo ?: 'SCRUMASTER'
        String subject = testParams.subject ?: 'Test New Product Backlog Email'
        String communicationEventId = testParams.communicationEventId ?: 'DEMO-COM-PRODUCT-1'

        // Create
        Map createCtx = [
                productId: productId,
                custRequestId: custRequestId,
                communicationEventTypeId: communicationEventTypeId,
                partyIdFrom: partyIdFrom,
                partyIdTo: partyIdTo,
                subject: subject,
                userLogin: systemUserLogin
        ]
        Map createResult = dispatcher.runSync('createCommunicationEvent', createCtx)
        assert ServiceUtil.isSuccess(createResult)

        // Update
        Map updateCtx = [
                communicationEventId: communicationEventId,
                productId: productId,
                custRequestId: custRequestId,
                communicationEventTypeId: communicationEventTypeId,
                subject: subject,
                userLogin: systemUserLogin
        ]
        Map updateResult = dispatcher.runSync('updateCommunicationEvent', updateCtx)
        assert ServiceUtil.isSuccess(updateResult)
    }

    // Shared by the 4 testUpdateSprintBacklogseq* tests below - identical apart from the mode
    // each test moves the backlog item by.
    private void updateSprintBacklogseq(String defaultMode) {
        GenericValue systemUserLogin = from('UserLogin').where('userLoginId', 'system').queryOne()
        String mode = testParams.mode ?: defaultMode
        String custRequestId = testParams.custRequestId ?: 'TEST9'
        String productId = testParams.productId ?: 'DEMO-PRODUCT-1'
        String custRequestItemSeqId = testParams.custRequestItemSeqId ?: 'TESTSEQ9'
        String statusId = testParams.statusId ?: 'CRQ_ACCEPTED'
        String searchOptionStatusId = testParams.searchOptionStatusId ?: 'CRQ_ACCEPTED'
        Map serviceCtx = [
                mode: mode,
                custRequestId: custRequestId,
                productId: productId,
                custRequestItemSeqId: custRequestItemSeqId,
                statusId: statusId,
                searchOption_statusId: searchOptionStatusId,
                userLogin: systemUserLogin
        ]
        Map serviceResult = dispatcher.runSync('updateSprintBacklogseq', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    @Test
    @Order(8)
    void testUpdateSprintBacklogseqDown() {
        updateSprintBacklogseq('DWN')
    }

    @Test
    @Order(9)
    void testUpdateSprintBacklogseqUP() {
        updateSprintBacklogseq('UP')
    }

    @Test
    @Order(10)
    void testUpdateSprintBacklogseqBotton() {
        updateSprintBacklogseq('BOT')
    }

    @Test
    @Order(11)
    void testUpdateSprintBacklogseqTOP() {
        updateSprintBacklogseq('TOP')
    }

}
