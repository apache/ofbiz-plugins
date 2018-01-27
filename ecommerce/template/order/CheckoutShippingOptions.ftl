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

<script language="javascript" type="text/javascript">
  //<![CDATA[
  function submitForm(form, mode, value) {
    if ("DN" == mode) {
      // done action; checkout
      form.action = "<@ofbizUrl>checkoutoptions</@ofbizUrl>";
      form.submit();
    } else if ("CS" == mode) {
      // continue shopping
      form.action = "<@ofbizUrl>updateCheckoutOptions/showcart</@ofbizUrl>";
      form.submit();
    } else if ("NA" == mode) {
      // new address
      form.action = "<@ofbizUrl>updateCheckoutOptions/editcontactmech?preContactMechTypeId=POSTAL_ADDRESS&contactMechPurposeTypeId=SHIPPING_LOCATION&DONE_PAGE=checkoutoptions</@ofbizUrl>";
      form.submit();
    } else if ("EA" == mode) {
      // edit address
      form.action = "<@ofbizUrl>updateCheckoutOptions/editcontactmech?DONE_PAGE=checkoutshippingaddress&contactMechId="+ value+"</@ofbizUrl>";
      form.submit();
    } else if ("NC" == mode) {
      // new credit card
      form.action = "<@ofbizUrl>updateCheckoutOptions/editcreditcard?DONE_PAGE=checkoutoptions</@ofbizUrl>";
      form.submit();
    } else if ("EC" == mode) {
      // edit credit card
      form.action = "<@ofbizUrl>updateCheckoutOptions/editcreditcard?DONE_PAGE=checkoutoptions&paymentMethodId="+ value+"</@ofbizUrl>";
      form.submit();
    } else if ("NE" == mode) {
      // new eft account
      form.action = "<@ofbizUrl>updateCheckoutOptions/editeftaccount?DONE_PAGE=checkoutoptions</@ofbizUrl>";
      form.submit();
    } else if ("EE" == mode) {
      // edit eft account
      form.action = "<@ofbizUrl>updateCheckoutOptions/editeftaccount?DONE_PAGE=checkoutoptions&paymentMethodId="+ value+"</@ofbizUrl>";
      form.submit();
    }
  }

  //]]>
</script>

