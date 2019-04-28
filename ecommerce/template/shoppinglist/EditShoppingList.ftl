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

<script type="text/javascript">
//<![CDATA[
    <!-- function to add extra info for Timestamp format -->
    function TimestampSubmit(obj) {
       reservStartStr = jQuery(obj).find("input[name='reservStartStr']");
       val1 = reservStartStr.val();
       reservStart = jQuery(obj).find("input[name='reservStart']");
       if (reservStartStr.val().length == 10) {
           reservStart.val(reservStartStr.val() + " 00:00:00.000000000");
       } else {
           reservStart.val(reservStartStr.val());
       }
       jQuery(obj).submit();
    }
    
    function callDocumentByPaginate(info) {
        var str = info.split('~');
        var checkUrl = '<@ofbizUrl>showShoppingListAjaxFired</@ofbizUrl>';
        if(checkUrl.search("http"))
            var ajaxUrl = '<@ofbizUrl>showShoppingListAjaxFired</@ofbizUrl>';
        else
            var ajaxUrl = '<@ofbizUrl>showShoppingListAjaxFiredSecure</@ofbizUrl>';
        //jQuerry Ajax Request
        jQuery.ajax({
            url: ajaxUrl,
            type: 'POST',
            data: {"shoppingListId" : str[0], "VIEW_SIZE" : str[1], "VIEW_INDEX" : str[2]},
            error: function(msg) {
                alert("An error occurred loading content! : " + msg);
            },
            success: function(msg) {
                jQuery('#div3').html(msg);
            }
        });
     }
//]]>
</script>
<#macro paginationControls>
  <#assign viewIndexMax = Static["java.lang.Math"].ceil((listSize)?double / viewSize?double)>
  <#if (viewIndexMax?int > 0)>
        <#-- Start Page Select Drop-Down -->
        <select name="pageSelect" class="custom-select float-right mb-2" onchange="callDocumentByPaginate(this[this.selectedIndex].value);">
            <option value="#">${uiLabelMap.CommonPage} ${viewIndex?int} ${uiLabelMap.CommonOf} ${viewIndexMax}</option>
            <#if (viewIndex?int > 1)>
                <#list 0..viewIndexMax as curViewNum>
                     <option value="${shoppingListId!}~${viewSize}~${curViewNum?int + 1}">${uiLabelMap.CommonGotoPage} ${curViewNum + 1}</option>
                </#list>
            </#if>
        </select>
        <#-- End Page Select Drop-Down -->

        <#if (viewIndex?int > 1)>
          <a href="javascript: void(0);" onclick="callDocumentByPaginate('${shoppingListId!}~${viewSize}~${viewIndex?int - 1}');" class="btn btn-outline-secondary">${uiLabelMap.CommonPrevious}</a>
        </#if>
        <#if ((listSize?int - viewSize?int) > 0)>
            <span>${lowIndex} - ${highIndex} ${uiLabelMap.CommonOf} ${listSize}</span>
        </#if>
        <#if highIndex?int < listSize?int>
          <a href="javascript: void(0);" onclick="callDocumentByPaginate('${shoppingListId!}~${viewSize}~${viewIndex?int + 1}');" class="btn btn-outline-secondary">${uiLabelMap.CommonNext}</a>
        </#if>
</#if>
</#macro>

<div class="card">
  <div class="card-header">
    <strong>${uiLabelMap.EcommerceShoppingLists}</strong>
    <form id="createEmptyShoppingList" action="<@ofbizUrl>createEmptyShoppingList</@ofbizUrl>" method="post">
       <input type="hidden" name="productStoreId" value="${productStoreId!}" />
       <a href="javascript:document.getElementById('createEmptyShoppingList').submit();" class="float-right">${uiLabelMap.CommonCreateNew}</a>
    </form>
  </div>
    <div class="card-body">
        <#if shoppingLists?has_content>
          <form id="selectShoppingList" method="post" action="<@ofbizUrl>editShoppingList</@ofbizUrl>">
            <div class="row">
              <div class="col-sm-6">
                <select name="shoppingListId" class="custom-select form-control">
                  <#if shoppingList?has_content>
                    <option value="${shoppingList.shoppingListId}">${shoppingList.listName}</option>
                    <option value="${shoppingList.shoppingListId}">--</option>
                  </#if>
                  <#list shoppingLists as list>
                    <option value="${list.shoppingListId}">${list.listName}</option>
                  </#list>
                </select>
              </div>
              <div class="col-sm-6">
                <a href="javascript:$('#selectShoppingList').submit();" class="btn btn-outline-secondary">${uiLabelMap.CommonEdit}</a>
              </div>
            </div>
          </form>
        <#else>
          <label class="mb-2">${uiLabelMap.EcommerceNoShoppingListsCreate}.</label>
          <form id="createEmptyShoppingList" action="<@ofbizUrl>createEmptyShoppingList</@ofbizUrl>" method="post">
             <input type="hidden" name="productStoreId" value="${productStoreId!}" />
             <input type="submit" name="submit" class="btn btn-primary" value="${uiLabelMap.CommonCreateNew}"/>
          </form>
        </#if>
    </div>
