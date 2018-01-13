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

<script type="text/javascript">
//<![CDATA[
function submitForm(form, mode, value) {
    if ("DN" == mode) {
        // done action; checkout
        form.action="<@ofbizUrl>checkout</@ofbizUrl>";
        form.submit();
    } else if ("CS" == mode) {
        // continue shopping
        form.action="<@ofbizUrl>updateCheckoutOptions/showcart</@ofbizUrl>";
        form.submit();
    } else if ("NA" == mode) {
        // new address
        form.action="<@ofbizUrl>updateCheckoutOptions/editcontactmech?DONE_PAGE=quickcheckout&partyId=${shoppingCart.getPartyId()}&preContactMechTypeId=POSTAL_ADDRESS&contactMechPurposeTypeId=SHIPPING_LOCATION</@ofbizUrl>";
        form.submit();
    } else if ("EA" == mode) {
        // edit address
        form.action="<@ofbizUrl>updateCheckoutOptions/editcontactmech?DONE_PAGE=quickcheckout&partyId=${shoppingCart.getPartyId()}&contactMechId="+value+"</@ofbizUrl>";
        form.submit();
    } else if ("NC" == mode) {
        // new credit card
        form.action="<@ofbizUrl>updateCheckoutOptions/editcreditcard?DONE_PAGE=quickcheckout&partyId=${shoppingCart.getPartyId()}</@ofbizUrl>";
        form.submit();
    } else if ("EC" == mode) {
        // edit credit card
        form.action="<@ofbizUrl>updateCheckoutOptions/editcreditcard?DONE_PAGE=quickcheckout&partyId=${shoppingCart.getPartyId()}&paymentMethodId="+value+"</@ofbizUrl>";
        form.submit();
    } else if ("GC" == mode) {
        // edit gift card
        form.action="<@ofbizUrl>updateCheckoutOptions/editgiftcard?DONE_PAGE=quickcheckout&partyId=${shoppingCart.getPartyId()}&paymentMethodId="+value+"</@ofbizUrl>";
        form.submit();
    } else if ("NE" == mode) {
        // new eft account
        form.action="<@ofbizUrl>updateCheckoutOptions/editeftaccount?DONE_PAGE=quickcheckout&partyId=${shoppingCart.getPartyId()}</@ofbizUrl>";
        form.submit();
    } else if ("EE" == mode) {
        // edit eft account
        form.action="<@ofbizUrl>updateCheckoutOptions/editeftaccount?DONE_PAGE=quickcheckout&partyId=${shoppingCart.getPartyId()}&paymentMethodId="+value+"</@ofbizUrl>";
        form.submit();
    } else if ("SP" == mode) {
        // split payment
        form.action="<@ofbizUrl>updateCheckoutOptions/checkoutpayment?partyId=${shoppingCart.getPartyId()}</@ofbizUrl>";
        form.submit();
    } else if ("SA" == mode) {
        // selected shipping address
        form.action="<@ofbizUrl>updateCheckoutOptions/quickcheckout</@ofbizUrl>";
        form.submit();
    } else if ("SC" == mode) {
        // selected ship to party
        form.action="<@ofbizUrl>cartUpdateShipToCustomerParty</@ofbizUrl>";
        form.submit();
    }
}
//]]>
</script>

