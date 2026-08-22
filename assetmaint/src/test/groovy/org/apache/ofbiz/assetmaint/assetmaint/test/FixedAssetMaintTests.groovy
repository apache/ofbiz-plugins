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
package org.apache.ofbiz.assetmaint.assetmaint.test

import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.testtools.JunitJupiterTest
import org.apache.ofbiz.testtools.JupiterTestHelper
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

import java.sql.Timestamp

@JunitJupiterTest
@SuppressWarnings(['PublicMethodsBeforeNonPublicMethods', 'JUnitTestMethodWithoutAssert'])
class FixedAssetMaintTests implements JupiterTestHelper {

    // Shared by testCreateFixedAssetMaintUpdateWorkEffortWithProductMaint and
    // testUpdateFixedAssetMaintAndWorkEffort - both need a freshly-created FixedAssetMaint (with
    // product maintenance) as their starting point, one to assert on directly, the other as
    // fixture setup before its own update assertions.
    private Map createFixedAssetMaintWithProductMaint() {
        String fixedAssetId = testParams.fixedAssetId ?: 'DEMO_VEHICLE_01'
        String statusId = testParams.statusId ?: 'FAM_CREATED'
        String productMaintSeqId = testParams.productMaintSeqId ?: 'seq03'
        String intervalMeterTypeId = testParams.intervalMeterTypeId ?: 'ODOMETER'
        Timestamp estimatedStartDate = UtilDateTime.toTimestamp(testParams.estimatedStartDate ?: '2009-12-18 00:00:00.000')
        Timestamp estimatedCompletionDate = UtilDateTime.toTimestamp(testParams.estimatedCompletionDate ?: '2009-12-18 00:00:00.000')
        Timestamp actualStartDate = UtilDateTime.toTimestamp(testParams.actualStartDate ?: '2009-12-20 00:00:00.000')
        Map serviceCtx = [fixedAssetId: fixedAssetId,
                          statusId: statusId,
                          productMaintSeqId: productMaintSeqId,  // product maintenance,
                          intervalMeterTypeId: intervalMeterTypeId,
                          estimatedStartDate: estimatedStartDate,
                          estimatedCompletionDate: estimatedCompletionDate,
                          actualStartDate: actualStartDate,
                          userLogin: userLogin]
        Map serviceResult = dispatcher.runSync('createFixedAssetMaintUpdateWorkEffort', serviceCtx)
        return [fixedAssetId: fixedAssetId, serviceCtx: serviceCtx, serviceResult: serviceResult]
    }

    @Test
    @Order(1)
    void testCreateFixedAssetMaintUpdateWorkEffortWithProductMaint() {
        // Test case for service createFixedAssetMaintUpdateWorkEffort with a product Maintenance
        Map created = createFixedAssetMaintWithProductMaint()
        String fixedAssetId = created.fixedAssetId
        Map serviceCtx = created.serviceCtx
        Map serviceResult = created.serviceResult
        GenericValue fixedAssetMaint = from('FixedAssetMaint')
                .where('fixedAssetId', fixedAssetId,
                'maintHistSeqId', serviceResult.maintHistSeqId)
                .queryOne()
        GenericValue workEffort = from('WorkEffort')
                .where('workEffortId', fixedAssetMaint.scheduleWorkEffortId)
                .queryOne()
        assert fixedAssetMaint
        assert fixedAssetMaint.scheduleWorkEffortId
        assert workEffort
        assert workEffort.estimatedStartDate == serviceCtx.estimatedStartDate
        assert workEffort.estimatedCompletionDate == serviceCtx.estimatedCompletionDate
        assert workEffort.actualStartDate == serviceCtx.actualStartDate
    }

