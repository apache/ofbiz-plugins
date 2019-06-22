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

<div class="card m-3">
  <div class="card-header">
    <div class="boxlink">
      <#if "TRUE" == showMessageLinks?default("false")?upper_case>
        <a href="<@ofbizUrl>messagelist</@ofbizUrl>" class="submenutextright">${uiLabelMap.EcommerceViewList}</a>
      </#if>
    </div>
    <strong>${pageHeader}</strong>
  </div>
  <div class="card-body">
    <form name="contactus" method="post" action="<@ofbizUrl>${submitRequest}</@ofbizUrl>" style="margin: 0;">
      <input type="hidden" name="partyIdFrom" value="${userLogin.partyId}"/>
      <input type="hidden" name="contactMechTypeId" value="WEB_ADDRESS"/>
      <input type="hidden" name="communicationEventTypeId" value="WEB_SITE_COMMUNICATI"/>
      <#if productStore?has_content>
        <input type="hidden" name="partyIdTo" value="${productStore.payToPartyId!}"/>
      </#if>
        <input type="hidden" name="note"
               value="${Static["org.apache.ofbiz.base.util.UtilHttp"].getFullRequestUrl(request)}"/>
      <#if message?has_content>
        <input type="hidden" name="parentCommEventId" value="${communicationEvent.communicationEventId}"/>
        <#if (communicationEvent.origCommEventId?? && communicationEvent.origCommEventId?length > 0)>
          <#assign orgComm = communicationEvent.origCommEventId>
        <#else>
          <#assign orgComm = communicationEvent.communicationEventId>
        </#if>
        <input type="hidden" name="origCommEventId" value="${orgComm}"/>
      </#if>
      <div class="row">
        <div class="col-2">
          <strong>${uiLabelMap.CommonFrom} :</strong>
        </div>
        <div class="col-10">
          ${sessionAttributes.autoName!} [${userLogin.partyId}] (${uiLabelMap.CommonNotYou}?&nbsp;<a
                          href="<@ofbizUrl>autoLogout</@ofbizUrl>" class="buttontext">${uiLabelMap.CommonClickHere}</a>)
        </div>
      </div>
      <hr/>
      <#if partyIdTo?has_content>
        <#assign partyToName =
            Static["org.apache.ofbiz.party.party.PartyHelper"].getPartyName(delegator, partyIdTo, true)>
        <input type="hidden" name="partyIdTo" value="${partyIdTo}"/>
        <div class="row">
          <div class="col-2">
            <strong>${uiLabelMap.CommonTo} :</strong>
          </div>
          <div class="col-10">
            ${partyToName?default("N/A")}
          </div>
        </div>
        <hr/>
      </#if>
      <#assign defaultSubject = (communicationEvent.subject)?default("")>
      <#if (defaultSubject?length == 0)>
        <#assign replyPrefix = "RE: ">
        <#if parentEvent?has_content>
          <#if !parentEvent.subject?default("")?upper_case?starts_with(replyPrefix)>
            <#assign defaultSubject = replyPrefix>
          </#if>
          <#assign defaultSubject = defaultSubject + parentEvent.subject?default("")>
        </#if>
      </#if>
      <div class="row">
        <div class="col-2">
          <strong>${uiLabelMap.EcommerceSubject} :</strong>
        </div>
        <div class="col-10">
          <input type="input" class="inputBox form-control form-control-sm" name="subject" size="20" value="${defaultSubject}"/>
        </div>
      </div>
      <hr/>
      <div class="row">
        <div class="col-2">
          <strong>${uiLabelMap.CommonMessage} :</strong>
        </div>
        <div class="col-10">
          <textarea name="content" class="textAreaBox form-control form-control-sm" rows="5"></textarea>
        </div>
      </div>
      <div class="row">
        <div class="col-12">
          <input type="submit" class="smallSubmit btn btn-outline-secondary" value="${uiLabelMap.CommonSend}"/>
        </div>
      </div>
    </form>
  </div>
</div>
