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

import java.sql.Timestamp;
import java.util.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.IsoFields;
import java.sql.Timestamp;
import java.util.Calendar;

import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityListIterator
import org.apache.ofbiz.service.ModelService
import org.apache.ofbiz.service.ServiceUtil

GenericValue value = null;
GenericValue dwhInitialised;

def initialiseDwh() {
    // Updating BI system properties
    futureIncrementProp = from('SystemProperty').where('systemResourceId','bi', 'systemPropertyId','dwh.futureIncrement ').queryOne()
    if(futureIncrement != futureIncrementProp.systemPropertyValue) {
        futureIncrementProp.systemPropertyValue = futureIncrement
        futureIncrementProp.store()
    }
    useTimeUomIdProp = from('SystemProperty').where('systemResourceId','bi', 'systemPropertyId','dwh.useTimeUomId ').queryOne()
    if(useTimeUomId != useTimeUomIdProp) {
        useTimeUomIdProp.systemPropertyValue = useTimeUomId
        useTimeUomIdProp.store()
    }
    // get the time instance
    Calendar calendar = Calendar.getInstance()
    
    // get thruDate
    switch(useTimeUomId) {
    case "TF_mon":
        calendar.add(Calendar.MONTH, futureIncrement)
        break
    case "TF_yr":
        calendar.add(Calendar.YEAR, futureIncrement)
        break
    }
    futureDate = new java.sql.Date(calendar.getTimeInMillis())

    // update dimension and fact tables
    Map inMap = dispatcher.getDispatchContext().makeValidContext("updateDwh", ModelService.IN_PARAM, parameters)
    serviceResult = run service: "updateDwh", with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)
    
    // set update schedule
    updateJobs = from("JobSandbox").where("serviceName", "updateDwh").queryList()
    if(!updateJobs) {
        dwhUpdateSlotProp = from('SystemProperty').where('systemResourceId','bi', 'systemPropertyId','dwh.updateSlot').queryOne()
        dwhUpdateSlot = dwhUpdateSlotProp.systemPropertyValue
        calculatedHour = dwhUpdateSlot.substring(5)
        // calculate inital runtime
        calendar = Calendar.getInstance()
        calendar.add(Calendar.DATE, 1)
        calculatedDate = new java.sql.Date(calendar.getTimeInMillis())
        calculatedRunTime = calculatedDate.toString() + " " + calculatedHour + ":00:00"
        // set Job
        newEntity = makeValue('JobSandbox')
        newEntity.jobId="BI_UPDATE_DWH"
        newEntity.jobName= "Update DWH"
        newEntity.serviceName="updateDwh"
        newEntity.poolId="pool"
        newEntity.runAsUser="system"
        newEntity.maxRecurrenceCount="-1"
        newEntity.tempExprId=dwhUpdateSlot
        newEntity.runTime=calculatedRunTime
        newEntity.create();
    }
    
    // finalise dwh initialisation
    dwhInitialisedProp = from('SystemProperty').where('systemResourceId','bi', 'systemPropertyId','dwh.initialised').queryOne()
    if("false".equals(dwhInitialisedProp.systemPropertyValue)) {
        dwhInitialisedProp.systemPropertyValue = "true"
        dwhInitialisedProp.store()
    }
}

