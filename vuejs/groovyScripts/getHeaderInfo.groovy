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
import org.apache.ofbiz.base.component.ComponentConfig
import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.base.util.StringUtil;

ofbizServerName = request.getAttribute("_serverId") ? request.getAttribute("_serverId") : "default-server"
Debug.logInfo("ofbizServerName="+ofbizServerName, "getHeaderInfo")
displayApps = org.apache.ofbiz.webapp.control.LoginWorker.getAppBarWebInfos(security, userLogin, ofbizServerName, "main")
displaySecondaryApps = org.apache.ofbiz.webapp.control.LoginWorker.getAppBarWebInfos(security, userLogin, ofbizServerName, "secondary")

apps = []
primaryApps = []
secondaryApps = []
for (ComponentConfig.WebappInfo displayApp : displayApps) {
    appli = new HashMap()
    appli.name = displayApp.name
    servletPath = org.apache.ofbiz.webapp.WebAppUtil.getControlServletPath(displayApp)
    appli.thisURL = StringUtil.wrapString(servletPath).toString()
//    appli.description = uiLabelMap.get(displayApp.description)
    appli.description = displayApp.description
    primaryApps.add(appli)
    apps.add(appli)
}
for (ComponentConfig.WebappInfo displaySecondaryApp : displaySecondaryApps) {
    appli = new HashMap()
    appli.name = displaySecondaryApp.name
    servletPath = org.apache.ofbiz.webapp.WebAppUtil.getControlServletPath(displaySecondaryApp)
    appli.thisURL = StringUtil.wrapString(servletPath).toString()
//    appli.description = uiLabelMap.get(displaySecondaryApp.description)
    appli.description = displaySecondaryApp.description
    secondaryApps.add(appli)
    apps.add(appli)
}
request.setAttribute("displayApps", apps)
request.setAttribute("locale",locale.getProperties())
return "success"


