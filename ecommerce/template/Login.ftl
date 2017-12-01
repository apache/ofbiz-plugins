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
<#assign janrainEnabled = Static["org.apache.ofbiz.entity.util.EntityUtilProperties"].getPropertyValue("ecommerce", "janrain.enabled", delegator)>
<#assign appName = Static["org.apache.ofbiz.entity.util.EntityUtilProperties"].getPropertyValue("ecommerce", "janrain.appName", delegator)>
<#if "Y" == janrainEnabled>
<script type="text/javascript">
(function() {
    if (typeof window.janrain !== 'object') window.janrain = {};
    window.janrain.settings = {};
    
    janrain.settings.tokenUrl = '<@ofbizUrl fullPath="true" secure="true">janrainCheckLogin</@ofbizUrl>';

    function isReady() { janrain.ready = true; };
    if (document.addEventListener) {
      document.addEventListener("DOMContentLoaded", isReady, false);
    } else {
      window.attachEvent('onload', isReady);
    }

    var e = document.createElement('script');
    e.type = 'text/javascript';
    e.id = 'janrainAuthWidget';

    if (document.location.protocol === 'https:') {
      e.src = 'https://rpxnow.com/js/lib/${appName}/engage.js';
    } else {
      e.src = 'http://widget-cdn.rpxnow.com/js/lib/${appName}/engage.js';
    }

    var s = document.getElementsByTagName('script')[0];
    s.parentNode.insertBefore(e, s);
})();
</script>


<div class="d-flex justify-content-center">
  <div class="card p-6">
    <div class="card-header">
      <h3>${uiLabelMap.CommonRegistered}</h3>
    </div>
    <div class="card-block p-1 m-2">
      <form method="post" action="<@ofbizUrl>login</@ofbizUrl>" name="loginform">
        <div class="form-group">
          <label for="userName">${uiLabelMap.CommonUsername}</label>
          <input type="text" class="form-control" id="userName" name="USERNAME" value="<#if requestParameters.USERNAME?has_content>${requestParameters.USERNAME}<#elseif autoUserLogin?has_content>${autoUserLogin.userLoginId}</#if>"/>
          <#if autoUserLogin?has_content>
            <p>(${uiLabelMap.CommonNot} ${autoUserLogin.userLoginId}? <a href="<@ofbizUrl>${autoLogoutUrl}</@ofbizUrl>">${uiLabelMap.CommonClickHere}</a>)</p>
          </#if>
        </div>
        <div class="form-group">
          <label for="password">${uiLabelMap.CommonPassword}</label>
          <input type="password" class="form-control" id="password" name="PASSWORD" autocomplete="off" value=""/>
        </div>
        <div class="form-group">
          <input type="submit" class="btn btn-primary btn-block"  value="${uiLabelMap.CommonLogin}"/>
        </div>
        <div class="form-group">
          <label for="newcustomer_submit">${uiLabelMap.CommonMayCreateNewAccountHere}:
            <a href="<@ofbizUrl>newcustomer</@ofbizUrl>">${uiLabelMap.CommonMayCreate}</a>
          </label>
        </div>
      </form>
      <div id="janrainEngageEmbed"></div>
    </div>
  </div>
</div>
<#else>
<div class="d-flex justify-content-center">
  <div class="card p-6">
    <div class="card-header">
      <h3>${uiLabelMap.CommonRegistered}</h3>
    </div>
    <div class="card-block p-1 m-2">
      <form method="post" action="<@ofbizUrl>login</@ofbizUrl>" name="loginform">
        <div class="form-group">
          <label for="userName">${uiLabelMap.CommonUsername}</label>
          <input type="text" class="form-control" id="userName" name="USERNAME" value="<#if requestParameters.USERNAME?has_content>${requestParameters.USERNAME}<#elseif autoUserLogin?has_content>${autoUserLogin.userLoginId}</#if>"/>
          <#if autoUserLogin?has_content>
            <p>(${uiLabelMap.CommonNot} ${autoUserLogin.userLoginId}? <a href="<@ofbizUrl>${autoLogoutUrl}</@ofbizUrl>">${uiLabelMap.CommonClickHere}</a>)</p>
          </#if>
        </div>
        <div class="form-group">
          <label for="password">${uiLabelMap.CommonPassword}</label>
          <input type="password" class="form-control" id="password" name="PASSWORD" autocomplete="off" value=""/>
        </div>
        <div class="form-group">
          <input type="submit" class="btn btn-primary btn-block " value="${uiLabelMap.CommonLogin}"/>
        </div>
        <div class="form-group">
          <label for="newcustomer_submit">${uiLabelMap.CommonMayCreateNewAccountHere}:
            <a href="<@ofbizUrl>newcustomer</@ofbizUrl>">${uiLabelMap.CommonMayCreate}</a>
          </label>
        </div>
      </form>
    </div>
  </div>
</div>

<div class="d-flex justify-content-center">
  <a data-toggle="collapse" href="#collapseForm" aria-expanded="false" aria-controls="collapseForm">
    ${uiLabelMap.CommonForgotYourPassword}
  </a>
</div>
<div class="collapse" id="collapseForm">
  <div class="d-flex justify-content-center">
    <div class="card">
      <div class="card-header">
        <h3>${uiLabelMap.CommonForgotYourPassword}</h3>
      </div>
      <div class="card-block p-1 m-2">
        <form method="post" action="<@ofbizUrl>forgotpassword</@ofbizUrl>">
            <div class="form-group">
              <label for="forgotpassword_userName">${uiLabelMap.CommonUsername}</label>
              <input type="text" class="form-control" id="forgotpassword_userName" name="USERNAME" value="<#if requestParameters.USERNAME?has_content>${requestParameters.USERNAME}<#elseif autoUserLogin?has_content>${autoUserLogin.userLoginId}</#if>"/>
            </div>
            <div class="form-group">
              <input type="submit" class="btn btn-outline-secondary" name="GET_PASSWORD_HINT" value="${uiLabelMap.CommonGetPasswordHint}"/>
              <input type="submit" class="btn btn-outline-secondary" name="EMAIL_PASSWORD" value="${uiLabelMap.CommonEmailPassword}"/>
            </div>
        </form>
      </div>
    </div>
  </div>
</div>
</#if>

<div class="endcolumns">&nbsp;</div>

<script language="JavaScript" type="text/javascript">
  <#if autoUserLogin?has_content>document.loginform.PASSWORD.focus();</#if>
  <#if !autoUserLogin?has_content>document.loginform.USERNAME.focus();</#if>
</script>
