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
import org.apache.ofbiz.service.testtools.OFBizTestCase

class FixedAssetMaintTests extends OFBizTestCase {

    public FixedAssetMaintTests(String name) {
        super(name)
    }

    void testCreateFixedAssetMaintUpdateWorkEffortWithProductMaint() {
        // Test case for service createFixedAssetMaintUpdateWorkEffort with a product Maintenance
        String fixedAssetId = 'DEMO_VEHICLE_01'
        Map serviceCtx = [fixedAssetId: fixedAssetId,
                          statusId: 'FAM_CREATED',
                          productMaintSeqId: 'seq03',  // product maintenance,
                          intervalMeterTypeId: 'ODOMETER',
                          estimatedStartDate: UtilDateTime.toTimestamp('2009-12-18 00:00:00.000'),
                          estimatedCompletionDate: UtilDateTime.toTimestamp('2009-12-18 00:00:00.000'),
                          actualStartDate: UtilDateTime.toTimestamp('2009-12-20 00:00:00.000'),
                          userLogin: userLogin]
        Map serviceResult = dispatcher.runSync('createFixedAssetMaintUpdateWorkEffort', serviceCtx)
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
    void testCreateFixedAssetMaintUpdateWorkEffortWithoutProductMaint() {
        // Test case for service createFixedAssetMaintUpdateWorkEffort without a product maintenance
        String fixedAssetId = 'DEMO_VEHICLE_01'
        Map serviceCtx = [fixedAssetId: fixedAssetId,
                          statusId: 'FAM_CREATED',
                          productMaintTypeId: 'OIL_CHANGE',
                          intervalMeterTypeId: 'ODOMETER',
                          estimatedStartDate: UtilDateTime.toTimestamp('2009-12-18 00:00:00.000'),
                          estimatedCompletionDate: UtilDateTime.toTimestamp('2009-12-18 00:00:00.000'),
                          actualStartDate: UtilDateTime.toTimestamp('2009-12-20 00:00:00.000'),
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

    void testUpdateFixedAssetMaintAndWorkEffort() {
        // Test case for service updateFixedAssetMaintAndWorkEffort
        String fixedAssetId = 'DEMO_VEHICLE_01'
        Map serviceCtx = [fixedAssetId: fixedAssetId,
                          statusId: 'FAM_CREATED',
                          productMaintSeqId: 'seq03',  // product maintenance,
                          intervalMeterTypeId: 'ODOMETER',
                          estimatedStartDate: UtilDateTime.toTimestamp('2009-12-18 00:00:00.000'),
                          estimatedCompletionDate: UtilDateTime.toTimestamp('2009-12-18 00:00:00.000'),
                          actualStartDate: UtilDateTime.toTimestamp('2009-12-20 00:00:00.000'),
                          userLogin: userLogin]
        Map serviceResult = dispatcher.runSync('createFixedAssetMaintUpdateWorkEffort', serviceCtx)
        String maintHistSeqId = serviceResult.maintHistSeqId
        GenericValue fixedAssetMaint = from('FixedAssetMaint')
                .where('fixedAssetId', fixedAssetId,
                        'maintHistSeqId', maintHistSeqId)
                .queryOne()
        serviceCtx = [fixedAssetId: fixedAssetId,
                          maintHistSeqId: maintHistSeqId,
                          statusId: 'FAM_CREATED',
                          productMaintTypeId: 'OIL_CHANGE',
                          intervalMeterTypeId: 'ODOMETER',
                          estimatedCompletionDate: UtilDateTime.toTimestamp('2009-12-22 01:00:00.000'),
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
        serviceCtx = [fixedAssetId: fixedAssetMaint.fixedAssetId,
                          maintHistSeqId: fixedAssetMaint.maintHistSeqId,
                          scheduleWorkEffortId: fixedAssetMaint.scheduleWorkEffortId,
                          statusId: 'FAM_COMPLETED',
                          actualCompletionDate: UtilDateTime.nowTimestamp(),
                          userLogin: userLogin]

        dispatcher.runSync('updateFixedAssetMaintAndWorkEffort', serviceCtx)
        GenericValue newFixedAssetMaint = from('FixedAssetMaint')
                .where('fixedAssetId', 'DEMO_VEHICLE_01',
                        'maintHistSeqId', maintHistSeqId)
                .queryOne()
        assert newFixedAssetMaint.statusId == 'FAM_COMPLETED'
        workEffort = from('WorkEffort')
                .where('workEffortId', fixedAssetMaint.scheduleWorkEffortId)
                .queryOne()
        assert workEffort.currentStatusId == 'CAL_COMPLETED'
        assert workEffort.actualCompletionDate == serviceCtx.actualCompletionDate
    }

}
