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

<#if getUsername>
<script type="text/javascript">
  //<![CDATA[
  lastFocusedName = null;
  function setLastFocused(formElement) {
    lastFocusedName = formElement.name;
    document.write.lastFocusedName;
  }
  function clickUsername() {
    if (document.getElementById('UNUSEEMAIL').checked) {
      if ("UNUSEEMAIL" == lastFocusedName) {
        jQuery('#PASSWORD').focus();
      } else if ("PASSWORD" == lastFocusedName) {
        jQuery('#UNUSEEMAIL').focus();
      } else {
        jQuery('#PASSWORD').focus();
      }
    }
  }
  function changeEmail() {
    if (document.getElementById('UNUSEEMAIL').checked) {
      document.getElementById('USERNAME').value = jQuery('#CUSTOMER_EMAIL').val();
    }
  }
  function setEmailUsername() {
    if (document.getElementById('UNUSEEMAIL').checked) {
      document.getElementById('USERNAME').value = jQuery('#CUSTOMER_EMAIL').val();
      // don't disable, make the browser not submit the field: document.getElementById('USERNAME').disabled=true;
    } else {
      document.getElementById('USERNAME').value = '';
      // document.getElementById('USERNAME').disabled=false;
    }
  }
  function hideShowUsaStates() {
    var customerStateElement = document.getElementById('newuserform_stateProvinceGeoId');
    var customerCountryElement = document.getElementById('newuserform_countryGeoId');
    if ("USA" == customerCountryElement.value || "UMI" == customerCountryElement.value) {
      customerStateElement.style.display = "block";
    } else {
      customerStateElement.style.display = "none";
    }
  }
  //]]>
</script>
</#if>

<#------------------------------------------------------------------------------
NOTE: all page headings should start with an h2 tag, not an H1 tag, as 
there should generally always only be one h1 tag on the page and that 
will generally always be reserved for the logo at the top of the page.
------------------------------------------------------------------------------->
<div class="d-flex justify-content-center">
  <h2>${uiLabelMap.PartyRequestNewAccount}</h2>
</div>
<div class="d-flex justify-content-center">
  <h3>
    ${uiLabelMap.PartyAlreadyHaveAccount},
    <a href='<@ofbizUrl>checkLogin/main</@ofbizUrl>'>${uiLabelMap.CommonLoginHere}</a>
  </h3>
</div>

<#macro fieldErrors fieldName>
  <#if errorMessageList?has_content>
    <#assign fieldMessages =
        Static["org.apache.ofbiz.base.util.MessageString"].getMessagesForField(fieldName, true, errorMessageList)>
      <ul>
        <#list fieldMessages as errorMsg>
          <li class="errorMessage">${errorMsg}</li>
        </#list>
      </ul>
  </#if>
</#macro>
<#macro fieldErrorsMulti fieldName1 fieldName2 fieldName3 fieldName4>
  <#if errorMessageList?has_content>
    <#assign fieldMessages =
        Static["org.apache.ofbiz.base.util.MessageString"].getMessagesForField(fieldName1, fieldName2,
        fieldName3, fieldName4, true, errorMessageList)>
  <ul>
    <#list fieldMessages as errorMsg>
      <li class="errorMessage">${errorMsg}</li>
    </#list>
  </ul>
  </#if>
</#macro>
<div class="d-flex justify-content-center">
  <a href="<@ofbizUrl>${donePage}</@ofbizUrl>" class="btn btn-outline-secondary btn-sm">${uiLabelMap.CommonCancel}</a>&nbsp;
  <a href="javascript:document.getElementById('newuserform').submit()" class="btn btn-outline-secondary btn-sm">${uiLabelMap.CommonSave}</a>
</div>
<div class="d-flex justify-content-center">
<div class="card p-2 m-3">

<form method="post" action="<@ofbizUrl>createcustomer${previousParams}</@ofbizUrl>" id="newuserform" name="newuserform">

  <div class="card-block">
    ${uiLabelMap.CommonFieldsMarkedAreRequired}
  </div>