</div>

<#if shoppingList?has_content>
    <#if canView>

<div class="card">
  <div class="card-header">
    <strong>${uiLabelMap.EcommerceShoppingListDetail} - ${shoppingList.listName}</strong>
          <a class="float-right ml-2" href='javascript:document.createCustRequestFromShoppingList.submit();'>${uiLabelMap.OrderCreateCustRequestFromShoppingList}</a>
  </div>
    <div class="card-body">
      <form name= "createCustRequestFromShoppingList" method= "post" action= "<@ofbizUrl>createCustRequestFromShoppingList</@ofbizUrl>">
          <input type= "hidden" name= "shoppingListId" value= "${shoppingList.shoppingListId}"/>
      </form>
      <form name="createQuoteFromShoppingList" method="post" action="<@ofbizUrl>createQuoteFromShoppingList</@ofbizUrl>">
          <input type="hidden" name="shoppingListId" value="${shoppingList.shoppingListId}"/>
      </form>
      <form name="updateList" method="post" action="<@ofbizUrl>updateShoppingList</@ofbizUrl>">
          <input type="hidden" class="inputBox" name="shoppingListId" value="${shoppingList.shoppingListId}" />
          <input type="hidden" class="inputBox" name="partyId" value="${shoppingList.partyId?if_exists}" />
            <div class="row">
              <div class="col-sm-6">
              <label for="listName">${uiLabelMap.EcommerceListName}</label>
              <input type="text" class="form-control" name="listName" id="listName" value="${shoppingList.listName}" />
              </div>
            </div>
            <div class="row">
              <div class="col-sm-6">
              <label for="description">${uiLabelMap.CommonDescription}</label>
              <input type="text" class="form-control" name="description" id="description" value="${shoppingList.description?if_exists}" />
            </div>
            </div>
            <div class="row">
              <div class="col-sm-6">
              <label for="shoppingListTypeId">${uiLabelMap.OrderListType}</label>
              <select name="shoppingListTypeId" id="shoppingListTypeId" class="form-control custom-select">
                <#if shoppingListType??>
                  <option value="${shoppingListType.shoppingListTypeId}">${shoppingListType.get("description",locale)?default(shoppingListType.shoppingListTypeId)}</option>
                  <option value="${shoppingListType.shoppingListTypeId}">--</option>
                </#if>
                <#list shoppingListTypes as shoppingListType>
                  <option value="${shoppingListType.shoppingListTypeId}">${shoppingListType.get("description",locale)?default(shoppingListType.shoppingListTypeId)}</option>
                </#list>
              </select>
            </div>
            </div>
            <div class="row">
              <div class="col-sm-6">
              <label for="isPublic">${uiLabelMap.EcommercePublic}?</label>
              <select name="isPublic" id="isPublic" class="form-control custom-select">
                <#if ("Y" == ((shoppingList.isPublic)!""))><option value="Y">${uiLabelMap.CommonY}</option></#if>
                <#if ("N" == ((shoppingList.isPublic)!""))><option value="N">${uiLabelMap.CommonN}</option></#if>
                <option></option>
                <option value="Y">${uiLabelMap.CommonY}</option>
                <option value="N">${uiLabelMap.CommonN}</option>
              </select>
              </div>
            </div>
            <div class="row">
              <div class="col-sm-6">
              <label for="isActive">${uiLabelMap.EcommerceActive}?</label>
              <select name="isActive" id="isActive" class="form-control custom-select">
                <#if ("Y" == ((shoppingList.isActive)!""))><option value="Y">${uiLabelMap.CommonY}</option></#if>
                <#if ("N" == ((shoppingList.isActive)!""))><option value="N">${uiLabelMap.CommonN}</option></#if>
                <option></option>
                <option value="Y">${uiLabelMap.CommonY}</option>
                <option value="N">${uiLabelMap.CommonN}</option>
              </select>
            </div>
            </div>
            <div class="row">
              <div class="col-sm-6">
              <label for="parentShoppingListId">${uiLabelMap.EcommerceParentList}</label>
              <select name="parentShoppingListId" id="parentShoppingListId" class="form-control custom-select">
                <#if parentShoppingList??>
                  <option value="${parentShoppingList.shoppingListId}">${parentShoppingList.listName?default(parentShoppingList.shoppingListId)}</option>
                </#if>
                <option value="">${uiLabelMap.EcommerceNoParent}</option>
                <#list allShoppingLists as newParShoppingList>
                  <option value="${newParShoppingList.shoppingListId}">${newParShoppingList.listName?default(newParShoppingList.shoppingListId)}</option>
                </#list>
              </select>
              <#if parentShoppingList??>
                <a href="<@ofbizUrl>editShoppingList?shoppingListId=${parentShoppingList.shoppingListId}</@ofbizUrl>" class="btn btn-link mt-2">${uiLabelMap.CommonGotoParent} (${parentShoppingList.listName?default(parentShoppingList.shoppingListId)})</a>
              </#if>
              </div>
            </div>
              <a href="javascript:document.updateList.submit();" class="btn btn-outline-secondary mt-2">${uiLabelMap.CommonSave}</a>
        </form>
    </div>