    @Test
    @Order(2)
    void testCreateFixedAssetMaintUpdateWorkEffortWithoutProductMaint() {
        // Test case for service createFixedAssetMaintUpdateWorkEffort without a product maintenance
        String fixedAssetId = testParams.fixedAssetId ?: 'DEMO_VEHICLE_01'
        String statusId = testParams.statusId ?: 'FAM_CREATED'
        String productMaintTypeId = testParams.productMaintTypeId ?: 'OIL_CHANGE'
        String intervalMeterTypeId = testParams.intervalMeterTypeId ?: 'ODOMETER'
        Timestamp estimatedStartDate = UtilDateTime.toTimestamp(testParams.estimatedStartDate ?: '2009-12-18 00:00:00.000')
        Timestamp estimatedCompletionDate = UtilDateTime.toTimestamp(testParams.estimatedCompletionDate ?: '2009-12-18 00:00:00.000')
        Timestamp actualStartDate = UtilDateTime.toTimestamp(testParams.actualStartDate ?: '2009-12-20 00:00:00.000')
        Map serviceCtx = [fixedAssetId: fixedAssetId,
                          statusId: statusId,
                          productMaintTypeId: productMaintTypeId,
                          intervalMeterTypeId: intervalMeterTypeId,
                          estimatedStartDate: estimatedStartDate,
                          estimatedCompletionDate: estimatedCompletionDate,
                          actualStartDate: actualStartDate,
                          userLogin: userLogin]
        Map serviceResult = dispatcher.runSync('createFixedAssetMaintUpdateWorkEffort', serviceCtx)
        String maintHistSeqId = serviceResult.maintHistSeqId

        GenericValue fixedAssetMaint = from('FixedAssetMaint')
                .where('fixedAssetId', fixedAssetId,
                        'maintHistSeqId', maintHistSeqId)
                .queryOne()
        GenericValue workEffort = from('WorkEffort')
                .where('workEffortId', fixedAssetMaint.scheduleWorkEffortId)
                .queryOne()

        assert fixedAssetMaint
        assert fixedAssetMaint.scheduleWorkEffortId
        assert workEffort
        assert workEffort.estimatedStartDate == serviceCtx.estimatedStartDate
        assert workEffort.estimatedCompletionDate == serviceCtx.estimatedCompletionDate
        assert workEffort.actualStartDate == serviceCtx.actualStartDate
    }

    @Test
    @Order(3)
    void testUpdateFixedAssetMaintAndWorkEffort() {
        // Test case for service updateFixedAssetMaintAndWorkEffort
        Map created = createFixedAssetMaintWithProductMaint()
        String fixedAssetId = created.fixedAssetId
        String statusId = created.serviceCtx.statusId
        String intervalMeterTypeId = created.serviceCtx.intervalMeterTypeId
        String maintHistSeqId = created.serviceResult.maintHistSeqId
        GenericValue fixedAssetMaint = from('FixedAssetMaint')
                .where('fixedAssetId', fixedAssetId,
                        'maintHistSeqId', maintHistSeqId)
                .queryOne()

        String productMaintTypeId = testParams.productMaintTypeId ?: 'OIL_CHANGE'
        Timestamp updatedEstimatedCompletionDate =
                UtilDateTime.toTimestamp(testParams.updatedEstimatedCompletionDate ?: '2009-12-22 01:00:00.000')
        Map serviceCtx = [fixedAssetId: fixedAssetId,
                          maintHistSeqId: maintHistSeqId,
                          statusId: statusId,
                          productMaintTypeId: productMaintTypeId,
                          intervalMeterTypeId: intervalMeterTypeId,
                          estimatedCompletionDate: updatedEstimatedCompletionDate,
                          scheduleWorkEffortId: fixedAssetMaint.scheduleWorkEffortId,
                          userLogin: userLogin]

        dispatcher.runSync('updateFixedAssetMaintAndWorkEffort', serviceCtx)
        GenericValue workEffort = from('WorkEffort')
                .where('workEffortId', fixedAssetMaint.scheduleWorkEffortId)
                .queryOne()

        assert fixedAssetMaint
        assert fixedAssetMaint.scheduleWorkEffortId
        assert workEffort
        assert workEffort.estimatedCompletionDate == serviceCtx.estimatedCompletionDate

        // Test case for service updateFixedAssetMaintAndWorkEffort
        String completedStatusId = testParams.completedStatusId ?: 'FAM_COMPLETED'
        serviceCtx = [fixedAssetId: fixedAssetMaint.fixedAssetId,
                          maintHistSeqId: fixedAssetMaint.maintHistSeqId,
                          scheduleWorkEffortId: fixedAssetMaint.scheduleWorkEffortId,
                          statusId: completedStatusId,
                          actualCompletionDate: UtilDateTime.nowTimestamp(),
                          userLogin: userLogin]

        dispatcher.runSync('updateFixedAssetMaintAndWorkEffort', serviceCtx)
        GenericValue newFixedAssetMaint = from('FixedAssetMaint')
                .where('fixedAssetId', fixedAssetId,
                        'maintHistSeqId', maintHistSeqId)
                .queryOne()
        assert newFixedAssetMaint.statusId == completedStatusId
        workEffort = from('WorkEffort')
                .where('workEffortId', fixedAssetMaint.scheduleWorkEffortId)
                .queryOne()
        assert workEffort.currentStatusId == 'CAL_COMPLETED'
        assert workEffort.actualCompletionDate == serviceCtx.actualCompletionDate
    }

}
