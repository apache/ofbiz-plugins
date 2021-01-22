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

import java.util.ArrayList
import java.util.Collection
import java.util.HashMap
import java.util.Iterator
import java.util.LinkedList
import java.util.List
import java.util.Map
import java.util.Set
import java.util.TreeSet

import org.apache.ofbiz.base.util.*
import org.apache.ofbiz.entity.*
import org.apache.ofbiz.security.*
import org.apache.ofbiz.service.*
import org.apache.ofbiz.entity.model.*
import org.apache.ofbiz.content.content.PermissionRecorder
import org.apache.ofbiz.content.ContentManagementWorker

import javax.servlet.*
import javax.servlet.http.*

paramMap = UtilHttp.getParameterMap(request)
//logInfo("in permprep, userLogin(0):" + userLogin)

// Get permission from pagedef config file
permission = context.permission
permissionType = context.permissionType ?: "simple"

entityName = context.entityName
entityOperation = context.entityOperation
targetOperation = context.targetOperation
//logInfo("in permprep, targetOperation(0):" + targetOperation)

mode = paramMap.mode
//logInfo("in permprep, contentId(0):" + request.getAttribute("contentId"))
currentValue = request.getAttribute("currentValue")
//logInfo("in permprep, paramMap(1):" + paramMap)
//logInfo("in permprep, currentValue(1):" + currentValue)

if ("add".equals(mode)) {
    entityOperation = context.addEntityOperation ?: context.entityOperation ?: "_CREATE"
    targetOperation = context.addTargetOperation ?: context.get("targetOperation") ?: "CONTENT_CREATE"
    //logInfo("in permprep, targetOperation:" + targetOperation)
} else {
    if (!entityOperation) {
        entityOperation = "_UPDATE"
    }
    if (!targetOperation) {
        targetOperation = "CONTENT_UPDATE"
    }
}

if ("complex".equals(permissionType)) {
    mapIn = [:]
    mapIn.userLogin = userLogin
    targetOperationList = StringUtil.split(targetOperation, "|")
    mapIn.targetOperationList = targetOperationList
    thisContentId = null

    //logInfo("in permprep, userLogin(1):" + userLogin)
    //if (userLogin != null) {
        //logInfo("in permprep, userLoginId(1):" + userLogin.get("userLoginId"))
    //}
    if (!currentValue || !"Content".equals(entityName)) {
        permissionIdName = context.permissionIdName
        //logInfo("in permprep, permissionIdName(1):" + permissionIdName)
        if (!permissionIdName) {
            thisContentId = ContentManagementWorker.getFromSomewhere(permissionIdName, paramMap, request, context)
        } else if (!thisContentId) {
            thisContentId = ContentManagementWorker.getFromSomewhere("subContentId", paramMap, request, context)
        } else if (!thisContentId) {
            thisContentId = ContentManagementWorker.getFromSomewhere("contentIdTo", paramMap, request, context)
        } else if (!thisContentId) {
            thisContentId = ContentManagementWorker.getFromSomewhere("contentId", paramMap, request, context)
        }
        //logInfo("in permprep, thisContentId(2):" + thisContentId)
    } else {
        thisContentId = currentValue.contentId
    }
    //logInfo("in permprep, thisContentId(3):" + thisContentId)

    if (!currentValue || !"Content".equals(entityName)) {
        if (thisContentId) {
            currentValue = from("Content").where("contentId", thisContentId).queryOne()
        }
    }
    if ("add".equals(mode)) {
        addEntityOperation = context.addEntityOperation
        if (addEntityOperation) {
            entityOperation = addEntityOperation
        }
    } else {
        editEntityOperation = context.editEntityOperation
        if (editEntityOperation) {
            entityOperation = editEntityOperation
        }
    }
    //logInfo("in permprep, currentValue(2):" + currentValue)
    if ("Content".equals(currentValue?.getEntityName())) {
        mapIn.currentContent = currentValue
    }
    mapIn.entityOperation = entityOperation

    contentPurposeTypeId = context.contentPurposeTypeId
    if (contentPurposeTypeId) {
        mapIncontentPurposeList = StringUtil.split(contentPurposeTypeId, "|")
    }

    //logInfo("in permprep, mapIn:" + mapIn)
    result = runService('checkContentPermission', mapIn)
    permissionStatus = result.permissionStatus
    //logInfo("in permprep, permissionStatus:" + permissionStatus)
    if ("granted".equals(permissionStatus)) {
        context.hasPermission = true
        request.setAttribute("hasPermission", true)
        request.setAttribute("permissionStatus", "granted")
    } else {
        context.hasPermission = false
        request.setAttribute("hasPermission", false)
        request.setAttribute("permissionStatus", "")
        errorMessage = "Permission to display:" + page.getPageName() + " is denied."
        recorder = result.permissionRecorder
        //logInfo("recorder(0):" + recorder)
        if (recorder) {
            permissionMessage = recorder.toHtml()
            //logInfo("permissionMessage(0):" + permissionMessage)
            errorMessage += " \n " + permissionMessage
        }
        request.setAttribute("errorMsgReq", errorMessage)
    }
    //logInfo("in permprep, contentId(1):" + request.getAttribute("contentId"))
} else {
    //logInfo("permission:" + permission )
    //logInfo("entityOperation:" + entityOperation )
    if (security.hasEntityPermission(permission, entityOperation, session)) {
        //logInfo("hasEntityPermission is true:" )
        context.hasPermission = true
        request.setAttribute("hasPermission", true)
        request.setAttribute("permissionStatus", "granted")
    } else {
        //logInfo("hasEntityPermission is false:" )
        context.hasPermission = false
        request.setAttribute("hasPermission", false)
        request.setAttribute("permissionStatus", "")
    }
}
