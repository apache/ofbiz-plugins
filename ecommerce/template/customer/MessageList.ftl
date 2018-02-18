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

<#macro showMessage communicationEvent isSentMessage index>
  <#if communicationEvent.partyIdFrom?has_content>
    <#assign partyNameFrom =
        Static["org.apache.ofbiz.party.party.PartyHelper"].getPartyName(delegator, communicationEvent.partyIdFrom, true)>
  <#else>
    <#assign partyNameFrom = "${uiLabelMap.CommonNA}">
  </#if>
  <#if communicationEvent.partyIdTo?has_content>
    <#assign partyNameTo =
        Static["org.apache.ofbiz.party.party.PartyHelper"].getPartyName(delegator, communicationEvent.partyIdTo, true)>
  <#else>
    <#assign partyNameTo = "${uiLabelMap.CommonNA}">
  </#if>
  <tbody>
  <tr>
    <td>
      ${partyNameFrom}
    </td>
    <td>
      ${partyNameTo}
    </td>
    <td>
      ${communicationEvent.subject?default("N/A")}
    </td>
    <td>
      ${communicationEvent.entryDate}
    </td>
    <td>
      <form method="post" action="<@ofbizUrl>readmessage</@ofbizUrl>" name="ecomm_read_mess${index}">
        <input name="communicationEventId" value="${communicationEvent.communicationEventId}" type="hidden"/>
      </form>
      <a href="javascript:document.ecomm_read_mess${index}.submit()">${uiLabelMap.EcommerceRead}</a>
      <#if isSentMessage>
        <form method="post" action="<@ofbizUrl>newmessage</@ofbizUrl>" name="ecomm_sent_mess${index}">
          <input name="communicationEventId" value="${communicationEvent.communicationEventId}" type="hidden"/>
        </form>
        <a href="javascript:document.ecomm_sent_mess${index}.submit()">${uiLabelMap.PartyReply}</a>
      </#if>
    </td>
  </tr>
  </tbody>
</#macro>

<div class="card">
  <div class="card-header">
    <div class="boxlink">
      <#if "true" == parameters.showSent!>
        <a href="<@ofbizUrl>messagelist</@ofbizUrl>" class="submenutextright">
          ${uiLabelMap.EcommerceViewReceivedOnly}
        </a>
      <#else>
        <a href="<@ofbizUrl>messagelist?showSent=true</@ofbizUrl>" class="submenutextright">
          ${uiLabelMap.EcommerceViewSent}
        </a>
      </#if>
    </div>
    <strong>${uiLabelMap.CommonMessages}</strong>
  </div>
  <div class="card-body">
    <#if (!receivedCommunicationEvents?has_content && !sentCommunicationEvents?has_content)>
      <div class="alert alert-light" role="alert">
        ${uiLabelMap.EcommerceNoMessages}.
      </div>
    <#else>
    <table class="table table-responsive-sm">
      <thead class="thead-light">
        <tr>
          <th>
            ${uiLabelMap.CommonFrom}
          </th>
          <th>
            ${uiLabelMap.CommonTo}
          </th>
          <th>
            ${uiLabelMap.EcommerceSubject}
          </th>
          <th>
            ${uiLabelMap.EcommerceSentDate}
          </th>
          <th></th>
        </tr>
      </thead>
      <#list receivedCommunicationEvents! as receivedCommunicationEvent>
        <@showMessage communicationEvent=receivedCommunicationEvent
            isSentMessage=false index=receivedCommunicationEvent_index/>
      </#list>
      <#list sentCommunicationEvents! as sentCommunicationEvent>
        <@showMessage communicationEvent=sentCommunicationEvent isSentMessage=true index=sentCommunicationEvent_index/>
      </#list>
    </table>
    </#if>

  </div>
</div>
