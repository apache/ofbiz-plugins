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

<#if party??>
    <div class="d-flex justify-content-between">
      <div class="p-2">
        <h2>${uiLabelMap.PartyTheProfileOf}
          <#if person??>
          ${person.personalTitle!}
          ${person.firstName!}
          ${person.middleName!}
          ${person.lastName!}
          ${person.suffix!}
          <#else>
            "${uiLabelMap.PartyNewUser}"
          </#if>
        </h2>
      </div>
      <div class="p-2">
        <#if showOld>
          <a href="<@ofbizUrl>viewprofile</@ofbizUrl>" class="btn btn-outline-secondary">${uiLabelMap.PartyHideOld}</a>
        <#else>
          <a href="<@ofbizUrl>viewprofile?SHOW_OLD=true</@ofbizUrl>" class="btn btn-outline-secondary">${uiLabelMap.PartyShowOld}</a>
        </#if>
        <#if "Y" == (productStore.enableDigProdUpload)!>
          <a href="<@ofbizUrl>digitalproductlist</@ofbizUrl>" class="btn btn-secondary-outline">${uiLabelMap.EcommerceDigitalProductUpload}</a>
        </#if>
      </div>
    </div>

    <div class="card">
      <div class="card-header">
        <div class="row">
        <div class="col-lg-3">
          <strong>${uiLabelMap.PartyPersonalInformation}</strong>
        </div>
        <div class="col-lg-9 text-right">
        <a href="<@ofbizUrl>editperson</@ofbizUrl>">
        <#if person??>${uiLabelMap.CommonUpdate}<#else>${uiLabelMap.CommonCreate}</#if></a>
        </div>
        </div>
      </div>
      <div class="card-body">
        <#if person??>
        <div class="row">
          <div class="col-lg-6">
          <dl class="row">
            <dt class="col-lg-2">${uiLabelMap.PartyName}</dt>
            <dd class="col-lg-10">
              ${person.personalTitle!}
              ${person.firstName!}
              ${person.middleName!}
              ${person.lastName!}
              ${person.suffix!}
            </dd>
            <#if person.nickname?has_content>
              <dt class="col-lg-2">${uiLabelMap.PartyNickName}</dt>
              <dd class="col-lg-10">${person.nickname}</dd>
            </#if>
            <#if person.gender?has_content>
              <dt class="col-lg-2">${uiLabelMap.PartyGender}</dt>
              <dd class="col-lg-10">${person.gender}</dd>
            </#if>
          <#if person.birthDate??>
            <dt class="col-lg-2">${uiLabelMap.PartyBirthDate}</dt>
            <dd class="col-lg-10">${person.birthDate.toString()}</dd>
          </#if>
          <#if person.height??>
            <dt class="col-lg-2">${uiLabelMap.PartyHeight}</dt>
            <dd class="col-lg-10">${person.height}</dd>
          </#if>
          <#if person.weight??>
            <dt class="col-lg-2">${uiLabelMap.PartyWeight}</dt>
            <dd class="col-lg-10">${person.weight}</dd>
          </#if>
          <#if person.maritalStatusEnumId?has_content>
            <#assign maritalStatus = EntityQuery.use(delegator).from("Enumeration").where("enumId", person.maritalStatusEnumId!).cache(true).queryOne()!>
            <dt class="col-lg-2">${uiLabelMap.PartyMaritalStatus}</dt>
            <dd class="col-lg-10">${maritalStatus.description!person.maritalStatusEnumId}</dd>
          </#if>
        </dl>
        </div>
        <div class="col-lg-6">
          <dl class="row">
            <#if person.mothersMaidenName?has_content>
              <dt class="col-lg-3">${uiLabelMap.PartyMaidenName}</dt>
              <dd class="col-lg-9">${person.mothersMaidenName}</dd>
            </#if>
            <#if person.socialSecurityNumber?has_content>
              <dt class="col-lg-3">${uiLabelMap.PartySocialSecurityNumber}</dt>
              <dd class="col-lg-9">${person.socialSecurityNumber}</dd>
            </#if>
            <#if person.passportNumber?has_content>
              <dt class="col-lg-3">${uiLabelMap.PartyPassportNumber}</dt>
              <dd class="col-lg-9">${person.passportNumber}</dd>
            </#if>
            <#if person.passportExpireDate??>
              <dt class="col-lg-3">${uiLabelMap.PartyPassportExpireDate}</dt>
              <dd class="col-lg-9">${person.passportExpireDate.toString()}</dd>
            </#if>
            <#if person.totalYearsWorkExperience??>
              <dt class="col-lg-3">${uiLabelMap.PartyYearsWork}</dt>
              <dd class="col-lg-9">${person.totalYearsWorkExperience}</dd>
            </#if>
            <#if person.comments?has_content>
              <dt class="col-lg-3">${uiLabelMap.CommonComments}</dt>
              <dd class="col-lg-9">${person.comments}</dd>
            </#if>
          </dl>
        </div>
        </div>
        <#else>
          <label>${uiLabelMap.PartyPersonalInformationNotFound}</label>
        </#if>
        </div>
    </div>

    <#-- ============================================================= -->
    <#if monthsToInclude?? && totalSubRemainingAmount?? && totalOrders??>
    <div class="card">
      <div class="card-header"><strong>${uiLabelMap.EcommerceLoyaltyPoints}</strong></div>
      <div class="card-body">
        <label>${uiLabelMap.EcommerceYouHave} ${totalSubRemainingAmount} ${uiLabelMap.EcommercePointsFrom} ${totalOrders} ${uiLabelMap.EcommerceOrderInLast} ${monthsToInclude} ${uiLabelMap.EcommerceMonths}</label>
      </div>
    </div>
    </#if>

    <#-- ============================================================= -->
    <div class="card">
      <div class="card-header">
        <div class="row">
          <div class="col-lg-3"><strong>${uiLabelMap.PartyContactInformation}</strong></div>
          <div class="col-lg-9 text-right"><a href="<@ofbizUrl>editcontactmech</@ofbizUrl>" class="card-link">${uiLabelMap.CommonCreate}</a></div>
        </div>
      </div>

      <div class="card-body">
      <#if partyContactMechValueMaps?has_content>
        <table class="table table-responsive-sm">
          <thead class="thead-dark">
          <tr>
            <th>${uiLabelMap.PartyContactType}</th>
            <th>${uiLabelMap.CommonInformation}</th>
            <th>${uiLabelMap.PartySolicitingOk}?</th>
            <th></th>
          </tr>
          </thead>
          <#list partyContactMechValueMaps as partyContactMechValueMap>
            <#assign contactMech = partyContactMechValueMap.contactMech! />
            <#assign contactMechType = partyContactMechValueMap.contactMechType! />
            <#assign partyContactMech = partyContactMechValueMap.partyContactMech! />
              <tbody>
              <tr>
                <td>
                  ${contactMechType.get("description",locale)}
                </td>
                <td>
                  <#list partyContactMechValueMap.partyContactMechPurposes! as partyContactMechPurpose>
                    <#assign contactMechPurposeType = partyContactMechPurpose.getRelatedOne("ContactMechPurposeType", true) />
                      <#if contactMechPurposeType??>
                        ${contactMechPurposeType.get("description",locale)}
                        <#if "SHIPPING_LOCATION" == contactMechPurposeType.contactMechPurposeTypeId && (profiledefs.defaultShipAddr)?default("") == contactMech.contactMechId>
                          <label>${uiLabelMap.EcommerceIsDefault}</label>
                        <#elseif "SHIPPING_LOCATION" == contactMechPurposeType.contactMechPurposeTypeId>
                          <form name="defaultShippingAddressForm" method="post" action="<@ofbizUrl>setprofiledefault/viewprofile</@ofbizUrl>">
                            <input type="hidden" name="productStoreId" value="${productStoreId}" />
                            <input type="hidden" name="defaultShipAddr" value="${contactMech.contactMechId}" />
                            <input type="hidden" name="partyId" value="${party.partyId}" />
                            <input type="submit" value="${uiLabelMap.EcommerceSetDefault}" class="btn btn-outline-secondary" />
                          </form>
                        </#if>
                      <#else>
                        ${uiLabelMap.PartyPurposeTypeNotFound}: "${partyContactMechPurpose.contactMechPurposeTypeId}"
                      </#if>
                      <#if partyContactMechPurpose.thruDate??>(${uiLabelMap.CommonExpire}:${partyContactMechPurpose.thruDate.toString()})</#if>
                  </#list>
                  <#if contactMech.contactMechTypeId! = "POSTAL_ADDRESS">
                    <#assign postalAddress = partyContactMechValueMap.postalAddress! />
                    <div>
                      <#if postalAddress??>
                        <#if postalAddress.toName?has_content>${uiLabelMap.CommonTo}: ${postalAddress.toName}<br /></#if>
                        <#if postalAddress.attnName?has_content>${uiLabelMap.PartyAddrAttnName}: ${postalAddress.attnName}<br /></#if>
                        ${postalAddress.address1}<br />
                        <#if postalAddress.address2?has_content>${postalAddress.address2}<br /></#if>
                        ${postalAddress.city}<#if partyContactMechValueMap.stateProvinceGeoName?has_content>,&nbsp;${partyContactMechValueMap.stateProvinceGeoName}</#if>&nbsp;${postalAddress.postalCode!}
                        <#if partyContactMechValueMap.countryGeoName?has_content><br />${partyContactMechValueMap.countryGeoName}</#if>
                        <#if (!postalAddress.countryGeoId?has_content || postalAddress.countryGeoId! = "USA")>
                          <#assign addr1 = postalAddress.address1! />
                          <#if (addr1.indexOf(" ") > 0)>
                            <#assign addressNum = addr1.substring(0, addr1.indexOf(" ")) />
                            <#assign addressOther = addr1.substring(addr1.indexOf(" ")+1) />
                            <a target="_blank" href="${uiLabelMap.CommonLookupWhitepagesAddressLink}" class="linktext">(${uiLabelMap.CommonLookupWhitepages})</a>
                          </#if>
                        </#if>
                      <#else>
                        ${uiLabelMap.PartyPostalInformationNotFound}.
                      </#if>
                      </div>
                  <#elseif contactMech.contactMechTypeId! = "TELECOM_NUMBER">
                    <#assign telecomNumber = partyContactMechValueMap.telecomNumber!>
                    <div>
                    <#if telecomNumber??>
                      ${telecomNumber.countryCode!}
                      <#if telecomNumber.areaCode?has_content>${telecomNumber.areaCode}-</#if>${telecomNumber.contactNumber!}
                      <#if partyContactMech.extension?has_content>ext&nbsp;${partyContactMech.extension}</#if>
                      <#if (!telecomNumber.countryCode?has_content || telecomNumber.countryCode = "011")>
                        <a target="_blank" href="${uiLabelMap.CommonLookupAnywhoLink}" class="linktext">${uiLabelMap.CommonLookupAnywho}</a>
                        <a target="_blank" href="${uiLabelMap.CommonLookupWhitepagesTelNumberLink}" class="linktext">${uiLabelMap.CommonLookupWhitepages}</a>
                      </#if>
                    <#else>
                      ${uiLabelMap.PartyPhoneNumberInfoNotFound}.
                    </#if>
                    </div>
                  <#elseif contactMech.contactMechTypeId! = "EMAIL_ADDRESS">
                      ${contactMech.infoString}
                      <a href="mailto:${contactMech.infoString}" class="linktext">(${uiLabelMap.PartySendEmail})</a>
                  <#elseif contactMech.contactMechTypeId! = "WEB_ADDRESS">
                    <div>
                      ${contactMech.infoString}
                      <#assign openAddress = contactMech.infoString! />
                      <#if !openAddress.startsWith("http") && !openAddress.startsWith("HTTP")><#assign openAddress = "http://" + openAddress /></#if>
                      <a target="_blank" href="${openAddress}" class="linktext">(${uiLabelMap.CommonOpenNewWindow})</a>
                    </div>
                  <#else>
                    ${contactMech.infoString!}
                  </#if>
                  <div>(${uiLabelMap.CommonUpdated}:&nbsp;${partyContactMech.fromDate.toString()})</div>
                  <#if partyContactMech.thruDate??><div>${uiLabelMap.CommonDelete}:&nbsp;${partyContactMech.thruDate.toString()}</div></#if>
                </td>
                <td>(${partyContactMech.allowSolicitation!})</td>
                <td>
                  <form name= "deleteContactMech_${contactMech.contactMechId}" method= "post" action= "<@ofbizUrl>deleteContactMech</@ofbizUrl>">
                    <input type= "hidden" name= "contactMechId" value= "${contactMech.contactMechId}"/>
                    <a href="<@ofbizUrl>editcontactmech?contactMechId=${contactMech.contactMechId}</@ofbizUrl>" class="btn btn-outline-secondary">${uiLabelMap.CommonUpdate}</a>
                    <a href='javascript:document.deleteContactMech_${contactMech.contactMechId}.submit()' class='btn btn-outline-secondary'>${uiLabelMap.CommonExpire}</a>
                  </form>
                </td>
              </tr>
              </tbody>
          </#list>
        </table>
      <#else>
        <label>${uiLabelMap.PartyNoContactInformation}.</label>
      </#if>
      </div>
    </div>

    <#-- ============================================================= -->

    <div class="card">
      <div class="card-header">
        <div class="row">
          <div class="col-lg-3">
            <strong>${uiLabelMap.AccountingPaymentMethodInformation}</strong>
          </div>
          <div class="col-lg-9 text-right">
            <a href="<@ofbizUrl>editcreditcard</@ofbizUrl>" class="mr-2">${uiLabelMap.PartyCreateNewCreditCard}</a><a href="<@ofbizUrl>editgiftcard</@ofbizUrl>" class="mr-2">${uiLabelMap.PartyCreateNewGiftCard}</a><a href="<@ofbizUrl>editeftaccount</@ofbizUrl>" class="mr-2">${uiLabelMap.PartyCreateNewEftAccount}</a>
          </div>
        </div>
      </div>
      <div class="card-body">
              <#if paymentMethodValueMaps?has_content>
              <table class="table table-responsive-sm">
                <#list paymentMethodValueMaps as paymentMethodValueMap>
                  <#assign paymentMethod = paymentMethodValueMap.paymentMethod! />
                  <#assign creditCard = paymentMethodValueMap.creditCard! />
                  <#assign giftCard = paymentMethodValueMap.giftCard! />
                  <#assign eftAccount = paymentMethodValueMap.eftAccount! />
                  <tr>
                    <#if "CREDIT_CARD" == paymentMethod.paymentMethodTypeId!>
                    <td>
                      <dl>
                        <dt>
                        ${uiLabelMap.AccountingCreditCard}</dt>
                        <dd><#if creditCard.companyNameOnCard?has_content>${creditCard.companyNameOnCard}</#if></dd>
                        <dd><#if creditCard.titleOnCard?has_content>${creditCard.titleOnCard}</#if>
                        ${creditCard.firstNameOnCard}
                        <#if creditCard.middleNameOnCard?has_content>${creditCard.middleNameOnCard}</#if>
                        ${creditCard.lastNameOnCard}</dd>
                        <dd>
                        <#if creditCard.suffixOnCard?has_content>${creditCard.suffixOnCard}</#if>
                        ${Static["org.apache.ofbiz.party.contact.ContactHelper"].formatCreditCard(creditCard)}
                        <#if paymentMethod.description?has_content>(${paymentMethod.description})</#if>
                        <#if paymentMethod.fromDate?has_content>(${uiLabelMap.CommonUpdated}:&nbsp;${paymentMethod.fromDate.toString()})</#if>
                        <#if paymentMethod.thruDate??>(${uiLabelMap.CommonDelete}:${paymentMethod.thruDate.toString()})</#if>
                        </dd>
                      </dl>
                    </td>
                    <td>
                      <a href="<@ofbizUrl>editcreditcard?paymentMethodId=${paymentMethod.paymentMethodId}</@ofbizUrl>" class="btn btn-outline-secondary">
                                ${uiLabelMap.CommonUpdate}</a>
                    </td>
                    <#elseif "GIFT_CARD" == paymentMethod.paymentMethodTypeId!>
                      <#if giftCard?has_content && giftCard.cardNumber?has_content>
                        <#assign giftCardNumber = "" />
                        <#assign pcardNumber = giftCard.cardNumber />
                        <#if pcardNumber?has_content>
                          <#assign psize = pcardNumber?length - 4 />
                          <#if (0 < psize)>
                            <#list 0 .. psize-1 as foo>
                              <#assign giftCardNumber = giftCardNumber + "*" />
                            </#list>
                             <#assign giftCardNumber = giftCardNumber + pcardNumber[psize .. psize + 3] />
                          <#else>
                             <#assign giftCardNumber = pcardNumber />
                          </#if>
                        </#if>
                      </#if>
                      <td>
                        <dt>${uiLabelMap.AccountingGiftCard}</dt><dd>${giftCardNumber}
                          <#if paymentMethod.description?has_content>(${paymentMethod.description})</#if>
                          <#if paymentMethod.fromDate?has_content>(${uiLabelMap.CommonUpdated}:&nbsp;${paymentMethod.fromDate.toString()})</#if>
                          <#if paymentMethod.thruDate??>(${uiLabelMap.CommonDelete}:&nbsp;${paymentMethod.thruDate.toString()})</#if></dd>
                      </td>
                      <td>
                        <a href="<@ofbizUrl>editgiftcard?paymentMethodId=${paymentMethod.paymentMethodId}</@ofbizUrl>" class="btn btn-outline-secondary">
                                ${uiLabelMap.CommonUpdate}</a>
                      </td>
                      <#elseif "EFT_ACCOUNT" == paymentMethod.paymentMethodTypeId!>
                      <td>
                        <dt>${uiLabelMap.AccountingEFTAccount}</dt> <dd>${eftAccount.nameOnAccount!}</dd><dd><#if eftAccount.bankName?has_content>${uiLabelMap.AccountingBank}: ${eftAccount.bankName}</#if></dd><dd> <#if eftAccount.accountNumber?has_content>${uiLabelMap.AccountingAccount} #: ${eftAccount.accountNumber}</#if></dd>
                          <dd><#if paymentMethod.description?has_content>(${paymentMethod.description})</#if>
                          <#if paymentMethod.fromDate?has_content>(${uiLabelMap.CommonUpdated}:${paymentMethod.fromDate.toString()})</#if>
                          <#if paymentMethod.thruDate??>(${uiLabelMap.CommonDelete}:${paymentMethod.thruDate.toString()})</#if></dd>
                      </td>
                      <td>
                        <a href="<@ofbizUrl>editeftaccount?paymentMethodId=${paymentMethod.paymentMethodId}</@ofbizUrl>" class="btn btn-outline-secondary">
                                ${uiLabelMap.CommonUpdate}</a>
                      </td>
                    </#if>
                    <td>
                      <div class="input-group">
                     <a href="<@ofbizUrl>deletePaymentMethod/viewprofile?paymentMethodId=${paymentMethod.paymentMethodId}</@ofbizUrl>" class="btn btn-outline-secondary mr-2">
                            ${uiLabelMap.CommonExpire}</a>
                      <#if (profiledefs.defaultPayMeth)?default("") == paymentMethod.paymentMethodId>
                        <label>${uiLabelMap.EcommerceIsDefault}</label>
                      <#else>
                        <form name="defaultPaymentMethodForm" method="post" action="<@ofbizUrl>setprofiledefault/viewprofile</@ofbizUrl>">
                          <input type="hidden" name="productStoreId" value="${productStoreId}" />
                          <input type="hidden" name="defaultPayMeth" value="${paymentMethod.paymentMethodId}" />
                          <input type="hidden" name="partyId" value="${party.partyId}" />
                          <input type="submit" value="${uiLabelMap.EcommerceSetDefault}" class="btn btn-outline-secondary" />
                        </form>
                      </#if>
                      </div>
                    </td>
                    <td>

                    </td>
                  </tr>
                </#list>
              </table>
              <#else>
                ${uiLabelMap.AccountingNoPaymentMethodInformation}.
              </#if>
      </div>
    </div>

    <#-- ============================================================= -->
    <div class="card">
      <div class="card-header">
        <div class="row">
          <div class="col-lg-3">
            <strong>${uiLabelMap.CommonUsername} &amp; ${uiLabelMap.CommonPassword}</strong>
          </div>
          <div class="col-lg-9 text-right">
            <a href="<@ofbizUrl>passwordChange</@ofbizUrl>">${uiLabelMap.PartyChangePassword}</a>
          </div>
        </div>
      </div>
      <div class="card-body">
        <dl>
        <dt>${uiLabelMap.CommonUsername}</dt>
        <dd>${userLogin.userLoginId}</dd>
        </dl>
      </div>
    </div>

    <#-- ============================================================= -->
    <form name="setdefaultshipmeth" action="<@ofbizUrl>setprofiledefault/viewprofile</@ofbizUrl>" method="post">
      <input type="hidden" name="productStoreId" value="${productStoreId}" />
      <div class="card">
        <div class="card-header">
          <#if profiledefs?has_content && profiledefs.defaultShipAddr?has_content && carrierShipMethods?has_content><a href="javascript:document.setdefaultshipmeth.submit();" class="submenutextright">${uiLabelMap.EcommerceSetDefault}</a></#if>
          <strong>${uiLabelMap.EcommerceDefaultShipmentMethod}</strong>
        </div>
        <div class="card-body">
          <table class="table table-responsive-sm">
            <#if profiledefs?has_content && profiledefs.defaultShipAddr?has_content && carrierShipMethods?has_content>
              <#list carrierShipMethods as shipMeth>
                <#assign shippingMethod = shipMeth.shipmentMethodTypeId + "@" + shipMeth.partyId />
                <tr>
                  <td></td>
                  <td>
                    <#if shipMeth.partyId != "_NA_">${shipMeth.partyId!}</#if>${shipMeth.get("description",locale)!}
                  </td>
                </tr>
              </#list>
            <#else>
            <tr>
              <td><input type="radio" name="defaultShipMeth" value="${shippingMethod!}" <#if (profiledefs.defaultShipMeth)! == shippingMethod!>checked="checked"</#if> /></td>
              <td>${uiLabelMap.EcommerceDefaultShipmentMethodMsg}</td>
            </tr>
            </#if>
          </table>
        </div>
      </div>
    </form>

    <#-- ============================================================= -->
    <div class="card">
      <div class="card-header">
      <strong>${uiLabelMap.EcommerceFileManager}</strong>
      </div>
      <div class="card-body">
          <#if partyContent?has_content>
            <table class="table table-responsive-sm">
              <#list partyContent as contentRole>
            <#assign content = contentRole.getRelatedOne("Content", false) />
            <#assign contentType = content.getRelatedOne("ContentType", true) />
            <#assign mimeType = content.getRelatedOne("MimeType", true)! />
            <#assign status = content.getRelatedOne("StatusItem", true) />
              <tr>
                <td><a href="<@ofbizUrl>img?imgId=${content.dataResourceId!}</@ofbizUrl>" class="btn btn-link">${content.contentId}</a></td>
                <td>${content.contentName!}</td>
                <td>${(contentType.get("description",locale))!}</td>
                <td>${(mimeType.description)!}</td>
                <td>${(status.get("description",locale))!}</td>
                <td>${contentRole.fromDate!}</td>
                <td>
                  <form name="removeContent_${contentRole.contentId}" method="post" action="removePartyAsset">
                    <input name="partyId" type="hidden" value="${userLogin.partyId}"/>
                    <input name="contentId" type="hidden" value="${contentRole.contentId}"/>
                    <input name="roleTypeId" type="hidden" value="${contentRole.roleTypeId}"/>
                  </form>
                  <a href="<@ofbizUrl>img?imgId=${content.dataResourceId!}</@ofbizUrl>" class="btn btn-outline-secondary">${uiLabelMap.CommonView}</a>
                  <a href="javascript:document.removeContent_${contentRole.contentId}.submit();" class="btn btn-outline-secondary">${uiLabelMap.CommonRemove}</a>
                </td>
              </tr>
            </#list>
          </table>
          <#else>
            <p class="card-text">${uiLabelMap.EcommerceNoFiles}</p>
          </#if>
          <form method="post" enctype="multipart/form-data" action="<@ofbizUrl>uploadPartyContent</@ofbizUrl>">
            <input type="hidden" name="partyId" value="${party.partyId}"/>
            <input type="hidden" name="dataCategoryId" value="PERSONAL"/>
            <input type="hidden" name="contentTypeId" value="DOCUMENT"/>
            <input type="hidden" name="statusId" value="CTNT_PUBLISHED"/>
            <input type="hidden" name="roleTypeId" value="OWNER"/>
            <label class="mr-2">${uiLabelMap.EcommerceUploadNewFile}</label>
            <div class="custom-file mr-2">
              <input type="file" name="uploadedFile" class="custom-file-input" id="customFile" required/>
              <label class="custom-file-label" for="customFile">Choose file</label>
              <div class="invalid-feedback">Example invalid custom file feedback</div>
            </div>
            <script> <#-- This is to replace the "Choose a file" label by the real file name -->
                $('#customFile').on('change',function(){
                    //get the file name
                    var fileName = $(this).val();
                    fileName = fileName.replace('C:\\fakepath\\', " ");
                    //replace the "Choose a file" label
                    $(this).next('.custom-file-label').html(fileName);
                })
            </script>
            <select name="partyContentTypeId" class="custom-select mr-2">
              <option value="">${uiLabelMap.PartySelectPurpose}</option>
              <#list partyContentTypes as partyContentType>
                <option value="${partyContentType.partyContentTypeId}">${partyContentType.get("description", locale)?default(partyContentType.partyContentTypeId)}</option>
              </#list>
            </select>
            <select name="mimeTypeId" class="custom-select mr-2">
              <option value="">${uiLabelMap.PartySelectMimeType}</option>
              <#list mimeTypes as mimeType>
                <option value="${mimeType.mimeTypeId}">${mimeType.get("description", locale)?default(mimeType.mimeTypeId)}</option>
              </#list>
            </select>
            <input type="submit" value="${uiLabelMap.CommonUpload}" class="btn btn-primary"/>
          </form>
      </div>
    </div>

    <#-- ============================================================= -->
    <div class="card">
      <div class="card-header">
      <strong>${uiLabelMap.PartyContactLists}</strong>
      </div>
      <div class="card-body">
        <table class="table table-responsive-sm">
          <tr>
            <th>${uiLabelMap.EcommerceListName}</th>
            <#-- <th >${uiLabelMap.OrderListType}</th> -->
            <th>${uiLabelMap.CommonFromDate}</th>
            <th>${uiLabelMap.CommonThruDate}</th>
            <th>${uiLabelMap.CommonStatus}</th>
            <th>${uiLabelMap.CommonEmail}</th>
            <th>${uiLabelMap.MarketingContactListOptInVerifyCode}</th>
            <th></th>
          </tr>
          <#list contactListPartyList as contactListParty>
          <#assign contactList = contactListParty.getRelatedOne("ContactList", false)! />
          <#assign statusItem = contactListParty.getRelatedOne("StatusItem", true)! />
          <#assign emailAddress = contactListParty.getRelatedOne("PreferredContactMech", true)! />
          <#-- <#assign contactListType = contactList.getRelatedOne("ContactListType", true)/> -->
          <tr>
            <td>${contactList.contactListName!}<#if contactList.description?has_content>&nbsp;-&nbsp;${contactList.description}</#if></td>
            <#-- <td><div>${contactListType.get("description",locale)!}</div></td> -->
            <td>${contactListParty.fromDate!}</td>
            <td>${contactListParty.thruDate!}</td>
            <td>${(statusItem.get("description",locale))!}</td>
            <td>${emailAddress.infoString!}</td>
            <td>
              <#if ("CLPT_ACCEPTED" == contactListParty.statusId!)>
                <form method="post" class="form-inline" action="<@ofbizUrl>updateContactListParty</@ofbizUrl>" name="clistRejectForm${contactListParty_index}">
                  <#assign productStoreId = Static["org.apache.ofbiz.product.store.ProductStoreWorker"].getProductStoreId(request) />
                  <input type="hidden" name="productStoreId" value="${productStoreId!}" />
                  <input type="hidden" name="partyId" value="${party.partyId}"/>
                  <input type="hidden" name="contactListId" value="${contactListParty.contactListId}"/>
                  <input type="hidden" name="preferredContactMechId" value="${contactListParty.preferredContactMechId}"/>
                  <input type="hidden" name="fromDate" value="${contactListParty.fromDate}"/>
                  <input type="hidden" name="statusId" value="CLPT_REJECTED"/>
                  <input type="submit" value="${uiLabelMap.EcommerceUnsubscribe}" class="btn btn-outline-secondary"/>
                </form>
              <#elseif ("CLPT_PENDING" == contactListParty.statusId!)>
                <form method="post" class="form-inline" action="<@ofbizUrl>updateContactListParty</@ofbizUrl>" name="clistAcceptForm${contactListParty_index}">
                  <input type="hidden" name="partyId" value="${party.partyId}"/>
                  <input type="hidden" name="contactListId" value="${contactListParty.contactListId}"/>
                  <input type="hidden" name="preferredContactMechId" value="${contactListParty.preferredContactMechId}"/>
                  <input type="hidden" name="fromDate" value="${contactListParty.fromDate}"/>
                  <input type="hidden" name="statusId" value="CLPT_ACCEPTED"/>
                  <div class="btn-group">
                  <input type="text" class="form-control mr-2" name="optInVerifyCode" value=""/>
                  <input type="submit" value="${uiLabelMap.EcommerceVerifySubscription}" class="btn btn-outline-secondary"/>
                  </div>
                </form>
              <#elseif ("CLPT_REJECTED" == contactListParty.statusId!)>
                <form method="post" class="form-inline" action="<@ofbizUrl>updateContactListParty</@ofbizUrl>" name="clistPendForm${contactListParty_index}">
                  <input type="hidden" name="partyId" value="${party.partyId}"/>
                  <input type="hidden" name="contactListId" value="${contactListParty.contactListId}"/>
                  <input type="hidden" name="preferredContactMechId" value="${contactListParty.preferredContactMechId}"/>
                  <input type="hidden" name="fromDate" value="${contactListParty.fromDate}"/>
                  <input type="hidden" name="statusId" value="CLPT_PENDING"/>
                  <input type="submit" value="${uiLabelMap.EcommerceSubscribe}" class="btn btn-outline-secondary"/>
                </form>
              </#if>
            </td>
          </tr>
          </#list>
        </table>
          <form method="post" class="form-inline" action="<@ofbizUrl>createContactListParty</@ofbizUrl>" name="clistPendingForm">
            <input type="hidden" name="partyId" value="${party.partyId}"/>
            <label class="mr-2">${uiLabelMap.EcommerceNewListSubscription}: </label>
            <input type="hidden" name="statusId" value="CLPT_PENDING"/>
            <select name="contactListId" class="custom-select mr-2">
              <#list publicContactLists as publicContactList>
                <#-- <#assign publicContactListType = publicContactList.getRelatedOne("ContactListType", true)> -->
                <#assign publicContactMechType = publicContactList.getRelatedOne("ContactMechType", true)! />
                <option value="${publicContactList.contactListId}">${publicContactList.contactListName!} <#-- ${publicContactListType.get("description",locale)} --> <#if publicContactMechType?has_content>[${publicContactMechType.get("description",locale)}]</#if></option>
              </#list>
            </select>
            <select name="preferredContactMechId" class="custom-select mr-2">
            <#-- <option></option> -->
              <#list partyAndContactMechList as partyAndContactMech>
                <option value="${partyAndContactMech.contactMechId}"><#if partyAndContactMech.infoString?has_content>${partyAndContactMech.infoString}<#elseif partyAndContactMech.tnContactNumber?has_content>${partyAndContactMech.tnCountryCode!}-${partyAndContactMech.tnAreaCode!}-${partyAndContactMech.tnContactNumber}<#elseif partyAndContactMech.paAddress1?has_content>${partyAndContactMech.paAddress1}, ${partyAndContactMech.paAddress2!}, ${partyAndContactMech.paCity!}, ${partyAndContactMech.paStateProvinceGeoId!}, ${partyAndContactMech.paPostalCode!}, ${partyAndContactMech.paPostalCodeExt!} ${partyAndContactMech.paCountryGeoId!}</#if></option>
              </#list>
            </select>
            <input type="submit" value="${uiLabelMap.EcommerceSubscribe}" class="btn btn-outline-secondary"/>
          </form>
        <label class="mt-2">${uiLabelMap.EcommerceListNote}</label>
      </div>
    </div>

    <#-- ============================================================= -->
    <#if surveys?has_content>
    <div class="card">
      <div class="card-header">
        <strong>${uiLabelMap.EcommerceSurveys}</strong>
      </div>
      <div class="card-body">
        <table class="table table-responsive-sm">
          <#list surveys as surveyAppl>
            <#assign survey = surveyAppl.getRelatedOne("Survey", false) />
            <tbody>
            <tr>
              <td>${survey.surveyName!}&nbsp;-&nbsp;${survey.description!}</td>
              <td>
                <#assign responses = Static["org.apache.ofbiz.product.store.ProductStoreWorker"].checkSurveyResponse(request, survey.surveyId)?default(0)>
                <#if (responses < 1)>${uiLabelMap.EcommerceNotCompleted}<#else>${uiLabelMap.EcommerceCompleted}</#if>
              </td>
              <#if (responses == 0 || "Y"  == survey.allowMultiple?default("N"))>
                <#assign surveyLabel = uiLabelMap.EcommerceTakeSurvey />
                <#if (responses > 0 && "Y"  == survey.allowUpdate?default("N"))>
                  <#assign surveyLabel = uiLabelMap.EcommerceUpdateSurvey />
                </#if>
                <td><a href="<@ofbizUrl>takesurvey?productStoreSurveyId=${surveyAppl.productStoreSurveyId}</@ofbizUrl>" class="btn btn-outline-secondary">${surveyLabel}</a></td>
              </#if>
            </tr>
            </tbody>
          </#list>
        </table>
      </div>
    </div>
    </#if>

    <#-- ============================================================= -->
    <#-- only 5 messages will show; edit the ViewProfile.groovy to change this number -->
    ${screens.render("component://ecommerce/widget/CustomerScreens.xml#messagelist-include")}

    ${screens.render("component://ecommerce/widget/CustomerScreens.xml#FinAccountList-include")}

    <#-- Serialized Inventory Summary -->
    ${screens.render('component://ecommerce/widget/CustomerScreens.xml#SerializedInventorySummary')}

    <#-- Subscription Summary -->
    ${screens.render('component://ecommerce/widget/CustomerScreens.xml#SubscriptionSummary')}

    <#-- Reviews -->
    ${screens.render('component://ecommerce/widget/CustomerScreens.xml#showProductReviews')}
<#else>
    <#if userLogin??>
        <h3>${uiLabelMap.PartyNoPartyForCurrentUserName}: ${userLogin.userLoginId}</h3>
    </#if>
</#if>
