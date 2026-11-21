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

<#assign primaryFactors = [] />
<#assign backupCodesCount = 0 />
<#list mfaFactors as factor>
  <#if factor.factorType == "ULAF_BACKUP">
    <#assign backupCodesCount = backupCodesCount + 1 />
  <#else>
    <#assign primaryFactors = primaryFactors + [factor] />
  </#if>
</#list>

<div class="card mb-4">
  <div class="card-header d-flex justify-content-between align-items-center">
    <h3 class="mb-0">${uiLabelMap.EcommerceMfaTitle}</h3>
    <a href="<@ofbizUrl>viewprofile</@ofbizUrl>" class="btn btn-outline-secondary btn-sm">${uiLabelMap.CommonGoBack}</a>
  </div>
  <div class="card-body">
    <#include "component://common-theme/template/includes/Messages.ftl"/>
    <p class="text-muted">${uiLabelMap.EcommerceMfaManageDescription}</p>

    <#-- One-time display of freshly generated backup recovery codes -->
    <#assign flashBackupCodes = (session.getAttribute("mfaBackupCodes"))! />
    <#if flashBackupCodes?has_content>
      <div class="alert alert-warning border-warning shadow-sm mb-4" role="alert">
        <h4 class="alert-heading text-dark font-weight-bold mb-2">
          ${uiLabelMap.EcommerceMfaBackupCodesGeneratedTitle}
        </h4>
        <p class="text-dark mb-3">
          <strong>${uiLabelMap.EcommerceMfaBackupCodesWarning}</strong>
        </p>
        <div class="p-3 bg-light rounded border text-monospace mb-3" style="font-size: 1.15rem; letter-spacing: 1px;">
          <div class="row">
            <#list flashBackupCodes as bCode>
              <div class="col-6 col-md-3 py-1 text-center font-weight-bold text-dark">${bCode}</div>
            </#list>
          </div>
        </div>
        <#-- Rendered as normal (singly) HTML-escaped text, then read back via textContent at copy time:
             keeps the "copied" confirmation correct regardless of the page's auto-escaping policy,
             instead of re-escaping it a second time for inlining into the onclick attribute below. -->
        <span id="mfaEcommerceBackupCodesCopiedLabel" class="d-none">${uiLabelMap.EcommerceMfaBackupCodesCopied}</span>
        <div>
          <button type="button" class="btn btn-dark btn-sm mr-2" onclick="navigator.clipboard.writeText('${flashBackupCodes?join('\n')?js_string}'); alert(document.getElementById('mfaEcommerceBackupCodesCopiedLabel').textContent);">
            ${uiLabelMap.EcommerceMfaCopyCodes}
          </button>
          <button type="button" class="btn btn-outline-dark btn-sm" onclick="window.print();">
            ${uiLabelMap.EcommerceMfaPrintCodes}
          </button>
        </div>
      </div>
      <#-- Remove from session so plain-text codes are only displayed once -->
      <#assign dummy = session.removeAttribute("mfaBackupCodes")! />
    </#if>

    <#-- Add Authenticator Form -->
    <div class="card mb-4">
      <div class="card-header">
        <strong>${uiLabelMap.EcommerceMfaAddAuthenticator}</strong>
      </div>
      <div class="card-body">
        <form method="post" action="<@ofbizUrl>beginTotpEnrollment</@ofbizUrl>" name="startMfaEnrollmentForm">
          <div class="row">
            <div class="col-md-5">
              <div class="form-group">
                <label for="label">${uiLabelMap.EcommerceMfaDeviceLabel}</label>
                <input type="text" name="label" id="label" class="form-control" value="Authenticator" required />
                <small class="form-text text-muted">${uiLabelMap.EcommerceMfaDeviceLabelHelp}</small>
              </div>
            </div>
            <div class="col-md-5">
              <div class="form-group">
                <label for="currentPassword">${uiLabelMap.CommonCurrentPassword}</label>
                <input type="password" name="currentPassword" id="currentPassword" class="form-control" autocomplete="current-password" required />
                <small class="form-text text-muted">${uiLabelMap.EcommerceMfaCurrentPasswordRequiredHelp}</small>
              </div>
            </div>
            <div class="col-md-2 d-flex align-items-center">
              <input type="submit" class="btn btn-primary btn-block mt-1" value="${uiLabelMap.CommonAdd}" />
            </div>
          </div>
        </form>
      </div>
    </div>

    <#-- Active Authenticators Table -->
    <div class="card mb-4">
      <div class="card-header">
        <strong>${uiLabelMap.EcommerceMfaActiveAuthenticators}</strong>
      </div>
      <div class="card-body">
        <#if primaryFactors?has_content>
          <div class="table-responsive">
            <table class="table table-bordered table-striped mb-0">
              <thead>
                <tr>
                  <th>${uiLabelMap.EcommerceMfaDeviceLabel}</th>
                  <th>${uiLabelMap.CommonType}</th>
                  <th>${uiLabelMap.EcommerceMfaEnrolledDate}</th>
                  <th>${uiLabelMap.EcommerceMfaRemoveAuthenticator}</th>
                </tr>
              </thead>
              <tbody>
                <#list primaryFactors as factor>
                  <tr>
                    <td><strong>${factor.factorLabel!"Authenticator"}</strong></td>
                    <td>${(factor.factorType == "ULAF_TOTP")?then("TOTP", factor.factorType)}</td>
                    <td>${factor.fromDate?string("yyyy-MM-dd HH:mm")!}</td>
                    <td>
                      <form method="post" action="<@ofbizUrl>revokeMfaFactor</@ofbizUrl>" class="form-inline">
                        <input type="hidden" name="factorId" value="${factor.factorId}"/>
                        <div class="input-group input-group-sm">
                          <input type="password" name="currentPassword" class="form-control" placeholder="${uiLabelMap.CommonCurrentPassword}" required autocomplete="current-password"/>
                          <div class="input-group-append">
                            <input type="submit" class="btn btn-danger btn-sm" value="${uiLabelMap.EcommerceMfaRevoke}" />
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
          <p class="text-muted mb-0">${uiLabelMap.EcommerceMfaNoAuthenticators}</p>
        </#if>
      </div>
    </div>

    <#-- Backup Recovery Codes Card -->
    <div class="card">
      <div class="card-header d-flex justify-content-between align-items-center">
        <strong>${uiLabelMap.EcommerceMfaBackupCodesTitle}</strong>
        <span class="badge badge-${(backupCodesCount > 2)?then('success', (backupCodesCount > 0)?then('warning', 'danger'))} p-2">
          ${backupCodesCount} ${uiLabelMap.EcommerceMfaBackupCodesRemaining}
        </span>
      </div>
      <div class="card-body">
        <p class="text-muted mb-3">${uiLabelMap.EcommerceMfaBackupCodesDescription}</p>
        <form method="post" action="<@ofbizUrl>generateBackupCodes</@ofbizUrl>" class="form-inline">
          <div class="form-group mr-2 mb-2">
            <label for="generateBackupPassword" class="sr-only">${uiLabelMap.CommonCurrentPassword}</label>
            <input type="password" name="currentPassword" id="generateBackupPassword" class="form-control form-control-sm" placeholder="${uiLabelMap.CommonCurrentPassword}" required autocomplete="current-password" />
          </div>
          <input type="submit" class="btn btn-outline-secondary btn-sm mb-2" value="${uiLabelMap.EcommerceMfaGenerateNewBackupCodes}" onclick="return confirm('${uiLabelMap.EcommerceMfaRegenerateWarning}');" />
        </form>
      </div>
    </div>

  </div>
</div>
