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
package org.apache.ofbiz.assetmaint.test

import java.sql.Timestamp
import java.text.SimpleDateFormat
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.service.testtools.OFBizTestCase

class FixedAssetMaintTests extends OFBizTestCase {
    public FixedAssetMaintTests(String name) {
        super(name)
    }

    void testCreateFixedAssetMaintUpdateWorkEffortWithProductMaint() {
        // Test case for service createFixedAssetMaintUpdateWorkEffort with a product Maintenance
        String fixedAssetId = "DEMO_VEHICLE_01"
        Map serviceCtx = [:]
        serviceCtx.fixedAssetId = fixedAssetId
        serviceCtx.statusId = "FAM_CREATED"
        serviceCtx.productMaintSeqId = "seq03"  // product maintenance
        serviceCtx.intervalMeterTypeId = "ODOMETER"

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String estimatedStartDate = "2009-12-18 00:00:00.000"
        serviceCtx.estimatedStartDate = new Timestamp(sdf.parse(estimatedStartDate).getTime());

        String estimatedCompletionDate = "2009-12-18 01:00:00.000"
        serviceCtx.estimatedCompletionDate = new Timestamp(sdf.parse(estimatedCompletionDate).getTime())

        String actualStartDate = "2009-12-20 00:00:00.000"
        serviceCtx.actualStartDate = new Timestamp(sdf.parse(actualStartDate).getTime())

        GenericValue userLogin = from("UserLogin").where("userLoginId", "system").queryOne()
        serviceCtx.userLogin = userLogin
        Map serviceResult = dispatcher.runSync('createFixedAssetMaintUpdateWorkEffort', serviceCtx)
        String maintHistSeqId = serviceResult.maintHistSeqId

        GenericValue fixedAssetMaint = from("FixedAssetMaint").where("fixedAssetId", fixedAssetId).queryFirst()
        GenericValue workEffort = from("WorkEffort").where("workEffortId", fixedAssetMaint.scheduleWorkEffortId).queryOne()
        assert fixedAssetMaint
        assert fixedAssetMaint.scheduleWorkEffortId
        assert workEffort
        assert workEffort.estimatedStartDate == serviceCtx.estimatedStartDate
        assert workEffort.estimatedCompletionDate == serviceCtx.estimatedCompletionDate
        assert workEffort.actualStartDate == serviceCtx.actualStartDate
    }

    void testCreateFixedAssetMaintUpdateWorkEffortWithoutProductMaint() {
        // Test case for service createFixedAssetMaintUpdateWorkEffort without a product maintenance
        String fixedAssetId = "DEMO_VEHICLE_01"
        Map serviceCtx = [:]
        serviceCtx.fixedAssetId = fixedAssetId
        serviceCtx.statusId = "FAM_CREATED"
        serviceCtx.productMaintTypeId = "OIL_CHANGE"
        serviceCtx.intervalMeterTypeId = "ODOMETER"

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String estimatedStartDate = "2009-12-18 00:00:00.000"
        serviceCtx.estimatedStartDate = new Timestamp(sdf.parse(estimatedStartDate).getTime());

        String estimatedCompletionDate = "2009-12-18 01:00:00.000"
        serviceCtx.estimatedCompletionDate = new Timestamp(sdf.parse(estimatedCompletionDate).getTime())

        String actualStartDate = "2009-12-20 00:00:00.000"
        serviceCtx.actualStartDate = new Timestamp(sdf.parse(actualStartDate).getTime())

        GenericValue userLogin = from("UserLogin").where("userLoginId", "system").queryOne()
        serviceCtx.userLogin = userLogin
        Map serviceResult = dispatcher.runSync('createFixedAssetMaintUpdateWorkEffort', serviceCtx)
        String maintHistSeqId = serviceResult.maintHistSeqId

        GenericValue fixedAssetMaint = from("FixedAssetMaint").where("fixedAssetId", fixedAssetId, "maintHistSeqId", maintHistSeqId).queryOne()
        GenericValue workEffort = from("WorkEffort").where("workEffortId", fixedAssetMaint.scheduleWorkEffortId).queryOne()

        assert fixedAssetMaint
        assert fixedAssetMaint.scheduleWorkEffortId
        assert workEffort
        assert workEffort.estimatedStartDate == serviceCtx.estimatedStartDate
        assert workEffort.estimatedCompletionDate == serviceCtx.estimatedCompletionDate
        assert workEffort.actualStartDate == serviceCtx.actualStartDate
    }