<div class="row">
  <div class="col-6">
    <fieldset>
      <legend>${uiLabelMap.PartyFullName}</legend>
      <input type="hidden" name="emailProductStoreId" value="${productStoreId}"/>
      <div class="row form-group">
        <div class="col-12">
          <label for="USER_TITLE">${uiLabelMap.CommonTitle}</label>
          <@fieldErrors fieldName="USER_TITLE"/>
          <select name="USER_TITLE" class="form-control form-control-sm">
          <#if requestParameters.USER_TITLE?has_content >
            <option>${requestParameters.USER_TITLE}</option>
            <option value="${requestParameters.USER_TITLE}"> --</option>
          <#else>
            <option value="">${uiLabelMap.CommonSelectOne}</option>
          </#if>
            <option>${uiLabelMap.CommonTitleMr}</option>
            <option>${uiLabelMap.CommonTitleMrs}</option>
            <option>${uiLabelMap.CommonTitleMs}</option>
            <option>${uiLabelMap.CommonTitleDr}</option>
          </select>
        </div>
      </div>

      <div class="row form-group">
        <div class="col-12">
          <label for="USER_FIRST_NAME">${uiLabelMap.PartyFirstName}*</label>
          <@fieldErrors fieldName="USER_FIRST_NAME"/>
          <input type="text" name="USER_FIRST_NAME" id="USER_FIRST_NAME" value="${requestParameters.USER_FIRST_NAME!}" class="form-control form-control-sm"/>
        </div>
      </div>

      <div class="row form-group">
        <div class="col-12">
          <label for="USER_MIDDLE_NAME">${uiLabelMap.PartyMiddleInitial}</label>
          <@fieldErrors fieldName="USER_MIDDLE_NAME"/>
          <input type="text" name="USER_MIDDLE_NAME" id="USER_MIDDLE_NAME" value="${requestParameters.USER_MIDDLE_NAME!}" class="form-control form-control-sm"/>
        </div>
      </div>

      <div class="row form-group">
        <div class="col-12">
          <label for="USER_LAST_NAME">${uiLabelMap.PartyLastName}*</label>
          <@fieldErrors fieldName="USER_LAST_NAME"/>
          <input type="text" name="USER_LAST_NAME" id="USER_LAST_NAME" value="${requestParameters.USER_LAST_NAME!}" class="form-control form-control-sm"/>
        </div>
      </div>

      <div class="row form-group">
        <div class="col-12">
          <label for="USER_SUFFIX">${uiLabelMap.PartySuffix}</label>
          <@fieldErrors fieldName="USER_SUFFIX"/>
          <input type="text" name="USER_SUFFIX" id="USER_SUFFIX" value="${requestParameters.USER_SUFFIX!}" class="form-control form-control-sm"/>
          </div>
      </div>
  </fieldset>
  <hr/>
  <fieldset>
      <legend>${uiLabelMap.PartyEmailAddress}</legend>
      <div class="row form-group">
        <div class="col-12">
          <label for="CUSTOMER_EMAIL">${uiLabelMap.PartyEmailAddress}*</label>
          <@fieldErrors fieldName="CUSTOMER_EMAIL"/>
          <input type="text" name="CUSTOMER_EMAIL" id="CUSTOMER_EMAIL" value="${requestParameters.CUSTOMER_EMAIL!}"
            class="form-control form-control-sm" onchange="changeEmail()" onkeyup="changeEmail()"/>
        </div>
      </div>
      <div class="row form-group">
        <div class="col-12">
          <label for="CUSTOMER_EMAIL_ALLOW_SOL">${uiLabelMap.PartyAllowSolicitation}</label>
          <select name="CUSTOMER_EMAIL_ALLOW_SOL" id="CUSTOMER_EMAIL_ALLOW_SOL" class="form-control form-control-sm">
            <#if ("Y" == ((requestParameters.CUSTOMER_EMAIL_ALLOW_SOL)!""))>
              <option value="Y">${uiLabelMap.CommonY}</option></#if>
            <#if ("N" == ((requestParameters.CUSTOMER_EMAIL_ALLOW_SOL)!""))>
              <option value="N">${uiLabelMap.CommonN}</option></#if>
              <option></option>
              <option value="Y">${uiLabelMap.CommonY}</option>
              <option value="N">${uiLabelMap.CommonN}</option>
          </select>
        </div>
      </div>
    </fieldset>
  </div>
  <div class="col-6">

  <fieldset>
    <legend>${uiLabelMap.PartyShippingAddress}</legend>
    <div class="row form-group">
      <div class="col-12">
        <label for="CUSTOMER_ADDRESS1">${uiLabelMap.PartyAddressLine1}*</label>
        <@fieldErrors fieldName="CUSTOMER_ADDRESS1"/>
        <input type="text" name="CUSTOMER_ADDRESS1" id="CUSTOMER_ADDRESS1"
          value="${requestParameters.CUSTOMER_ADDRESS1!}" class="form-control form-control-sm"/>
      </div>
    </div>
    <div class="row form-group">
      <div class="col-12">
        <label for="CUSTOMER_ADDRESS2">${uiLabelMap.PartyAddressLine2}</label>
        <@fieldErrors fieldName="CUSTOMER_ADDRESS2"/>
          <input type="text" name="CUSTOMER_ADDRESS2" id="CUSTOMER_ADDRESS2"
            value="${requestParameters.CUSTOMER_ADDRESS2!}" class="form-control form-control-sm"/>
      </div>
    </div>
    <div class="row form-group">
      <div class="col-12">
        <label for="CUSTOMER_CITY">${uiLabelMap.PartyCity}*</label>
        <@fieldErrors fieldName="CUSTOMER_CITY"/>
        <input type="text" name="CUSTOMER_CITY" id="CUSTOMER_CITY" value="${requestParameters.CUSTOMER_CITY!}" class="form-control form-control-sm"/>
      </div>
    </div>
    <div class="row form-group">
      <div class="col-12">
        <label for="CUSTOMER_POSTAL_CODE">${uiLabelMap.PartyZipCode}*</label>
        <@fieldErrors fieldName="CUSTOMER_POSTAL_CODE"/>
        <input type="text" name="CUSTOMER_POSTAL_CODE" id="CUSTOMER_POSTAL_CODE"
          value="${requestParameters.CUSTOMER_POSTAL_CODE!}" class="form-control form-control-sm"/>
      </div>
    </div>
    <div class="row form-group">
      <div class="col-12">
        <label for="customerCountry">${uiLabelMap.CommonCountry}*</label>
        <@fieldErrors fieldName="CUSTOMER_COUNTRY"/>
        <select name="CUSTOMER_COUNTRY" id="newuserform_countryGeoId" class="form-control form-control-sm">
          ${screens.render("component://common/widget/CommonScreens.xml#countries")}
          <#assign defaultCountryGeoId =
              Static["org.apache.ofbiz.entity.util.EntityUtilProperties"].getPropertyValue("general",
              "country.geo.id.default", delegator)>
          <option selected="selected" value="${defaultCountryGeoId}">
            <#assign countryGeo = delegator.findOne("Geo",Static["org.apache.ofbiz.base.util.UtilMisc"]
                .toMap("geoId",defaultCountryGeoId), false)>
            ${countryGeo.get("geoName",locale)}
          </option>
        </select>
      </div>
    </div>
    <div class="row form-group">
      <div class="col-12">
        <label for="customerState">${uiLabelMap.PartyState}*</label>
        <@fieldErrors fieldName="CUSTOMER_STATE"/>
        <select name="CUSTOMER_STATE" id="newuserform_stateProvinceGeoId" class="form-control form-control-sm"></select>
      </div>
    </div>
    <div class="row form-group">
      <div class="col-12">
        <label for="CUSTOMER_ADDRESS_ALLOW_SOL">${uiLabelMap.PartyAllowAddressSolicitation}</label>
        <@fieldErrors fieldName="CUSTOMER_ADDRESS_ALLOW_SOL"/>
        <select name="CUSTOMER_ADDRESS_ALLOW_SOL" id="CUSTOMER_ADDRESS_ALLOW_SOL" class="form-control form-control-sm">
          <#if ("Y" == ((requestParameters.CUSTOMER_ADDRESS_ALLOW_SOL)!""))>
            <option value="Y">${uiLabelMap.CommonY}</option>
          </#if>
          <#if ("N" == ((requestParameters.CUSTOMER_ADDRESS_ALLOW_SOL)!""))>
            <option value="N">${uiLabelMap.CommonN}</option>
          </#if>
          <option></option>
          <option value="Y">${uiLabelMap.CommonY}</option>
          <option value="N">${uiLabelMap.CommonN}</option>
        </select>
      </div>
    </div>
  </fieldset>
  </div>