</div>

<#if shoppingListType?? && "SLT_AUTO_REODR" == shoppingListType.shoppingListTypeId>
  <#assign nowTimestamp = Static["org.apache.ofbiz.base.util.UtilDateTime"].monthBegin()>
<div class="card">
    <div class="card-header">
        <strong>
            ${uiLabelMap.EcommerceShoppingListReorder} - ${shoppingList.listName}
            <#if "N" == shoppingList.isActive?default("N")>
                ${uiLabelMap.EcommerceOrderNotActive}
            </#if>
        </strong>
      <a href="javascript:document.reorderinfo.submit();" class="float-right">${uiLabelMap.CommonSave}</a>
    </div>
    <div class="card-body">
        <form name="reorderinfo" method="post" action="<@ofbizUrl>updateShoppingList</@ofbizUrl>">
            <input type="hidden" name="shoppingListId" value="${shoppingList.shoppingListId}" />
                <label>${uiLabelMap.EcommerceRecurrence}</label>
                <#if recurrenceInfo?has_content>
                  <#assign recurrenceRule = recurrenceInfo.getRelatedOne("RecurrenceRule", false)!>
                </#if>
          <div class="row">
            <div class="col-sm-6">
            <select name="intervalNumber" class="custom-select mt-2">
                  <option value="">${uiLabelMap.EcommerceSelectInterval}</option>
                  <option value="1" <#if (recurrenceRule.intervalNumber)?default(0) == 1>selected="selected"</#if>>${uiLabelMap.EcommerceEveryDay}</option>
                  <option value="2" <#if (recurrenceRule.intervalNumber)?default(0) == 2>selected="selected"</#if>>${uiLabelMap.EcommerceEveryOther}</option>
                  <option value="3" <#if (recurrenceRule.intervalNumber)?default(0) == 3>selected="selected"</#if>>${uiLabelMap.EcommerceEvery3rd}</option>
                  <option value="6" <#if (recurrenceRule.intervalNumber)?default(0) == 6>selected="selected"</#if>>${uiLabelMap.EcommerceEvery6th}</option>
                  <option value="9" <#if (recurrenceRule.intervalNumber)?default(0) == 9>selected="selected"</#if>>${uiLabelMap.EcommerceEvery9th}</option>
                </select>
            </div>
          </div>
          <div class="row">
            <div class="col-sm-6">
            <select name="frequency" class="custom-select my-2">
                  <option value="">${uiLabelMap.EcommerceSelectFrequency}</option>
                  <option value="4" <#if "DAILY" == (recurrenceRule.frequency)?default("")>selected="selected"</#if>>${uiLabelMap.CommonDay}</option>
                  <option value="5" <#if "WEEKLY" == (recurrenceRule.frequency)?default("")>selected="selected"</#if>>${uiLabelMap.CommonWeek}</option>
                  <option value="6" <#if "MONTHLY" == (recurrenceRule.frequency)?default("")>selected="selected"</#if>>${uiLabelMap.CommonMonth}</option>
                  <option value="7" <#if "YEARLY" == (recurrenceRule.frequency)?default("")>selected="selected"</#if>>${uiLabelMap.CommonYear}</option>
                </select>
            </div>
          </div>
              <div class="row">
                <div class="col-sm-6">
                  <label>${uiLabelMap.CommonStartDate}</label>
                  <@htmlTemplate.renderDateTimeField name="startDateTime" className="form-control" event="" action="" alert="" title="Format: yyyy-MM-dd HH:mm:ss.SSS" value="${(recurrenceInfo.startDateTime)!}" size="25" maxlength="30" id="startDateTime1" dateType="date" shortDateInput=false timeDropdownParamName="" defaultDateTimeString="" localizedIconTitle="" timeDropdown="" timeHourName="" classString="" hour1="" hour2="" timeMinutesName="" minutes="" isTwelveHour="" ampmName="" amSelected="" pmSelected="" compositeType="" formName=""/>
                </div>
              </div>
              <div class="row">
                <div class="col-sm-6">
                <label>${uiLabelMap.CommonEndDate}</label>
                  <@htmlTemplate.renderDateTimeField name="endDateTime" className="textBox form-control" event="" action="" alert="" title="Format: yyyy-MM-dd HH:mm:ss.SSS" value="${(recurrenceRule.untilDateTime)!}" size="25" maxlength="30" id="endDateTime1" dateType="date" shortDateInput=false timeDropdownParamName="" defaultDateTimeString="" localizedIconTitle="" timeDropdown="" timeHourName="" classString="" hour1="" hour2="" timeMinutesName="" minutes="" isTwelveHour="" ampmName="" amSelected="" pmSelected="" compositeType="" formName=""/>
                </div>
              </div>
            <div class="row">
              <div class="col-sm-6">
              <label>${uiLabelMap.OrderShipTo}</label>
                <select name="contactMechId" class="custom-select form-control" onchange="javascript:document.reorderinfo.submit()">
                  <option value="">${uiLabelMap.OrderSelectAShippingAddress}</option>
                  <#if shippingContactMechList?has_content>
                    <#list shippingContactMechList as shippingContactMech>
                      <#assign shippingAddress = shippingContactMech.getRelatedOne("PostalAddress", false)>
                      <option value="${shippingContactMech.contactMechId}"<#if (shoppingList.contactMechId)?default("") == shippingAddress.contactMechId> selected="selected"</#if>>${shippingAddress.address1}</option>
                    </#list>
                  <#else>
                    <option value="">${uiLabelMap.OrderNoAddressesAvailable}</option>
                  </#if>
                </select>
              </div>
            </div>
            <div class="row">
              <div class="col-sm-6">
                <label>${uiLabelMap.OrderShipVia}</label>
                  <select name="shippingMethodString" class="custom-select form-control">
                    <option value="">${uiLabelMap.OrderSelectShippingMethod}</option>
                    <#if carrierShipMethods?has_content>
                      <#list carrierShipMethods as shipMeth>
                        <#assign shippingEst = shippingEstWpr.getShippingEstimate(shipMeth)?default(-1)>
                        <#assign shippingMethod = shipMeth.shipmentMethodTypeId + "@" + shipMeth.partyId>
                        <option value="${shippingMethod}"<#if shippingMethod == chosenShippingMethod> selected="selected"</#if>>
                          <#if shipMeth.partyId != "_NA_">
                            ${shipMeth.partyId!}&nbsp;
                          </#if>
                          ${shipMeth.description!}
                          <#if shippingEst?has_content>
                            &nbsp;-&nbsp;
                            <#if (shippingEst > -1)>
                              <@ofbizCurrency amount=shippingEst isoCode=listCart.getCurrency()/>
                            <#else>
                              ${uiLabelMap.OrderCalculatedOffline}
                            </#if>
                          </#if>
                        </option>
                      </#list>
                    <#else>
                      <option value="">${uiLabelMap.OrderSelectAddressFirst}</option>
                    </#if>
                  </select>
              </div>
            </div>
            <div class="row">
              <div class="col-sm-6">
                <label>${uiLabelMap.OrderPayBy}</label>
                  <select name="paymentMethodId" class="custom-select form-control">
                    <option value="">${uiLabelMap.OrderSelectPaymentMethod}</option>
                    <#list paymentMethodList as paymentMethod>
                      <#if "CREDIT_CARD" == paymentMethod.paymentMethodTypeId>
                        <#assign creditCard = paymentMethod.getRelatedOne("CreditCard", false)>
                        <option value="${paymentMethod.paymentMethodId}" <#if (shoppingList.paymentMethodId)?default("") == paymentMethod.paymentMethodId>selected="selected"</#if>>CC:&nbsp;${Static["org.apache.ofbiz.party.contact.ContactHelper"].formatCreditCard(creditCard)}</option>
                      <#elseif "EFT_ACCOUNT" == paymentMethod.paymentMethodTypeId>
                        <#assign eftAccount = paymentMethod.getRelatedOne("EftAccount", false)>
                        <option value="${paymentMethod.paymentMethodId}">EFT:&nbsp;${eftAccount.bankName!}: ${eftAccount.accountNumber!}</option>
                      </#if>
                    </#list>
                  </select>
              </div>
            </div>
          <div class="row mt-3">
            <div class="col-sm-12">
                <a href="javascript:document.reorderinfo.submit();" class="btn btn-outline-secondary">${uiLabelMap.CommonSave}</a>
                <a href="<@ofbizUrl>editcontactmech?preContactMechTypeId=POSTAL_ADDRESS&amp;contactMechPurposeTypeId=SHIPPING_LOCATION&amp;DONE_PAGE=editShoppingList</@ofbizUrl>" class="btn btn-outline-secondary">${uiLabelMap.PartyAddNewAddress}</a>
                <a href="<@ofbizUrl>editcreditcard?DONE_PAGE=editShoppingList</@ofbizUrl>" class="btn btn-outline-secondary">${uiLabelMap.EcommerceNewCreditCard}</a>
                <a href="<@ofbizUrl>editeftaccount?DONE_PAGE=editShoppingList</@ofbizUrl>" class="btn btn-outline-secondary">${uiLabelMap.EcommerceNewEFTAccount}</a>
            </div>
          </div>
              <#if "Y" == shoppingList.isActive?default("N")>
                <div>
                  <#assign nextTime = recInfo.next(lastSlOrderTime)?if_exists />
                  <#if nextTime?has_content>
                    <#assign nextTimeStamp = Static["org.apache.ofbiz.base.util.UtilDateTime"].getTimestamp(nextTime)?if_exists />
                    <#if nextTimeStamp?has_content>
                      <#assign nextTimeString = Static["org.apache.ofbiz.base.util.UtilFormatOut"].formatDate(nextTimeStamp)?if_exists />
                    </#if>
                  </#if>
                  <#if lastSlOrderDate?has_content>
                    <#assign lastOrderedString = Static["org.apache.ofbiz.base.util.UtilFormatOut"].formatDate(lastSlOrderDate)!>
                  </#if>
                    <div class="tabletext">
                      <table>
                        <tr>
                          <td>${uiLabelMap.OrderLastOrderedDate}</div></td>
                          <td>:</td>
                          <td>${lastOrderedString?default("${uiLabelMap.OrderNotYetOrdered}")}</td>
                        </tr>
                        <tr>
                          <td>${uiLabelMap.EcommerceEstimateNextOrderDate}</td>
                          <td>:</td>
                          <td>${nextTimeString?default("${uiLabelMap.EcommerceNotYetKnown}")}</td>
                        </tr>
                      </table>
                    </div>
                  </tr>
                </tr>
              </#if>
            </table>
        </form>
    </div>
