package org.apache.ofbiz.scrum.test

import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.service.testtools.OFBizTestCase

class ProductBacklogTests extends OFBizTestCase {

    ProductBacklogTests(String name) {
        super(name)
    }

    void testAdminOperations() {
        GenericValue adminUserLogin = from('UserLogin').where('userLoginId', 'admin').queryOne()
        assert adminUserLogin

        // Create
        Map createCtx = [
                custRequestName: 'TEST Product Backlog',
                productId: 'DEMO-PRODUCT-1',
                description: 'TEST Product Backlog',
                custRequestTypeId: 'RF_PROD_BACKLOG',
                statusId: 'CRQ_ACCEPTED',
                fromPartyId: adminUserLogin.partyId,
                userLogin: adminUserLogin
        ]
        Map createResult = dispatcher.runSync('createCustRequest', createCtx)
        assert ServiceUtil.isSuccess(createResult)

        // Update
        Map updateCtx = [
                custRequestId: 'TEST5',
                custRequestName: 'TEST Product Backlog',
                description: 'TEST Product Backlog',
                statusId: 'CRQ_ACCEPTED',
                userLogin: adminUserLogin
        ]
        Map updateResult = dispatcher.runSync('updateCustRequest', updateCtx)
        assert ServiceUtil.isSuccess(updateResult)

        // Delete
        GenericValue result = from('CustRequest').where('custRequestId', 'TEST5').queryOne()
        if (result && result.statusId == 'CRQ_ACCEPTED') {
            Map deleteCtx = [
                    custRequestId: 'TEST5',
                    statusId: 'CRQ_CANCELLED',
                    userLogin: adminUserLogin
            ]
            Map deleteResult = dispatcher.runSync('updateCustRequest', deleteCtx)
            assert ServiceUtil.isSuccess(deleteResult)
        }
    }

    void testProductOwnerOperations() {
        GenericValue poUserLogin = from('UserLogin').where('userLoginId', 'productowner').queryOne()
        assert poUserLogin

        // Create
        Map createCtx = [
                custRequestName: 'TEST Product Backlog',
                productId: 'DEMO-PRODUCT-1',
                description: 'TEST Product Backlog',
                custRequestTypeId: 'RF_PROD_BACKLOG',
                statusId: 'CRQ_ACCEPTED',
                fromPartyId: poUserLogin.partyId,
                userLogin: poUserLogin
        ]
        Map createResult = dispatcher.runSync('createCustRequest', createCtx)
        assert ServiceUtil.isSuccess(createResult)

        // Update
        Map updateCtx = [
                custRequestId: 'TEST6',
                custRequestName: 'TEST Product Backlog',
                description: 'TEST Product Backlog',
                statusId: 'CRQ_ACCEPTED',
                userLogin: poUserLogin
        ]
        Map updateResult = dispatcher.runSync('updateCustRequest', updateCtx)
        assert ServiceUtil.isSuccess(updateResult)

        // Delete
        GenericValue result = from('CustRequest').where('custRequestId', 'TEST6').queryOne()
        if (result && result.statusId == 'CRQ_ACCEPTED') {
            Map deleteCtx = [
                    custRequestId: 'TEST6',
                    statusId: 'CRQ_CANCELLED',
                    userLogin: poUserLogin
            ]
            Map deleteResult = dispatcher.runSync('updateCustRequest', deleteCtx)
            assert ServiceUtil.isSuccess(deleteResult)
        }
    }

    void testScrumMasterOperations() {
        GenericValue smUserLogin = from('UserLogin').where('userLoginId', 'scrummaster').queryOne()
        assert smUserLogin

        // Create
        Map createCtx = [
                custRequestName: 'TEST Product Backlog',
                productId: 'DEMO-PRODUCT-1',
                description: 'TEST Product Backlog',
                custRequestTypeId: 'RF_PROD_BACKLOG',
                statusId: 'CRQ_ACCEPTED',
                fromPartyId: smUserLogin.partyId,
                userLogin: smUserLogin
        ]
        Map createResult = dispatcher.runSync('createCustRequest', createCtx)
        assert ServiceUtil.isSuccess(createResult)

        // Delete
        Map deleteCtx = [
                custRequestId: 'TEST7',
                statusId: 'CRQ_CANCELLED',
                userLogin: smUserLogin
        ]
        Map deleteResult = dispatcher.runSync('updateCustRequest', deleteCtx)
        assert ServiceUtil.isSuccess(deleteResult)
    }

