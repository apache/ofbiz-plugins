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

<#if !creditCard?has_content>
  <#assign creditCard = requestParameters>
</#if>

<#if !paymentMethod?has_content>
  <#assign paymentMethod = requestParameters>
</#if>

<label class="mt-2">${uiLabelMap.AccountingCompanyNameCard}</label>
<div class="row">
  <div class="col-sm-6">
    <input type="text" class="form-control" maxlength="60" name="companyNameOnCard" value="${creditCard.companyNameOnCard!}"/>
  </div>
</div>
<label>${uiLabelMap.AccountingPrefixCard}</label>
<div class="row">
  <div class="col-sm-6">
    <select name="titleOnCard" class="form-control custom-select">
      <option value="">${uiLabelMap.CommonSelectOne}</option>
      <option<#if ("${uiLabelMap.CommonTitleMr}" == (creditCard.titleOnCard)?default(""))> selected="selected"</#if>>${uiLabelMap.CommonTitleMr}</option>
      <option<#if ("Mrs." == (creditCard.titleOnCard)?default(""))> selected="selected"</#if>>${uiLabelMap.CommonTitleMrs}</option>
      <option<#if ("Ms." == (creditCard.titleOnCard)?default(""))> selected="selected"</#if>>${uiLabelMap.CommonTitleMs}</option>
      <option<#if ("Dr." == (creditCard.titleOnCard)?default(""))> selected="selected"</#if>>${uiLabelMap.CommonTitleDr}</option>
    </select>
  </div>
</div>
<label class="required">${uiLabelMap.AccountingFirstNameCard}</label>
<div class="row">
  <div class="col-sm-6">
    <input type="text" class="form-control" maxlength="60" name="firstNameOnCard" value="${(creditCard.firstNameOnCard)!}"/>
  <#if showToolTip?has_content><span class="tooltip">${uiLabelMap.CommonRequired}</span></#if></td>
  </div>
</div>
<label>${uiLabelMap.AccountingMiddleNameCard}</label>
<div class="row">
  <div class="col-sm-6">
    <input type="text" class="form-control" maxlength="60" name="middleNameOnCard" value="${(creditCard.middleNameOnCard)!}"/>
  </div>
</div>
<label class="required">${uiLabelMap.AccountingLastNameCard}</label>
<div class="row">
  <div class="col-sm-6">
    <input type="text" class="form-control" maxlength="60" name="lastNameOnCard" value="${(creditCard.lastNameOnCard)!}"/>
  <#if showToolTip?has_content><span class="tooltip">${uiLabelMap.CommonRequired}</span></#if>
  </div>
</div>
<label>${uiLabelMap.AccountingSuffixCard}</label>
<div class="row">
  <div class="col-sm-6">
    <select name="suffixOnCard" class="form-control custom-select">
      <option value="">${uiLabelMap.CommonSelectOne}</option>
      <option<#if ("Jr." == (creditCard.suffixOnCard)?default(""))> selected="selected"</#if>>Jr.</option>
      <option<#if ("Sr." == (creditCard.suffixOnCard)?default(""))> selected="selected"</#if>>Sr.</option>
      <option<#if ("I" == (creditCard.suffixOnCard)?default(""))> selected="selected"</#if>>I</option>
      <option<#if ("II" == (creditCard.suffixOnCard)?default(""))> selected="selected"</#if>>II</option>
      <option<#if ("III" == (creditCard.suffixOnCard)?default(""))> selected="selected"</#if>>III</option>
      <option<#if ("IV" == (creditCard.suffixOnCard)?default(""))> selected="selected"</#if>>IV</option>
      <option<#if ("V" == (creditCard.suffixOnCard)?default(""))> selected="selected"</#if>>V</option>
    </select>
  </div>
</div>
<label class="required">${uiLabelMap.AccountingCardType}</label>
<div class="row">
  <div class="col-sm-6">
    <select name="cardType" class="custom-select form-control">
    <#if creditCard.cardType??>
      <option>${creditCard.cardType}</option>
      <option value="${creditCard.cardType}">---</option>
    </#if>
    ${screens.render("component://common/widget/CommonScreens.xml#cctypes")}
    </select>
  <#if showToolTip?has_content><span class="tooltip">${uiLabelMap.CommonRequired}</span></#if>
  </div>
</div>
<label class="required">${uiLabelMap.AccountingCardNumber}</label>
<div class="row">
  <div class="col-sm-6">
  <#if creditCard?has_content>
    <#if cardNumberMinDisplay?has_content>
    <#-- create a display version of the card where all but the last four digits are * -->
      <#assign cardNumberDisplay = "">
      <#assign cardNumber = creditCard.cardNumber!>
      <#if cardNumber?has_content>
        <#assign size = cardNumber?length - 4>
        <#if (size > 0)>
          <#list 0 .. size-1 as foo>
            <#assign cardNumberDisplay = cardNumberDisplay + "*">
          </#list>
          <#assign cardNumberDisplay = cardNumberDisplay + cardNumber[size .. size + 3]>
        <#else>
        <#-- but if the card number has less than four digits (ie, it was entered incorrectly), display it in full -->
          <#assign cardNumberDisplay = cardNumber>
        </#if>
      </#if>
      <input type="text" class="required form-control"maxlength="30" name="cardNumber" onfocus="javascript:this.value = '';" value="${cardNumberDisplay!}" />
    <#else>
      <input type="text" class="form-control" maxlength="30" name="cardNumber" value="${creditCard.cardNumber!}"/>
    </#if>
  <#else>
    <input type="text" class="form-control" maxlength="30" name="cardNumber" value="${creditCard.cardNumber!}"/>
  </#if>
  <#if showToolTip?has_content><span class="tooltip">${uiLabelMap.CommonRequired}</span></#if>
  </div>
</div>
  <label class="required">${uiLabelMap.AccountingExpirationDate}</label>
  <#assign expMonth = "">
  <#assign expYear = "">
  <#if creditCard?? && creditCard.expireDate??>
    <#assign expDate = creditCard.expireDate>
    <#if (expDate?? && expDate.indexOf("/") > 0)>
      <#assign expMonth = expDate.substring(0,expDate.indexOf("/"))>
      <#assign expYear = expDate.substring(expDate.indexOf("/")+1)>
    </#if>
  </#if>
<div class="row">
  <div class="col-sm-3">
  <select name="expMonth" class="custom-select form-control">
    <option>${uiLabelMap.CommonSelect}</option>
    <#if creditCard?has_content && expMonth?has_content>
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
    <select name="expYear" class="custom-select form-control">
      <option>${uiLabelMap.CommonSelect}</option>
    <#if creditCard?has_content && expYear?has_content>
      <#assign ccExprYear = expYear>
    <#else>
      <#assign ccExprYear = requestParameters.expYear!>
    </#if>
    <#if ccExprYear?has_content>
      <option value="${ccExprYear!}">${ccExprYear!}</option>
    </#if>
    ${screens.render("component://common/widget/CommonScreens.xml#ccyears")}
    </select>
  <#if showToolTip?has_content><span class="tooltip">${uiLabelMap.CommonRequired}</span></#if>
</div>
</div>
<label>${uiLabelMap.CommonDescription}</label>
<div class="row">
  <div class="col-sm-6">
    <input type="text" size="20" class="form-control" name="description" value="${paymentMethod.description!}"/>
  </div>
</div>