</div>
</#if>

<#if childShoppingListDatas?has_content>
<div class="card">
    <div class="card-header">
      <strong>${uiLabelMap.EcommerceChildShoppingList} - ${shoppingList.listName}</strong>
      <a href="<@ofbizUrl>addListToCart?shoppingListId=${shoppingList.shoppingListId}&amp;includeChild=yes</@ofbizUrl>" class="float-right">${uiLabelMap.EcommerceAddChildListsToCart}</a>
    </div>
    <div class="card-body">
        <table class="table">
          <thead>
              <tr>
                <th>${uiLabelMap.EcommerceListName}</th>
                <th>${uiLabelMap.EcommerceListName}</th>
                <th>&nbsp;</th>
                <th>&nbsp;</th>
              </tr>
          </thead>
          <tbody>
          <#list childShoppingListDatas as childShoppingListData>
              <#assign childShoppingList = childShoppingListData.childShoppingList/>
              <#assign totalPrice = childShoppingListData.totalPrice/>
              <tr>
                <td>
                  <a href="<@ofbizUrl>editShoppingList?shoppingListId=${childShoppingList.shoppingListId}</@ofbizUrl>" class="button">${childShoppingList.listName?default(childShoppingList.shoppingListId)}</a>
                </td>
                <td>
                  <@ofbizCurrency amount=totalPrice isoCode=currencyUomId/>
                </td>
                <td>
                  <a href="<@ofbizUrl>editShoppingList?shoppingListId=${childShoppingList.shoppingListId}</@ofbizUrl>" class="btn btn-link">${uiLabelMap.EcommerceGoToList}</a>
                  <a href="<@ofbizUrl>addListToCart?shoppingListId=${childShoppingList.shoppingListId}</@ofbizUrl>" class="btn btn-link">${uiLabelMap.EcommerceAddListToCart}</a>
                </td>
              </tr>
            </form>
          </#list>
          <tr>
            <td>&nbsp;</td>
            <td>
              <@ofbizCurrency amount=shoppingListChildTotal isoCode=currencyUomId/>
            </td>
            <td>&nbsp;</td>
          </tr>
          </tbody>
        </table>
    </div>
