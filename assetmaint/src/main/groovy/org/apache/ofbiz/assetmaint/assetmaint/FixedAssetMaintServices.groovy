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
package org.apache.ofbiz.assetmaint.assetmaint


import org.apache.ofbiz.entity.GenericValue

/**
 * Create FixedAssetMaint and Update Schedule information in WorkEffort
 * @return
 */
def createFixedAssetMaintUpdateWorkEffort() {
    Map serviceResult = run service: 'createFixedAssetMaint', with: parameters
    
    GenericValue findAssetMaint = from('FixedAssetMaint')
            .where(maintHistSeqId: serviceResult.maintHistSeqId,
                    fixedAssetId: parameters.fixedAssetId)
            .queryOne()

    run service: 'updateWorkEffort', with: [*: parameters,
                                            workEffortId: findAssetMaint.scheduleWorkEffortId]
    return success([maintHistSeqId: serviceResult.maintHistSeqId])
}

/**
 * Update FixedAssetMaint and Schedule information in WorkEffort
 * @return
 */

def updateFixedAssetMaintAndWorkEffort() {
    run service: 'updateFixedAssetMaint', with: parameters

    Map updateWorkEffortCtx = [*: parameters]
    if (parameters.statusId == 'FAM_CANCELLED') {
        updateWorkEffortCtx.currentStatusId = 'CAL_CANCELLED'
    }
    updateWorkEffortCtx.workEffortId = parameters.scheduleWorkEffortId
    GenericValue workEffort = from('WorkEffort').where(workEffortId: parameters.scheduleWorkEffortId).queryOne()
    updateWorkEffortCtx.actualCompletionDate = parameters.actualCompletionDate ?:
            (workEffort ? workEffort.actualCompletionDate : null)

    run service: 'updateWorkEffort', with: updateWorkEffortCtx
    return success()
}

/**
 * Create WorkEffort and Associate it with Parent (identified by workEffortFromId)
 * @return
 */
def createWorkEffortAndAssocWithParent() {
    Map serviceResult = run service: 'createWorkEffortAndAssoc', with: [*:parameters,
                                                                        workEffortId: parameters.workEffortIdTo]
    return serviceResult
}

/**
 * Asset Maintenance permission logic
 * @return
 */
def assetMaintPermissionCheck() {
    Map serviceResult = run service: 'genericBasePermissionCheck', with: [*:parameters,
                                                                          primaryPermission: 'ASSETMAINT']
    return serviceResult
}
