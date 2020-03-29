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
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityListIterator
import org.apache.ofbiz.service.ModelService
import org.apache.ofbiz.service.ServiceUtil

def quickInitDataWarehouse() {
    // load records  in the Date Dimension
    Map inMap = dispatcher.getDispatchContext().makeValidContext("loadDateDimension", ModelService.IN_PARAM, parameters)
    serviceResult = run service: "loadDateDimension", with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    // load records in the Asset Dimension
    serviceResult = run service: "loadAssetDimension"
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    // load records in the Country Dimension
    serviceResult = run service: "loadCountryDimension"
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    // load records in the Currency Dimension
    serviceResult = run service: "loadCurrencyDimension"
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
   // load records in the Facility Dimension
    serviceResult = run service: "loadFacilityDimension"
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    // load records in the Organisation Dimension
    serviceResult = run service: "loadOrganisationDimension"
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    // load records in the Carrier Dimension
    inMap.clear()
    inMap.role="Carrier"
    serviceResult = run service: "loadRolePartyDimension", with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    // load records in the Customer Dimension
    inMap.clear()
    inMap.role="Customer"
    serviceResult = run service: "loadRolePartyDimension", with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    // load records in the Supplier Dimension
    inMap.clear()
    inMap.role="Supplier"
    serviceResult = run service: "loadRolePartyDimension", with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    // loads all products in the ProductDimension
    serviceResult = run service: "loadAllProductsInProductDimension"
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    // load records in the Store Dimension
    serviceResult = run service: "loadStoreDimension"
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    // loads the invoice items in the SalesInvoiceItemFact fact entity
    entryExprs = EntityCondition.makeCondition([
            EntityCondition.makeCondition("invoiceTypeId", EntityOperator.EQUALS, "SALES_INVOICE"),
            EntityCondition.makeCondition("invoiceDate", EntityOperator.GREATER_THAN_EQUAL_TO, parameters.fromDate),
            EntityCondition.makeCondition("invoiceDate", EntityOperator.LESS_THAN_EQUAL_TO, parameters.thruDate)
    ], EntityOperator.AND)
    EntityListIterator listIterator = from("Invoice").where(entryExprs).queryIterator()
    GenericValue iterator
    while (iterator = listIterator.next()) {
        inMap.invoiceId = iterator.invoiceId
        serviceResult = run service: "loadSalesInvoiceFact", with: inMap
        if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    }

    // loads the order items in the SalesOrderItemFact fact entity
    entryExprs = EntityCondition.makeCondition([
            EntityCondition.makeCondition("orderTypeId", EntityOperator.EQUALS, "SALES_ORDER"),
            EntityCondition.makeCondition("orderDate", EntityOperator.GREATER_THAN_EQUAL_TO, parameters.fromDate),
            EntityCondition.makeCondition("orderDate", EntityOperator.LESS_THAN_EQUAL_TO, parameters.thruDate)
    ], EntityOperator.AND)
    listIterator = from("OrderHeader").where(entryExprs).queryIterator()
    inMap.clear()
    while (iterator = listIterator.next()) {
        inMap.orderId = iterator.orderId
        serviceResult = run service: "loadSalesOrderFact", with: inMap
        if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    }

    // loads the inventory items in the InventoryItemFact fact entity
    listIterator = from("InventoryItem").where("inventoryItemTypeId", "NON_SERIAL_INV_ITEM").queryIterator()
    inMap.clear()
    while (iterator = listIterator.next()) {
        inMap.inventoryItemId = iterator.inventoryItemId
        serviceResult = run service: "loadInventoryItemFact", with: inMap
        if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    }
}

def loadAssetDimension() {
    // Initialize the AssetDimension using the update strategy of 'type 1
    dimensionEntityName = "AssetDimension";
    queryListIterator = from("FixedAsset").queryIterator();
    while(fixedAsset = queryListIterator.next()){
        fixedAssetId = fixedAsset.fixedAssetId;
        dimRecord = getDimensionRecord(dimensionEntityName,"fixedAssetId", fixedAssetId);
        if(!dimRecord) {
            dimensionId = delegator.getNextSeqId(dimensionEntityName);
            newEntity = makeValue(dimensionEntityName);
            newEntity.dimensionId = dimensionId;
            newEntity.fixedAssetId = fixedAssetId;
            newEntity.fixedAssetName = fixedAsset.fixedAssetName;
            newEntity.create();
        };
    };
    queryListIterator.close();
};

def loadCountryDimension(){
    // Initialize the CountryDimension using the update strategy of 'type 1
    queryListIterator = from("Geo").where("geoTypeId","COUNTRY").queryIterator()
    while(country = queryListIterator.next()){
        countryId = country.geoId
        dimRecord = getDimensionRecord("CountryDimension","countryId", countryId)
        if(!dimRecord){
            dimensionId = countryId
            newEntity = makeValue("CountryDimension")
            newEntity.dimensionId = dimensionId
            newEntity.countryId = countryId
            newEntity.countryCode = country.geoCode
            newEntity.countryNumCode = country.geoSecCode
            countryTeleRecord = from("CountryTeleCode").where("countryCode", country.geoCode).queryOne()
            if(countryTeleRecord){
                newEntity.countryTeleCode = countryTeleRecord.teleCode
            }
            newEntity.countryName = country.geoName
            newEntity.create()
        }
    }
    queryListIterator.close()
}

