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

import java.text.DateFormat

import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.party.contact.ContactMechWorker

DateFormat df = UtilDateTime.toDateTimeFormat(UtilDateTime.getDateTimeFormat(), timeZone, null);

partyId = request.getAttribute("partyId")
if (! partyId) partyId = request.getParameter("partyId")

showOldStr = request.getAttribute("showOld")
if (! showOldStr) showOldStr = request.getParameter("showOld")
showOld = "true".equals(showOldStr)

contactMeches = ContactMechWorker.getPartyContactMechValueMaps(delegator, partyId, showOld)

for (Map<String, Object> contactMeche: contactMeches) {
    gvCM = (GenericValue)contactMeche.get("partyContactMech")
    Map<String, Object> partyContactMech = gvCM.getAllFields()

    partyContactMech.put("fromDate", df.format((java.util.Date) gvCM.getTimestamp("fromDate")))
    if (gvCM.getTimestamp("thruDate"))
        partyContactMech.put("thruDate", df.format((java.util.Date) gvCM.getTimestamp("thruDate")))
    contactMeche.put("partyContactMech", partyContactMech)

    gvCMPList = (List)contactMeche.get("partyContactMechPurposes")
    if (gvCMPList) {
        for (int i = 0; i < gvCMPList.size(); i++) {
            gvCMP = (GenericValue)gvCMPList.get(i)
                    Map<String, Object> partyContactMechPurpose = gvCMP.getAllFields()
                    partyContactMechPurpose.put("fromDate", df.format((java.util.Date) gvCMP.getTimestamp("fromDate")))
                    if (gvCMP.getTimestamp("thruDate"))
                        partyContactMechPurpose.put("thruDate", df.format((java.util.Date) gvCMP.getTimestamp("thruDate")))
                        gvCMPList.set(i,partyContactMechPurpose)
        }
    }
}
request.setAttribute("valueMaps", contactMeches)
