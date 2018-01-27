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
    <strong>${uiLabelMap.CommonContactUs}</strong>
  </div>
  <script type="text/javascript" language="JavaScript">
    <!--
    function reloadCaptcha(fieldName) {
      var captchaUri = "<@ofbizUrl>captcha.jpg?captchaCodeId=" + fieldName + "&amp;unique=_PLACEHOLDER_</@ofbizUrl>";
      var unique = Date.now();
      captchaUri = captchaUri.replace("_PLACEHOLDER_", unique);
      document.getElementById(fieldName).src = captchaUri;
    }
    //-->
  </script>
  <div class="card-body">
    <form id="contactForm" method="post" action="<@ofbizUrl>submitAnonContact</@ofbizUrl>">
      <input type="hidden" name="partyIdFrom" value="${(userLogin.partyId)!}"/>
      <input type="hidden" name="partyIdTo" value="${productStore.payToPartyId!}"/>
      <input type="hidden" name="contactMechTypeId" value="WEB_ADDRESS"/>
      <input type="hidden" name="communicationEventTypeId" value="WEB_SITE_COMMUNICATI"/>
      <input type="hidden" name="productStoreId" value="${productStore.productStoreId}"/>
      <input type="hidden" name="emailType" value="CONT_NOTI_EMAIL"/>

      <div class="form-group row">
        <label for="${uiLabelMap.EcommerceSubject}" class="col-2 col-form-label">${uiLabelMap.EcommerceSubject}</label>
        <div class="col-10">
          <input type="text" name="subject" id="subject" class="required form-control form-control-sm" value="${requestParameters.subject!}"/>
        </div>
      </div>
      <div class="form-group row">
        <label for="${uiLabelMap.CommonMessage}" class="col-2 col-form-label">${uiLabelMap.CommonMessage}</label>
        <div class="col-10">
          <textarea name="content" id="message" class="required form-control form-control-sm" rows="4">
            ${requestParameters.content!}
          </textarea>
        </div>
      </div>
      <div class="form-group row">
        <label for="${uiLabelMap.FormFieldTitle_emailAddress}" class="col-2 col-form-label">${uiLabelMap.FormFieldTitle_emailAddress}</label>
        <div class="col-10">
           <input type="email" name="emailAddress" id="emailAddress" class="required form-control form-control-sm" value="${requestParameters.emailAddress!}"/>
        </div>
      </div>
      <div class="row">
        <div class="col-6">
          <div class="form-group row">
            <label for="${uiLabelMap.PartyFirstName}" class="col-4 col-form-label">${uiLabelMap.PartyFirstName}</label>
            <div class="col-8">
              <input type="text" name="firstName" id="firstName" class="required form-control form-control-sm" value="${requestParameters.firstName!}"/>
            </div>
          </div>
        </div>
        <div class="col-6">
          <div class="form-group row">
            <label for="${uiLabelMap.PartyLastName}" class="col-4 col-form-label">${uiLabelMap.PartyLastName}</label>
            <div class="col-8">
              <input type="text" name="lastName" id="lastName" class="required form-control form-control-sm" value="${requestParameters.lastName!}"/>
            </div>
          </div>
        </div>
      </div>
      <div class="form-group row">
        <label for="${uiLabelMap.CommonCaptchaCode}" class="col-2 col-form-label">${uiLabelMap.CommonCaptchaCode}</label>
        <div class="col-10">
          <img id="captchaImage" src="<@ofbizUrl>captcha.jpg?captchaCodeId=captchaImage&amp;unique=${nowTimestamp.getTime()}</@ofbizUrl>" alt=""/>
        </div>
      </div>
      <div class="row">
        <div class="col-2"></div>
        <div class="col-10">
          <a href="javascript:reloadCaptcha('captchaImage');">${uiLabelMap.CommonReloadCaptchaCode}</a>
        </div>
      </div>
      <div class="form-group row">
        <label for="${uiLabelMap.CommonVerifyCaptchaCode}" class="col-2 col-form-label">${uiLabelMap.CommonVerifyCaptchaCode}</label>
        <div class="col-10">
          <input type="text" autocomplete="off" maxlength="30" size="23" name="captcha" class="form-control form-control-sm"/>
        </div>
      </div>
      <div class="row">
        <div class="col-12">
          <input type="submit" value="${uiLabelMap.CommonSubmit}" class="btn btn-outline-secondary"/>
        </div>
      </div>
    </form>
  </div>
</div>
