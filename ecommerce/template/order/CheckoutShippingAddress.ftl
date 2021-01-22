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

<script type="application/javascript">
function submitForm(form, mode, value) {
    if ("DN" == mode) {
        // done action; checkout
        form.action="<@ofbizUrl>checkoutoptions</@ofbizUrl>";
        form.submit();
    } else if ("CS" == mode) {
        // continue shopping
        form.action="<@ofbizUrl>updateCheckoutOptions/showcart</@ofbizUrl>";
        form.submit();
    } else if ("NA" == mode) {
        // new address
        form.action="<@ofbizUrl>updateCheckoutOptions/editcontactmech?preContactMechTypeId=POSTAL_ADDRESS&contactMechPurposeTypeId=SHIPPING_LOCATION&DONE_PAGE=checkoutoptions</@ofbizUrl>";
        form.submit();
    } else if ("EA" == mode) {
        // edit address
        form.action="<@ofbizUrl>updateCheckoutOptions/editcontactmech?DONE_PAGE=checkoutoptions&contactMechId="+value+"</@ofbizUrl>";
        form.submit();
    }
}

function toggleBillingAccount(box) {
    var amountName = box.value + "_amount";
    box.checked = true;
    box.form.elements[amountName].disabled = false;

    for (var i = 0; i < box.form.elements[box.name].length; i++) {
        if (!box.form.elements[box.name][i].checked) {
            box.form.elements[box.form.elements[box.name][i].value + "_amount"].disabled = true;
        }
    }
}

</script>
<#assign cart = shoppingCart!/>
<form method="post" name="checkoutInfoForm" style="margin:0;">
    <input type="hidden" name="checkoutpage" value="shippingaddress"/>
    <div class="card">
        <h4 class="card-header">
          1)&nbsp;${uiLabelMap.OrderWhereShallWeShipIt}?
        </h4>
        <div class="card-body">
            <table class="table table-responsive-sm">
              <tr>
                <td colspan="2">
                  <a href="<@ofbizUrl>splitship</@ofbizUrl>" class="buttontext">${uiLabelMap.OrderSplitShipment}</a>
                  <a href="javascript:submitForm(document.checkoutInfoForm, 'NA', '');" class="buttontext">${uiLabelMap.PartyAddNewAddress}</a>
                  <#if (cart.getShipGroupSize() > 1)>
                    <div style="color: red;">${uiLabelMap.OrderNOTEMultipleShipmentsExist}</div>
                  </#if>
                </td>
              </tr>
               <#if shippingContactMechList?has_content>
                 <#list shippingContactMechList as shippingContactMech>
                   <#assign shippingAddress = shippingContactMech.getRelatedOne("PostalAddress", false)>
                   <#assign checkThisAddress = (shippingContactMech_index == 0 && !cart.getShippingContactMechId()?has_content) || (cart.getShippingContactMechId()?default("") == shippingAddress.contactMechId)/>
                   <tr>
                     <td>
                     <div class="form-check">
                       <input type="radio" class="form-check-input" name="shipping_contact_mech_id" value="${shippingAddress.contactMechId}"<#if checkThisAddress> checked="checked"</#if> />
                       <label>
                         <#if shippingAddress.toName?has_content><b>${uiLabelMap.CommonTo}:</b>&nbsp;${shippingAddress.toName}<br /></#if>
                         <#if shippingAddress.attnName?has_content><b>${uiLabelMap.PartyAddrAttnName}:</b>&nbsp;${shippingAddress.attnName}<br /></#if>
                         <#if shippingAddress.address1?has_content>${shippingAddress.address1},</#if>
                         <#if shippingAddress.address2?has_content>${shippingAddress.address2}<br /></#if>
                         <#if shippingAddress.city?has_content>${shippingAddress.city}</#if>
                         <#if shippingAddress.stateProvinceGeoId?has_content>${shippingAddress.stateProvinceGeoId}</#if>
                         <#if shippingAddress.postalCode?has_content><br />${shippingAddress.postalCode}</#if>
                         <#if shippingAddress.countryGeoId?has_content><br />${shippingAddress.countryGeoId}</#if>
                         <a href="javascript:submitForm(document.checkoutInfoForm, 'EA', '${shippingAddress.contactMechId}');" class="buttontext">${uiLabelMap.CommonUpdate}</a>
                       </label>
                     </div>
                     </td>
                   </tr>
                 </#list>
               </#if>
              </table>
             <h5>${uiLabelMap.AccountingAgreementInformation}</h5>
               <table class="table table-responsive-sm">
                 <#if agreements??>
                   <#if agreements.size()!=1>
                     <tr>
                       <td>
                         ${uiLabelMap.OrderSelectAgreement}
                       </td>
                       <td>
                         <select name="agreementId" class="form-control">
                           <#list agreements as agreement>
                             <option value='${agreement.agreementId!}'>${agreement.agreementId} - ${agreement.description!}</option>
                           </#list>
                         </select>
                       </td>
                     </tr>
                   <#else>
                     <#list agreements as agreement>
                       <div class="form-check"><input type="radio" class="form-check-input" name="agreementId" value="${agreement.agreementId!}"<#if checkThisAddress> checked="checked"</#if> /><label>${agreement.description!} will be used for this order.</label></div>
                     </#list>
                   </#if>
                 </#if>
               </table>
             <br />
            <#-- Party Tax Info -->
            <strong>&nbsp;${uiLabelMap.PartyTaxIdentification}</strong>
            ${screens.render("component://ecommerce/widget/OrderScreens.xml#customertaxinfo")}
        </div>
    </div>
</form>
<div class="row">
  <div class="col-auto mr-auto">
    <a href="javascript:submitForm(document.checkoutInfoForm, 'CS', '');" class="btn btn-secondary">${uiLabelMap.OrderBacktoShoppingCart}</a>
  </div>
  <div class="col-auto">
    <a href="javascript:submitForm(document.checkoutInfoForm, 'DN', '');" class="btn btn-primary">${uiLabelMap.CommonNext}</a>
  </div>
</div>