</div>
<hr/>
  <fieldset>
    <legend>${uiLabelMap.PartyPhoneNumbers}</legend>
    <table class="table table-responsive"
        summary="Tabular form for entering multiple telecom numbers for different purposes.
        Each row allows user to enter telecom number for a purpose">
      <thead class="thead-light">
        <tr>
          <th></th>
          <th>${uiLabelMap.CommonCountry}</th>
          <th>${uiLabelMap.PartyAreaCode}</th>
          <th>${uiLabelMap.PartyContactNumber}</th>
          <th>${uiLabelMap.PartyExtension}</th>
          <th>${uiLabelMap.PartyAllowSolicitation}</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <th>${uiLabelMap.PartyHomePhone}</th>
          <td>
            <input type="text" name="CUSTOMER_HOME_COUNTRY" size="5" class="form-control form-control-sm" value="${requestParameters.CUSTOMER_HOME_COUNTRY!}"/>
          </td>
          <td>
            <input type="text" name="CUSTOMER_HOME_AREA" size="5" class="form-control form-control-sm" value="${requestParameters.CUSTOMER_HOME_AREA!}"/>
          </td>
          <td>
            <input type="text" name="CUSTOMER_HOME_CONTACT" class="form-control form-control-sm" value="${requestParameters.CUSTOMER_HOME_CONTACT!}"/></td>
          <td>
            <input type="text" name="CUSTOMER_HOME_EXT" size="6" class="form-control form-control-sm" value="${requestParameters.CUSTOMER_HOME_EXT!}"/>
          </td>
          <td>
            <select name="CUSTOMER_HOME_ALLOW_SOL" class="form-control form-control-sm">
              <#if ("Y" == ((requestParameters.CUSTOMER_HOME_ALLOW_SOL)!""))>
                <option value="Y">${uiLabelMap.CommonY}</option></#if>
              <#if ("N" == ((requestParameters.CUSTOMER_HOME_ALLOW_SOL)!""))>
                <option value="N">${uiLabelMap.CommonN}</option>
              </#if>
              <option></option>
              <option value="Y">${uiLabelMap.CommonY}</option>
              <option value="N">${uiLabelMap.CommonN}</option>
            </select>
          </td>
        </tr>
        <tr>
          <th scope="row">${uiLabelMap.PartyBusinessPhone}</th>
          <td>
            <input type="text" name="CUSTOMER_WORK_COUNTRY" size="5" class="form-control form-control-sm" value="${requestParameters.CUSTOMER_WORK_COUNTRY!}"/>
          </td>
          <td>
            <input type="text" name="CUSTOMER_WORK_AREA" size="5" class="form-control form-control-sm" value="${requestParameters.CUSTOMER_WORK_AREA!}"/>
          </td>
          <td>
            <input type="text" name="CUSTOMER_WORK_CONTACT" class="form-control form-control-sm" value="${requestParameters.CUSTOMER_WORK_CONTACT!}"/>
          </td>
          <td>
            <input type="text" name="CUSTOMER_WORK_EXT" class="form-control form-control-sm" size="6" value="${requestParameters.CUSTOMER_WORK_EXT!}"/>
          </td>
          <td>
            <select name="CUSTOMER_WORK_ALLOW_SOL" class="form-control form-control-sm">
              <#if ("Y" == ((requestParameters.CUSTOMER_WORK_ALLOW_SOL)!""))>
                <option value="Y">${uiLabelMap.CommonY}</option></#if>
              <#if ("N" == ((requestParameters.CUSTOMER_WORK_ALLOW_SOL)!""))>
              <option value="N">${uiLabelMap.CommonN}</option></#if>
              <option></option>
              <option value="Y">${uiLabelMap.CommonY}</option>
              <option value="N">${uiLabelMap.CommonN}</option>
            </select>
          </td>
        </tr>
        <tr>
          <th scope="row">${uiLabelMap.PartyFaxNumber}</th>
          <td>
            <input type="text" name="CUSTOMER_FAX_COUNTRY" size="5" class="form-control form-control-sm" value="${requestParameters.CUSTOMER_FAX_COUNTRY!}"/>
          </td>
          <td>
            <input type="text" name="CUSTOMER_FAX_AREA" size="5" class="form-control form-control-sm" value="${requestParameters.CUSTOMER_FAX_AREA!}"/>
          </td>
          <td>
            <input type="text" name="CUSTOMER_FAX_CONTACT" class="form-control form-control-sm" value="${requestParameters.CUSTOMER_FAX_CONTACT!}"/>
          </td>
          <td></td>
          <td>
            <select name="CUSTOMER_FAX_ALLOW_SOL" class="form-control form-control-sm">
              <#if ("Y" == ((requestParameters.CUSTOMER_FAX_ALLOW_SOL)!""))>
                <option value="Y">${uiLabelMap.CommonY}</option></#if>
              <#if ("N" == ((requestParameters.CUSTOMER_FAX_ALLOW_SOL)!""))>
              <option value="N">${uiLabelMap.CommonN}</option></#if>
              <option></option>
              <option value="Y">${uiLabelMap.CommonY}</option>
              <option value="N">${uiLabelMap.CommonN}</option>
            </select>
          </td>
        </tr>
        <tr>
          <th scope="row">${uiLabelMap.PartyMobilePhone}</th>
          <td>
            <input type="text" name="CUSTOMER_MOBILE_COUNTRY" size="5"
                value="${requestParameters.CUSTOMER_MOBILE_COUNTRY!}" class="form-control form-control-sm"/>
          </td>
          <td>
            <input type="text" name="CUSTOMER_MOBILE_AREA" size="5" class="form-control form-control-sm" value="${requestParameters.CUSTOMER_MOBILE_AREA!}"/>
          </td>
          <td>
            <input type="text" name="CUSTOMER_MOBILE_CONTACT" class="form-control form-control-sm" value="${requestParameters.CUSTOMER_MOBILE_CONTACT!}"/>
          </td>
          <td></td>
          <td>
            <select name="CUSTOMER_MOBILE_ALLOW_SOL" class="form-control form-control-sm">
              <#if ("Y" == ((requestParameters.CUSTOMER_MOBILE_ALLOW_SOL)!""))>
                <option value="Y">${uiLabelMap.CommonY}</option></#if>
              <#if ("N" == ((requestParameters.CUSTOMER_MOBILE_ALLOW_SOL)!""))>
              <option value="N">${uiLabelMap.CommonN}</option></#if>
              <option></option>
              <option value="Y">${uiLabelMap.CommonY}</option>
              <option value="N">${uiLabelMap.CommonN}</option>
            </select>
          </td>
        </tr>
      </tbody>
    </table>
  </fieldset>
  <hr/>
  <div class="row form-group">
  <div class="col-6">
  <fieldset>
    <legend><#if getUsername>${uiLabelMap.CommonUsername}</#if></legend>
    <#if getUsername>
      <@fieldErrors fieldName="USERNAME"/>
      <#if !requestParameters.preferredUsername?has_content>
        <div class="form-check">
          <label class="form-check-label"></label>
            <input type="checkbox" name="UNUSEEMAIL" class="checkbox form-check-input" id="UNUSEEMAIL" value="on"
                onclick="setEmailUsername();" onfocus="setLastFocused(this);"/> ${uiLabelMap.EcommerceUseEmailAddress}
        </div>
      </#if>

      <div class="row form-group">
      <div class="col-12">
        <label for="USERNAME">${uiLabelMap.CommonUsername}*</label>
        <#if requestParameters.preferredUsername?has_content>
          <input type="text" name="showUserName" id="showUserName" value="${requestParameters.USERNAME!}"
              disabled="disabled"/>
          <input type="hidden" name="USERNAME" id="USERNAME" value="${requestParameters.USERNAME!}"/>
        <#else>
          <input type="text" name="USERNAME" id="USERNAME" value="${requestParameters.USERNAME!}"
              class="form-control form-control-sm" onfocus="clickUsername();" onchange="changeEmail();"/>
        </#if>
      </div>
      </div>
    </#if>
  </fieldset>
  </div>
  <div class="col-6">
  <fieldset>
    <legend>${uiLabelMap.CommonPassword}</legend>
    <#if createAllowPassword>
      <div class="row form-group">
      <div class="col-12">
        <label for="PASSWORD">${uiLabelMap.CommonPassword}*</label>
        <@fieldErrors fieldName="PASSWORD"/>
        <input type="password" name="PASSWORD" class="form-control form-control-sm" autocomplete="off" id="PASSWORD" onfocus="setLastFocused(this);"/>
      </div>
      </div>

      <div class="row form-group">
      <div class="col-12">
        <label for="CONFIRM_PASSWORD">${uiLabelMap.PartyRepeatPassword}*</label>
        <@fieldErrors fieldName="CONFIRM_PASSWORD"/>
        <input type="password" class="form-control form-control-sm" name="CONFIRM_PASSWORD" id="CONFIRM_PASSWORD" autocomplete="off" value="" maxlength="50"/>
      </div>
      </div>

      <div class="row form-group">
      <div class="col-12">
        <label for="PASSWORD_HINT">${uiLabelMap.PartyPasswordHint}</label>
        <@fieldErrors fieldName="PASSWORD_HINT"/>
        <input type="text" class="form-control form-control-sm" name="PASSWORD_HINT" id="PASSWORD_HINT"
            value="${requestParameters.PASSWORD_HINT!}" maxlength="100"/>
      </div>
      </div>
    <#else>
      <div>
        <label>${uiLabelMap.PartyReceivePasswordByEmail}.</div>
      </div>
    </#if>
  </fieldset>
  </div>
  </div>
</form>
</div>
</div>

<#------------------------------------------------------------------------------
To create a consistent look and feel for all buttons, input[type=submit], 
and a tags acting as submit buttons, all button actions should have a 
class name of "button". No other class names should be used to style 
button actions.
------------------------------------------------------------------------------->
<div class="d-flex justify-content-center">
  <a href="<@ofbizUrl>${donePage}</@ofbizUrl>" class="btn btn-outline-secondary btn-sm">${uiLabelMap.CommonCancel}</a>&nbsp;
  <a href="javascript:document.getElementById('newuserform').submit()" class="btn btn-outline-secondary btn-sm">${uiLabelMap.CommonSave}</a>
</div>

<script type="text/javascript">
  //<![CDATA[
  hideShowUsaStates();
  //]]>
</script>