def updateDwh() {
    dwhInitialisedProp = from('SystemProperty').where('systemResourceId','bi', 'systemPropertyId','dwh.initialised').queryOne()
    dwhUpdateModeProp = from('SystemProperty').where('systemResourceId','bi', 'systemPropertyId','dwh.updateMode').queryOne()
    parameters.updateMode = dwhUpdateModeProp.systemPropertyValue
    // get nowTimestamp
    Calendar calendar = Calendar.getInstance();
    todayDate = new java.sql.Date(calendar.getTimeInMillis())
    
    // get date for the day before
    calendar.add(Calendar.DATE, -1)
    theDayBeforeDate = new java.sql.Date(calendar.getTimeInMillis())
    
    if (!parameters.fromDate) {
        parameters.fromDate = theDayBeforeDate.toString() + " 00:00:00"
    }

    // Prepare for updating dimension and fact tables

    // prepare future date for date dimension
    futureIncrementProp = from('SystemProperty').where('systemResourceId','bi', 'systemPropertyId','dwh.futureIncrement ').queryOne()
    futureIncrement = futureIncrementProp.systemPropertyValue.toInteger()
    useTimeUomIdProp = from('SystemProperty').where('systemResourceId','bi', 'systemPropertyId','dwh.useTimeUomId ').queryOne()
    useTimeUomId = useTimeUomIdProp.systemPropertyValue
    switch (useTimeUomId){
        case "TF_mon":
            calendar.add(Calendar.MONTH, futureIncrement)
            break
        case "TF_yr":
            calendar.add(Calendar.YEAR, futureIncrement);
            break
        }
    futureDate = new java.sql.Date(calendar.getTimeInMillis())
    parameters.thruDate = futureDate
    Map inMap = dispatcher.getDispatchContext().makeValidContext("updateDateDimension", ModelService.IN_PARAM, parameters)
    serviceResult = run service: "updateDateDimension", with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    if("true".equals(dwhInitialisedProp.systemPropertyValue)) {
        parameters.thruDate = theDayBeforeDate.toString() + " 23:59:59.999"
    } else {
        parameters.thruDate = todayDate.toString() + " 23:59:59.999"
    }

    //update RoleParty Dimensions
    serviceDef = "updateRolePartyDimension"
    // Carrier Dimension
    parameters.role = "Carrier"
    Debug.logInfo("In updateDwh, applying " + serviceDef + " for role " + parameters.role, "DwhServices")
    inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
    serviceResult = run service: serviceDef, with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    // Customer Dimension
    parameters.role = "Customer"
    Debug.logInfo("In updateDwh, applying " + serviceDef + " for role " + parameters.role, "DwhServices")
    inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
    serviceResult = run service: serviceDef, with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    // Employee Dimension
    parameters.role = "Employee"
    Debug.logInfo("In updateDwh, applying " + serviceDef + " for role " + parameters.role, "DwhServices")
    inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
    serviceResult = run service: serviceDef, with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    // Supplier Dimension
    parameters.role = "Supplier"
    Debug.logInfo("In updateDwh, applying " + serviceDef + " for role " + parameters.role, "DwhServices")
    inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
    serviceResult = run service: serviceDef, with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    serviceDef = "updateOfbizDimension"
    // update Asset Dimension
    parameters.dimensionEntityName = "AssetDimension"
    Debug.logInfo("In updateDwh, applying " + serviceDef + " for dimension " + parameters.dimensionEntityName, "DwhServices")
    inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
    serviceResult = run service: serviceDef, with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    // update Facility Dimension
    parameters.dimensionEntityName = "FacilityDimension"
    Debug.logInfo("In updateDwh, applying " + serviceDef + " for dimension " + parameters.dimensionEntityName, "DwhServices")
    inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
    serviceResult = run service: serviceDef, with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    // update Organisation Dimension
    parameters.dimensionEntityName = "OrganisationDimension"
    Debug.logInfo("In updateDwh, applying " + serviceDef + " for dimension " + parameters.dimensionEntityName, "DwhServices")
    inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
    serviceResult = run service: serviceDef, with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    // update Project Dimension
    parameters.dimensionEntityName = "ProjectDimension"
    Debug.logInfo("In updateDwh, applying " + serviceDef + " for dimension " + parameters.dimensionEntityName, "DwhServices")
    inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
    serviceResult = run service: serviceDef, with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    // update SalesChannel Dimension
    parameters.dimensionEntityName = "SalesChannelDimension"
    Debug.logInfo("In updateDwh, applying " + serviceDef + " for dimension " + parameters.dimensionEntityName, "DwhServices")
    inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
    serviceResult = run service: serviceDef, with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    // update SalesPromo Dimension
    parameters.dimensionEntityName = "SalesPromoDimension"
    Debug.logInfo("In updateDwh, applying " + serviceDef + " for dimension " + parameters.dimensionEntityName, "DwhServices")
    inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
    serviceResult = run service: serviceDef, with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    // update Store Dimension
    parameters.dimensionEntityName = "StoreDimension"
    Debug.logInfo("In updateDwh, applying " + serviceDef + " for dimension " + parameters.dimensionEntityName, "DwhServices")
    inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
    serviceResult = run service: serviceDef, with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

     // update Product Dimension
    parameters.dimensionEntityName = "ProductDimension"
    serviceDef = "updateProductDimension"
    Debug.logInfo("In updateDwh, applying " + serviceDef + " for dimension " + parameters.dimensionEntityName, "DwhServices")
    inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
    serviceResult = run service: serviceDef, with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    serviceDef = "updateGenericDimension"
    //update Country Dimension
    parameters.dimensionEntityName = "CountryDimension"
    Debug.logInfo("In updateDwh, applying " + serviceDef + " for dimension " + parameters.dimensionEntityName, "DwhServices")
    inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
    serviceResult = run service: serviceDef, with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    //update Currency Dimension
    parameters.dimensionEntityName = "CurrencyDimension"
    Debug.logInfo("In updateDwh, applying " + serviceDef + " for dimension " + parameters.dimensionEntityName, "DwhServices")
    inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
    serviceResult = run service: serviceDef, with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    // update Facts
    serviceDef = "updateOfbizFact"
    // update InventoryItemFact
    parameters.factEntityName = "InventoryItemFact"
    Debug.logInfo("In updateDwh, applying " + serviceDef + " for fact " + parameters.factEntityName, "DwhServices")
    inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
    serviceResult = run service: serviceDef, with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    // update PurchaseInvoiceItemFact
    parameters.factEntityName = "PurchaseInvoiceItemFact"
    Debug.logInfo("In updateDwh, applying " + serviceDef + " for fact " + parameters.factEntityName, "DwhServices")
    inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
    serviceResult = run service: serviceDef, with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    // update PurchaseOrderItemFact
    parameters.factEntityName = "PurchaseOrderItemFact"
    Debug.logInfo("In updateDwh, applying " + serviceDef + " for fact " + parameters.factEntityName, "DwhServices")
    inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
    serviceResult = run service: serviceDef, with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    // update SalesInvoiceItemFact
    parameters.factEntityName = "SalesInvoiceItemFact"
    Debug.logInfo("In updateDwh, applying " + serviceDef + " for fact " + parameters.factEntityName, "DwhServices")
    inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
    serviceResult = run service: serviceDef, with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    // update SalesOrderItemFact
    parameters.factEntityName = "SalesOrderItemFact"
    Debug.logInfo("In updateDwh, applying " + serviceDef + " for fact " + parameters.factEntityName, "DwhServices")
    inMap = dispatcher.getDispatchContext().makeValidContext(serviceDef, ModelService.IN_PARAM, parameters)
    serviceResult = run service: serviceDef, with: inMap
    if (!ServiceUtil.isSuccess(serviceResult)) return error(serviceResult.errorMessage)

    Debug.logInfo("In updateDwh - update completed","DwhServices")
}