    void testUpdateFixedAssetMaintAndWorkEffort() {
        // Test case for service updateFixedAssetMaintAndWorkEffort
        String fixedAssetId = "DEMO_VEHICLE_01"
        String maintHistSeqId = "00001"    // Sequence created by testCreateFixedAssetMaintUpdateWorkEffortWithProductMaint
        Map serviceCtx = [:]
        serviceCtx.fixedAssetId = fixedAssetId
        serviceCtx.maintHistSeqId = maintHistSeqId
        serviceCtx.statusId = "FAM_CREATED"
        serviceCtx.productMaintTypeId = "OIL_CHANGE"
        serviceCtx.intervalMeterTypeId = "ODOMETER"

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String estimatedCompletionDate = "2009-12-22 01:00:00.000"  // Changed estimatedCompletionDate to test update service
        serviceCtx.estimatedCompletionDate = new Timestamp(sdf.parse(estimatedCompletionDate).getTime())

        GenericValue fixedAssetMaint = from("FixedAssetMaint").where("fixedAssetId", fixedAssetId).queryFirst()
        serviceCtx.workEffortId = fixedAssetMaint.scheduleWorkEffortId

        GenericValue userLogin = from("UserLogin").where("userLoginId", "system").queryOne()
        serviceCtx.userLogin = userLogin

        Map serviceResult = dispatcher.runSync('updateFixedAssetMaintAndWorkEffort', serviceCtx)

        fixedAssetMaint.clear()
        fixedAssetMaint = from("FixedAssetMaint").where("fixedAssetId", fixedAssetId).queryFirst()
        GenericValue workEffort = from("WorkEffort").where("workEffortId", fixedAssetMaint.scheduleWorkEffortId).queryOne()

        assert fixedAssetMaint
        assert fixedAssetMaint.scheduleWorkEffortId
        assert workEffort
        assert workEffort.estimatedCompletionDate == serviceCtx.estimatedCompletionDate
    }

    void testUpdateFixedAssetMaintAndWorkEffortComplete() {
        // Test case for service updateFixedAssetMaintAndWorkEffort
        GenericValue fixedAssetMaint = from("FixedAssetMaint").where("fixedAssetId", "DEMO_VEHICLE_01", "maintHistSeqId", "00001").queryOne()
        Map serviceCtx = [:]
        serviceCtx.fixedAssetId = fixedAssetMaint.fixedAssetId
        serviceCtx.maintHistSeqId = fixedAssetMaint.maintHistSeqId
        serviceCtx.scheduleWorkEffortId = fixedAssetMaint.scheduleWorkEffortId
        serviceCtx.statusId = "FAM_COMPLETED"
        serviceCtx.actualCompletionDate = UtilDateTime.nowTimestamp()

        GenericValue userLogin = from("UserLogin").where("userLoginId", "system").queryOne()
        serviceCtx.userLogin = userLogin

        Map serviceResult = dispatcher.runSync('updateFixedAssetMaintAndWorkEffort', serviceCtx)
        GenericValue newFixedAssetMaint = from("FixedAssetMaint").where("fixedAssetId", "DEMO_VEHICLE_01", "maintHistSeqId", "00001").queryOne()
        assert newFixedAssetMaint.statusId == "FAM_COMPLETED"
        GenericValue workEffort = from("WorkEffort").where("workEffortId", fixedAssetMaint.scheduleWorkEffortId).queryOne()
        assert workEffort.currentStatusId == "CAL_COMPLETED"
        assert workEffort.actualCompletionDate == serviceCtx.actualCompletionDate
    }
}