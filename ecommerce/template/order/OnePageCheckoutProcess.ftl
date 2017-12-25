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

<div>
  <#assign shoppingCart = sessionAttributes.shoppingCart! />
    <h3>${uiLabelMap.EcommerceOnePageCheckout}</h3>
  <#if shoppingCart?has_content && shoppingCart.size() &gt; 0>
    <div id="checkoutPanel">

    <#-- ========================================================================================================================== -->
      <div id="cartPanel" class="screenlet">
      ${screens.render("component://ecommerce/widget/CartScreens.xml#UpdateCart")}
      </div>

    <#-- ========================================================================================================================== -->
      <div id="shippingPanel" class="card">
        <div class="card-header bg-info text-white">${uiLabelMap.EcommerceStep} 2: ${uiLabelMap.FacilityShipping}</div>
        <div id="shippingSummaryPanel" class="card-body" style="display: none;">
          <div id="shippingCompleted">
            <h4>${uiLabelMap.OrderShipTo}</h4>
            <ul>
              <li id="completedShipToAttn"></li>
              <li id="completedShippingContactNumber"></li>
              <li id="completedEmailAddress"></li>
            </ul>
            <h4>${uiLabelMap.EcommerceLocation}</h4>
            <ul>
              <li id="completedShipToAddress1"></li>
              <li id="completedShipToAddress2"></li>
              <li id="completedShipToGeo"></li>
            </ul>
          </div>
          <a href="javascript:void(0);" id="openShippingPanel" class="btn btn-outline-secondary">
            ${uiLabelMap.EcommerceClickHereToEdit}
          </a>
        </div>

      <#-- ============================================================= -->
        <div id="editShippingPanel" class="card-body" style="display: none;">
          <form id="shippingForm" action="<@ofbizUrl>createUpdateShippingAddress</@ofbizUrl>" method="post">
              <input type="hidden" id="shipToContactMechId" name="shipToContactMechId" value="${shipToContactMechId!}"/>
              <input type="hidden" id="billToContactMechIdInShipingForm" name="billToContactMechId"
                     value="${billToContactMechId!}"/>
              <input type="hidden" id="shipToPartyId" name="partyId" value="${partyId!}"/>
              <input type="hidden" id="shipToPhoneContactMechId" name="shipToPhoneContactMechId"
                     value="${(shipToTelecomNumber.contactMechId)!}"/>
              <input type="hidden" id="emailContactMechId" name="emailContactMechId" value="${emailContactMechId!}"/>
              <input type="hidden" name="shipToName" value="${shipToName!}"/>
              <input type="hidden" name="shipToAttnName" value="${shipToAttnName!}"/>
              <#if userLogin??>
                <input type="hidden" name="keepAddressBook" value="Y"/>
                <input type="hidden" name="setDefaultShipping" value="Y"/>
                <input type="hidden" name="userLoginId" id="userLoginId" value="${userLogin.userLoginId!}"/>
                <#assign productStoreId = Static["org.apache.ofbiz.product.store.ProductStoreWorker"].getProductStoreId(request) />
                <input type="hidden" name="productStoreId" value="${productStoreId!}"/>
              <#else>
                <input type="hidden" name="keepAddressBook" value="N"/>
              </#if>
              <div id="shippingFormServerError" class="errorMessage"></div>

              <div class="form-row">
                <div class="form-group col-md-6">
                  <label for="firstName">${uiLabelMap.PartyFirstName}*</label>
                  <span id="advice-required-firstName" style="display: none" class="errorMessage">
                    (${uiLabelMap.CommonRequired})
                  </span>
                  <input id="firstName" name="firstName" class="required form-control" type="text" value="${firstName!}"/>
                </div>
                <div class="form-group col-md-6">
                  <label for="lastName">${uiLabelMap.PartyLastName}*
                  <span id="advice-required-lastName" style="display:none" class="errorMessage">
                    (${uiLabelMap.CommonRequired})
                  </span>
                  </label>
                  <input id="lastName" name="lastName" class="required form-control" type="text" value="${lastName!}"/>
                </div>
              </div>
              <div class="form-row">
                <#if shipToTelecomNumber?has_content>
                  <div class="form-group col-md-2">
                    <label for="shipToCountryCode">${uiLabelMap.CommonCountry}*
                      <span id="advice-required-shipToCountryCode" style="display:none" class="errorMessage">
                        (${uiLabelMap.CommonRequired})
                      </span>
                    </label>
                    <input type="text" name="shipToCountryCode" class="form-control required" id="shipToCountryCode"
                           value="${shipToTelecomNumber.countryCode!}" maxlength="10"/>
                  </div>
                  <div class="form-group col-md-2">
                    <label for="shipToAreaCode">${uiLabelMap.PartyAreaCode}*
                      <span id="advice-required-shipToAreaCode" style="display:none" class="errorMessage">
                        (${uiLabelMap.CommonRequired})
                      </span>
                    </label>
                    <input type="text" name="shipToAreaCode" class="form-control required" id="shipToAreaCode"
                        value="${shipToTelecomNumber.areaCode!}" maxlength="10"/>
                  </div>
                  <div class="form-group col-md-2">
                      <label for="shipToContactNumber">
                        ${uiLabelMap.PartyContactNumber}*
                        <span id="advice-required-shipToContactNumber" style="display:none" class="errorMessage">
                          (${uiLabelMap.CommonRequired})
                        </span>
                      </label>
                      <input type="text" name="shipToContactNumber" class="form-control required" id="shipToContactNumber"
                          value="${shipToTelecomNumber.contactNumber!}" maxlength="15"/>
                  </div>
                  <div class="form-group col-md-2">
                    <label for="shipToExtension">${uiLabelMap.PartyExtension}</label>
                    <input type="text" class="form-control" name="shipToExtension" id="shipToExtension" value="${shipToExtension!}"
                        size="5" maxlength="10"/>
                  </div>
                <#else>
                  <div class="form-group col-md-2">
                    <label for="shipToCountryCode">${uiLabelMap.CommonCountry}*
                      <span id="advice-required-shipToCountryCode" style="display:none" class="errorMessage">
                        (${uiLabelMap.CommonRequired})
                      </span>
                    </label>
                      <input type="text" name="shipToCountryCode" class="form-control required" id="shipToCountryCode"
                          value="${parameters.shipToCountryCode!}" maxlength="10"/> -
                  </div>
                  <div class="form-group col-md-2">
                    <label for="shipToAreaCode">${uiLabelMap.PartyAreaCode}*
                      <span id="advice-required-shipToAreaCode" style="display:none" class="errorMessage">
                        (${uiLabelMap.CommonRequired})
                      </span>
                    </label>
                    <input type="text" name="shipToAreaCode" class="form-control required" id="shipToAreaCode"
                        value="${parameters.shipToAreaCode!}" maxlength="10"/>
                  </div>
                  <div class="form-group col-md-4">
                    <label for="shipToContactNumber">${uiLabelMap.PartyContactNumber}*
                      <span id="advice-required-shipToContactNumber" style="display:none" class="errorMessage">
                        (${uiLabelMap.CommonRequired})
                      </span>
                    </label>
                    <input type="text" name="shipToContactNumber" class="form-control required" id="shipToContactNumber"
                        value="${parameters.shipToContactNumber!}" maxlength="15"/>
                  </div>
                  <div class="form-group col-md-2">
                    <label for="shipToExtension">${uiLabelMap.PartyExtension}</label>
                    <input type="text" class="form-control" name="shipToExtension" id="shipToExtension"
                        value="${parameters.shipToExtension!}" maxlength="10"/>
                  </div>
                </#if>
                <div class="form-group col-md-4">
                  <label for="emailAddress">${uiLabelMap.PartyEmailAddress}*
                    <span id="advice-required-emailAddress" style="display:none" class="errorMessage">
                      (${uiLabelMap.CommonRequired})
                    </span>
                  </label>
                  <input id="emailAddress" name="emailAddress" class="form-control required validate-email" maxlength="255"
                      type="text" value="${emailAddress!}"/>
                </div>
              </div>
              <div class="form-row">
                <div class="form-group col-md-6">
                  <label for="shipToAddress1">${uiLabelMap.PartyAddressLine1}*
                    <span id="advice-required-shipToAddress1" class="custom-advice errorMessage" style="display:none">
                      (${uiLabelMap.CommonRequired})
                    </span>
                  </label>
                  <input id="shipToAddress1" name="shipToAddress1" class="form-control required" type="text"
                      value="${shipToAddress1!}" maxlength="255"/>
                </div>
                <div class="form-group col-md-6">
                  <label for="shipToAddress2">${uiLabelMap.PartyAddressLine2}</label>
                  <input id="shipToAddress2" class="form-control" name="shipToAddress2" type="text" value="${shipToAddress2!}"
                      maxlength="255"/>
                </div>
              </div>
              <div class="form-row">
                <div class="form-group col-md-3">
                  <label for="shipToCity">${uiLabelMap.CommonCity}*
                    <span id="advice-required-shipToCity" class="custom-advice errorMessage" style="display:none">
                      (${uiLabelMap.CommonRequired})
                    </span>
                  </label>
                  <input id="shipToCity" name="shipToCity" class="form-control required" type="text" value="${shipToCity!}"
                      maxlength="255" size="40"/>
                </div>
                <div class="form-group col-md-3">
                  <label for="shipToPostalCode">${uiLabelMap.PartyZipCode}*
                    <span id="advice-required-shipToPostalCode" class="custom-advice errorMessage" style="display:none">
                      (${uiLabelMap.CommonRequired})
                    </span>
                  </label>
                  <input id="shipToPostalCode" name="shipToPostalCode" class="form-control required" type="text"
                      value="${shipToPostalCode!}" size="12" maxlength="10"/>
                </div>
                <div class="form-group col-md-3">
                  <label for="shipToCountryGeoId">${uiLabelMap.CommonCountry}*
                    <span id="advice-required-shipToCountryGeo" style="display:none" class="errorMessage">
                      (${uiLabelMap.CommonRequired})
                    </span>
                  </label>
                  <select name="shipToCountryGeoId" class="form-control" id="shipToCountryGeoId">
                    <#if shipToCountryGeoId??>
                      <option value="${shipToCountryGeoId!}">
                        ${shipToCountryProvinceGeo?default(shipToCountryGeoId!)}
                      </option>
                    </#if>
                    ${screens.render("component://common/widget/CommonScreens.xml#countries")}
                  </select>
                </div>
                <div class="form-group col-md-3">
                  <label for="shipToStateProvinceGeoId">${uiLabelMap.CommonState}*
                    <span id="advice-required-shipToStateProvinceGeoId" style="display:none"
                      class="errorMessage">(${uiLabelMap.CommonRequired})</span>
                  </label>
                  <select id="shipToStateProvinceGeoId" class="form-control" name="shipToStateProvinceGeoId">
                    <#if shipToStateProvinceGeoId?has_content>
                      <option value='${shipToStateProvinceGeoId!}'>
                        ${shipToStateProvinceGeo?default(shipToStateProvinceGeoId!)}
                      </option>
                    <#else>
                      <option value="_NA_">${uiLabelMap.PartyNoState}</option>
                    </#if>
                    ${screens.render("component://common/widget/CommonScreens.xml#states")}
                  </select>
                </div>
              </div>
              <a href="javascript:void(0);" class="btn btn-primary" id="savePartyAndShippingContact">
                ${uiLabelMap.EcommerceContinueToStep} 3
              </a>
              <a style="display:none" class="button" href="javascript:void(0);" id="processingShippingOptions">
                ${uiLabelMap.EcommercePleaseWait}....
              </a>
          </form>
        </div>
      </div>

    <#-- ========================================================================================================================== -->
      <div id="shippingOptionPanel" class="card">
        <div class="card-header bg-info text-white">${uiLabelMap.EcommerceStep} 3: ${uiLabelMap.PageTitleShippingOptions}</div>
        <div id="shippingOptionSummaryPanel" class="card-body" style="display: none;">
          <div class="completed" id="shippingOptionCompleted">
            <ul>
              <li>${uiLabelMap.CommonMethod}</li>
              <li id="selectedShipmentOption"></li>
            </ul>
          </div>
          <a href="javascript:void(0);" id="openShippingOptionPanel"
             class="btn btn-outline-secondary">${uiLabelMap.EcommerceClickHereToEdit}</a>
        </div>

      <#-- ============================================================= -->
        <div id="editShippingOptionPanel" class="card-body" style="display: none;">
          <form id="shippingOptionForm" action="<@ofbizUrl></@ofbizUrl>" method="post">
              <div id="shippingOptionFormServerError" class="errorMessage"></div>
              <div class="form-group">
                <label for="shipMethod">${uiLabelMap.OrderSelectShippingMethod}*
                  <span id="advice-required-shipping_method" class="custom-advice"
                        style="display:none"> (${uiLabelMap.CommonRequired})</span>
                </label>
                <select id="shipMethod" name="shipMethod" class="form-control required">
                  <option value=""></option>
                </select>
              </div>
              <a href="javascript:void(0);" class="btn btn-primary" id="saveShippingMethod">
                ${uiLabelMap.EcommerceContinueToStep} 4
              </a>
              <a style="display:none" class="button" href="javascript:void(0);" id="processingBilling">
                ${uiLabelMap.EcommercePleaseWait}....
              </a>
          </form>
        </div>
      </div>

    <#-- ========================================================================================================================== -->
      <div id="billingPanel" class="card">
        <div class="card-header bg-info text-white">${uiLabelMap.EcommerceStep} 4: ${uiLabelMap.AccountingBilling}</div>
        <div id="billingSummaryPanel" class="card-body" style="display: none;">
          <a href="javascript:void(0);" id="openBillingPanel" class="button">${uiLabelMap.EcommerceClickHereToEdit}</a>
          <div class="completed" id="billingCompleted">
            <h4>${uiLabelMap.OrderBillUpTo}</h4>
            <ul>
              <li id="completedBillToAttn"></li>
              <li id="completedBillToPhoneNumber"></li>
              <li id="paymentMethod"></li>
              <li id="completedCCNumber"></li>
              <li id="completedExpiryDate"></li>
            </ul>
            <h4>${uiLabelMap.EcommerceLocation}</h4>
            <ul>
              <li id="completedBillToAddress1"></li>
              <li id="completedBillToAddress2"></li>
              <li id="completedBillToGeo"></li>
            </ul>
          </div>
        </div>

      <#-- ============================================================= -->

        <div id="editBillingPanel" class="card-body" style="display: none;">
          <form id="billingForm" class="theform" action="<@ofbizUrl></@ofbizUrl>" method="post">
              <input type="hidden" id="billToContactMechId" name="billToContactMechId" value="${billToContactMechId!}"/>
              <input type="hidden" id="shipToContactMechIdInBillingForm" name="shipToContactMechId"
                     value="${shipToContactMechId!}"/>
              <input type="hidden" id="paymentMethodId" name="paymentMethodId" value="${paymentMethodId!}"/>
              <input type="hidden" id="paymentMethodTypeId" name="paymentMethodTypeId"
                     value="${paymentMethodTypeId?default("CREDIT_CARD")}"/>
              <input type="hidden" id="billToPartyId" name="partyId" value="${parameters.partyId!}"/>
              <input type="hidden" name="expireDate" value="${expireDate!}"/>
              <input type="hidden" id="billToPhoneContactMechId" name="billToPhoneContactMechId"
                     value="${(billToTelecomNumber.contactMechId)!}"/>
              <input type="hidden" name="billToName" value="${billToName!}"/>
              <input type="hidden" name="billToAttnName" value="${billToAttnName!}"/>
              <#if userLogin??>
                <input type="hidden" name="keepAddressBook" value="Y"/>
                <input type="hidden" name="setDefaultBilling" value="Y"/>
                <#assign productStoreId = Static["org.apache.ofbiz.product.store.ProductStoreWorker"].getProductStoreId(request) />
                <input type="hidden" name="productStoreId" value="${productStoreId!}"/>
              <#else>
                <input type="hidden" name="keepAddressBook" value="N"/>
              </#if>
              <div id="billingFormServerError" class="errorMessage"></div>
              <div class="form-row">
                <div class="form-group col-md-6">
                  <label for="firstNameOnCard">${uiLabelMap.PartyFirstName}*
                    <span id="advice-required-firstNameOnCard" style="display: none;" class="errorMessage">
                      (${uiLabelMap.CommonRequired})
                    </span>
                  </label>
                  <input id="firstNameOnCard" name="firstNameOnCard" class="form-control required" type="text"
                      value="${firstNameOnCard!}"/>
                </div>
                <div class="form-group col-md-6">
                  <label for="lastNameOnCard">${uiLabelMap.PartyLastName}*
                    <span id="advice-required-lastNameOnCard" style="display: none;"
                        class="errorMessage"> (${uiLabelMap.CommonRequired})</span>
                  </label>
                  <input id="lastNameOnCard" name="lastNameOnCard" class="form-control required" type="text"
                      value="${lastNameOnCard!}"/>
                </div>
              </div>
              <div class="form-row">
                <#if billToTelecomNumber?has_content>
                  <div class="form-group col-md-3">
                    <label for="billToCountryCode">${uiLabelMap.CommonCountry}*
                      <span id="advice-required-billToCountryCode" style="display:none"
                          class="errorMessage"> (${uiLabelMap.CommonRequired})</span>
                    </label>
                    <input type="text" name="billToCountryCode" class="required" id="billToCountryCode"
                        value="${billToTelecomNumber.countryCode!}" size="5" maxlength="10"/> -
                  </div>
                  <div class="form-group col-md-3">
                    <label for="billToAreaCode">${uiLabelMap.PartyAreaCode}*
                        <span id="advice-required-billToAreaCode" style="display:none" class="errorMessage">
                          (${uiLabelMap.CommonRequired})
                        </span>
                    </label>
                    <input type="text" name="billToAreaCode" class="required" id="billToAreaCode"
                        value="${billToTelecomNumber.areaCode!}" size="5" maxlength="10"/> -
                  </div>
                  <div class="form-group col-md-3">
                    <label for="billToContactNumber">${uiLabelMap.PartyContactNumber}*
                      <span id="advice-required-billToContactNumber" style="display:none"
                          class="errorMessage"> (${uiLabelMap.CommonRequired})</span>
                    </label>
                    <input type="text" name="billToContactNumber" class="required" id="billToContactNumber"
                        value="${billToTelecomNumber.contactNumber!}" size="10" maxlength="15"/> -
                  </span>
                  <div class="form-group col-md-3">
                    <label for="billToExtension">${uiLabelMap.PartyExtension}</label>
                    <input type="text" name="billToExtension" id="billToExtension"
                        value="${billToExtension!}" size="5" maxlength="10"/>
                  </div>
                <#else>
                  <div class="form-group col-md-3">
                    <label for="billToCountryCode">${uiLabelMap.CommonCountry}*
                      <span id="advice-required-billToCountryCode" style="display:none" class="errorMessage">
                        (${uiLabelMap.CommonRequired})
                      </span>
                    </label>
                    <input type="text" name="billToCountryCode" class="form-control required" id="billToCountryCode"
                        value="${parameters.billToCountryCode!}" maxlength="10"/>
                  </div>
                  <div class="form-group col-md-3">
                    <label for="billToAreaCode">${uiLabelMap.PartyAreaCode}*
                        <span id="advice-required-billToAreaCode" style="display:none" class="errorMessage">
                          (${uiLabelMap.CommonRequired})
                        </span>
                    </label>
                    <input type="text" name="billToAreaCode" class="form-control required" id="billToAreaCode"
                        value="${parameters.billToAreaCode!}" maxlength="10"/>
                  </div>
                  <div class="form-group col-md-3">
                    <label for="billToContactNumber">${uiLabelMap.PartyContactNumber}*
                      <span id="advice-required-billToContactNumber" style="display:none"
                          class="errorMessage"> (${uiLabelMap.CommonRequired})</span>
                    </label>
                    <input type="text" name="billToContactNumber" class="form-control required" id="billToContactNumber"
                        value="${parameters.billToContactNumber!}" maxlength="15"/>
                  </div>
                  <div class="form-group col-md-3">
                    <label for="billToExtension">${uiLabelMap.PartyExtension}</label>
                    <input type="text" class="form-control" name="billToExtension" id="billToExtension"
                        value="${parameters.billToExtension!}" maxlength="10"/>
                  </div>
                </#if>
              </div>
              <div class="form-row">
                <div class="form-group col-md-2">
                  <label for="cardType">${uiLabelMap.AccountingCardType}*
                    <span id="advice-required-cardType" style="display: none;" class="errorMessage">
                      (${uiLabelMap.CommonRequired})
                    </span>
                  </label>
                  <select name="cardType" id="cardType" class="form-control">
                    <#if cardType?has_content>
                      <option label="${cardType!}" value="${cardType!}">${cardType!}</option>
                    </#if>
                    ${screens.render("component://common/widget/CommonScreens.xml#cctypes")}
                  </select>
                </div>
                <div class="form-group col-md-4">
                  <label for="cardNumber">${uiLabelMap.AccountingCardNumber}*
                    <span id="advice-required-cardNumber" style="display: none;" class="errorMessage">
                      (${uiLabelMap.CommonRequired})
                    </span>
                  </label>
                  <input id="cardNumber" name="cardNumber" class="form-control required creditcard" type="text"
                      value="${cardNumber!}" maxlength="16"/>
                </div>
                <div class="form-group col-md-2">
                  <label for="billToCardSecurityCode">CVV2</label>
                  <input id="billToCardSecurityCode" class="form-control" name="billToCardSecurityCode" type="text"
                      maxlength="4" value=""/>
                </div>
                <div class="form-group col-md-2">
                  <label for="expMonth">${uiLabelMap.CommonMonth}:*
                    <span id="advice-required-expMonth" style="display:none" class="errorMessage">
                      (${uiLabelMap.CommonRequired})
                    </span>
                  </label>
                  <select id="expMonth" name="expMonth" class="form-control required">
                    <#if expMonth?has_content>
                      <option label="${expMonth!}" value="${expMonth!}">${expMonth!}</option>
                    </#if>
                    ${screens.render("component://common/widget/CommonScreens.xml#ccmonths")}
                  </select>
                </div>
                <div class="form-group col-md-2">
                  <label for="expYear">${uiLabelMap.CommonYear}:*
                      <span id="advice-required-expYear" style="display:none" class="errorMessage">
                        (${uiLabelMap.CommonRequired})
                      </span>
                  </label>
                  <select id="expYear" name="expYear" class="form-control required">
                    <#if expYear?has_content>
                      <option value="${expYear!}">${expYear!}</option>
                    </#if>
                    ${screens.render("component://common/widget/CommonScreens.xml#ccyears")}
                  </select>
                </div>
              </div>
              <div>
                <input class="checkbox" id="useShippingAddressForBilling" name="useShippingAddressForBilling"
                       type="checkbox" value="Y"
                       <#if useShippingAddressForBilling?has_content && useShippingAddressForBilling?default("")=="Y">checked="checked"</#if>/>
                <label for="useShippingAddressForBilling">${uiLabelMap.FacilityBillingAddressSameShipping}</label>
              </div>
              <div id="billingAddress"
                  <#if useShippingAddressForBilling?has_content && useShippingAddressForBilling?default("")=="Y">style="display:none"</#if>>
                <div class="form-row">
                  <div class="form-group col-md-6">
                    <label for="billToAddress1">${uiLabelMap.PartyAddressLine1}*
                      <span id="advice-required-billToAddress1" style="display:none"
                        class="errorMessage"> (${uiLabelMap.CommonRequired})
                      </span>
                    </label>
                    <input id="billToAddress1" name="billToAddress1" class="form-control required" size="30" type="text"
                        value="${billToAddress1!}"/>
                  </div>
                  <div class="form-group col-md-6">
                    <label for="billToAddress2">${uiLabelMap.PartyAddressLine2}</label>
                    <input id="billToAddress2" name="billToAddress2" class="form-control" type="text" value="${billToAddress2!}" size="30"/>
                  </div>
                </div>
                <div class="form-row">
                <div class="form-group col-md-3">
                  <label for="billToCity">${uiLabelMap.CommonCity}*
                    <span id="advice-required-billToCity" style="display:none" class="errorMessage">
                      (${uiLabelMap.CommonRequired})
                    </span>
                  </label>
                  <input id="billToCity" name="billToCity" class="form-control required" type="text" value="${billToCity!}"/>
                </div>
                <div class="form-group col-md-3">
                  <label for="billToPostalCode">${uiLabelMap.PartyZipCode}*
                    <span id="advice-required-billToPostalCode" style="display:none" class="errorMessage">
                      (${uiLabelMap.CommonRequired})
                    </span>
                  </label>
                  <input id="billToPostalCode" name="billToPostalCode" class="form-control required" type="text"
                      value="${billToPostalCode!}" maxlength="10"/>
                </div>
                <div class="form-group col-md-3">
                  <label for="billToCountryGeoId">${uiLabelMap.CommonCountry}*
                    <span id="advice-required-billToCountryGeoId" style="display:none" class="errorMessage">
                      (${uiLabelMap.CommonRequired})
                    </span>
                  </label>
                  <select name="billToCountryGeoId" id="billToCountryGeoId" class="form-control">
                    <#if billToCountryGeoId??>
                      <option value='${billToCountryGeoId!}'>
                        ${billToCountryProvinceGeo?default(billToCountryGeoId!)}
                      </option>
                    </#if>
                    ${screens.render("component://common/widget/CommonScreens.xml#countries")}
                  </select>
                </div>
                <div class="form-group col-md-3">
                  <label for="billToStateProvinceGeoId">${uiLabelMap.CommonState}*
                    <span id="advice-required-billToStateProvinceGeoId" style="display:none"
                        class="errorMessage"> (${uiLabelMap.CommonRequired})</span>
                  </label>
                  <select id="billToStateProvinceGeoId" name="billToStateProvinceGeoId" class="form-control">
                    <#if billToStateProvinceGeoId?has_content>
                      <option value='${billToStateProvinceGeoId!}'>
                        ${billToStateProvinceGeo?default(billToStateProvinceGeoId!)}
                      </option>
                    <#else>
                      <option value="_NA_">${uiLabelMap.PartyNoState}</option>
                    </#if>
                  </select>
                </div>
                </div>
              </div>
              <a href="javascript:void(0);" class="btn btn-primary" id="savePaymentAndBillingContact">
                ${uiLabelMap.EcommerceContinueToStep} 5
              </a>
              <a href="javascript:void(0);" class="button" style="display: none;" id="processingOrderSubmitPanel">
                ${uiLabelMap.EcommercePleaseWait}....
              </a>
          </form>
        </div>
      </div>

    <#-- ========================================================================================================================== -->
      <div class="card">
        <div class="card-header bg-info text-white">${uiLabelMap.EcommerceStep} 5: ${uiLabelMap.OrderSubmitOrder}</div>
        <div id="orderSubmitPanel" style="display: none;" class="card-body">
          <form id="orderSubmitForm" action="<@ofbizUrl>onePageProcessOrder</@ofbizUrl>" method="post">
              <input type="button" id="processOrderButton" class="btn btn-primary" name="processOrderButton"
                  value="${uiLabelMap.OrderSubmitOrder}"/>
              <input type="button" style="display: none;" id="processingOrderButton" name="processingOrderButton"
                  value="${uiLabelMap.OrderSubmittingOrder}"/>
          </form>
        </div>
      </div>
    </div>
  </#if>

<#-- ========================================================================================================================== -->
  <div id="emptyCartCheckoutPanel" <#if shoppingCart?has_content && shoppingCart.size() &gt; 0>
       style="display: none;"</#if>>
    <h3>${uiLabelMap.EcommerceStep} 1: ${uiLabelMap.PageTitleShoppingCart}</h3>
    <span>You currently have no items in your cart. Click
      <a href="<@ofbizUrl>main</@ofbizUrl>">here</a> to view our products.
    </span>
    <h4>${uiLabelMap.EcommerceStep} 2: ${uiLabelMap.FacilityShipping}</h4>
    <h4>${uiLabelMap.EcommerceStep} 3: ${uiLabelMap.PageTitleShippingOptions}</h4>
    <h4>${uiLabelMap.EcommerceStep} 4: ${uiLabelMap.AccountingBilling}</h4>
    <h4>${uiLabelMap.EcommerceStep} 5: ${uiLabelMap.OrderSubmitOrder}</h4>
  </div>
</div>
