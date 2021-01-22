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


<!-- TODO : Need formatting -->
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
    } else if ("NC" == mode) {
        // new credit card
        form.action="<@ofbizUrl>updateCheckoutOptions/editcreditcard?DONE_PAGE=checkoutpayment</@ofbizUrl>";
        form.submit();
    } else if ("EC" == mode) {
        // edit credit card
        form.action="<@ofbizUrl>updateCheckoutOptions/editcreditcard?DONE_PAGE=checkoutpayment&paymentMethodId="+value+"</@ofbizUrl>";
        form.submit();
    } else if ("GC" == mode) {
        // edit gift card
        form.action="<@ofbizUrl>updateCheckoutOptions/editgiftcard?paymentMethodId="+value+"</@ofbizUrl>";
        form.submit();
    } else if ("NE" == mode) {
        // new eft account
        form.action="<@ofbizUrl>updateCheckoutOptions/editeftaccount?DONE_PAGE=checkoutpayment</@ofbizUrl>";
        form.submit();
    } else if ("EE" == mode) {
        // edit eft account
        form.action="<@ofbizUrl>updateCheckoutOptions/editeftaccount?DONE_PAGE=checkoutpayment&paymentMethodId="+value+"</@ofbizUrl>";
        form.submit();
    }else if(mode = "EG")
    //edit gift card
        form.action="<@ofbizUrl>updateCheckoutOptions/editgiftcard?DONE_PAGE=checkoutpayment&paymentMethodId="+value+"</@ofbizUrl>";
        form.submit();
}
</script>


<#assign cart = shoppingCart! />

