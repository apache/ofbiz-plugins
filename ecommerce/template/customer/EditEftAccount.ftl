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

<#if canNotView>
  <h3>${uiLabelMap.AccountingEFTNotBelongToYou}.</h3>
  <a href="<@ofbizUrl>${donePage}</@ofbizUrl>" class="btn btn-outline-secondary">${uiLabelMap.CommonGoBack}</a>
<#else>
  <#if !eftAccount??>
    <h1>${uiLabelMap.AccountingAddNewEftAccount}</h1>
    <form method="post" action="<@ofbizUrl>createEftAccount?DONE_PAGE=${donePage}</@ofbizUrl>" name="editeftaccountform">
  <#else>
    <h1>${uiLabelMap.PageTitleEditEFTAccount}</h1>
    <form method="post" action="<@ofbizUrl>updateEftAccount?DONE_PAGE=${donePage}</@ofbizUrl>" name="editeftaccountform">
    <input type="hidden" name="paymentMethodId" value="${paymentMethodId}"/>
  </#if>
  <div class="form-group">
  <a href="<@ofbizUrl>${donePage}</@ofbizUrl>" class="btn btn-outline-secondary">${uiLabelMap.CommonGoBack}</a>
  <a href="javascript:document.editeftaccountform.submit()" class="btn btn-outline-secondary">${uiLabelMap.CommonSave}</a>
  </div>
    <label class="required">${uiLabelMap.AccountingNameOnAccount}</label>
    <div class="row">
      <div class="col-sm-6">
        <input type="text" class="form-control"name="nameOnAccount" value="${eftAccountData.nameOnAccount!}"/>
      </div>
    </div>
    <label class="required">${uiLabelMap.AccountingCompanyNameOnAccount}</label>
    <div class="row">
      <div class="col-sm-6">
        <input type="text" class="form-control" name="companyNameOnAccount" value="${eftAccountData.companyNameOnAccount!}"/>
      </div>
    </div>
    <label class="required">${uiLabelMap.AccountingBankName}</label>
    <div class="row">
      <div class="col-sm-6">
        <input type="text" class="form-control" name="bankName"value="${eftAccountData.bankName!}"/>
      </div>
    </div>
    <label class="required">${uiLabelMap.AccountingRoutingNumber}</label>
    <div class="row">
      <div class="col-sm-6">
        <input type="text" class="form-control" name="routingNumber" value="${eftAccountData.routingNumber!}"/>
      </div>
    </div>
    <label class="required">${uiLabelMap.AccountingAccountType}</label>
    <div class="row">
      <div class="col-sm-6">
         <select name="accountType" class="custom-select form-control">
            <option>${uiLabelMap.CommonSelect}</option>
            <option>${eftAccountData.accountType!}</option>
            <option>${uiLabelMap.CommonChecking}</option>
            <option>${uiLabelMap.CommonSavings}</option>
         </select>
      </div>
    </div>
    <label class="required">${uiLabelMap.AccountingAccountNumber}</label>
    <div class="row">
      <div class="col-sm-6">
        <input type="text" class="form-control" name="accountNumber" value="${eftAccountData.accountNumber!}"/>
      </div>
    </div>
    <label class="required">${uiLabelMap.CommonDescription}</label>
    <div class="row">
      <div class="col-sm-6">
        <input type="text" class="form-control" name="description" value="${paymentMethodData.description!}"/>
      </div>
    </div>
    <label class="mb-2"><strong>${uiLabelMap.PartyBillingAddress}</strong></label>
        <#-- Removed because is confusing, can add but would have to come back here with all data populated as before...
        <a href="<@ofbizUrl>editcontactmech</@ofbizUrl>" class="buttontext">
          [Create New Address]</a>&nbsp;&nbsp;
        -->
          <#if curPostalAddress??>
          <div class="row">
            <div class="col-sm-12">
              <input type="radio" name="contactMechId" value="${curContactMechId}" checked="checked" class="form-control"/>
                <label><strong>${uiLabelMap.PartyUseCurrentAddress}:</strong></label>
                <#list curPartyContactMechPurposes as curPartyContactMechPurpose>
                  <#assign curContactMechPurposeType =
                      curPartyContactMechPurpose.getRelatedOne("ContactMechPurposeType", true)>
                    <strong>${curContactMechPurposeType.get("description",locale)!}</strong>
                    <#if curPartyContactMechPurpose.thruDate??>
                      (${uiLabelMap.CommonExpire}:${curPartyContactMechPurpose.thruDate.toString()})
                    </#if>
                </#list>
                  <#if curPostalAddress.toName??>
                    <strong>${uiLabelMap.CommonTo}:</strong> ${curPostalAddress.toName}
                  </#if>
                  <#if curPostalAddress.attnName??>
                    <strong>${uiLabelMap.PartyAddrAttnName}:</strong> ${curPostalAddress.attnName}
                  </#if>
                  ${curPostalAddress.address1!}<br/>
                  <#if curPostalAddress.address2??>${curPostalAddress.address2}<br/></#if>
                  ${curPostalAddress.city}
                  <#if curPostalAddress.stateProvinceGeoId?has_content>,&nbsp;
                    ${curPostalAddress.stateProvinceGeoId}
                  </#if>
                  ${curPostalAddress.postalCode}
                  <#if curPostalAddress.countryGeoId??><br/>${curPostalAddress.countryGeoId}</#if>
                  (${uiLabelMap.CommonUpdated}:${(curPartyContactMech.fromDate.toString())!})
                <#if curPartyContactMech.thruDate??>
                  ${uiLabelMap.CommonDelete}:${curPartyContactMech.thruDate.toString()}
                </#if>
            </div>
          </div>
          <#else>
            <#--
              <tr>
                <td valign="top" colspan="2">
                  <div>${uiLabelMap.PartyNoBillingAddress}</div>
                </td>
              </tr>
            -->
          </#if>
          <#-- is confusing
          <tr>
            <td valign="top" colspan="2">
              <div><b>${uiLabelMap.EcommerceMessage3}</b></div>
            </td>
          </tr>
          -->
          <#list postalAddressInfos as postalAddressInfo>
            <#assign contactMech = postalAddressInfo.contactMech>
            <#assign partyContactMechPurposes = postalAddressInfo.partyContactMechPurposes>
            <#assign postalAddress = postalAddressInfo.postalAddress>
            <#assign partyContactMech = postalAddressInfo.partyContactMech>
              <div class="row">
                <div class="col-sm-12">
                <input type="radio" name="contactMechId" value="${contactMech.contactMechId}"/>
                <#list partyContactMechPurposes as partyContactMechPurpose>
                  <#assign contactMechPurposeType =
                      partyContactMechPurpose.getRelatedOne("ContactMechPurposeType", true)>
                    ${contactMechPurposeType.get("description",locale)!}
                    <#if partyContactMechPurpose.thruDate??>
                      (${uiLabelMap.CommonExpire}:${partyContactMechPurpose.thruDate})
                    </#if>
                </#list>
                  <#if postalAddress.toName??><label><b>${uiLabelMap.CommonTo}:</b> ${postalAddress.toName}</label></#if>
                  <#if postalAddress.attnName??>
                    <strong>${uiLabelMap.PartyAddrAttnName}:</strong> ${postalAddress.attnName}<br/>
                  </#if>
                  ${postalAddress.address1!}<br/>
                  <#if postalAddress.address2??>${postalAddress.address2}<br/></#if>
                  ${postalAddress.city}
                  <#if postalAddress.stateProvinceGeoId?has_content>,&nbsp;
                    ${postalAddress.stateProvinceGeoId}
                  </#if>
                  ${postalAddress.postalCode}
                  <#if postalAddress.countryGeoId??><br/>${postalAddress.countryGeoId}</#if>
                <div>(${uiLabelMap.CommonUpdated}:&nbsp;${(partyContactMech.fromDate.toString())!})</div>
                <#if partyContactMech.thruDate??>
                  <div><b>${uiLabelMap.CommonDelete}:&nbsp;${partyContactMech.thruDate.toString()}</b></div>
                </#if>
              </div>
            </div>
          </#list>

          <#if !postalAddressInfos?has_content && !curContactMech??>
            <label>${uiLabelMap.PartyNoContactInformation}.</label>
          </#if>
</form>
  <a href="<@ofbizUrl>${donePage}</@ofbizUrl>" class="btn btn-outline-secondary"><span class="glyphicon glyphicon-circle-arrow-left">${uiLabelMap.CommonGoBack}</span></a>
  <a href="javascript:document.editeftaccountform.submit()" class="btn btn-outline-secondary">${uiLabelMap.CommonSave}</a>
</#if>

