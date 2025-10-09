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
package org.apache.ofbiz.scrum

import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityConditionBuilder

List products = []
String productId = parameters.productId
String internalName = parameters.internalName
String statusId = parameters.statusId
if (statusId == 'Any') {
    statusId = null
}
partyId = parameters.partyId

if (userLogin) {
    EntityCondition condition = new EntityConditionBuilder().AND {
        EQUALS(productTypeId: 'SCRUM_ITEM')
        EQUALS(roleTypeId: 'PRODUCT_OWNER_COMP')
        EQUALS(thruDate: null)
        if (partyId) {
            EQUALS(partyId: partyId)
        }
        if (productId) {
            LIKE(productId: "$productId%")
        }
        if (internalName) {
            LIKE(internalName: "%$internalName%")
        }
        if (statusId) {
            if (statusId == 'PRODUCT_ACTIVE') {
                EQUALS(supportDiscontinuationDate: null)
            } else {
                NOT_EQUAL(supportDiscontinuationDate: null)
            }
        }
    }
    List allProducts = from('ProductAndRole')
            .where(condition)
            .orderBy('groupName', 'internalName')
            .queryList()

    condition = new EntityConditionBuilder().AND {
        EQUALS(partyId: userLogin.partyId)
        NOT_EQUAL(partyStatusId: 'PARTY_DISABLED')
        EQUALS(thruDate: null)
    }
    List partyAndSecurityGroupList = select('partyId', 'groupId')
            .from('ScrumMemberUserLoginAndSecurityGroup')
            .where(condition)
            .orderBy('partyId')
            .queryList()
    context.partyAndSecurityGroupList = partyAndSecurityGroupList

    boolean addAllProducts = false
    allProducts.each { it ->
        Map product = it.getAllFields()
        GenericValue productGV = from('Product').where('productId', product.productId).queryOne()
        product.longDescription = productGV.longDescription
        if (security.hasEntityPermission('SCRUM', '_ADMIN', session)) {
            addAllProducts = true
        } else {
            boolean isMember = false
            if (partyAndSecurityGroupList) {
                String groupId = partyAndSecurityGroupList.first().groupId
                switch (groupId) {
                    case 'SCRUM_PRODUCT_OWNER':
                        isMember = from('ProductRole')
                                .where(productId: product.productId, partyId: userLogin.partyId, thruDate: null)
                                .queryCount() > 0
                        break
                    case 'SCRUM_STAKEHOLDER':
                        // check in company relationship.
                        condition = new EntityConditionBuilder().AND {
                            EQUALS(partyId: userLogin.partyId)
                            EQUALS(roleTypeId: 'STAKEHOLDER')
                            NOT_EQUAL(partyStatusId: 'PARTY_DISABLED')
                            EQUALS(thruDate: null)
                        }
                        String partyIdFrom = from('ScrumRolesPersonAndCompany')
                                .where(condition)
                                .queryFirst()?.partyIdFrom

                        isMember = from('ProductRole')
                                .where(partyId: partyIdFrom, roleTypeId: 'PRODUCT_OWNER_COMP', thruDate: null)
                                .queryCount() > 0 ?:
                                from('ProductAndRole')
                                        .where(productId: product.productId,
                                                partyId: userLogin.partyId,
                                                roleTypeId: 'STAKEHOLDER',
                                                supportDiscontinuationDate: null,
                                                thruDate: null)
                                        .queryCount() > 0
                        break
                    case 'SCRUM_MASTER':
                        //check in product.
                        isMember = from('ProductAndRole')
                                .where(productId: product.productId,
                                        partyId: userLogin.partyId,
                                        roleTypeId: 'SCRUM_MASTER',
                                        supportDiscontinuationDate: null,
                                        thruDate: null)
                                .queryCount() > 0

                        //check in project.
                        if (!isMember) {
                            List projectIds =
                                    from('WorkEffortAndProduct')
                                            .where(productId: product.productId,
                                                    workEffortTypeId: 'SCRUM_PROJECT',
                                                    currentStatusId: 'SPJ_ACTIVE')
                                            .select('workEffortId')
                                            .queryList()*.workEffortId
                            condition = new EntityConditionBuilder().AND {
                                EQUALS(partyId: userLogin.partyId)
                                IN(workEffortId: projectIds)
                            }
                            isMember = from('WorkEffortPartyAssignment')
                                    .where(condition)
                                    .queryCount() > 0
                        }

                        //check in sprint.
                        if (!isMember) {
                            List projectIds = from('WorkEffortAndProduct')
                                    .where(productId: product.productId,
                                            workEffortTypeId: 'SCRUM_PROJECT',
                                            currentStatusId: 'SPJ_ACTIVE')
                                    .queryList()*.workEffortId
                            condition = new EntityConditionBuilder().AND {
                                EQUALS(currentStatusId: 'SPRINT_ACTIVE')
                                IN(workEffortParentId: projectIds)
                            }
                            List sprintIds = from('WorkEffort')
                                    .where(condition)
                                    .queryList()*.workEffortId
                            condition = new EntityConditionBuilder().AND {
                                EQUALS(partyId: userLogin.partyId)
                                IN(workEffortId: sprintIds)
                            }
                            isMember = from('WorkEffortPartyAssignment')
                                    .where(condition)
                                    .queryCount() > 0
                        }
                        break
                    default:
                        List projectIds = from('WorkEffortAndProduct')
                                .where(productId: product.productId,
                                        workEffortTypeId: 'SCRUM_PROJECT',
                                        currentStatusId: 'SPJ_ACTIVE')
                                .queryList()*.workEffortId
                        condition = new EntityConditionBuilder().AND {
                            EQUALS(currentStatusId: 'SPRINT_ACTIVE')
                            IN(workEffortParentId: projectIds)
                        }
                        List sprintIds = from('WorkEffort')
                                .where(condition)
                                .queryList()*.workEffortId
                        condition = new EntityConditionBuilder().AND {
                            EQUALS(partyId: userLogin.partyId)
                            IN(workEffortId: sprintIds)
                        }
                        isMember = from('WorkEffortPartyAssignment')
                                .where()
                                .queryCount() > 0
                        if (!isMember) {
                            List unplannedBacklogIds = from('UnPlannedBacklogsAndTasks')
                                    .where(condition)*.workEffortId
                            if (unplannedBacklogIds) {
                                condition = new EntityConditionBuilder().AND {
                                    EQUALS(partyId: userLogin.partyId)
                                    IN(workEffortId: unplannedBacklogIds)
                                }
                                isMember = from('WorkEffortPartyAssignment')
                                        .where(condition)
                                        .queryCount() > 0
                            }
                        }
                        break
                }
                if (isMember) {
                    products << product
                }
            }
        }
        if (addAllProducts) {
            products << product
        }
    }
} else {
    logError('Party ID missing =========>>> : null ')
}

if (products) {
    context.listIt = products
}
