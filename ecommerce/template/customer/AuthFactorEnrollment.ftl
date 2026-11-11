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

<div class="d-flex justify-content-center my-4">
  <div class="card" style="max-width: 560px; width: 100%;">
    <div class="card-header">
      <h3>Set Up Authenticator</h3>
    </div>
    <div class="card-body">
      <p class="text-muted">Scan this QR code using Google Authenticator, Microsoft Authenticator, or 2FAS on your mobile device.</p>

      <#if mfaQrCodeDataUri?has_content>
        <div class="text-center my-3">
          <img src="${mfaQrCodeDataUri}" width="200" height="200" alt="QR Code" class="img-thumbnail" />
        </div>
      </#if>

      <#if mfaSecret?has_content>
        <div class="form-group">
          <label>Manual Entry Key</label>
          <input type="text" class="form-control" value="${mfaSecret}" readonly />
          <small class="form-text text-muted">If you cannot scan the QR code, enter this key manually into your authenticator app.</small>
        </div>
      </#if>

      <#assign confirmAction = isForcedEnrollment?default(false)?then("confirmForcedTotpEnrollment", "confirmTotpEnrollment")/>
      <form method="post" action="<@ofbizUrl>${confirmAction}</@ofbizUrl>" name="confirmEnrollmentForm">
        <input type="hidden" name="factorId" value="${mfaFactorId!}"/>

        <div class="form-group">
          <label for="enrollCode">Verification Code</label>
          <input type="text" class="form-control" id="enrollCode" name="code" autocomplete="off" placeholder="123456" maxlength="6" autofocus required />
          <small class="form-text text-muted">Enter the 6-digit code displayed in your authenticator app to activate.</small>
        </div>

        <div class="form-group">
          <input type="submit" class="btn btn-primary btn-block" value="Activate Authenticator" />
        </div>
        <div class="form-group text-center">
          <a href="<@ofbizUrl>authFactorSetup</@ofbizUrl>">${uiLabelMap.CommonCancel}</a>
        </div>
      </form>
    </div>
  </div>
</div>
