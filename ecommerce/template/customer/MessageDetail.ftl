<#--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

<#assign delegator = requestAttributes.delegator>
<#if communicationEvent.partyIdFrom??>
  <#assign fromName =
      Static["org.apache.ofbiz.party.party.PartyHelper"].getPartyName(delegator, communicationEvent.partyIdFrom, true)>
</#if>
<#if communicationEvent.partyIdTo??>
  <#assign toName =
      Static["org.apache.ofbiz.party.party.PartyHelper"].getPartyName(delegator, communicationEvent.partyIdTo, true)>
</#if>

<div class="card m-3">
  <div class="card-header">
    <div class="boxlink">
    <#if (communicationEvent.partyIdFrom! != (userLogin.partyId)!)>
      <a href="<@ofbizUrl>newmessage?communicationEventId=${communicationEvent.communicationEventId}</@ofbizUrl>"
          class="submenutext">${uiLabelMap.PartyReply}
      </a>
    </#if>
      <a href="<@ofbizUrl>messagelist</@ofbizUrl>" class="submenutextright">${uiLabelMap.EcommerceViewList}</a>
    </div>
    <strong>${uiLabelMap.EcommerceReadMessage}</strong>
  </div>
  <div class="card-body">
    <div class="row">
      <div class="col-1">
        <strong>${uiLabelMap.CommonFrom} :</strong>
      </div>
      <div class="col-11">
        ${fromName?default("N/A")}
      </div>
    </div>
    <hr/>
    <div class="row">
      <div class="col-1">
        <strong>${uiLabelMap.CommonTo} :</strong>
      </div>
      <div class="col-11">
        ${toName?default("N/A")}
      </div>
    </div>
    <hr/>
    <div class="row">
      <div class="col-1">
        <strong>${uiLabelMap.CommonDate} :</strong>
      </div>
      <div class="col-11">
        ${communicationEvent.entryDate?default("N/A")}
      </div>
    </div>
    <hr/>
    <div class="row">
      <div class="col-1">
        <strong>${uiLabelMap.EcommerceSubject} :</strong>
      </div>
      <div class="col-11">
        ${(communicationEvent.subject)?default("[${uiLabelMap.EcommerceNoSubject}]")}
      </div>
    </div>
    <hr/>
    <div class="row">
      <div class="col-1">
      </div>
      <div class="col-11">
        ${StringUtil.wrapString(communicationEvent.content)?default("[${uiLabelMap.EcommerceEmptyBody}]")}
      </div>
    </div>
  </div>
</div>