<#assign shipping = !shoppingCart.containAllWorkEffortCartItems()> <#-- contains items which need shipping? -->
<form method="post" name="checkoutInfoForm">
  <input type="hidden" name="checkoutpage" value="quick"/>
  <input type="hidden" name="BACK_PAGE" value="quickcheckout"/>
        <div class="card">
          <h4 class="card-header">
            <#if shipping == true>
              1.&nbsp;${uiLabelMap.OrderWhereShallWeShipIt}?
            <#else>
              1)&nbsp;${uiLabelMap.OrderInformationAboutYou}
            </#if>
          </h4>
          <div class="card-body" >
            <div class="form-group">
              <label for="shipToCustomerPartyId">${uiLabelMap.OrderShipToParty}:</label>
              <select name="shipToCustomerPartyId" id="shipToCustomerPartyId" class="form-control" onchange="javascript:submitForm(document.checkoutInfoForm, 'SC', null);">
                <#list cartParties as cartParty>
                  <option value="${cartParty}">${cartParty}</option>
                </#list>
              </select>
            </div>
            <div class="btn-toolbar mb-2">
              <a href="javascript:submitForm(document.checkoutInfoForm, 'NA', '');" class="btn btn-outline-secondary mr-2">${uiLabelMap.CommonAdd} ${uiLabelMap.PartyAddNewAddress}</a>
              <#if (shoppingCart.getTotalQuantity() > 1) && !shoppingCart.containAllWorkEffortCartItems()> <#-- no splitting when only rental items -->
                <a href="<@ofbizUrl>splitship</@ofbizUrl>" class="btn btn-outline-secondary">${uiLabelMap.OrderSplitIntoMultipleShipments}</a>
                <#if (shoppingCart.getShipGroupSize() > 1)>
                  <div style="color: red;">${uiLabelMap.OrderNOTEMultipleShipmentsExist}.</div>
                </#if>
              </#if>
            </div>
            <#if shippingContactMechList?has_content>
              <#list shippingContactMechList as shippingContactMech>
                <#assign shippingAddress = shippingContactMech.getRelatedOne("PostalAddress", false)>
                  <div class="form-check">
                    <input type="radio" name="shipping_contact_mech_id" id="shipping_contact_mech_id" value="${shippingAddress.contactMechId}" onclick="javascript:submitForm(document.checkoutInfoForm, 'SA', null);"<#if shoppingCart.getShippingContactMechId()?default("") == shippingAddress.contactMechId> checked="checked"</#if>/>
                    <label for="shipping_contact_mech_id">
                      <#if shippingAddress.toName?has_content><strong>${uiLabelMap.CommonTo}:</strong>&nbsp;${shippingAddress.toName}</#if>
                      <#if shippingAddress.attnName?has_content><strong>${uiLabelMap.PartyAddrAttnName}:</strong>&nbsp;${shippingAddress.attnName}</#if>
                      <#if shippingAddress.address1?has_content><strong>${uiLabelMap.OrderAddress}:</strong>&nbsp;${shippingAddress.address1}</#if>
                      <#if shippingAddress.address2?has_content>${shippingAddress.address2}</#if>
                      <#if shippingAddress.city?has_content>${shippingAddress.city}</#if>
                      <#if shippingAddress.stateProvinceGeoId?has_content>${shippingAddress.stateProvinceGeoId}</#if>
                      <#if shippingAddress.postalCode?has_content>${shippingAddress.postalCode}</#if>
                      <#if shippingAddress.countryGeoId?has_content>${shippingAddress.countryGeoId}</#if>
                    </label>
                  </div>
                  <a href="javascript:submitForm(document.checkoutInfoForm, 'EA', '${shippingAddress.contactMechId}');" class="btn btn-secondary">${uiLabelMap.CommonUpdate}</a>
                <#if shippingContactMech_has_next>
                </#if>
              </#list>
            </#if>
          </div>
        </div>
        <div class="card" >
            <h4 class="card-header">
                <#if shipping == true>
                  2.&nbsp;${uiLabelMap.OrderHowShallWeShipIt}?
                <#else>
                  2.&nbsp;${uiLabelMap.OrderOptions}?
                </#if>
            </h4>
            <div class="card-body" >
                <#if shipping == true>
                  <div class="row">
                  <#list carrierShipmentMethodList as carrierShipmentMethod>
                    <#assign shippingMethod = carrierShipmentMethod.shipmentMethodTypeId + "@" + carrierShipmentMethod.partyId>
                    <div class="col-lg-4 col-sm-6">
                      <div class="form-check">
                        <input type="radio" name="shipping_method" id="shipping_method_${carrierShipmentMethod_index}" value="${shippingMethod}" <#if shippingMethod == chosenShippingMethod?default("N@A")>checked="checked"</#if>/>
                        <label for="shipping_method_${carrierShipmentMethod_index}">
                          <#if shoppingCart.getShippingContactMechId()??>
                            <#assign shippingEst = shippingEstWpr.getShippingEstimate(carrierShipmentMethod)?default(-1)>
                          </#if>
                          <#if carrierShipmentMethod.partyId != "_NA_">${carrierShipmentMethod.partyId!}&nbsp;</#if>${carrierShipmentMethod.description!}
                          <#if shippingEst?has_content> - <#if (shippingEst > -1)><@ofbizCurrency amount=shippingEst isoCode=shoppingCart.getCurrency()/><#else>${uiLabelMap.OrderCalculatedOffline}</#if></#if>
                        </label>
                      </div>
                    </div>
                  </#list>
                  </div>
                  <#if !carrierShipmentMethodList?? || carrierShipmentMethodList?size == 0>
                        <label>
                        <input type="radio" name="shipping_method" value="Default" checked="checked"/>
                        ${uiLabelMap.OrderUseDefault}.
                        </label>
                  </#if>
                  <h5>${uiLabelMap.OrderShipAllAtOnce}?</h5>
                  <label>
                    <input type="radio" <#if "N" == shoppingCart.getMaySplit()?default("N")>checked="checked"</#if> name="may_split" value="false"/>
                    ${uiLabelMap.OrderPleaseWaitUntilBeforeShipping}.
                  </label>
                  <label>
                    <input <#if "Y" == shoppingCart.getMaySplit()?default("N")>checked="checked"</#if> type="radio" name="may_split" value="true"/>
                    ${uiLabelMap.OrderPleaseShipItemsBecomeAvailable}.
                  </label>
                <#else>
                    <input type="hidden" name="shipping_method" value="NO_SHIPPING@_NA_"/>
                    <input type="hidden" name="may_split" value="false"/>
                    <input type="hidden" name="is_gift" value="false"/>
                </#if>
                      <h5>${uiLabelMap.OrderSpecialInstructions}</h5>
                      <textarea rows="3" wrap="hard" name="shipping_instructions">${shoppingCart.getShippingInstructions()!}</textarea>
                 <#if shipping == true>
                  <#if productStore.showCheckoutGiftOptions! != "N" && giftEnable! != "N">
                      <div>
                        <h5>${uiLabelMap.OrderIsThisGift}</h5>
                        <div class="form-check">
                          <input type="radio" <#if "Y" == shoppingCart.getIsGift()?default("Y")>checked="checked"</#if> name="is_gift" id="is_gift" value="true">
                          <label for="is_gift">${uiLabelMap.CommonYes}</label>
                          <input type="radio" <#if "N" == shoppingCart.getIsGift()?default("Y")>checked="checked"</#if> name="is_gift" id="is_not_gift" value="false">
                          <label for="is_not_gift">${uiLabelMap.CommonNo}</label>
                        </div>
                      </div>
                      <h5>${uiLabelMap.OrderGiftMessage}</h5>
                      <textarea rows="3" wrap="hard" name="gift_message">${shoppingCart.getGiftMessage()!}</textarea>
                  <#else>
                  <input type="hidden" name="is_gift" value="false"/>
                  </#if>
                 </#if>
                      <h5>${uiLabelMap.PartyEmailAddresses}</h5>
                      <div>${uiLabelMap.OrderEmailSentToFollowingAddresses}:</div>
                      <div>
                      <strong>
                      <#list emailList as email>
                        ${email.infoString!}<#if email_has_next>,</#if>
                      </#list>
                      </strong>
                      </div>
                      <div>${uiLabelMap.OrderUpdateEmailAddress} <a href="<#if customerDetailLink??>${customerDetailLink}${shoppingCart.getPartyId()}" target="partymgr"
                        <#else><@ofbizUrl>viewprofile?DONE_PAGE=quickcheckout</@ofbizUrl>"</#if> class="buttontext">${uiLabelMap.PartyProfile}</a>.</div>
                      <br />
                      <label for="order_additional_emails">${uiLabelMap.OrderCommaSeperatedEmailAddresses}:</label>
                      <input type="text" class="form-control" name="order_additional_emails" id="order_additional_emails" value="${shoppingCart.getOrderAdditionalEmails()!}"/>

            </div>
        </div>

        <#-- Payment Method Selection -->
        <div class="card" >
            <h4 class="card-header">
              3.&nbsp${uiLabelMap.OrderHowShallYouPay}?
            </h4>
            <div class="card-body" >
                  <h5>${uiLabelMap.CommonAdd}:</h5>
                  <div class="btn-toolbar form-group">
                    <#if productStorePaymentMethodTypeIdMap.CREDIT_CARD??>
                      <a href="javascript:submitForm(document.checkoutInfoForm, 'NC', '');" class="btn btn-outline-secondary mr-2">${uiLabelMap.AccountingCreditCard}</a>
                    </#if>
                    <#if productStorePaymentMethodTypeIdMap.EFT_ACCOUNT??>
                      <a href="javascript:submitForm(document.checkoutInfoForm, 'NE', '');" class="btn btn-outline-secondary mr-2">${uiLabelMap.AccountingEFTAccount}</a>
                    </#if>
                    <a href="javascript:submitForm(document.checkoutInfoForm, 'SP', '');" class="btn btn-outline-secondary">${uiLabelMap.AccountingSplitPayment}</a>
                  </div>

                  <#if productStorePaymentMethodTypeIdMap.EXT_OFFLINE??>
                    <div class="form-check">
                      <input type="radio" id="checkOutEXT_OFFLINE" name="checkOutPaymentId" value="EXT_OFFLINE" <#if "EXT_OFFLINE" == checkOutPaymentId>checked="checked"</#if>/>
                      <label for="checkOutEXT_OFFLINE">${uiLabelMap.OrderMoneyOrder}</label>
                    </div>
                  </#if>
                  <#if productStorePaymentMethodTypeIdMap.EXT_COD??>
                    <div class="form-check">
                      <input type="radio" id="checkOutEXT_COD" name="checkOutPaymentId" value="EXT_COD" <#if "EXT_COD" == checkOutPaymentId>checked="checked"</#if>/>
                      <label for="checkOutEXT_COD">${uiLabelMap.OrderCOD}</label>
                    </div>
                  </#if>
                  <#if productStorePaymentMethodTypeIdMap.EXT_WORLDPAY??>
                    <div class="form-check">
                      <input type="radio" id="checkOutEXT_WORLDPAY" name="checkOutPaymentId" value="EXT_WORLDPAY" <#if "EXT_WORLDPAY" == checkOutPaymentId>checked="checked"</#if>/>
                      <label for="checkOutEXT_WORLDPAY">${uiLabelMap.AccountingPayWithWorldPay}</label>
                    </div>
                  </#if>
                  <#if productStorePaymentMethodTypeIdMap.EXT_PAYPAL??>
                    <div class="form-check">
                      <input type="radio" id="checkOutEXT_PAYPAL" name="checkOutPaymentId" value="EXT_PAYPAL" <#if "EXT_PAYPAL" == checkOutPaymentId>checked="checked"</#if>/>
                      <label for="checkOutEXT_PAYPAL">${uiLabelMap.AccountingPayWithPayPal}</label>
                    </div>
                  </#if>

                  <#-- financial accounts -->
                  <#list finAccounts as finAccount>
                    <div class="form-check">
                      <input type="radio" name="checkOutFIN_ACCOUNT${finAccount.finAccountId}" value="FIN_ACCOUNT|${finAccount.finAccountId}" <#if "FIN_ACCOUNT" == checkOutPaymentId>checked="checked"</#if>/>
                      <label for="checkOutFIN_ACCOUNT${finAccount.finAccountId}">${uiLabelMap.AccountingFinAccount} #${finAccount.finAccountId}</label>
                    </div>
                  </#list>

                  <#if !paymentMethodList?has_content>
                    <#if (!finAccounts?has_content)>
                          <h5><strong>${uiLabelMap.AccountingNoPaymentMethods}</strong></h5>
                    </#if>
                  <#else>
                  <#list paymentMethodList as paymentMethod>
                    <#if "CREDIT_CARD" == paymentMethod.paymentMethodTypeId>
                     <#if productStorePaymentMethodTypeIdMap.CREDIT_CARD??>
                      <#assign creditCard = paymentMethod.getRelatedOne("CreditCard", false)>
                        <div class="form-check">
                          <input type="radio" name="checkOutPaymentId" value="${paymentMethod.paymentMethodId}" <#if shoppingCart.isPaymentSelected(paymentMethod.paymentMethodId)>checked="checked"</#if>/>
                          <label>CC:&nbsp;${Static["org.apache.ofbiz.party.contact.ContactHelper"].formatCreditCard(creditCard)}</label>
                        </div>
                          <a href="javascript:submitForm(document.checkoutInfoForm, 'EC', '${paymentMethod.paymentMethodId}');" class="buttontext">${uiLabelMap.CommonUpdate}</a>
                          <#if paymentMethod.description?has_content><br /><span>(${paymentMethod.description})</span></#if>
                          &nbsp;${uiLabelMap.OrderCardSecurityCode}&nbsp;<input type="text" size="5" maxlength="10" name="securityCode_${paymentMethod.paymentMethodId}" value=""/>
                     </#if>
                    <#elseif "EFT_ACCOUNT" == paymentMethod.paymentMethodTypeId>
                     <#if productStorePaymentMethodTypeIdMap.EFT_ACCOUNT??>
                      <#assign eftAccount = paymentMethod.getRelatedOne("EftAccount", false)>
                        <div class="form-check">
                          <input type="radio" name="checkOutPaymentId" id="checkOutPaymentId" value="${paymentMethod.paymentMethodId}" <#if shoppingCart.isPaymentSelected(paymentMethod.paymentMethodId)>checked="checked"</#if>/>
                          <label for="checkOutPaymentId">${uiLabelMap.AccountingEFTAccount}:&nbsp;${eftAccount.bankName!}: ${eftAccount.accountNumber!}</label>
                        </div>
                          <a href="javascript:submitForm(document.checkoutInfoForm, 'EE', '${paymentMethod.paymentMethodId}');" class="buttontext">${uiLabelMap.CommonUpdate}</a>
                          <#if paymentMethod.description?has_content><br /><span>(${paymentMethod.description})</span></#if>
                     </#if>
                    <#elseif "GIFT_CARD" == paymentMethod.paymentMethodTypeId>
                     <#if productStorePaymentMethodTypeIdMap.GIFT_CARD??>
                      <#assign giftCard = paymentMethod.getRelatedOne("GiftCard", false)>

                      <#if giftCard?has_content && giftCard.cardNumber?has_content>
                        <#assign giftCardNumber = "">
                        <#assign pcardNumber = giftCard.cardNumber>
                        <#if pcardNumber?has_content>
                          <#assign psize = pcardNumber?length - 4>
                          <#if 0 < psize>
                            <#list 0 .. psize-1 as foo>
                              <#assign giftCardNumber = giftCardNumber + "*">
                            </#list>
                            <#assign giftCardNumber = giftCardNumber + pcardNumber[psize .. psize + 3]>
                          <#else>
                            <#assign giftCardNumber = pcardNumber>
                          </#if>
                        </#if>
                      </#if>

                          <label>
                          <input type="radio" name="checkOutPaymentId" value="${paymentMethod.paymentMethodId}" <#if shoppingCart.isPaymentSelected(paymentMethod.paymentMethodId)>checked="checked"</#if>/>
                          <span>${uiLabelMap.AccountingGift}:&nbsp;${giftCardNumber}</span>
                          </label>
                          <a href="javascript:submitForm(document.checkoutInfoForm, 'EG', '${paymentMethod.paymentMethodId}');" class="buttontext">[${uiLabelMap.CommonUpdate}]</a>
                          <#if paymentMethod.description?has_content><br /><span>(${paymentMethod.description})</span></#if>
                     </#if>
                    </#if>
                  </#list>
                  </#if>

                <#-- special billing account functionality to allow use w/ a payment method -->
                <#if productStorePaymentMethodTypeIdMap.EXT_BILLACT??>
                  <#if billingAccountList?has_content>
                        <label for="billingAccountId">${uiLabelMap.FormFieldTitle_billingAccountId}</label>

                        <select name="billingAccountId" id="billingAccountId" class="form-control">
                          <option value=""></option>
                            <#list billingAccountList as billingAccount>
                              <#assign availableAmount = billingAccount.accountBalance?double>
                              <#assign accountLimit = billingAccount.accountLimit?double>
                              <option value="${billingAccount.billingAccountId}" <#if billingAccount.billingAccountId == selectedBillingAccountId?default("")>selected="selected"</#if>>${billingAccount.description?default("")} [${billingAccount.billingAccountId}] Available: <@ofbizCurrency amount=availableAmount isoCode=billingAccount.accountCurrencyUomId/> Limit: <@ofbizCurrency amount=accountLimit isoCode=billingAccount.accountCurrencyUomId/></option>
                            </#list>
                        </select>
                        <input type="text" size="5" name="billingAccountAmount" value=""/>
                        ${uiLabelMap.OrderBillUpTo}
                  </#if>
                </#if>
                <#-- end of special billing account functionality -->

                <#if productStorePaymentMethodTypeIdMap.GIFT_CARD??>
                    <div class="form-check">
                      <input type="checkbox" id="addGiftCard" name="addGiftCard" value="Y"/>
                      <label for="addGiftCard">${uiLabelMap.AccountingUseGiftCardNotOnFile}</label>
                    </div>
                    <div class="form-group">
                      <label for="giftCardNumber">${uiLabelMap.AccountingNumber}</label>
                      <input type="text" class="form-control" name="giftCardNumber" id="giftCardNumber" value="${(requestParameters.giftCardNumber)!}" onFocus="document.checkoutInfoForm.addGiftCard.checked=true;"/>
                    </div>
                  <#if shoppingCart.isPinRequiredForGC(delegator)>
                    <div class="form-group">
                      <label for="giftCardPin">${uiLabelMap.AccountingPIN}</label>
                      <input type="text" class="form-control" name="giftCardPin" id="giftCardPin" value="${(requestParameters.giftCardPin)!}" onFocus="document.checkoutInfoForm.addGiftCard.checked=true;"/>
                    </div>
                  </#if>
                    <div class="form-group">
                      <label for="giftCardAmount">${uiLabelMap.AccountingAmount}</label>
                      <input type="text" class="form-control" name="giftCardAmount" id="giftCardAmount" value="${(requestParameters.giftCardAmount)!}" onFocus="document.checkoutInfoForm.addGiftCard.checked=true;"/>
                    </div>
                </#if>
            </div>
        </div>
        <#-- End Payment Method Selection -->
</form>
<div class="row">
  <div class="col-auto mr-auto">
    <a href="javascript:submitForm(document.checkoutInfoForm, 'CS', '');" class="btn btn-secondary">${uiLabelMap.OrderBacktoShoppingCart}</a>
  </div>
  <div class="col-auto">
    <a href="javascript:submitForm(document.checkoutInfoForm, 'DN', '');" class="btn btn-primary">${uiLabelMap.OrderContinueToFinalOrderReview}</a>
  </div>
</div>