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

<!-- Global IE fix to avoid layout crash when single word size wider than column width -->
<!--[if IE]><style type="text/css"> body {word-wrap: break-word;}</style><![endif]-->

  <div id="wait-spinner" style="display:none">
    <div id="wait-spinner-image"></div>
  </div>
  <div class="container-fluid">
    <div class="header">

      <div class="header-top">
        <div class="row align-items-center">
          <div class="col">
            <a class="navbar-brand" href="<@ofbizUrl>main</@ofbizUrl>">
              <img src="/multiflex/images/ofbizLogo.gif" alt="Logo" height="32px"/>
            </a>
          </div>
        </div>

        <div class="col text-center d-none d-lg-block">
          <#if !productStore??>
            <h3><a href="<@ofbizUrl>main</@ofbizUrl>" title="Go to Start page">${uiLabelMap.EcommerceNoProductStore}</a></h3>
          </#if>
          <#if (productStore.title)??>
            <h3><a href="<@ofbizUrl>main</@ofbizUrl>" title="Go to Start page">${productStore.title}</a></h3>
           </#if>
          <#if (productStore.subtitle)??>
            <div id="company-subtitle"><h6>${productStore.subtitle}</h6></div>
          </#if>
        </div>
      </div>

      <!-- A.2 HEADER MIDDLE -->
      <div class="header-middle">
        <div class="row">
          <div class="col-3 align-left">
            <h4>EASY &bull; FLEXIBLE &bull; ROBUST</h4>
          </div>
          <div class="col-3 offset-6">
          <span class="message">
           <#if sessionAttributes.autoName?has_content>
              ${uiLabelMap.CommonWelcome}&nbsp;${sessionAttributes.autoName}!
              (${uiLabelMap.CommonNotYou}?&nbsp;<a href="<@ofbizUrl>autoLogout</@ofbizUrl>" class="buttontext">${uiLabelMap.CommonClickHere}</a>)
            <#else>
              ${uiLabelMap.CommonWelcome}!
            </#if>
          </span>

            <h5><a href="#">&rsaquo;&rsaquo;&nbsp;More details</a></h5>
          </div>
        </div>
      </div>

      <!-- A.3 HEADER BOTTOM -->
      <div class="header-bottom">

        <!-- Navigation Level 2 (Drop-down menus) -->
        <div class="nav2">

          <#if userLogin?has_content && userLogin.userLoginId != "anonymous">
            <!-- Navigation item -->
            <ul>
              <li><a href="<@ofbizUrl>logout</@ofbizUrl>">${uiLabelMap.CommonLogout}</a></li>
            </ul>
          <#else>
            <!-- Navigation item -->
            <ul>
              <li><a href="<@ofbizUrl>${checkLoginUrl}</@ofbizUrl>">${uiLabelMap.CommonLogin}</a></li>
            </ul>

            <!-- Navigation item -->
            <ul>
              <li><a href="<@ofbizUrl>newcustomer</@ofbizUrl>">${uiLabelMap.EcommerceRegister}</a></li>
            </ul>
          </#if>

          <#if catalogQuickaddUse?has_content && catalogQuickaddUse>
            <!-- Navigation item -->
            <ul>
              <li id="header-bar-quickadd"><a href="<@ofbizUrl>quickadd</@ofbizUrl>">${uiLabelMap.CommonQuickAdd}</a></li>
            </ul>
          </#if>
          <ul>
            <li><a href="<@ofbizUrl>main</@ofbizUrl>">${uiLabelMap.CommonMain}</a></li>
          </ul>
          <ul>
            <li><a href="<@ofbizUrl>contactus</@ofbizUrl>">${uiLabelMap.CommonContactUs}</a></li>
          </ul>
          <ul>
            <li><a href="<@ofbizUrl>policies</@ofbizUrl>">${uiLabelMap.EcommerceSeeStorePoliciesHere}</a></li>
          </ul>




          <#if userLogin?has_content && userLogin.userLoginId != "anonymous">
            <!-- Navigation item -->
            <ul>
              <li><a href="#">${uiLabelMap.EcommerceMyAccount}<!--[if IE 7]><!--></a><!--<![endif]-->
                <!--[if lte IE 6]><table><tr><td><![endif]-->
                  <ul>
                    <li id="header-bar-viewprofile"><a href="<@ofbizUrl>viewprofile</@ofbizUrl>">${uiLabelMap.CommonProfile}</a></li>
                    <li id="header-bar-ListQuotes"><a href="<@ofbizUrl>ListQuotes</@ofbizUrl>">${uiLabelMap.OrderOrderQuotes}</a></li>
                    <li id="header-bar-ListRequests"><a href="<@ofbizUrl>ListRequests</@ofbizUrl>">${uiLabelMap.OrderRequests}</a></li>
                    <li id="header-bar-editShoppingList"><a href="<@ofbizUrl>editShoppingList</@ofbizUrl>">${uiLabelMap.EcommerceShoppingLists}</a></li>
                    <li id="header-bar-orderhistory"><a href="<@ofbizUrl>orderhistory</@ofbizUrl>">${uiLabelMap.EcommerceOrderHistory}</a></li>
                  </ul>
                <!--[if lte IE 6]></td></tr></table></a><![endif]-->
              </li>
            </ul>
          </#if>

           <ul class="flags">
             <li><a href="<@ofbizUrl>setSessionLocale?newLocale=it</@ofbizUrl>"><img src="/multiflex/images/flag_it.gif" alt="" /></a></li>
              <li><a href="<@ofbizUrl>setSessionLocale?newLocale=en</@ofbizUrl>"><img src="/multiflex/images/flag_en.gif" alt="" /></a></li>
              <li><a href="<@ofbizUrl>setSessionLocale?newLocale=de</@ofbizUrl>"><img src="/multiflex/images/flag_de.gif" alt="" /></a></li>
              <li><a href="<@ofbizUrl>setSessionLocale?newLocale=fr</@ofbizUrl>"><img src="/multiflex/images/flag_fr.gif" alt="" /></a></li>
            </ul>

        </div>
      </div>