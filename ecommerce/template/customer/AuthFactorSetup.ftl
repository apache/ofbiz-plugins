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

<#assign mfaFactors = EntityQuery.use(delegator).from("UserLoginAuthFactor")
    .where("userLoginId", userLogin.userLoginId, "thruDate", null, "needsValidation", "N")
    .orderBy("-fromDate")
    .queryList()! />

<div class="card mb-4">
  <div class="card-header d-flex justify-content-between align-items-center">
    <h3 class="mb-0">Multi-Factor Authentication</h3>
    <a href="<@ofbizUrl>viewprofile</@ofbizUrl>" class="btn btn-outline-secondary btn-sm">${uiLabelMap.CommonGoBack}</a>
  </div>
  <div class="card-body">
    <#include "component://common-theme/template/includes/Messages.ftl"/>
    <p class="text-muted">Manage time-based one-time password (TOTP) authenticators for your account.</p>

    <#-- Add Authenticator Form -->
    <div class="card mb-4">
      <div class="card-header">
        <strong>Add Authenticator</strong>
      </div>
      <div class="card-body">
        <form method="post" action="<@ofbizUrl>beginTotpEnrollment</@ofbizUrl>" name="startMfaEnrollmentForm">
          <div class="row">
            <div class="col-md-5">
              <div class="form-group">
                <label for="label">Device Label</label>
                <input type="text" name="label" id="label" class="form-control" value="Authenticator" required />
                <small class="form-text text-muted">A name to identify this device (e.g. Mobile Phone).</small>
              </div>
            </div>
            <div class="col-md-5">
              <div class="form-group">
                <label for="currentPassword">${uiLabelMap.CommonCurrentPassword}</label>
                <input type="password" name="currentPassword" id="currentPassword" class="form-control" autocomplete="current-password" required />
                <small class="form-text text-muted">Your current login password is required to add an authenticator.</small>
              </div>
            </div>
            <div class="col-md-2 d-flex align-items-center">
              <input type="submit" class="btn btn-primary btn-block mt-1" value="Add" />
            </div>
          </div>
        </form>
      </div>
    </div>

    <#-- Active Authenticators Table -->
    <div class="card">
      <div class="card-header">
        <strong>Active Authenticators</strong>
      </div>
      <div class="card-body">
        <#if mfaFactors?has_content>
          <div class="table-responsive">
            <table class="table table-bordered table-striped mb-0">
              <thead>
                <tr>
                  <th>Device Label</th>
                  <th>Type</th>
                  <th>Enrolled Date</th>
                  <th>Remove Authenticator</th>
                </tr>
              </thead>
              <tbody>
                <#list mfaFactors as factor>
                  <tr>
                    <td><strong>${factor.factorLabel!"Authenticator"}</strong></td>
                    <td>TOTP</td>
                    <td>${factor.fromDate?string("yyyy-MM-dd HH:mm")!}</td>
                    <td>
                      <form method="post" action="<@ofbizUrl>revokeMfaFactor</@ofbizUrl>" class="form-inline">
                        <input type="hidden" name="factorId" value="${factor.factorId}"/>
                        <div class="input-group input-group-sm">
                          <input type="password" name="currentPassword" class="form-control" placeholder="Current password" required autocomplete="current-password"/>
                          <div class="input-group-append">
                            <input type="submit" class="btn btn-danger btn-sm" value="Revoke" />
                          </div>
                        </div>
                      </form>
                    </td>
                  </tr>
                </#list>
              </tbody>
            </table>
          </div>
        <#else>
          <p class="text-muted mb-0">No authenticators currently enrolled.</p>
        </#if>
      </div>
    </div>

  </div>
</div>