<form method="post" id="checkoutInfoForm" action="">
    <input type="hidden" name="checkoutpage" value="payment" />
    <input type="hidden" name="BACK_PAGE" value="checkoutoptions" />
    <input type="hidden" name="issuerId" id="issuerId" value="" />

    <div class="card">
        <h4 class="card-header">
          3)&nbsp;${uiLabelMap.OrderHowShallYouPay}?
        </h4>
        <div class="card-body">
            <#-- Payment Method Selection -->
            <div>
                <label>${uiLabelMap.CommonAdd}:</label>
                <#if productStorePaymentMethodTypeIdMap.CREDIT_CARD??>
                  <a href="javascript:submitForm(document.getElementById('checkoutInfoForm'), 'NC', '');" class="button">${uiLabelMap.AccountingCreditCard}</a>
                </#if>
                <#if productStorePaymentMethodTypeIdMap.EFT_ACCOUNT??>
                  <a href="javascript:submitForm(document.getElementById('checkoutInfoForm'), 'NE', '');" class="button">${uiLabelMap.AccountingEFTAccount}</a>
                </#if>
              <#if productStorePaymentMethodTypeIdMap.EXT_OFFLINE??>
              </div>
              <div class="form-check">
                  <input type="radio" class="form-check-input" id="checkOutPaymentId_OFFLINE" name="checkOutPaymentId" value="EXT_OFFLINE" <#if "EXT_OFFLINE" == checkOutPaymentId>checked="checked"</#if> />
                  <label for="checkOutPaymentId_OFFLINE">${uiLabelMap.OrderMoneyOrder}</label>
              </div>
              </#if>
              <#if productStorePaymentMethodTypeIdMap.EXT_COD??>
              <div class="form-check">
                  <input class="form-check-input" type="radio" id="checkOutPaymentId_COD" name="checkOutPaymentId" value="EXT_COD" <#if "EXT_COD" == checkOutPaymentId>checked="checked"</#if> />
                  <label for="checkOutPaymentId_COD">${uiLabelMap.OrderCOD}</label>
              </div>
              </#if>
              <#if productStorePaymentMethodTypeIdMap.EXT_WORLDPAY??>
              <div class="form-check">
                  <input class="form-check-input" type="radio" id="checkOutPaymentId_WORLDPAY" name="checkOutPaymentId" value="EXT_WORLDPAY" <#if "EXT_WORLDPAY" == checkOutPaymentId>checked="checked"</#if> />
                  <label for="checkOutPaymentId_WORLDPAY">${uiLabelMap.AccountingPayWithWorldPay}</label>
              </div>
              </#if>
              <#if productStorePaymentMethodTypeIdMap.EXT_PAYPAL??>
              <div class="form-check">
                  <input class="form-check-input" type="radio" id="checkOutPaymentId_PAYPAL" name="checkOutPaymentId" value="EXT_PAYPAL" <#if "EXT_PAYPAL" == checkOutPaymentId>checked="checked"</#if> />
                  <label for="checkOutPaymentId_PAYPAL">${uiLabelMap.AccountingPayWithPayPal}</label>
              </div>
              </#if>
              <hr>
              <#if !paymentMethodList?has_content>
              <div>
                  <strong>${uiLabelMap.AccountingNoPaymentMethods}.</strong>
              </div>
            <#else>
              <#list paymentMethodList as paymentMethod>
                <#if "GIFT_CARD" == paymentMethod.paymentMethodTypeId>
                 <#if productStorePaymentMethodTypeIdMap.GIFT_CARD??>
                  <#assign giftCard = paymentMethod.getRelatedOne("GiftCard", false) />

                  <#if giftCard?has_content && giftCard.cardNumber?has_content>
                    <#assign giftCardNumber = "" />
                    <#assign pcardNumber = giftCard.cardNumber />
                    <#if pcardNumber?has_content>
                      <#assign psize = pcardNumber?length - 4 />
                      <#if 0 &lt; psize>
                        <#list 0 .. psize-1 as foo>
                          <#assign giftCardNumber = giftCardNumber + "*" />
                        </#list>
                        <#assign giftCardNumber = giftCardNumber + pcardNumber[psize .. psize + 3] />
                      <#else>
                        <#assign giftCardNumber = pcardNumber />
                      </#if>
                    </#if>
                  </#if>

                  <div>
                      <input type="checkbox" id="checkOutPayment_${paymentMethod.paymentMethodId}" class="form-check-input" name="checkOutPaymentId" value="${paymentMethod.paymentMethodId}" <#if cart.isPaymentSelected(paymentMethod.paymentMethodId)>checked="checked"</#if> />
                      <label for="checkOutPayment_${paymentMethod.paymentMethodId}">${uiLabelMap.AccountingGift}:${giftCardNumber}
                        <#if paymentMethod.description?has_content>(${paymentMethod.description})</#if></label>
                        <a href="javascript:submitForm(document.getElementById('checkoutInfoForm'), 'EG', '${paymentMethod.paymentMethodId}');" class="button">${uiLabelMap.CommonUpdate}</a>
                        <strong>${uiLabelMap.OrderBillUpTo}:</strong> <input type="text" size="5" class="inputBox" name="amount_${paymentMethod.paymentMethodId}" value="<#if (cart.getPaymentAmount(paymentMethod.paymentMethodId)?default(0) > 0)><@ofbizAmount amount=cart.getPaymentAmount(paymentMethod.paymentMethodId)!/></#if>"/>
                  </div>
                 </#if>
                <#elseif "CREDIT_CARD" == paymentMethod.paymentMethodTypeId>
                 <#if productStorePaymentMethodTypeIdMap.CREDIT_CARD??>
                  <#assign creditCard = paymentMethod.getRelatedOne("CreditCard", false) />
                  <div>
                      <input type="checkbox" id="checkOutPayment_${paymentMethod.paymentMethodId}" class="form-check-input" name="checkOutPaymentId" value="${paymentMethod.paymentMethodId}" <#if cart.isPaymentSelected(paymentMethod.paymentMethodId)>checked="checked"</#if> />
                      <label for="checkOutPayment_${paymentMethod.paymentMethodId}">CC:${Static["org.apache.ofbiz.party.contact.ContactHelper"].formatCreditCard(creditCard)}
                        <#if paymentMethod.description?has_content>(${paymentMethod.description})</#if></label>
                        <a href="javascript:submitForm(document.getElementById('checkoutInfoForm'), 'EC', '${paymentMethod.paymentMethodId}');" class="button">${uiLabelMap.CommonUpdate}</a>
                        <label for="amount_${paymentMethod.paymentMethodId}"><strong>${uiLabelMap.OrderBillUpTo}:</strong></label><input type="text" size="5" class="inputBox" id="amount_${paymentMethod.paymentMethodId}" name="amount_${paymentMethod.paymentMethodId}" value="<#if (cart.getPaymentAmount(paymentMethod.paymentMethodId)?default(0) > 0)><@ofbizAmount amount=cart.getPaymentAmount(paymentMethod.paymentMethodId)!/></#if>" />
                  </div>
                 </#if>
                <#elseif "EFT_ACCOUNT" == paymentMethod.paymentMethodTypeId>
                 <#if productStorePaymentMethodTypeIdMap.EFT_ACCOUNT??>
                  <#assign eftAccount = paymentMethod.getRelatedOne("EftAccount", false) />
                  <div class="form-check">
                      <input type="radio" id="checkOutPayment_${paymentMethod.paymentMethodId}" class="form-check-input" name="checkOutPaymentId" value="${paymentMethod.paymentMethodId}" <#if paymentMethod.paymentMethodId == checkOutPaymentId>checked="checked"</#if> />
                      <label for="checkOutPayment_${paymentMethod.paymentMethodId}">${uiLabelMap.AccountingEFTAccount}:${eftAccount.bankName!}: ${eftAccount.accountNumber!}
                        <#if paymentMethod.description?has_content><p>(${paymentMethod.description})</p></#if></label>
                      <a href="javascript:submitForm(document.getElementById('checkoutInfoForm'), 'EE', '${paymentMethod.paymentMethodId}');" class="button">${uiLabelMap.CommonUpdate}</a>
                  </div>
                 </#if>
                </#if>
              </#list>
            </#if>

            <#-- special billing account functionality to allow use w/ a payment method -->
            <#if productStorePaymentMethodTypeIdMap.EXT_BILLACT??>
              <#if billingAccountList?has_content>
                <div class="form-group">
                    <select class="form-control" name="billingAccountId" id="billingAccountId">
                      <option value=""></option>
                        <#list billingAccountList as billingAccount>
                          <#assign availableAmount = billingAccount.accountBalance>
                          <#assign accountLimit = billingAccount.accountLimit>
                          <option value="${billingAccount.billingAccountId}" <#if billingAccount.billingAccountId == selectedBillingAccountId?default("")>selected="selected"</#if>>${billingAccount.description?default("")} [${billingAccount.billingAccountId}] ${uiLabelMap.EcommerceAvailable} <@ofbizCurrency amount=availableAmount isoCode=billingAccount.accountCurrencyUomId/> ${uiLabelMap.EcommerceLimit} <@ofbizCurrency amount=accountLimit isoCode=billingAccount.accountCurrencyUomId/></option>
                        </#list>
                    </select>
                    <label for="billingAccountId">${uiLabelMap.FormFieldTitle_billingAccountId}</label>
                </div>
                <div>
                    <input type="text" size="5" id="billingAccountAmount" name="billingAccountAmount" value="" />
                    <label for="billingAccountAmount">${uiLabelMap.OrderBillUpTo}</label>
                </div>
              </#if>
            </#if>
            <#-- end of special billing account functionality -->

            <#if productStorePaymentMethodTypeIdMap.GIFT_CARD??>
              <div class="form-check">
                  <input class="form-check-input" type="checkbox" id="addGiftCard" name="addGiftCard" value="Y" />
                  <input type="hidden" name="singleUseGiftCard" value="Y" />
                  <label class="form-check-label" for="addGiftCard">${uiLabelMap.AccountingUseGiftCardNotOnFile}</label>
              </div>
              <div>
                  <label for="giftCardNumber">${uiLabelMap.AccountingNumber}</label>
                  <input type="text" class="form-control" id="giftCardNumber" name="giftCardNumber" value="${(requestParameters.giftCardNumber)!}" onfocus="document.getElementById('addGiftCard').checked=true;" />
              </div>
              <#if cart.isPinRequiredForGC(delegator)>
              <div>
                  <label for="giftCardPin">${uiLabelMap.AccountingPIN}</label>
                  <input type="text" class="form-control" class="inputBox" id="giftCardPin" name="giftCardPin" value="${(requestParameters.giftCardPin)!}" onfocus="document.getElementById('addGiftCard').checked=true;" />
              </div>
              </#if>
              <div>
                  <label for="giftCardAmount">${uiLabelMap.AccountingAmount}</label>
                  <input type="text" size="6" class="form-control" id="giftCardAmount" name="giftCardAmount" value="${(requestParameters.giftCardAmount)!}" onfocus="document.getElementById('addGiftCard').checked=true;" />
              </div>
            </#if>

              <div>
                    <#if productStorePaymentMethodTypeIdMap.CREDIT_CARD??><a href="<@ofbizUrl>setBilling?paymentMethodType=CC&amp;singleUsePayment=Y</@ofbizUrl>" class="button">${uiLabelMap.AccountingSingleUseCreditCard}</a></#if>
                    <#if productStorePaymentMethodTypeIdMap.GIFT_CARD??><a href="<@ofbizUrl>setBilling?paymentMethodType=GC&amp;singleUsePayment=Y</@ofbizUrl>" class="button">${uiLabelMap.AccountingSingleUseGiftCard}</a></#if>
                    <#if productStorePaymentMethodTypeIdMap.EFT_ACCOUNT??><a href="<@ofbizUrl>setBilling?paymentMethodType=EFT&amp;singleUsePayment=Y</@ofbizUrl>" class="button">${uiLabelMap.AccountingSingleUseEFTAccount}</a></#if>
              </div>
            <#-- End Payment Method Selection -->
        </div>
    </div>
</form>
<div class="row">
  <div class="col-auto mr-auto">
    <a href="javascript:submitForm(document.getElementById('checkoutInfoForm'), 'CS', '');" class="btn btn-secondary">${uiLabelMap.OrderBacktoShoppingCart}</a>
  </div>
  <div class="col-auto">
    <a href="javascript:submitForm(document.getElementById('checkoutInfoForm'), 'DN', '');" class="btn btn-primary">${uiLabelMap.OrderContinueToFinalOrderReview}</a>
  </div>
</div>