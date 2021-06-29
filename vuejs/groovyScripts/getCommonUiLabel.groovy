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
import org.apache.ofbiz.base.util.Debug

uiLabelMap = UtilProperties.getResourceBundleMap("CommonUiLabels", locale)
uiLabelMap.addBottomResourceBundle("VuejsUiLabels")

vuejsUiLabel = [:]
vuejsUiLabel.add             = uiLabelMap.CommonAdd
vuejsUiLabel.cancel          = uiLabelMap.CommonCancel
vuejsUiLabel.cancelAll       = uiLabelMap.CommonCancelAll
vuejsUiLabel.collapseToolTip = uiLabelMap["CommonCollapse"] // screenletCollapse
vuejsUiLabel.confirmButton   = uiLabelMap.FormFieldTitle_confirmButton
vuejsUiLabel.confirmDelete   = uiLabelMap.CommonConfirmDelete
vuejsUiLabel.date            = uiLabelMap.CommonDate

vuejsUiLabel.expandToolTip   = uiLabelMap.CommonExpand      // screenletExpand
vuejsUiLabel.expire          = uiLabelMap.CommonExpire
vuejsUiLabel.forgotYourPassword = uiLabelMap.CommonForgotYourPassword
vuejsUiLabel.login           = uiLabelMap.CommonLogin
vuejsUiLabel.loginSuccessMessage = uiLabelMap.VuejsLoginSuccessMessage
//FormFieldTitle_expireButton
vuejsUiLabel.now             = uiLabelMap.CommonNow
vuejsUiLabel.ofLabel         = uiLabelMap.CommonOf          // pagination CommonRequired
vuejsUiLabel.password        = uiLabelMap.CommonPassword
vuejsUiLabel.rememberMe      = uiLabelMap.VuejsRememberMe
vuejsUiLabel.registred       = uiLabelMap.CommonRegistered
vuejsUiLabel.required        = uiLabelMap.CommonRequired
vuejsUiLabel.showAll         = uiLabelMap.CommonShowAll
vuejsUiLabel.summary         = uiLabelMap.CommonSummary
vuejsUiLabel.time            = uiLabelMap.CommonTime
vuejsUiLabel.userName        = uiLabelMap.CommonUsername
request.setAttribute("commonUiLabels", vuejsUiLabel)
return "success"

//<set field="uiLabels.CommonCreate" value="${uiLabelMap.CommonCreate}"/>

