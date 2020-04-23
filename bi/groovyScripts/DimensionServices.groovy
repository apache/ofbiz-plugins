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

import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityListIterator
import org.apache.ofbiz.service.ModelService
import org.apache.ofbiz.service.ServiceUtil

def updateGenericDimension() {
    Map inMap

    dimensionEntityName = parameters.dimensionEntityName;
    entryExprs = EntityCondition.makeCondition([
        EntityCondition.makeCondition("lastUpdatedStamp", EntityOperator.GREATER_THAN_EQUAL_TO, parameters.fromDate),
        EntityCondition.makeCondition("lastUpdatedStamp", EntityOperator.LESS_THAN_EQUAL_TO, parameters.thruDate)
    ], EntityOperator.AND)
    switch (dimensionEntityName) {
    case "CountryDimension":
        sourceEntity = "DwhCountrySource"
        naturalKeyFields = "countryId"
        break
    case "CurrencyDimension":
        sourceEntity = "DwhCurrencySource"
        naturalKeyFields = "currencyId"
        break
    }
    queryListIterator = from(sourceEntity).where(entryExprs).queryIterator()
    while(sourceRecord = queryListIterator.next()){
        updateDimension = delegator.makeValue(dimensionEntityName)
        updateDimension.setNonPKFields(sourceRecord)
        inMap = dispatcher.getDispatchContext().makeValidContext("storeGenericDimension", ModelService.IN_PARAM, parameters)
        inMap.naturalKeyFields = naturalKeyFields
        inMap.dimensionValue = updateDimension
        serviceResult = run service: "storeGenericDimension", with: inMap
        if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    };
    queryListIterator.close();
}

def updateOfbizDimension() {
    Map inMap

    dimensionEntityName = parameters.dimensionEntityName;
    entryExprs = EntityCondition.makeCondition([
        EntityCondition.makeCondition("lastUpdatedStamp", EntityOperator.GREATER_THAN_EQUAL_TO, parameters.fromDate),
        EntityCondition.makeCondition("lastUpdatedStamp", EntityOperator.LESS_THAN_EQUAL_TO, parameters.thruDate)
    ], EntityOperator.AND)
    switch (dimensionEntityName) {
    case "AssetDimension":
        sourceEntity = "FixedAsset"
        naturalKeyFields = "fixedAssetId"
        break
    case "FacilityDimension":
        sourceEntity = "Facility"
        naturalKeyFields = "facilityId"
        break
    case "OrganisationDimension":
        sourceEntity = "DwhOrganisationSource"
        naturalKeyFields = "partyId"
        break
    case "ProjectDimension":
        sourceEntity = "DwhProjectSource"
        naturalKeyFields = "projectId"
        break
    case "ProjectTaskDimension":
        naturalKeyFields = "projectTaskId"
        break
    case "SalesChannelDimension":
        sourceEntity = "DwhSalesChannelSource"
        naturalKeyFields = "salesChannelId"
        break
    case "SalesPromoDimension":
        sourceEntity = "DwhSalesPromoSource"
        naturalKeyFields = "salesPromoId"
        break
    case "StoreDimension":
        sourceEntity = "ProductStore"
        naturalKeyFields = "productStoreId"
        break
    }
    queryListIterator = from(sourceEntity).where(entryExprs).queryIterator()
    while(sourceRecord = queryListIterator.next()){
        updateDimension = delegator.makeValue(dimensionEntityName)
        updateDimension.setNonPKFields(sourceRecord)
        inMap = dispatcher.getDispatchContext().makeValidContext("storeOfbizDimension", ModelService.IN_PARAM, parameters)
        inMap.naturalKeyFields = naturalKeyFields
        inMap.dimensionValue = updateDimension
        serviceResult = run service: "storeOfbizDimension", with: inMap
        if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    };
    queryListIterator.close();
};