def loadCurrencyDimension() {
    dimensionEntityName = "CurrencyDimension"
    queryListIterator = from("Uom").where("uomTypeId","CURRENCY_MEASURE ").queryIterator()
    while(currency = queryListIterator.next()){
        currencyId = currency.uomId
        dimRecord = getDimensionRecord(dimensionEntityName,"currencyId", currencyId)
        if(!dimRecord) {
            dimensionId = currencyId
            newEntity = makeValue(dimensionEntityName)
            newEntity.dimensionId = dimensionId
            newEntity.currencyId = dimensionId
            newEntity.description = currency.description
            newEntity.create()
        }
    }
    queryListIterator.close()
}

def loadFacilityDimension() {
    // Initialize the FacilityDimension using the update strategy of 'type 1
    dimensionEntityName = "FacilityDimension";
    queryListIterator = from("Facility").queryIterator();
    while(facility = queryListIterator.next()){
        facilityId = facility.facilityId;
        dimRecord = getDimensionRecord(dimensionEntityName,"facilityId", facilityId);
        if(!dimRecord) {
            dimensionId = delegator.getNextSeqId(dimensionEntityName);
            newEntity = makeValue(dimensionEntityName);
            newEntity.dimensionId = dimensionId;
            newEntity.facilityId = facilityId;
            newEntity.facilityName = facility.facilityName;
            newEntity.create();
        };
    };
    queryListIterator.close();
};

def loadOrganisationDimension(){
    // Initialize the OrganisationDimension using the update strategy of 'type 1
    organisationListIterator = from("PartyAcctgPrefAndGroup").where("roleTypeId", "INTERNAL_ORGANIZATIO").queryIterator();
    while(organisation = organisationListIterator.next()){
        partyId = organisation.partyId;
        dimRecord = getDimensionRecord("OrganisationDimension","partyId", partyId);
        if(!dimRecord){
            dimensionId = delegator.getNextSeqId("OrganisationDimension");
            newEntity = makeValue("OrganisationDimension");
            newEntity.dimensionId = dimensionId;
            newEntity.partyId = partyId;
            newEntity.partyName = organisation.groupName;
            newEntity.baseCurrencyDimId = organisation.baseCurrencyUomId;
            newEntity.create();
        };
    };
};

def loadRolePartyDimension(role){
    // Initialize the appropriate 'Role'Dimension using the update strategy of 'type 1
    role = parameters.role;
    dimensionName = role + "Dimension";
    roleTypeId = role.toUpperCase();
    partyListIterator = from("PartyRoleAndPartyDetail").where("roleTypeId", roleTypeId).queryIterator();
    while(party = partyListIterator.next()){
        partyId = party.partyId;
        dimRecord = getDimensionRecord(dimensionName,"partyId", partyId);
        if(!dimensionRecord){
            dimensionId = delegator.getNextSeqId(dimensionName);
            partyTypeId = party.partyTypeId;
            if("PARTY_GROUP" == partyTypeId){
                partyName = party.groupName;
            }else if("PERSON" == partyTypeId){
                partyName = party.firstName + " " + party.middleName + " " + party.lastName;
            } else {
                 partyName = party.groupName + " " + party.firstName + " " + party.middleInitial + " " + party.lastName;
            };
            partyName = partyName.replace("null","").trim();
            newEntity = makeValue(dimensionName);
            newEntity.dimensionId = dimensionId;
            newEntity.partyId = partyId;
            newEntity.partyName = partyName;
            newEntity.create();
        };
    };
    partyListIterator.close();
};

def loadStoreDimension(){
    // Initialize the FacilityDimension using the update strategy of 'type 1
    dimensionEntityName = "StoreDimension"
    queryListIterator = from("ProductStore").queryIterator()
    while(store = queryListIterator.next()){
        productStoreId = store.productStoreId
        dimRecord = getDimensionRecord(dimensionEntityName,"productStoreId", productStoreId)
        if(!dimRecord) {
            dimensionId = delegator.getNextSeqId(dimensionEntityName)
            newEntity = makeValue(dimensionEntityName)
            newEntity.dimensionId = dimensionId
            newEntity.productStoreId = store.productStoreId
            newEntity.storeName = store.storeName
            newEntity.create()
        };
    };
    queryListIterator.close()
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
    GenericValue productDimension
    if (ServiceUtil.isSuccess(serviceResult)) {
        productDimension  = serviceResult.productDimension
    }
    inMap.clear()
    inMap = dispatcher.getDispatchContext().makeValidContext("storeGenericDimension", ModelService.IN_PARAM, parameters)
    inMap.naturalKeyFields = "productId"
    inMap.dimensionValue = productDimension
    run service: "storeGenericDimension", with: inMap
}

def loadAllProductsInProductDimension() {
    EntityListIterator listIterator = from("Product").queryIterator()
    GenericValue product
    Map inMap
    while (product = listIterator.next()) {
        inMap = dispatcher.getDispatchContext().makeValidContext("loadProductInProductDimension", ModelService.IN_PARAM, parameters)
        inMap.productId = product.productId
        run service: "loadProductInProductDimension", with: inMap
    }
}

def getDimensionRecord(dimensionEntityName, naturalKeyName, keyValue){
    dimensionRecord = from(dimensionEntityName).where(naturalKeyName, keyValue).queryOne()
    if(dimensionRecord){
        return dimensionRecord
    }
}