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

<nav class="navbar navbar-light bg-light">
  <a class="navbar-brand" href="#">
    <#if sessionAttributes.overrideLogo??>
      <img src="<@ofbizContentUrl>${sessionAttributes.overrideLogo}</@ofbizContentUrl>" alt="Logo"/>
    <#elseif catalogHeaderLogo??>
      <img src="<@ofbizContentUrl>${catalogHeaderLogo}</@ofbizContentUrl>" alt="Logo"/>
    <#elseif layoutSettings.VT_HDR_IMAGE_URL?has_content>
      <img src="<@ofbizContentUrl>${layoutSettings.VT_HDR_IMAGE_URL}</@ofbizContentUrl>" alt="Logo"/>
    </#if>
  </a>
  <div class="navbar-text">
    <#if !productStore??>
      <h2>${uiLabelMap.EcommerceNoProductStore}</h2>
    </#if>
    <#if (productStore.title)??>
      <h2>${productStore.title}</h2>
    </#if>
    <#if (productStore.subtitle)??>
      <div id="company-subtitle">${productStore.subtitle}</div>
    </#if>
    <div id="welcome-message">
      <#if sessionAttributes.autoName?has_content>
        ${uiLabelMap.CommonWelcome}&nbsp;${sessionAttributes.autoName}!
        (${uiLabelMap.CommonNotYou}?&nbsp;
        <a href="<@ofbizUrl>autoLogout</@ofbizUrl>" class="linktext">${uiLabelMap.CommonClickHere}</a>)
      <#else>
        ${uiLabelMap.CommonWelcome}!
      </#if>
    </div>
  </div>
  <span class="navbar-text">
    ${screens.render("component://ecommerce/widget/CartScreens.xml#microcart")}
  </span>
</nav>

<div class="d-flex justify-content-end bd-highlight quick-links ml-2 mr-2">
  <#if userLogin?has_content && userLogin.userLoginId != "anonymous">
    <div class="p-2 bd-highlight">
      <a href="<@ofbizUrl>logout</@ofbizUrl>">${uiLabelMap.CommonLogout}</a>
    </div>
  <#else>
    <div class="p-2 bd-highlight">
      <a href="<@ofbizUrl>${checkLoginUrl}</@ofbizUrl>">${uiLabelMap.CommonLogin}</a>
    </div>
    <div class="p-2 bd-highlight">
      <a href="<@ofbizUrl>newcustomer</@ofbizUrl>">${uiLabelMap.EcommerceRegister}</a>
    </div>
  </#if>
  <div class="p-2 bd-highlight">
    <#if userLogin?has_content && userLogin.userLoginId != "anonymous">
      <a href="<@ofbizUrl>contactus</@ofbizUrl>">${uiLabelMap.CommonContactUs}</a>
    <#else>
      <a href="<@ofbizUrl>AnonContactus</@ofbizUrl>">${uiLabelMap.CommonContactUs}</a>
    </#if>
    </div>
  <div class="mr-auto p-2 bd-highlight">
    <a href="<@ofbizUrl>main</@ofbizUrl>">${uiLabelMap.CommonMain}</a>
  </div>

  <!-- NOTE: these are in reverse order because they are stacked right to left instead of left to right -->
  <#if !userLogin?has_content || (userLogin.userLoginId)! != "anonymous">
    <div class="p-2 bd-highlight">
      <a href="<@ofbizUrl>viewprofile</@ofbizUrl>">${uiLabelMap.CommonProfile}</a>
    </div>
    <div class="p-2 bd-highlight">
      <a href="<@ofbizUrl>messagelist</@ofbizUrl>">${uiLabelMap.CommonMessages}</a>
    </div>
    <div class="p-2 bd-highlight">
      <a href="<@ofbizUrl>ListQuotes</@ofbizUrl>">${uiLabelMap.OrderOrderQuotes}</a>
    </div>
    <div class="p-2 bd-highlight">
      <a href="<@ofbizUrl>ListRequests</@ofbizUrl>">${uiLabelMap.OrderRequests}</a>
    </div>
    <div class="p-2 bd-highlight">
      <a href="<@ofbizUrl>editShoppingList</@ofbizUrl>">${uiLabelMap.EcommerceShoppingLists}</a>
    </div>
    <div class="p-2 bd-highlight">
      <a href="<@ofbizUrl>orderhistory</@ofbizUrl>">${uiLabelMap.EcommerceOrderHistory}</a>
    </div>
  </#if>
  <#if catalogQuickaddUse>
    <div class="p-2 bd-highlight"><a href="<@ofbizUrl>quickadd</@ofbizUrl>">${uiLabelMap.CommonQuickAdd}</a></div>
  </#if>
</div>