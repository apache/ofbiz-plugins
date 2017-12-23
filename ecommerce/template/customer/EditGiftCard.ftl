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
  <a href="<@ofbizUrl>${donePage}</@ofbizUrl>" class="btn btn-outline-secondary">[${uiLabelMap.CommonGoBack}]</a>
<#else>
  <#if !giftCard??>
    <h1>${uiLabelMap.AccountingAddNewGiftCard}</h1>
    <form method="post" action="<@ofbizUrl>createGiftCard?DONE_PAGE=${donePage}</@ofbizUrl>"
        name="editgiftcardform">
  <#else>
    <h1>${uiLabelMap.AccountingEditGiftCard}</h1>
    <form method="post" action="<@ofbizUrl>updateGiftCard?DONE_PAGE=${donePage}</@ofbizUrl>" name="editgiftcardform">
      <input type="hidden" name="paymentMethodId" value="${paymentMethodId}"/>
  </#if>
    <div class="form-group">
      <a href="<@ofbizUrl>${donePage}</@ofbizUrl>" class="btn btn-outline-secondary">${uiLabelMap.CommonGoBack}</a>
      <a href="javascript:document.editgiftcardform.submit()" class="btn btn-outline-secondary">${uiLabelMap.CommonSave}</a>
    </div>
            <label>${uiLabelMap.AccountingCardNumber}</label>
            <#if giftCardData?has_content && giftCardData.cardNumber?has_content>
              <#assign pcardNumberDisplay = "">
              <#assign pcardNumber = giftCardData.cardNumber!>
              <#if pcardNumber?has_content>
                <#assign psize = pcardNumber?length - 4>
                <#if 0 < psize>
                  <#list 0 .. psize-1 as foo>
                    <#assign pcardNumberDisplay = pcardNumberDisplay + "*">
                  </#list>
                  <#assign pcardNumberDisplay = pcardNumberDisplay + pcardNumber[psize .. psize + 3]>
                <#else>
                  <#assign pcardNumberDisplay = pcardNumber>
                </#if>
              </#if>
            </#if>
            <div class="row">
              <div class="col-sm-6">
                <input type="text" class="form-control" name="cardNumber" value="${pcardNumberDisplay!}"/>
              </div>
            </div>
            <label>${uiLabelMap.AccountingPINNumber}</label>
            <div class="row">
              <div class="col-sm-6">
                <input type="password" class="form-control" name="pinNumber"
                autocomplete="off" value="${giftCardData.pinNumber!}"/>
              </div>
            </div>
            <label>${uiLabelMap.AccountingExpirationDate}</label>
            <#assign expMonth = "">
            <#assign expYear = "">
            <#if giftCardData?? && giftCardData.expireDate??>
              <#assign expDate = giftCard.expireDate>
              <#if (expDate?? && expDate.indexOf("/") > 0)>
                <#assign expMonth = expDate.substring(0,expDate.indexOf("/"))>
                <#assign expYear = expDate.substring(expDate.indexOf("/")+1)>
              </#if>
            </#if>
            <div class="row">
            <div class="col-sm-3">
            <select name="expMonth" class="custom-select form-control" onchange="javascript:makeExpDate();">
              <option>${uiLabelMap.CommonSelect}</option>
              <#if giftCardData?has_content && expMonth?has_content>
                <#assign ccExprMonth = expMonth>
              <#else>
                <#assign ccExprMonth = requestParameters.expMonth!>
              </#if>
              <#if ccExprMonth?has_content>
                <option value="${ccExprMonth!}">${ccExprMonth!}</option>
              </#if>
              ${screens.render("component://common/widget/CommonScreens.xml#ccmonths")}
            </select>
            </div>
            <div class="col-sm-3">
            <select name="expYear" class="custom-select form-control" onchange="javascript:makeExpDate();">
              <option>${uiLabelMap.CommonSelect}</option>
              <#if giftCard?has_content && expYear?has_content>
                <#assign ccExprYear = expYear>
              <#else>
                <#assign ccExprYear = requestParameters.expYear!>
              </#if>
              <#if ccExprYear?has_content>
                <option value="${ccExprYear!}">${ccExprYear!}</option>
              </#if>
              ${screens.render("component://common/widget/CommonScreens.xml#ccyears")}
            </select>
            </div>
          </div>
            <label>${uiLabelMap.CommonDescription}</label>
            <div class="row">
            <div class="col-sm-6">
            <input type="text" class="inputBox form-control" maxlength="60" name="description"
                value="${paymentMethodData.description!}"/>
            </div>
            </div>
    </form>
  <a href="<@ofbizUrl>${donePage}</@ofbizUrl>" class="btn btn-outline-secondary">${uiLabelMap.CommonGoBack}</a>
  <a href="javascript:document.editgiftcardform.submit()" class="btn btn-outline-secondary">${uiLabelMap.CommonSave}</a>
</#if>
