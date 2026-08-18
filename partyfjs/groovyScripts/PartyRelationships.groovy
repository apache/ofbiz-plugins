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

import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityConditionBuilder

partyId = parameters.partyId ?: parameters.party_id

roleTypeEntity = UtilProperties.getPropertyValue('general.properties', 'entityRoleTypeGroupe', 'RoleType') // to be able to use RoleTypeGroup if it exist
context.roleTypeGroupIdFrom = context.roleTypeGroupIdFrom
def roleTypeIdFromList = []
if (context.roleTypeGroupIdFrom) {
    listGv = from(roleTypeEntity).where("parentTypeId", context.roleTypeGroupIdFrom).queryList()
    listGv.each{ roleType -> roleTypeIdFromList << roleType.roleTypeId }
}

context.roleTypeGroupIdTo = context.roleTypeGroupIdTo
def roleTypeIdToList = []
if (context.roleTypeGroupIdTo) {
    listGv = from(roleTypeEntity).where("parentTypeId", context.roleTypeGroupIdTo).queryList()
    listGv.each{ roleType -> roleTypeIdToList << roleType.roleTypeId }
}

sortField = parameters.sortField ? parameters.sortField : "fromDate"

//Build condition
condList = []
exprBldr = new EntityConditionBuilder()
def EntityCondition condition
if (relationIs == "FROM") condition = exprBldr.AND() { EQUALS(partyIdFrom: parameters.partyId) }
if (relationIs != "FROM") condition = exprBldr.AND() { EQUALS(partyIdTo: parameters.partyId) }

context.partyRelationshipTypeIdAttr = context.partyRelationshipTypeId
if (context.partyRelationshipTypeIdAttr) condition = exprBldr.AND(condition) { EQUALS(partyRelationshipTypeId: context.partyRelationshipTypeIdAttr) }
    
if (parameters.roleTypeIdFrom) condition = exprBldr.AND(condition) { EQUALS(roleTypeIdFrom: parameters.roleTypeIdFrom) }
if (parameters.roleTypeIdTo) condition = exprBldr.AND(condition) { EQUALS(roleTypeIdTo: parameters.roleTypeIdTo) }

if (roleTypeIdFromList) condition = exprBldr.AND(condition) { IN(roleTypeIdFrom: roleTypeIdFromList) }
if (roleTypeIdToList) condition = exprBldr.AND(condition) { IN(roleTypeIdTo: roleTypeIdToList) }

//condition = exprBldr.AND() condList 

if (parameters.showHistory == "Y") 
    context.partyRelationList = from("PartyRelationship").where(condition).orderBy(sortField).queryList()
else
    context.partyRelationList = from("PartyRelationship").where(condition).orderBy(sortField).filterByDate().queryList()

context.Y = "Y" // to simplify use-when condition writing
context.showEditButton = context.showEditButton   ? context.showEditButton : "Y"
context.showDeleteButton = context.showDeleteButton ? context.showDeleteButton : "Y"
context.showHistoryButton = context.showHistoryButton ? context.showHistoryButton : "Y"
context.showHistory = parameters.showHistory ? parameters.showHistory : "N"
context.editArea = "PartyRelation" + (relationIs == "FROM" ? "From" : "To") + "s_editArea"

