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
<#if canNotView>
<h3>${uiLabelMap.PartyContactInfoNotBelongToYou}.</h3>
<a href="<@ofbizUrl>${donePage}</@ofbizUrl>" class="btn btn-outline-secondary">${uiLabelMap.CommonBack}</a>
<#else>
  <#if !contactMech??>
  <#-- When creating a new contact mech, first select the type, then actually create -->
    <#if !requestParameters.preContactMechTypeId?? && !preContactMechTypeId??>
    <h2>${uiLabelMap.PartyCreateNewContactInfo}</h2>
    <form method="post" class="form-inline" action='<@ofbizUrl>editcontactmechnosave</@ofbizUrl>' name="createcontactmechform">
            <label class="mr-2">${uiLabelMap.PartySelectContactType}:</label>
              <select name="preContactMechTypeId" class="form-control custom-select mr-2">
                <#list contactMechTypes as contactMechType>
                  <option value='${contactMechType.contactMechTypeId}'>
                    ${contactMechType.get("description",locale)}
                  </option>
                </#list>
              </select>
              <a href="javascript:document.createcontactmechform.submit()" class="btn btn-outline-secondary">${uiLabelMap.CommonCreate}</a>
    </form>
    <#-- <p><h3>ERROR: Contact information with ID "${contactMechId}" not found!</h3></p> -->
    </#if>
  </#if>

  <#if contactMechTypeId??>
    <#if !contactMech??>
    <h2>${uiLabelMap.PartyCreateNewContactInfo}</h2>
    <a href='<@ofbizUrl>${donePage}</@ofbizUrl>' class="btn btn-outline-secondary">${uiLabelMap.CommonGoBack}</a>
    <a href="javascript:document.editcontactmechform.submit()" class="btn btn-outline-secondary">${uiLabelMap.CommonSave}</a>
    <table width="90%" border="0" cellpadding="2" cellspacing="0">
    <form method="post" action='<@ofbizUrl>${reqName}</@ofbizUrl>' name="editcontactmechform" id="editcontactmechform">
      <input type='hidden' name='contactMechTypeId' value='${contactMechTypeId}'/>
      <#if contactMechPurposeType??>
        <div>(${uiLabelMap.PartyNewContactHavePurpose} "${contactMechPurposeType.get("description",locale)!}")</div>
      </#if>
      <#if cmNewPurposeTypeId?has_content>
        <input type='hidden' name='contactMechPurposeTypeId' value='${cmNewPurposeTypeId}'/>
      </#if>
      <#if preContactMechTypeId?has_content>
        <input type='hidden' name='preContactMechTypeId' value='${preContactMechTypeId}'/>
      </#if>
      <#if paymentMethodId?has_content>
        <input type='hidden' name='paymentMethodId' value='${paymentMethodId}'/>
      </#if>
    <#else>
      <h2>${uiLabelMap.PartyEditContactInfo}</h2>
      <a href="<@ofbizUrl>${donePage}</@ofbizUrl>" class="btn btn-outline-secondary">${uiLabelMap.CommonGoBack}</a>
      <a href="javascript:document.editcontactmechform.submit()" class="btn btn-outline-secondary">${uiLabelMap.CommonSave}</a>
      <p class="my-2"><strong>${uiLabelMap.PartyContactPurposes}</strong></p>
      <div class="row">
        <div class="col-sm-6">
            <#list partyContactMechPurposes! as partyContactMechPurpose>
              <#assign contactMechPurposeType = partyContactMechPurpose.getRelatedOne("ContactMechPurposeType", true) />
                  <form name="deletePartyContactMechPurpose_${partyContactMechPurpose.contactMechPurposeTypeId}" class="my-2"
                        method="post" action="<@ofbizUrl>deletePartyContactMechPurpose</@ofbizUrl>">
                    <div class="form-group">
                      <label class="my-2">
                        <#if contactMechPurposeType??>
                        ${contactMechPurposeType.get("description",locale)}
                        <#else>
                        ${uiLabelMap.PartyPurposeTypeNotFound}: "${partyContactMechPurpose.contactMechPurposeTypeId}"
                        </#if>
                        (${uiLabelMap.CommonSince}:${partyContactMechPurpose.fromDate.toString()})
                        <#if partyContactMechPurpose.thruDate??>(${uiLabelMap.CommonExpires}
                          :${partyContactMechPurpose.thruDate.toString()})</#if>
                      </label>
                      <input type="hidden" name="contactMechId" value="${contactMechId}"/>
                      <input type="hidden" name="contactMechPurposeTypeId"
                             value="${partyContactMechPurpose.contactMechPurposeTypeId}"/>
                      <input type="hidden" name="fromDate" value="${partyContactMechPurpose.fromDate}"/>
                      <input type="hidden" name="useValues" value="true"/>
                      <a href='javascript:document.deletePartyContactMechPurpose_${partyContactMechPurpose.contactMechPurposeTypeId}.submit()'
                         class="btn btn-outline-secondary">${uiLabelMap.CommonDelete}</a>
                    </div>
                  </form>
            </#list>
            <#if purposeTypes?has_content>
                  <form method="post" class="form-inline" action='<@ofbizUrl>createPartyContactMechPurpose</@ofbizUrl>'
                        name='newpurposeform'>
                      <input type="hidden" name="contactMechId" value="${contactMechId}"/>
                      <input type="hidden" name="useValues" value="true"/>
                      <select name='contactMechPurposeTypeId' class="custom-select form-control">
                        <option>${uiLabelMap.CommonSelect}</option>
                        <#list purposeTypes as contactMechPurposeType>
                          <option value='${contactMechPurposeType.contactMechPurposeTypeId}'>
                            ${contactMechPurposeType.get("description",locale)}
                          </option>
                        </#list>
                      </select>
                    <a href='javascript:document.newpurposeform.submit()' class="btn btn-outline-secondary">${uiLabelMap.PartyAddPurpose}</a>
                  </form>
            </#if>
          </div>
        </div>
    <form method="post" action='<@ofbizUrl>${reqName}</@ofbizUrl>' name="editcontactmechform" id="editcontactmechform">
      <input type="hidden" name="contactMechId" value='${contactMechId}'/>
      <input type="hidden" name="contactMechTypeId" value='${contactMechTypeId}'/>
    </#if>

    <#if contactMechTypeId = "POSTAL_ADDRESS">
      <div class="row">
        <div class="col-sm-6">
          <label class="my-2">${uiLabelMap.PartyToName}</label>
          <input type="text" class="form-control" name="toName"
              value="${postalAddressData.toName!}"/>
        </div>
      </div>
      <div class="row">
        <div class="col-sm-6">
          <label class="my-2">${uiLabelMap.PartyAttentionName}</label>
          <input type="text" class="form-control" name="attnName"
              value="${postalAddressData.attnName!}"/>
        </div>
      </div>
      <div class="row">
        <div class="col-sm-6">
          <label class="my-2">${uiLabelMap.PartyAddressLine1}</label>
          <input type="text" class="form-control" name="address1"
              value="${postalAddressData.address1!}"/>
        </div>
      </div>
      <div class="row">
        <div class="col-sm-6">
          <label class="my-2">${uiLabelMap.PartyAddressLine2}</label>
          <input type="text" class="form-control" name="address2"
              value="${postalAddressData.address2!}"/>
        </div>
      </div>
      <div class="row">
        <div class="col-sm-6">
        <label class="my-2">${uiLabelMap.PartyCity}</label>
          <input type="text" class="form-control" name="city" value="${postalAddressData.city!}"/>
        </div>
      </div>
      <div class="row">
        <div class="col-sm-6">
          <label class="my-2"> ${uiLabelMap.PartyState}</label>
          <select name="stateProvinceGeoId" id="editcontactmechform_stateProvinceGeoId" class="custom-select form-control">
          </select>
        </div>
      </div>
      <div class="row">
        <div class="col-sm-6">
          <label class="my-2">${uiLabelMap.PartyZipCode}</label>
          <input type="text" class="form-control" name="postalCode"
                 value="${postalAddressData.postalCode!}"/>
        </div>
      </div>
      <div class="row">
        <div class="col-sm-6">
          <label class="my-2">${uiLabelMap.CommonCountry}</label>
          <select name="countryGeoId" class="custom-select form-control" id="editcontactmechform_countryGeoId">
            ${screens.render("component://common/widget/CommonScreens.xml#countries")}
            <#if (postalAddress??) && (postalAddress.countryGeoId??)>
              <#assign defaultCountryGeoId = postalAddress.countryGeoId>
            <#else>
              <#assign defaultCountryGeoId = Static["org.apache.ofbiz.entity.util.EntityUtilProperties"]
                  .getPropertyValue("general", "country.geo.id.default", delegator)>
            </#if>
            <option selected="selected" value="${defaultCountryGeoId}">
              <#assign countryGeo = delegator.findOne("Geo",Static["org.apache.ofbiz.base.util.UtilMisc"]
                  .toMap("geoId",defaultCountryGeoId), false)>
              ${countryGeo.get("geoName",locale)}
            </option>
          </select>
        </div>
      </div>
    <#elseif contactMechTypeId = "TELECOM_NUMBER">
    <div class="form-group">
      <label class="my-2">${uiLabelMap.PartyPhoneNumber}</label>
      <div class="row">
        <div class="col-sm-2">
          <input type="text" class="form-control" name="countryCode"
              value="${telecomNumberData.countryCode!}" placeholder="${uiLabelMap.CommonCountryCode}"/>
        </div>
        <label class="my-2">-</label>
        <div class="col-sm-2">
          <input type="text" class="form-control" name="areaCode"
              value="${telecomNumberData.areaCode!}" placeholder="${uiLabelMap.PartyAreaCode}"/>
        </div>
        <label class="my-2">-</label>
        <div class="col-sm-2">
          <input type="text" class="form-control" name="contactNumber"
              value="${telecomNumberData.contactNumber!}" placeholder="${uiLabelMap.PartyContactNumber}"/>
        </div>
        <label class="my-2">-</label>
        <div class="col-sm-2">
          <input type="text" class="form-control"
              name="extension" value="${partyContactMechData.extension!}" placeholder="${uiLabelMap.PartyExtension}"/>
        </div>
      </div>
    </div>
    <#elseif contactMechTypeId = "EMAIL_ADDRESS">
      <div class="row">
        <div class="col-sm-6">
          <label class="my-2">${uiLabelMap.PartyEmailAddress}</label >
          <input type="text" class="form-control" name="emailAddress"
              value="<#if tryEntity>${contactMech.infoString!}<#else>${requestParameters.emailAddress!}</#if>"/>
        </div>
      </div>
    <#else>
      <div class="row">
        <div class="col-sm-6">
        <label class="my-2">${contactMechType.get("description",locale)!}</label>
        <input type="text" class="form-control" name="infoString"
              value="${contactMechData.infoString!}"/>
        </div>
      </div>
    </#if>
    <div class="row">
      <div class="col-sm-6">
        <label class="my-2">${uiLabelMap.PartyAllowSolicitation}?</label>
        <select name="allowSolicitation" class=" form-control custom-select mb-2">
          <#if ("Y" == ((partyContactMechData.allowSolicitation)!""))>
            <option value="Y">${uiLabelMap.CommonY}</option></#if>
          <#if ("N" == ((partyContactMechData.allowSolicitation)!""))>
            <option value="N">${uiLabelMap.CommonN}</option></#if>
          <option></option>
          <option value="Y">${uiLabelMap.CommonY}</option>
          <option value="N">${uiLabelMap.CommonN}</option>
        </select>
      </div>
    </div>
  </form>
  <a href="<@ofbizUrl>${donePage}</@ofbizUrl>" class="btn btn-outline-secondary mr-1">${uiLabelMap.CommonGoBack}</a>
  <a href="javascript:document.editcontactmechform.submit()" class="btn btn-outline-secondary">${uiLabelMap.CommonSave}</a>
  <#else>
    <a href="<@ofbizUrl>${donePage}</@ofbizUrl>" class="btn btn-outline-secondary mt-2">${uiLabelMap.CommonGoBack}</a>
  </#if>
</#if>