def updateRolePartyDimension() {
    fromDate = parameters.fromDate
    thruDate = parameters.thruDate
    updateMode = parameters.updateMode
    role = parameters.role
    roleTypeId = role.toUpperCase();
    dimensionEntityName = role+ "Dimension"
    naturalKeyFields = "partyId"

    entryExprs = EntityCondition.makeCondition([
        EntityCondition.makeCondition("lastUpdatedStamp", EntityOperator.GREATER_THAN_EQUAL_TO, parameters.fromDate),
        EntityCondition.makeCondition("lastUpdatedStamp", EntityOperator.LESS_THAN_EQUAL_TO, parameters.thruDate)
    ], EntityOperator.AND)
    // Get mutations in PartyGroup
    partyGroupListIterator = from("PartyGroup").where(entryExprs).queryIterator()
    // get mutations in Person
    personListIterator = from("Person").where(entryExprs).queryIterator()
    entryExprs = EntityCondition.makeCondition([
        EntityCondition.makeCondition("roleTypeId", EntityOperator.EQUALS, roleTypeId),
        EntityCondition.makeCondition("lastUpdatedStamp", EntityOperator.GREATER_THAN_EQUAL_TO, parameters.fromDate),
        EntityCondition.makeCondition("lastUpdatedStamp", EntityOperator.LESS_THAN_EQUAL_TO, parameters.thruDate)
    ], EntityOperator.AND)
    // Get mutation in PartyRole
    partyRoleListIterator = from("PartyRole").where(entryExprs).queryIterator()

    // Complete new records in PartyRole with PartyGroup and Person details
    while(sourceRecord = partyRoleListIterator.next()){
        partyId = sourceRecord.partyId
        party = from("Party").where("partyId", partyId).queryOne()
        if(party) {
            partyTypeId = party.partyTypeId
            switch(party.partyTypeId) {
            case "PERSON":
                person = from("Person").where("partyId", partyId).queryOne()
                partyName = person.firstName + " " + person.middleName + " " + person.lastName;
                partyName = partyName.replace("null","").trim();
                break
            case "PARTY_GROUP":
                partyGroup = from("PartyGroup").where("partyId", partyId).queryOne()
                partyName = partyGroup.groupName
                partyName = partyName.replace("null","").trim();
                break
            }
        }
        updateDimension = delegator.makeValue(dimensionEntityName)
        updateDimension.partyId = partyId
        updateDimension.partyName = partyName
        inMap = dispatcher.getDispatchContext().makeValidContext("storeOfbizDimension", ModelService.IN_PARAM, parameters)
        inMap.naturalKeyFields = naturalKeyFields
        inMap.dimensionValue = updateDimension
        serviceResult = run service: "storeOfbizDimension", with: inMap
        if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    };
    // update mutated PartyGroup records
    while(sourceRecord = partyGroupListIterator.next()){
        partyId = sourceRecord.partyId
        entryExprs = EntityCondition.makeCondition([
            EntityCondition.makeCondition("roleTypeId", EntityOperator.EQUALS, roleTypeId),
            EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, partyId),
        ], EntityOperator.AND)
        isRole = from("PartyRole").where(entryExprs).queryOne()
        if(isRole) {
            dimensionRecord = from(dimensionEntityName).where("partyId", partyId).queryOne()
            if(dimensionRecord) {
                party = from("Party").where("partyId", partyId).queryOne()
                if(party) {
                    partyTypeId = party.partyTypeId
                    switch(party.partyTypeId) {
                        case "PERSON":
                            person = from("Person").where("partyId", partyId).queryOne()
                            partyName = person.firstName + " " + person.middleName + " " + person.lastName;
                            partyName = partyName.replace("null","").trim();
                            break
                        case "PARTY_GROUP":
                            partyGroup = from("PartyGroup").where("partyId", partyId).queryOne()
                            partyName = partyGroup.groupName
                            partyName = partyName.replace("null","").trim();
                            break
                    }
                }
                updateDimension = delegator.makeValue(dimensionEntityName)
                updateDimension.partyId = partyId
                updateDimension.partyName = partyName
                inMap = dispatcher.getDispatchContext().makeValidContext("storeOfbizDimension", ModelService.IN_PARAM, parameters)
                inMap.naturalKeyFields = naturalKeyFields
                inMap.dimensionValue = updateDimension
                serviceResult = run service: "storeOfbizDimension", with: inMap
                if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
            }
        }
    };
    // update mutated Person records
    while(sourceRecord = personListIterator.next()){
        partyId = sourceRecord.partyId
        entryExprs = EntityCondition.makeCondition([
            EntityCondition.makeCondition("roleTypeId", EntityOperator.EQUALS, roleTypeId),
            EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, partyId),
        ], EntityOperator.AND)
        isRole = from("PartyRole").where(entryExprs).queryOne()
        if(isRole) {
            dimensionRecord = from(dimensionEntityName).where("partyId", partyId).queryOne()
            if(dimensionRecord) {
                party = from("Party").where("partyId", partyId).queryOne()
                if(party) {
                    partyTypeId = party.partyTypeId
                    switch(party.partyTypeId) {
                        case "PERSON":
                            person = from("Person").where("partyId", partyId).queryOne()
                            partyName = person.firstName + " " + person.middleName + " " + person.lastName;
                            partyName = partyName.replace("null","").trim();
                            break
                        case "PARTY_GROUP":
                            partyGroup = from("PartyGroup").where("partyId", partyId).queryOne()
                            partyName = partyGroup.groupName
                            partyName = partyName.replace("null","").trim();
                            break
                    }
                }
                updateDimension = delegator.makeValue(dimensionEntityName)
                updateDimension.partyId = partyId
                updateDimension.partyName = partyName
                inMap = dispatcher.getDispatchContext().makeValidContext("storeOfbizDimension", ModelService.IN_PARAM, parameters)
                inMap.naturalKeyFields = naturalKeyFields
                inMap.dimensionValue = updateDimension
                serviceResult = run service: "storeOfbizDimension", with: inMap
                if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
            }
        }
    };
}