<form method="post" name="checkoutInfoForm" style="margin:0;">
  <fieldset>
    <input type="hidden" name="checkoutpage" value="shippingoptions"/>
    <div class="card">
      <h4 class="card-header">
        2)&nbsp;${uiLabelMap.OrderHowShallWeShipIt}?
      </h4>
      <div class="card-body">
        <#list carrierShipmentMethodList as carrierShipmentMethod>
          <#assign shippingMethod = carrierShipmentMethod.shipmentMethodTypeId + "@" + carrierShipmentMethod.partyId>
          <div class="form-check">
            <input class="form-check-input" type="radio" id="shipping_method_${carrierShipmentMethod?index}" name="shipping_method" value="${shippingMethod}"
            <#if shippingMethod == StringUtil.wrapString(chosenShippingMethod!"N@A")>checked="checked"</#if>/>
            <#if shoppingCart.getShippingContactMechId()??>
              <#assign shippingEst = shippingEstWpr.getShippingEstimate(carrierShipmentMethod)?default(-1)>
            </#if>
            <label class="form-check-label" for="shipping_method_${carrierShipmentMethod?index}">
            <#if carrierShipmentMethod.partyId != "_NA_">${carrierShipmentMethod.partyId!}
              &nbsp;</#if>${carrierShipmentMethod.description!}
            <#if shippingEst?has_content> -
              <#if (shippingEst > -1)>
                <@ofbizCurrency amount=shippingEst isoCode=shoppingCart.getCurrency()/>
              <#else>
                ${uiLabelMap.OrderCalculatedOffline}
              </#if>
            </#if>
            </label>
          </div>
        </#list>
        <#if !carrierShipmentMethodList?? || carrierShipmentMethodList?size == 0>
          <div class="form-check">
          <input type="radio" name="shipping_method" class="form-check-input" value="Default" checked="checked"/>
          <label class="form-check-label" for="shipping_method">${uiLabelMap.OrderUseDefault}.</label>
          </div>
        </#if>
        <hr>
        <h4>${uiLabelMap.OrderShipAllAtOnce}?</h4>
        <div class="form-check">
          <input type="radio" class="form-check-input" <#if "Y" != shoppingCart.getMaySplit()?default("N")>checked="checked"</#if> id="may_split_no" name="may_split" value="false"/>
          <label class="form-check-label" for="may_split_no">${uiLabelMap.OrderPleaseWaitUntilBeforeShipping}.</label>
        </div>
        <div class="form-check">
        <input <#if "Y" == shoppingCart.getMaySplit()?default("N")>checked="checked"</#if> type="radio" class="form-check-input" id="may_split_yes" name="may_split" value="true"/>
        <label for="may_split_yes" class="form-check-label">${uiLabelMap.OrderPleaseShipItemsBecomeAvailable}.</label>
        </div>
        <hr>
        <h4>${uiLabelMap.OrderSpecialInstructions}</h4>
        <textarea class="form-control" name="shipping_instructions">${shoppingCart.getShippingInstructions()!}</textarea>
        <hr>
        <h4>${uiLabelMap.OrderPoNumber}</h4>
          <#if shoppingCart.getPoNumber()?? && shoppingCart.getPoNumber() != "(none)">
            <#assign currentPoNumber = shoppingCart.getPoNumber()>
          </#if>
        <input type="text" class="form-control" name="correspondingPoId" value="${currentPoNumber!}"/>
        <#if productStore.showCheckoutGiftOptions! != "N">
        <hr>
        <h4>${uiLabelMap.OrderIsThisGift}</h4>
        <div class="form-check">
          <input type="radio" class="form-check-input" <#if "Y" == shoppingCart.getIsGift()?default("N")>checked="checked"</#if> name="is_gift" id="is_gift_true" value="true"/><label class="form-check-label" for="is_gift_true">${uiLabelMap.CommonYes}</label>
        </div>
        <div class="form-check">
          <input type="radio" class="form-check-input"<#if "Y" != shoppingCart.getIsGift()?default("N")>checked="checked"</#if> name="is_gift" id="is_gift_false" value="false"/><label class="form-check-label" for="is_gift_false">${uiLabelMap.CommonNo}</label>
        </div>
        <hr>
        <h4>${uiLabelMap.OrderGiftMessage}</h4>
        <textarea class="textAreaBox" cols="30" rows="3" wrap="hard" name="gift_message">${shoppingCart.getGiftMessage()!}</textarea>
        <#else>
          <input type="hidden" name="is_gift" value="fcheckpoutalse"/>
        </#if>
        <hr>
        <h4>${uiLabelMap.PartyEmailAddresses}</h4>
        <div>${uiLabelMap.OrderEmailSentToFollowingAddresses}:</div>
          <strong>
            <#list emailList as email>
              ${email.infoString!}<#if email_has_next>,</#if>
            </#list>
          </strong>
          <div>
            ${uiLabelMap.OrderUpdateEmailAddress}
            <a href="<@ofbizUrl>viewprofile?DONE_PAGE=checkoutoptions</@ofbizUrl>" class="buttontext">
             ${uiLabelMap.PartyProfile}
            </a>.
          </div>
          <hr>
          <label for="order_additional_emails">${uiLabelMap.OrderCommaSeperatedEmailAddresses}:</label>
          <input type="text" class="form-control" name="order_additional_emails" id="order_additional_emails" value="${shoppingCart.getOrderAdditionalEmails()!}"/>
     </div>
    </div>
  </fieldset>
</form>
<div class="row">
  <div class="col-auto mr-auto">
      <a href="javascript:submitForm(document.checkoutInfoForm, 'CS', '');"
          class="btn btn-secondary">${uiLabelMap.OrderBacktoShoppingCart}</a>
  </div>
  <div class="col-auto">
      <a href="javascript:submitForm(document.checkoutInfoForm, 'DN', '');"
          class="btn btn-primary">${uiLabelMap.CommonNext}</a>
  </div>
</div>