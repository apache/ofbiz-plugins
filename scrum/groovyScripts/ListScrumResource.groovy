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

import org.apache.ofbiz.entity.GenericEntityException

performFindInMap = [:]
performFindInMap.entityName = "ScrumMemberUserLoginAndSecurityGroup"
inputFields = [:]
outputList = []

inputFields.putAll(parameters)
performFindInMap.noConditionFind = "Y"
performFindInMap.inputFields = inputFields
performFindInMap.orderBy = parameters.sortField
performFindInMap.filterByDate = "Y"
if (parameters.sortField) {
    performFindInMap.orderBy = "lastName"
}
performFindResults = runService('performFind', performFindInMap)
try {
    resultList = performFindResults.listIt.getCompleteList()
} catch (GenericEntityException e) {
    logError(e)
} finally {
    if (performFindResults.listIt != null) {
        try {
            performFindResults.listIt.close()
            } catch (GenericEntityException e) {
                logError(e)
            }
    }
}


resultList.each() { result ->
    if (!"N".equals(result.enabled)) {
        outputList.add(result)
    }
}
if (outputList) {
    context.listIt = outputList
}