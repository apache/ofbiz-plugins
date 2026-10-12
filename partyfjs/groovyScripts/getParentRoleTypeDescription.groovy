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

import org.apache.ofbiz.base.util.UtilMisc
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator

//get list of roleTypeId with childs and its description

partyId = parameters.partyId ?: parameters.party_id

roleTypeEntity = UtilProperties.getPropertyValue('general.properties', 'entityRoleTypeGroupe', 'RoleType') // to be able to use RoleTypeGroup if it exist

parentRoleList = select("parentTypeId").from(roleTypeEntity).where(EntityCondition.makeCondition("parentTypeId", EntityOperator.NOT_EQUAL, null)).distinct().queryList()
parentRoleList.each { parentRole ->
    roleType = from("RoleType").where("roleTypeId", parentRole.parentTypeId).queryOne();
    parentRole.description = roleType.get('description',locale)
}
context.parentRoleList = UtilMisc.sortMaps(parentRoleList, UtilMisc.toList("+description"))