</div>
</#if>

<div class="card">
    <div class="card-header">
      <strong>${uiLabelMap.EcommerceListItems} - ${shoppingList.listName}</strong>
            <a href="<@ofbizUrl>addListToCart?shoppingListId=${shoppingList.shoppingListId}</@ofbizUrl>" class="float-right">${uiLabelMap.EcommerceAddListToCart}</a>
    </div>
    <div class="card-body">
    <@paginationControls/>
      <#if shoppingListItemDatas?has_content>
        <#-- Pagination -->
            <table class="table table-responsive-sm">
              <thead class="thead-dark">
                  <tr>
                    <th>${uiLabelMap.OrderProduct}</th>
                    <th>Reservation</th>
                    <#-- <td nowrap="nowrap" align="center"><div><b>Purchased</b></div></td> -->
                    <th>${uiLabelMap.EcommercePrice}</th>
                    <th>${uiLabelMap.OrderTotal}</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
              <#list shoppingListItemDatas[lowIndex-1..highIndex-1] as shoppingListItemData>
                <#assign shoppingListItem = shoppingListItemData.shoppingListItem/>
                <#assign product = shoppingListItemData.product/>
                <#assign productContentWrapper = Static["org.apache.ofbiz.product.product.ProductContentWrapper"].makeProductContentWrapper(product, request)/>
                <#assign unitPrice = shoppingListItemData.unitPrice/>
                <#assign totalPrice = shoppingListItemData.totalPrice/>
                <#assign productVariantAssocs = shoppingListItemData.productVariantAssocs!/>
                <#assign isVirtual = product.isVirtual?? && product.isVirtual.equals("Y")/>
                  <tr>
                    <td>
                      <a href="<@ofbizUrl>product?product_id=${shoppingListItem.productId}</@ofbizUrl>" class="btn btn-link">${shoppingListItem.productId}
                      ${productContentWrapper.get("PRODUCT_NAME", "html")?default("No Name")}</a> <p class="ml-4"><small> ${productContentWrapper.get("DESCRIPTION", "html")!} </small></p>
                    </td>
                    <td>
                      <form method="post" action="<@ofbizUrl>updateShoppingListItem</@ofbizUrl>" name="listform_${shoppingListItem.shoppingListItemSeqId}">
                          <input type="hidden" name="shoppingListId" value="${shoppingListItem.shoppingListId}" />
                          <input type="hidden" name="shoppingListItemSeqId" value="${shoppingListItem.shoppingListItemSeqId}" />
                          <input type="hidden" name="reservStart" />
                        <dl>
                          <#if "ASSET_USAGE" == product.productTypeId>
                            <dt>${uiLabelMap.EcommerceStartdate}</dt>
                            <dd><input type="text" class="inputBox" size="10" name="reservStartStr" value="${shoppingListItem.reservStart?if_exists}" /></dd>
                            <dt>${uiLabelMap.EcommerceNbrOfDays}</dt>
                            <dd><input type="text" class="inputBox" size="2" name="reservLength" value="${shoppingListItem.reservLength?if_exists}" /></dd>
                            <dt>${uiLabelMap.EcommerceNbrOfPersons}</dt>
                            <dd><input type="text" class="inputBox" size="3" name="reservPersons" value="${shoppingListItem.reservPersons?if_exists}" /></dd>
                          <#else>
                              <dt>${uiLabelMap.EcommerceStartdate}</dt>
                              <dd>--</dd>
                              <dt>${uiLabelMap.EcommerceNbrOfDays}</dt>
                              <dd>--</dd>
                              <dt>${uiLabelMap.EcommerceNbrOfPersons}</dt>
                              <dd>--</dd>
                                <input type="hidden" name="reservStartStr" value="" />
                          </#if>
                          <dt>${uiLabelMap.CommonQuantity}</dt>
                          <dd><input size="6" class="inputBox" type="text" name="quantity" value="${shoppingListItem.quantity?string.number}" /></dd>
                        </dl>
                      </form>
                    </td>
                    <#--
                    <td nowrap="nowrap" align="center">
                      <div>${shoppingListItem.quantityPurchased?default(0)?string.number}</div>
                    </td>
                    -->
                    <td class="amount">
                      <@ofbizCurrency amount=unitPrice isoCode=currencyUomId/>
                    </td>
                    <td class="amount">
                      <@ofbizCurrency amount=totalPrice isoCode=currencyUomId/>
                    </td>
                    <td>
                      <div class="btn-group">
                        <a href="javascript:TimestampSubmit(listform_${shoppingListItem.shoppingListItemSeqId});" class="btn btn-outline-secondary">${uiLabelMap.CommonUpdate}</a>
                      </div>
                      <div class="btn-group">
                          <form name="removeFromShoppingList" method="post" action="<@ofbizUrl>removeFromShoppingList</@ofbizUrl>">
                              <input type="hidden" name="shoppingListId" value="${shoppingListItem.shoppingListId!}">
                              <input type="hidden" name="shoppingListItemSeqId" value="${shoppingListItem.shoppingListItemSeqId}">
                              <input type="submit" value="${uiLabelMap.CommonRemove}" class="btn btn-outline-secondary"/>
                          </form>
                      </div>
                      <#if isVirtual && productVariantAssocs?has_content>
                        <#assign replaceItemAction = "/replaceShoppingListItem/" + requestAttributes._CURRENT_VIEW_?if_exists />
                        <#assign addToCartAction = "/additem/" + requestAttributes._CURRENT_VIEW_?if_exists />
                        <form method="post" action="<@ofbizUrl>${addToCartAction}</@ofbizUrl>" name="listreplform_${shoppingListItem.shoppingListItemSeqId}">
                            <input type="hidden" name="shoppingListId" value="${shoppingListItem.shoppingListId}" />
                            <input type="hidden" name="shoppingListItemSeqId" value="${shoppingListItem.shoppingListItemSeqId}" />
                            <input type="hidden" name="quantity" value="${shoppingListItem.quantity}" />
                          <div class="btn-group">
                          <select name="add_product_id" class="selectBox custom-select mt-2">
                              <#list productVariantAssocs as productVariantAssoc>
                                <#assign variantProduct = productVariantAssoc.getRelatedOne("AssocProduct", true) />
                                <#if variantProduct??>
                                <#assign variantProductContentWrapper = Static["org.apache.ofbiz.product.product.ProductContentWrapper"].makeProductContentWrapper(variantProduct, request) />
                                  <option value="${variantProduct.productId}">${productContentWrapper.get("PRODUCT_NAME", "html")?default("No Name")} [${variantProduct.productId}]</option>
                                </#if>
                              </#list>
                            </select>
                          </div>
                          <div class="btn-group">
                            <a href="javascript:document.listreplform_${shoppingListItem.shoppingListItemSeqId}.action='<@ofbizUrl>${addToCartAction}</@ofbizUrl>';document.listreplform_${shoppingListItem.shoppingListItemSeqId}.submit();" class="btn btn-primary mt-2">${uiLabelMap.CommonAdd}&nbsp;${shoppingListItem.quantity?string}${uiLabelMap.EcommerceVariationToCart}</a>
                          </div>
                          <div class="btn-group">
                            <a href="javascript:document.listreplform_${shoppingListItem.shoppingListItemSeqId}.action='<@ofbizUrl>${replaceItemAction}</@ofbizUrl>';document.listreplform_${shoppingListItem.shoppingListItemSeqId}.submit();" class="btn btn-outline-secondary mt-2">${uiLabelMap.EcommerceReplaceWithVariation}</a>
                          </div>
                        </form>
                      <#else>
                      <div class="btn-group">
                        <a href="<@ofbizUrl>additem<#if requestAttributes._CURRENT_VIEW_?exists>/${requestAttributes._CURRENT_VIEW_}</#if>?shoppingListId=${shoppingListItem.shoppingListId}&amp;shoppingListItemSeqId=${shoppingListItem.shoppingListItemSeqId}&amp;quantity=${shoppingListItem.quantity}&amp;reservStart=${shoppingListItem.reservStart?if_exists}&amp;reservPersons=${shoppingListItem.reservPersons?if_exists}&amp;reservLength=${shoppingListItem.reservLength?if_exists}&amp;configId=${shoppingListItem.configId?if_exists}&amp;add_product_id=${shoppingListItem.productId}</@ofbizUrl>" class="btn btn-primary mt-2">${uiLabelMap.CommonAdd} ${shoppingListItem.quantity?string}&nbsp;${uiLabelMap.OrderToCart}</a>
                      </div>
                      </#if>
                    </td>
                  </tr>
              </#list>
              <tr>
                <td></td>
                <td></td>
                <#--<td><div>&nbsp;</div></td>-->
                <td></td>
                <td class="amount">
                  <@ofbizCurrency amount=shoppingListItemTotal isoCode=currencyUomId/>
                </td>
                <td></td>
              </tr>
              </tbody>
            </table>
        <#else>
            <h2>${uiLabelMap.EcommerceShoppingListEmpty}.</h2>
        </#if>
    </div>