    void testCreateBacklogSetStatus() {
        GenericValue poUserLogin = from('UserLogin').where('userLoginId', 'productowner').queryOne()
        Map serviceCtx = [
                custRequestName: 'TEST Product Backlog',
                productId: 'DEMO-PRODUCT-1',
                description: 'TEST Product Backlog',
                custRequestTypeId: 'RF_PROD_BACKLOG',
                statusId: 'CRQ_ACCEPTED',
                fromPartyId: poUserLogin.partyId,
                userLogin: poUserLogin
        ]
        Map serviceResult = dispatcher.runSync('createCustRequest', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    void testCreateDefaultBacklogs() {
        GenericValue adminUserLogin = from('UserLogin').where('userLoginId', 'admin').queryOne()
        Map serviceCtx = [
                workEffortName: 'Test Default Task',
                description: 'Test Project',
                workEffortTypeId: 'SCRUM_TASK_IMPL',
                workEffortPurposeTypeId: 'SCRUM_DEFAULT_TASK',
                currentStatusId: 'STS_CREATED',
                userLogin: adminUserLogin
        ]
        Map serviceResult = dispatcher.runSync('createWorkEffort', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    void testProductBacklogCategoryOperations() {
        GenericValue adminUserLogin = from('UserLogin').where('userLoginId', 'admin').queryOne()

        // Create
        Map createCtx = [
                custRequestName: 'Backlog',
                custRequestTypeId: 'RF_PROD_BACKLOG',
                statusId: 'CRQ_ACCEPTED',
                fromPartyId: adminUserLogin.partyId,
                userLogin: adminUserLogin
        ]
        Map createResult = dispatcher.runSync('createCustRequest', createCtx)
        assert ServiceUtil.isSuccess(createResult)

        String custRequestId = createResult.custRequestId
        Map itemCtx = [
                custRequestId: custRequestId,
                productId: 'DEMO-PRODUCT-1',
                userLogin: adminUserLogin
        ]
        Map itemResult = dispatcher.runSync('createCustRequestItem', itemCtx)
        assert ServiceUtil.isSuccess(itemResult)

        // Update
        Map updateCtx = [
                custRequestId: 'TEST10',
                custRequestName: 'Backlog',
                userLogin: adminUserLogin
        ]
        Map updateResult = dispatcher.runSync('updateCustRequest', updateCtx)
        assert ServiceUtil.isSuccess(updateResult)
    }

    void testProductBacklogEmailOperations() {
        GenericValue systemUserLogin = from('UserLogin').where('userLoginId', 'system').queryOne()

        // Create
        Map createCtx = [
                productId: 'DEMO-PRODUCT-1',
                custRequestId: 'TEST10',
                communicationEventTypeId: 'EMAIL_COMMUNICATION',
                partyIdFrom: 'DemoCustomer-1',
                partyIdTo: 'SCRUMASTER',
                subject: 'Test New Product Backlog Email',
                userLogin: systemUserLogin
        ]
        Map createResult = dispatcher.runSync('createCommunicationEvent', createCtx)
        assert ServiceUtil.isSuccess(createResult)

        // Update
        Map updateCtx = [
                communicationEventId: 'DEMO-COM-PRODUCT-1',
                productId: 'DEMO-PRODUCT-1',
                custRequestId: 'TEST10',
                communicationEventTypeId: 'EMAIL_COMMUNICATION',
                subject: 'Test New Product Backlog Email',
                userLogin: systemUserLogin
        ]
        Map updateResult = dispatcher.runSync('updateCommunicationEvent', updateCtx)
        assert ServiceUtil.isSuccess(updateResult)
    }

    void testUpdateSprintBacklogseqDown() {
        GenericValue systemUserLogin = from('UserLogin').where('userLoginId', 'system').queryOne()
        Map serviceCtx = [
                mode: 'DWN',
                custRequestId: 'TEST9',
                productId: 'DEMO-PRODUCT-1',
                custRequestItemSeqId: 'TESTSEQ9',
                statusId: 'CRQ_ACCEPTED',
                searchOption_statusId: 'CRQ_ACCEPTED',
                userLogin: systemUserLogin
        ]
        Map serviceResult = dispatcher.runSync('updateSprintBacklogseq', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    void testUpdateSprintBacklogseqUP() {
        GenericValue systemUserLogin = from('UserLogin').where('userLoginId', 'system').queryOne()
        Map serviceCtx = [
                mode: 'UP',
                custRequestId: 'TEST9',
                productId: 'DEMO-PRODUCT-1',
                custRequestItemSeqId: 'TESTSEQ9',
                statusId: 'CRQ_ACCEPTED',
                searchOption_statusId: 'CRQ_ACCEPTED',
                userLogin: systemUserLogin
        ]
        Map serviceResult = dispatcher.runSync('updateSprintBacklogseq', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    void testUpdateSprintBacklogseqBotton() {
        GenericValue systemUserLogin = from('UserLogin').where('userLoginId', 'system').queryOne()
        Map serviceCtx = [
                mode: 'BOT',
                custRequestId: 'TEST9',
                productId: 'DEMO-PRODUCT-1',
                custRequestItemSeqId: 'TESTSEQ9',
                statusId: 'CRQ_ACCEPTED',
                searchOption_statusId: 'CRQ_ACCEPTED',
                userLogin: systemUserLogin
        ]
        Map serviceResult = dispatcher.runSync('updateSprintBacklogseq', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

    void testUpdateSprintBacklogseqTOP() {
        GenericValue systemUserLogin = from('UserLogin').where('userLoginId', 'system').queryOne()
        Map serviceCtx = [
                mode: 'TOP',
                custRequestId: 'TEST9',
                productId: 'DEMO-PRODUCT-1',
                custRequestItemSeqId: 'TESTSEQ9',
                statusId: 'CRQ_ACCEPTED',
                searchOption_statusId: 'CRQ_ACCEPTED',
                userLogin: systemUserLogin
        ]
        Map serviceResult = dispatcher.runSync('updateSprintBacklogseq', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
    }

}
