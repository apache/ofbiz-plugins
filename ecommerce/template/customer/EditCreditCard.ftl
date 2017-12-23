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
  <h3>${uiLabelMap.AccountingCardInfoNotBelongToYou}.</h3>
  <a href="<@ofbizUrl>${donePage}</@ofbizUrl>" class="btn btn-outline-secondary">${uiLabelMap.CommonGoBack}</a>
<#else>
  <#if !creditCard??>
    <h2>${uiLabelMap.AccountingAddNewCreditCard}</h2>
    <form method="post" action="<@ofbizUrl>createCreditCard?DONE_PAGE=${donePage}</@ofbizUrl>" name="editcreditcardform">
    <div>
  <#else>
    <h2>${uiLabelMap.AccountingEditCreditCard}</h2>
    <form method="post" action="<@ofbizUrl>updateCreditCard?DONE_PAGE=${donePage}</@ofbizUrl>" name="editcreditcardform">
      <input type="hidden" name="paymentMethodId" value="${paymentMethodId}"/>
  </#if>
  <a href="<@ofbizUrl>${donePage}</@ofbizUrl>" class="btn btn-outline-secondary">${uiLabelMap.CommonGoBack}</a>
  <a href="javascript:document.editcreditcardform.submit()" class="btn btn-outline-secondary">${uiLabelMap.CommonSave}</a>
    ${screens.render("component://ecommerce/widget/CustomerScreens.xml#creditCardFields")}
      <label class="mb-2">${uiLabelMap.PartyBillingAddress}</label>
      <#-- Removed because is confusing, can add but would have to come back here with all data populated as before...
      <a href="<@ofbizUrl>editcontactmech</@ofbizUrl>" class="buttontext">
        [Create New Address]</a>&nbsp;&nbsp;
      -->
          <#assign hasCurrent = false />
          <#if curPostalAddress?has_content>
            <#assign hasCurrent = true />
            <div class="row">
              <div class="col-sm-6">
                <input type="radio" name="contactMechId" value="${curContactMechId}" checked="checked"/>
                <label>${uiLabelMap.PartyUseCurrentAddress}:</label>
                <#list curPartyContactMechPurposes as curPartyContactMechPurpose>
                  <#assign curContactMechPurposeType =
                      curPartyContactMechPurpose.getRelatedOne("ContactMechPurposeType", true) />
                    ${curContactMechPurposeType.get("description",locale)!}
                    <#if curPartyContactMechPurpose.thruDate??>
                      ((${uiLabelMap.CommonExpire}:${curPartyContactMechPurpose.thruDate.toString()})
                    </#if>
                </#list>
                  <#if curPostalAddress.toName??>${uiLabelMap.CommonTo}: ${curPostalAddress.toName}<br/></#if>
                  <#if curPostalAddress.attnName??>${uiLabelMap.PartyAddrAttnName}: ${curPostalAddress.attnName}
                  </#if>
                  ${curPostalAddress.address1!}
                  <#if curPostalAddress.address2??>${curPostalAddress.address2}<br/></#if>
                  ${curPostalAddress.city}
                  <#if curPostalAddress.stateProvinceGeoId?has_content>,&nbsp;
                    ${curPostalAddress.stateProvinceGeoId}
                  </#if>&nbsp;${curPostalAddress.postalCode}
                  <#if curPostalAddress.countryGeoId??>${curPostalAddress.countryGeoId}</#if>
                  <div>(${uiLabelMap.CommonUpdated}:&nbsp;${(curPartyContactMech.fromDate.toString())!})</div>
                  <#if curPartyContactMech.thruDate??>
                    ${uiLabelMap.CommonDelete}:&nbsp;${curPartyContactMech.thruDate.toString()}
                  </#if>
              </div>
            </div>
          <#else>
            <#-- <tr>
              <td valign="top" colspan="2">
                <div>${uiLabelMap.PartyBillingAddressNotSelected}</div>
              </td>
            </tr> -->
          </#if>
          <#-- is confusing
            <tr>
              <td valign="top" colspan="2">
                <div>${uiLabelMap.EcommerceMessage3}</div>
              </td>
            </tr>
          -->
          <#list postalAddressInfos as postalAddressInfo>
            <#assign contactMech = postalAddressInfo.contactMech />
            <#assign partyContactMechPurposes = postalAddressInfo.partyContactMechPurposes />
            <#assign postalAddress = postalAddressInfo.postalAddress />
            <#assign partyContactMech = postalAddressInfo.partyContactMech />
                <div class="row">
                <div class="col-sm-6">
                <input type="radio" name="contactMechId" value="${contactMech.contactMechId}"/>
                <#list partyContactMechPurposes as partyContactMechPurpose>
                  <#assign contactMechPurposeType =
                      partyContactMechPurpose.getRelatedOne("ContactMechPurposeType", true) />
                    ${contactMechPurposeType.get("description",locale)!}
                    <#if partyContactMechPurpose.thruDate??>
                      (${uiLabelMap.CommonExpire}:${partyContactMechPurpose.thruDate})
                    </#if>
                </#list>
                  <#if postalAddress.toName??><label>${uiLabelMap.CommonTo}: ${postalAddress.toName}</label></#if>
                  <#if postalAddress.attnName??>${uiLabelMap.PartyAddrAttnName}: ${postalAddress.attnName}</#if>
                  ${postalAddress.address1!}
                  <#if postalAddress.address2??>${postalAddress.address2}</#if>
                  ${postalAddress.city}
                  <#if postalAddress.stateProvinceGeoId?has_content>
                    ,&nbsp;${postalAddress.stateProvinceGeoId}
                  </#if>
                  ${postalAddress.postalCode}
                  <#if postalAddress.countryGeoId??><br/>${postalAddress.countryGeoId}</#if>
                (${uiLabelMap.CommonUpdated}:&nbsp;${(partyContactMech.fromDate.toString())!})
                <#if partyContactMech.thruDate??>
                  ${uiLabelMap.CommonDelete}:&nbsp;${partyContactMech.thruDate.toString()}
                </#if>
                </div>
                </div>
          </#list>
          <#if !postalAddressInfos?has_content && !curContactMech??>
                <label>${uiLabelMap.PartyNoContactInformation}.</label>
          </#if>
              <div class="input-group">
              <input type="radio" class="mr-2" name="contactMechId" value="_NEW_" <#if !hasCurrent>checked="checked"</#if>/>
              <label>${uiLabelMap.PartyCreateNewBillingAddress}.</label>
              </div>
</div>
</form>
  <a href="<@ofbizUrl>${donePage}</@ofbizUrl>" class="btn btn-outline-secondary">${uiLabelMap.CommonGoBack}</a>
  <a href="javascript:document.editcreditcardform.submit()" class="btn btn-outline-secondary">${uiLabelMap.CommonSave}</a>
</#if>

