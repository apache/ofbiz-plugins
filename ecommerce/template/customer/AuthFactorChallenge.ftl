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
  <div class="card" style="max-width: 500px; width: 100%;">
    <div class="card-header">
      <h3>${uiLabelMap.EcommerceMfaChallengeTitle}</h3>
    </div>
    <div class="card-body">
      <p class="text-muted">${uiLabelMap.EcommerceMfaChallengeDescription}</p>
      <form method="post" action="<@ofbizUrl>verifyAuthFactor</@ofbizUrl>" name="mfaChallengeForm">
        <div class="form-group">
          <label for="code">${uiLabelMap.EcommerceMfaVerificationCode}</label>
          <input type="text" class="form-control" id="code" name="code" autocomplete="off" placeholder="${uiLabelMap.EcommerceMfaCodePlaceholder!'123456 or abcd-efgh'}" maxlength="16" autofocus required />
          <small class="form-text text-muted">${uiLabelMap.EcommerceMfaCodeOrBackupHelp!'Enter your 6-digit authenticator code or an 8-character backup recovery code.'}</small>
        </div>
        <div class="form-group">
          <input type="submit" class="btn btn-primary btn-block" value="${uiLabelMap.EcommerceMfaVerifyAndContinue}" />
        </div>
        <div class="form-group text-center">
          <a href="<@ofbizUrl>main</@ofbizUrl>">${uiLabelMap.CommonCancel}</a>
        </div>
      </form>
    </div>
  </div>
</div>
