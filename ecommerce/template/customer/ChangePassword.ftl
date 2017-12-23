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
<div class="card">
  <div class="card-header">
    <h3>${uiLabelMap.PartyChangePassword}</h3>
  </div>
  <div class="card-body">
    <a id="CommonGoBack1" href="<@ofbizUrl>${donePage}</@ofbizUrl>" class="btn btn-outline-secondary">${uiLabelMap.CommonGoBack}</a>
    <a id="CommonSave1" href="javascript:document.getElementById('changepasswordform').submit()" class="btn btn-outline-secondary">
    ${uiLabelMap.CommonSave}
    </a>
    <form id="changepasswordform" method="post" action="<@ofbizUrl>updatePassword/${donePage}</@ofbizUrl>">
          <label class="mt-4 asteriskInput" for="currentPassword">${uiLabelMap.PartyOldPassword}</label>
          <div class="row">
            <div class="col-sm-6">
              <input type="password" class="form-control" name="currentPassword" autocomplete="off" id="currentPassword"/>
            </div>
          </div>
          <label  class="required" for="newPassword">${uiLabelMap.PartyNewPassword}</label>
          <div class="row">
            <div class="col-sm-6">
              <input type="password" class="form-control" name="newPassword" autocomplete="off" id="newPassword"/>
            </div>
          </div>
          <label class="required" for="newPasswordVerify">${uiLabelMap.PartyNewPasswordVerify}</label>
          <div class="row">
            <div class="col-sm-6">
                <input type="password" class="form-control" name="newPasswordVerify" autocomplete="off" id="newPasswordVerify"/>
            </div>
          </div>
          <label class="required" for="passwordHint">${uiLabelMap.PartyPasswordHint}</label>
          <div class="row">
            <div class="col-sm-6">
                <input type="text" class="form-control" name="passwordHint" id="passwordHint" value="${userLoginData.passwordHint!}"/>
            </div>
          </div>
        <div class="form-group">
          <label>${uiLabelMap.CommonFieldsMarkedAreRequired}</label>
        </div>
    </form>
    <a href="<@ofbizUrl>${donePage}</@ofbizUrl>" class="btn btn-outline-secondary">${uiLabelMap.CommonGoBack}</a>
    <a href="javascript:document.getElementById('changepasswordform').submit()" class="btn btn-outline-secondary">
      ${uiLabelMap.CommonSave}
    </a>
  </div>
</div>
