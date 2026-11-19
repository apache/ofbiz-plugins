// Currently not used, only for memory purpose, waiting exampleFjs finished (and its UI test associated)
//  Example de service en mnilang ou groovy dans un objectif de formation
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
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import org.apache.ofbiz.base.util.UtilProperties
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import groovy.util.logging.Log

 def createExample() {
     resultMap = [:]
     String statusId = parameters.statusId ?: parameters.statusId
     String exampleTypeId = parameters.exampleTypeId ?: parameters.exampleTypeId
     argListName = []
     def messageList
     successMessageList = []
     errorMessageList = []
     
     if("EXST_COMPLETE".equals(statusId)) {
         statusItem = from("StatusItem").where("statusId", statusId).queryOne()
         argListName << statusItem.description
         argListName << statusItem.statusCode
         messageList = UtilProperties.getMessage("ExampleUiLabels","ExampleMessageExampleStatusIs", argListName, locale)
         resultMap.successMessageList = messageList
     }
     
     exampleTypeId = null
     if(!exampleTypeId) {
         String errorMessage = UtilProperties.getMessage("ExampleUiLabels","ExampleMessageExampleTypeCannotBeEmpty", locale)
         logError(errorMessage)
         if(!messageList) {
             resultMap.errorMessage= errorMessage
         } else {
             errorMessageList << errorMessage
             resultMap.errorMessageList = errorMessageList
         }
         //parameters.description = null 
         parameters.description = "testeeff"
         if(!parameters.description) {
             String responseMessage = "fail"
             resultMap.errorMessage = responseMessage
             exampleTypeId = "INSPIRED"
         }
         else {
             errorMessageList << errorMessage
             resultMap.errorMessageList = errorMessageList
         }

     }
     
     if("EXST_APPROVED".equals(statusId)) {
         String successMessage = UtilProperties.getMessage("ExampleUiLabels","ExampleMessageCreateExampleIsSuccess", locale)
         resultMap.successMessage = successMessage
     } else {
         if(messageList) {
             successMessageList << UtilProperties.getMessage("ExampleUiLabels","ExampleMessageCreateExampleIsSuccess", locale)
             successMessageList << messageList
             resultMap.successMessageList = successMessageList
         }
     } 
     
     result = success()
     newEntity = makeValue("Example", parameters)
     newEntity.exampleId = delegator.getNextSeqId("Example")
     newEntity.create()
     resultMap.exampleId = newEntity.exampleId
     return resultMap
 }
 
 def deleteExample() {
     resultMap = [:]
     String exampleId = parameters.exampleId ?: parameters.exampleId
     successMessageList = []
     errorMessageList = []
     
     exampleDelete = from("Example").where("exampleId", exampleId).queryOne()
     exampleStatusDelete = delegator.removeByCondition("ExampleStatus", EntityCondition.makeCondition("exampleId", EntityOperator.EQUALS, exampleId))
     exampleItems = from("ExampleItem").where("exampleId", exampleId).queryList()
     if(!exampleItems) {
         exampleDelete.remove()
         String msgSucces = UtilProperties.getMessage("ExampleUiLabels","ExampleDeleteSuccess", locale)
         successMessageList << msgSucces
         resultMap.successMessageList = successMessageList
     } else {
         String msgError = UtilProperties.getMessage("ExampleUiLabels","ExampleDeleteFailed", locale)
         errorMessageList << msgError
         resultMap.errorMessageList = errorMessageList
     }
     return resultMap
 }
 

 