</div>

<div class="card">
  <div class="card-header">
    <strong>${uiLabelMap.EcommerceShoppingListPriceTotals} - ${shoppingList.listName}</strong>
  </div>
    <div class="card-body">
      <dl class="row">
        <dt class="col-sm-2">${uiLabelMap.EcommerceChildListTotalPrice}</dt>
        <dd class="col-sm-10 amount"><@ofbizCurrency amount=shoppingListChildTotal isoCode=currencyUomId/></dd>
        <dt class="col-sm-2">${uiLabelMap.EcommerceListItemsTotalPrice}</dt>
        <dd class="col-sm-10 amount"><@ofbizCurrency amount=shoppingListItemTotal isoCode=currencyUomId/></dd>
        <dt class="col-sm-2">${uiLabelMap.OrderGrandTotal}</dt>
        <dd class="col-sm-10 amount"><@ofbizCurrency amount=shoppingListTotalPrice isoCode=currencyUomId/></dd>
      </dl>
    </div>
</div>

<div class="card">
  <div class="card-header">
    <strong>${uiLabelMap.CommonQuickAddList}</strong>
  </div>
    <div class="card-body">
        <form name="addToShoppingList" class="form-inline" method="post" action="<@ofbizUrl>addItemToShoppingList</@ofbizUrl>">
            <input type="hidden" name="shoppingListId" value="${shoppingList.shoppingListId}" />

            <input type="text" class="form-control mr-2" name="productId" value="${requestParameters.add_product_id?if_exists}" />
            <#if reservStart?exists><label class="mr-2">${uiLabelMap.EcommerceStartDate}</label><input type="text" class="form-control mr-2" name="reservStart" value="${requestParameters.reservStart?default("")}" /><label class="mr-2"> ${uiLabelMap.EcommerceLength}:</label><input type="text" class="form-control mr-2" name="reservLength" value="${requestParameters.reservLength?default("")}" ><label>${uiLabelMap.OrderNbrPersons}:</label><input type="text" class="inputBox" size="3" name="reservPersons" value="${requestParameters.reservPersons?default("1")}" /></#if> <label class="mr-2">${uiLabelMap.CommonQuantity} :</label><input type="text" class="form-control mr-2" name="quantity" value="${requestParameters.quantity?default("1")}" />
            <!-- <input type="text" class="inputBox" size="5" name="quantity" value="${requestParameters.quantity?default("1")}" />-->
            <input type="submit" class="btn btn-outline-secondary" value="${uiLabelMap.OrderAddToShoppingList}" />
        </form>
    </div>
</div>

    <#else>
        <#-- shoppingList was found, but belongs to a different party -->
        <h2>${uiLabelMap.EcommerceShoppingListError} ${uiLabelMap.CommonId} ${shoppingList.shoppingListId}) ${uiLabelMap.EcommerceListDoesNotBelong}.</h2>
    </#if>
</#if>