def prepareProductDimensionData() {
    GenericValue product = from("Product").where("productId", parameters.productId).queryOne()
    if (product == null) {
        return error(UtilProperties.getMessage('ProductUiLabels', 'ProductProductNotFoundWithProduct', locale))
    }
    productDimension = delegator.makeValue("ProductDimension")
    productDimension.setNonPKFields(product)
    GenericValue productType = select("description").from("ProductType")
        .where("productTypeId", product.productTypeId).cache().queryOne()
    productDimension.productType = productType.description
    Map result = success()
    result.productDimension = productDimension
    return result
}

def loadProductInProductDimension() {
    Map inMap = dispatcher.getDispatchContext().makeValidContext("prepareProductDimensionData", ModelService.IN_PARAM, parameters)
    serviceResult = run service: "prepareProductDimensionData", with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    GenericValue productDimension
    if (ServiceUtil.isSuccess(serviceResult)) {
        productDimension  = serviceResult.productDimension
    }
    inMap.clear()
    inMap = dispatcher.getDispatchContext().makeValidContext("storeGenericDimension", ModelService.IN_PARAM, parameters)
    inMap.naturalKeyFields = "productId"
    inMap.dimensionValue = productDimension
    serviceResult = run service: "storeOfbizDimension", with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
}

def loadAllProductsInProductDimension() {
    EntityListIterator listIterator = from("Product").queryIterator()
    GenericValue product
    Map inMap
    while (product = listIterator.next()) {
        inMap = dispatcher.getDispatchContext().makeValidContext("loadProductInProductDimension", ModelService.IN_PARAM, parameters)
        inMap.productId = product.productId
        serviceResullt = run service: "loadProductInProductDimension", with: inMap
        if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    }
}

def updateProductDimension() {
    Map inMap
    dimensionEntityName = parameters.dimensionEntityName;
    sourceEntity = "Product"
    entryExprs = EntityCondition.makeCondition([
        EntityCondition.makeCondition("lastUpdatedStamp", EntityOperator.GREATER_THAN_EQUAL_TO, parameters.fromDate),
        EntityCondition.makeCondition("lastUpdatedStamp", EntityOperator.LESS_THAN_EQUAL_TO, parameters.thruDate)
    ], EntityOperator.AND)
    queryListIterator = from(sourceEntity).where(entryExprs).queryIterator()
    while(sourceRecord = queryListIterator.next()){
        inMap = dispatcher.getDispatchContext().makeValidContext("loadProductInProductDimension", ModelService.IN_PARAM, parameters)
        inMap.productId = sourceRecord.productId
        serviceResult = run service: "loadProductInProductDimension", with: inMap
        if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    };
    queryListIterator.close();
}