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

import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityFieldValue
import org.apache.ofbiz.entity.condition.EntityFunction
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.base.util.UtilMisc
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.base.util.Debug


delegator = request.getAttribute("delegator")
entityLabelMap = UtilProperties.getResourceBundleMap("PartyEntityLabels", locale)


andExprs = []
//Debug.logInfo("DEBUG request: ${request}", "FindContactMechPuroseType.groovy")
fieldValue = request.getAttribute("contactMechTypeId")
if (! fieldValue) fieldValue = request.getParameter("contactMechTypeId")
//Debug.logInfo("DEBUG fieldValue: ${fieldValue}", "FindContactMechPuroseType.groovy")
if (fieldValue) {
    andExprs.add(EntityCondition.makeCondition(EntityFunction.UPPER(EntityFieldValue.makeFieldValue("contactMechTypeId")),
            EntityOperator.EQUALS, fieldValue.toUpperCase()))
}
//Debug.logInfo("DEBUG andExprs: ${andExprs}", "FindContactMechPuroseType.groovy")
purposeTypeIdList = []
if (andExprs) {
    purposeTypeIdList = select("contactMechPurposeTypeId").from("ContactMechTypePurpose").where(andExprs).queryList()
    purposeTypeList = []
    for (GenericValue purposeTypeGV : purposeTypeIdList) {
        purposeTypeList.add(UtilMisc.toMap("contactMechPurposeTypeId", purposeTypeGV.contactMechPurposeTypeId,
                "description", entityLabelMap["ContactMechPurposeType.description." + purposeTypeGV.contactMechPurposeTypeId]))
    }
    //Debug.logInfo("purposeTypeList: ${purposeTypeList}", "FindContactMechPuroseType.groovy")
    request.setAttribute("purposeTypeList", UtilMisc.sortMaps(purposeTypeList, UtilMisc.toList("description")))
}
return "success"
