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
        function removeSelected() {
            var cform = document.cartform;
            cform.removeSelected.value = true;
            cform.submit();
        }
        function addToList() {
            var cform = document.cartform;
            cform.action = "<@ofbizUrl>addBulkToShoppingList</@ofbizUrl>";
            cform.submit();
        }
        function gwAll(e) {
            var cform = document.cartform;
            var len = cform.elements.length;
            var selectedValue = e.value;
            if ("" == selectedValue) {
                return;
            }

            var cartSize = ${shoppingCartSize};
            var passed = 0;
            for (var i = 0; i < len; i++) {
                var element = cform.elements[i];
                var ename = element.name;
                var sname = ename.substring(0,16);
                if ("option^GIFT_WRAP" == sname) {
                    var options = element.options;
                    var olen = options.length;
                    var matching = -1;
                    for (var x = 0; x < olen; x++) {
                        var thisValue = element.options[x].value;
                        if (thisValue == selectedValue) {
                            element.selectedIndex = x;
                            passed++;
                        }
                    }
                }
            }
            if (cartSize > passed && selectedValue != "NO^") {
                showErrorAlert("${uiLabelMap.CommonErrorMessage2}","${uiLabelMap.EcommerceSelectedGiftWrap}");
            }
            cform.submit();
        }
    //]]>
</script>

<script type="text/javascript">
    //<![CDATA[
        function setAlternateGwp(field) {
            window.location=field.value;
        };
    //]]>
</script>
<#assign fixedAssetExist = shoppingCart.containAnyWorkEffortCartItems() />
<#-- change display format when rental items exist in the shoppingcart -->
<div>
    <#if ((sessionAttributes.lastViewedProducts)?has_content && sessionAttributes.lastViewedProducts?size > 0)>
      <#assign continueLink = "/product?product_id=" + sessionAttributes.lastViewedProducts.get(0) />
    <#else>
      <#assign continueLink = "/main" />
    </#if>
    <a href="<@ofbizUrl>${continueLink}</@ofbizUrl>" class="submenutext">
      ${uiLabelMap.EcommerceContinueShopping}
    </a>
    <#if (shoppingCartSize > 0)>
      <a href="<@ofbizUrl>checkoutoptions</@ofbizUrl>" class="submenutext">
        ${uiLabelMap.OrderCheckout}
      </a>
    <#else>
      <span class="submenutextrightdisabled">
        ${uiLabelMap.OrderCheckout}
      </span>
    </#if>
    ${uiLabelMap.CommonQuickAdd}
  <div>
    <div>
      <form method="post"
          action="<@ofbizUrl>additem<#if requestAttributes._CURRENT_VIEW_?has_content>/${requestAttributes._CURRENT_VIEW_}</#if></@ofbizUrl>" class="form-inline"
          name="quickaddform">
        <div class="form-group mb-2">
          <label for="add_product_id" class="mr-2"> ${uiLabelMap.EcommerceProductNumber} </label>
          <input type="text" class="form-control form-control-sm" name="add_product_id" id="add_product_id" value="${requestParameters.add_product_id!}" />
          <#-- check if rental data present  insert extra fields in Quick Add-->
          <#if (product?? && "ASSET_USAGE" == product.getString("productTypeId")
              ) || (product?? && "ASSET_USAGE_OUT_IN" == product.getString("productTypeId"))>
            ${uiLabelMap.EcommerceStartDate}:
            <input type="text" class="inputBox" size="10" name="reservStart"
                value="${requestParameters.reservStart?default("")}" />
            ${uiLabelMap.EcommerceLength}:
            <input type="text" class="inputBox" size="2" name="reservLength"
                value="${requestParameters.reservLength?default("")}" />
            </div>
            <div>
            &nbsp;&nbsp;${uiLabelMap.OrderNbrPersons}:
            <input type="text" class="inputBox" size="3" name="reservPersons"
                value="${requestParameters.reservPersons?default("1")}" />
          </#if>
          <label for="quantity" class="mx-2">${uiLabelMap.CommonQuantity}:</label>
          <input type="text" class="form-control form-control-sm" name="quantity" id="quantity"
              value="${requestParameters.quantity?default("1")}" />
          <input type="submit" class="btn btn-outline-secondary btn-sm btn-sm" value="${uiLabelMap.OrderAddToCart}" />
          <#-- <a href="javascript:document.quickaddform.submit()" class="btn btn-outline-secondary btn-sm">
                 <span>[${uiLabelMap.OrderAddToCart}]</span>
               </a> -->
        </div>
      </form>
    </div>
  </div>
</div>

<script type="text/javascript">
    //<![CDATA[
        document.quickaddform.add_product_id.focus();
    //]]>
</script>

<div>
  <div>
    <div>
      <ul class="list-inline">
        <#--<a href="<@ofbizUrl>main</@ofbizUrl>" class="lightbuttontext">
              [${uiLabelMap.EcommerceContinueShopping}]
            </a>-->
        <#if (shoppingCartSize > 0)>
          <li class="list-inline-item">
          <a href="javascript:document.cartform.submit();">
            ${uiLabelMap.EcommerceRecalculateCart}
          </a>
          </li>
          <li class="list-inline-item">
          <a href="<@ofbizUrl>emptycart</@ofbizUrl>">
            ${uiLabelMap.EcommerceEmptyCart}
          </a>
          </li>
          <li class="list-inline-item">
          <a href="javascript:removeSelected();">
            ${uiLabelMap.EcommerceRemoveSelected}
          </a>
          </li>
        <#else>
          <li class="list-inline-item">
          <span class="submenutextdisabled">${uiLabelMap.EcommerceRecalculateCart}</span>
          </li>
          <li class="list-inline-item">
          <span class="submenutextdisabled">${uiLabelMap.EcommerceEmptyCart}</span>
          </li>
          <li class="list-inline-item">
          <span class="submenutextdisabled">${uiLabelMap.EcommerceRemoveSelected}</span>
          </li>
        </#if>
      </ul>
    </div>
    <div class="row mb-2">
      <div class="col-xl-12">
        <span class="h3">${uiLabelMap.OrderShoppingCart}</span>
        <#if (shoppingCartSize > 0)>
          <a class="btn btn-primary float-right" href="<@ofbizUrl>checkoutoptions</@ofbizUrl>">${uiLabelMap.OrderCheckout}</a>
        <#else>
          <a class="btn btn-primary disabled float-right" href="#">${uiLabelMap.OrderCheckout}</a>
        </#if>
      </div>
    </div>
  </div>
  <div>
    <#if (shoppingCartSize > 0)>
      <form method="post" action="<@ofbizUrl>modifycart</@ofbizUrl>" name="cartform">
          <input type="hidden" name="removeSelected" value="false" />
          <table class="table  table-responsive-sm">
            <thead class="thead-light">
              <tr>
                <th></th>
                <th scope="row">${uiLabelMap.OrderProduct}</th>
                <#if asslGiftWraps?has_content && productStore.showCheckoutGiftOptions! != "N">>
                  <th scope="row">
                    <select class="form-control" name="GWALL" onchange="javascript:gwAll(this);">
                      <option value="">${uiLabelMap.EcommerceGiftWrapAllItems}</option>
                      <option value="NO^">${uiLabelMap.EcommerceNoGiftWrap}</option>
                      <#list allgiftWraps as option>
                        <option value="${option.productFeatureId}">
                          ${option.description} : ${option.defaultAmount?default(0)}
                        </option>
                      </#list>
                    </select>
                <#else>
                  <th scope="row">&nbsp;</th>
                </#if>
                <#if fixedAssetExist == true>
                  <td>
                    <table>
                      <tr>
                        <td>- ${uiLabelMap.EcommerceStartDate} -</td>
                        <td>- ${uiLabelMap.EcommerceNbrOfDays} -</td>
                      </tr>
                      <tr>
                        <td >- ${uiLabelMap.EcommerceNbrOfPersons} -</td>
                        <td >- ${uiLabelMap.CommonQuantity} -</td>
                      </tr>
                    </table>
                  </td>
                <#else>
                  <th scope="row">${uiLabelMap.CommonQuantity}</th>
                </#if>
                <th class="amount">${uiLabelMap.EcommerceUnitPrice}</th>
                <th class="amount">${uiLabelMap.EcommerceAdjustments}</th>
                <th class="amount">${uiLabelMap.EcommerceItemTotal}</th>
                <th>
                  <input type="checkbox" name="selectAll" value="0" class="selectAll"/>
                </th>
              </tr>
            </thead>
            <tbody>
              <#assign itemsFromList = false />
              <#assign promoItems = false />
              <#list shoppingCart.items() as cartLine>
                <#assign cartLineIndex = shoppingCart.getItemIndex(cartLine) />
                <#assign lineOptionalFeatures = cartLine.getOptionalProductFeatures() />
                <#-- show adjustment info -->
                <#list cartLine.getAdjustments() as cartLineAdjustment>
                  <!-- cart line ${cartLineIndex} adjustment: ${cartLineAdjustment} -->
                </#list>
                <tr id="cartItemDisplayRow_${cartLineIndex}">
                  <td>
                    <#if cartLine.getShoppingListId()??>
                      <#assign itemsFromList = true />
                      <a href="<@ofbizUrl>editShoppingList?shoppingListId=${cartLine.getShoppingListId()}</@ofbizUrl>"
                          >L</a>&nbsp;&nbsp;
                    <#elseif cartLine.getIsPromo()>
                      <#assign promoItems = true />
                      <span class="badge badge-success">P</span>
                    <#else>
                      &nbsp;
                    </#if>
                  </td>
                  <td>
                  <div class="media">
                    <#if cartLine.getProductId()??>
                      <#-- product item -->
                      <#-- start code to display a small image of the product -->
                      <#if cartLine.getParentProductId()??>
                        <#assign parentProductId = cartLine.getParentProductId() />
                      <#else>
                        <#assign parentProductId = cartLine.getProductId() />
                      </#if>
                      <#assign smallImageUrl =
                          Static["org.apache.ofbiz.product.product.ProductContentWrapper"].getProductContentAsText(
                          cartLine.getProduct(), "SMALL_IMAGE_URL", locale, dispatcher, "html")! />
                      <#if !smallImageUrl?string?has_content>
                        <#assign smallImageUrl = "/images/defaultImage.jpg" />
                      </#if>
                      <#if smallImageUrl?string?has_content>
                        <a href="<@ofbizCatalogAltUrl productId=parentProductId/>">
                          <img src="<@ofbizContentUrl>${requestAttributes.contentPathPrefix!}${smallImageUrl}</@ofbizContentUrl>"
                              alt="Product Image" class="cart-image mr-3" />
                        </a>
                      </#if>
                      <#-- end code to display a small image of the product -->
                      <#-- ${cartLineIndex} - -->

                    <div class="media-body">
                      <h4 class="mt-0">
                      <a href="<@ofbizCatalogAltUrl productId=parentProductId/>"
                          >${cartLine.getProductId()} -
                        ${cartLine.getName(dispatcher)!}
                      </a>
                      </h4>
                        ${cartLine.getDescription(dispatcher)!}
                      <#-- For configurable products, the selected options are shown -->
                      <#if cartLine.getConfigWrapper()??>
                        <#assign selectedOptions = cartLine.getConfigWrapper().getSelectedOptions()! />
                        <#if selectedOptions??>
                          <div>&nbsp;</div>
                          <#list selectedOptions as option>
                            <div>
                              ${option.getDescription()}
                            </div>
                          </#list>
                        </#if>
                      </#if>

                      <#-- if inventory is not required check to see if it is out
                       of stock and needs to have a message shown about that... -->
                      <#assign itemProduct = cartLine.getProduct() />
                      <#assign isStoreInventoryNotRequiredAndNotAvailable =
                          Static["org.apache.ofbiz.product.store.ProductStoreWorker"]
                          .isStoreInventoryRequiredAndAvailable(request, itemProduct,
                          cartLine.getQuantity(), false, false) />
                      <#if isStoreInventoryNotRequiredAndNotAvailable && itemProduct.inventoryMessage?has_content>
                        (${itemProduct.inventoryMessage})
                      </#if>

                    <#else>
                      <#-- this is a non-product item -->
                      ${cartLine.getItemTypeDescription()!}: ${cartLine.getName(dispatcher)!}
                    </#if>

                    <#assign attrs = cartLine.getOrderItemAttributes()/>
                    <#if attrs?has_content>
                      <#assign attrEntries = attrs.entrySet()/>
                      <ul>
                        <#list attrEntries as attrEntry>
                          <li>
                            ${attrEntry.getKey()} : ${attrEntry.getValue()}
                          </li>
                        </#list>
                      </ul>
                    </#if>
                    <#if (cartLine.getIsPromo() && cartLine.getAlternativeOptionProductIds()?has_content)>
                      <#-- Show alternate gifts if there are any... -->
                      <div class="tableheadtext">${uiLabelMap.OrderChooseFollowingForGift}:</div>
                      <select name="dummyAlternateGwpSelect${cartLineIndex}"
                          onchange="setAlternateGwp(this);" class="selectBox">
                        <option value="">- ${uiLabelMap.OrderChooseAnotherGift} -</option>
                        <#list cartLine.getAlternativeOptionProductIds() as alternativeOptionProductId>
                          <#assign alternativeOptionName =
                            Static["org.apache.ofbiz.product.product.ProductWorker"].getGwpAlternativeOptionName(
                            dispatcher, delegator, alternativeOptionProductId, requestAttributes.locale) />
                          <option
                              value="<@ofbizUrl>setDesiredAlternateGwpProductId?alternateGwpProductId=${alternativeOptionProductId}&amp;alternateGwpLine=${cartLineIndex}</@ofbizUrl>">
                            ${alternativeOptionName?default(alternativeOptionProductId)}
                          </option>
                        </#list>
                      </select>
                      <#-- this is the old way, it lists out the options and is not as nice as the drop-down
                      <ul>
                      <#list cartLine.getAlternativeOptionProductIds() as alternativeOptionProductId>
                        <#assign alternativeOptionName = Static["org.apache.ofbiz.product.product.ProductWorker"]
                            .getGwpAlternativeOptionName(delegator, alternativeOptionProductId,
                            requestAttributes.locale) />
                        <li>
                          <a href="<@ofbizUrl>setDesiredAlternateGwpProductId?alternateGwpProductId=${alternativeOptionProductId}&alternateGwpLine=${cartLineIndex}</@ofbizUrl>"
                              class="btn btn-outline-secondary btn-sm">Select: ${alternativeOptionName?default(alternativeOptionProductId)}</a>
                        </li>
                      </#list>
                      </ul>
                      -->
                    </#if>
                    </div>
                    </div>
                  </td>

                  <#-- gift wrap option -->
                  <#assign showNoGiftWrapOptions = false />
                  <td >
                    <#assign giftWrapOption = lineOptionalFeatures.GIFT_WRAP! />
                    <#assign selectedOption = cartLine.getAdditionalProductFeatureAndAppl("GIFT_WRAP")! />
                    <#if giftWrapOption?has_content>
                      <select class="selectBox" name="option^GIFT_WRAP_${cartLineIndex}"
                          onchange="">
                        <option value="NO^">${uiLabelMap.EcommerceNoGiftWrap}</option>
                        <#list giftWrapOption as option>
                          <option value="${option.productFeatureId}"
                              <#if ((selectedOption.productFeatureId)?? && selectedOption.productFeatureId ==
                              option.productFeatureId)>selected="selected"</#if>>
                            ${option.description} : ${option.amount?default(0)}
                          </option>
                        </#list>
                      </select>
                    <#elseif showNoGiftWrapOptions>
                      <select class="selectBox" name="option^GIFT_WRAP_${cartLineIndex}"
                          onchange="">
                        <option value="">${uiLabelMap.EcommerceNoGiftWrap}</option>
                      </select>
                    <#else>
                      &nbsp;
                    </#if>
                  </td>
                  <#-- end gift wrap option -->

                  <td>
                    <#if cartLine.getIsPromo() || cartLine.getShoppingListId()??>
                      <#if fixedAssetExist == true>
                        <#if cartLine.getReservStart()??>
                          <table >
                            <tr>
                              <td>&nbsp;</td>
                              <td>${cartLine.getReservStart()?string("yyyy-mm-dd")}</td>
                              <td>${cartLine.getReservLength()?string.number}</td></tr>
                            <tr>
                              <td>&nbsp;</td>
                              <td>${cartLine.getReservPersons()?string.number}</td>
                              <td>
                        <#else>
                          <table >
                            <tr>
                              <td >--</td>
                              <td>--</td>
                            </tr>
                            <tr>
                              <td>--</td>
                              <td>
                        </#if>
                        ${cartLine.getQuantity()?string.number}</td></tr></table>
                      <#else><#-- fixedAssetExist -->
                        ${cartLine.getQuantity()?string.number}
                      </#if>
                    <#else><#-- Is Promo or Shoppinglist -->
                      <#if fixedAssetExist == true>
                        <#if cartLine.getReservStart()??>
                          <table>
                            <tr>
                              <td>&nbsp;</td>
                              <td>
                                <input type="text" class="inputBox" size="10" name="reservStart_${cartLineIndex}"
                                    value=${cartLine.getReservStart()?string}/>
                              </td>
                              <td>
                                <input type="text" class="inputBox" size="2" name="reservLength_${cartLineIndex}"
                                    value="${cartLine.getReservLength()?string.number}"/>
                              </td>
                            </tr>
                            <tr>
                              <td>&nbsp;</td>
                              <td>
                                <input type="text" class="inputBox" size="3" name="reservPersons_${cartLineIndex}"
                                    value=${cartLine.getReservPersons()?string.number} />
                              </td>
                              <td>
                        <#else>
                          <table>
                            <tr>
                              <td>--</td>
                              <td>--</td>
                            </tr>
                            <tr>
                              <td>--</td>
                              <td>
                        </#if>
                              <input class="inputBox form-control" type="number" name="update_${cartLineIndex}"
                                  value="${cartLine.getQuantity()?string.number}" min="1" />
                              </td>
                            </tr>
                          </table>
                        <#else><#-- fixedAssetExist -->
                          <input class="inputBox form-control" type="number" name="update_${cartLineIndex}"
                              value="${cartLine.getQuantity()?string.number}" min="1" />
                        </#if>
                    </#if>
                  </td>
                  <td class="amount"><@ofbizCurrency amount=cartLine.getDisplayPrice() isoCode=shoppingCart.getCurrency()/></td>
                  <td class="amount"><@ofbizCurrency amount=cartLine.getOtherAdjustments() isoCode=shoppingCart.getCurrency()/></td>
                  <td class="amount"><@ofbizCurrency amount=cartLine.getDisplayItemSubTotal() isoCode=shoppingCart.getCurrency()/></td>
                  <td>
                    <#if !cartLine.getIsPromo()>
                      <input type="checkbox" name="selectedItem" value="${cartLineIndex}" class="selectAllChild"/>
                    <#else>
                      &nbsp;
                    </#if>
                  </td>
                </tr>
              </#list>
            </tbody>
          <tfoot>
            <tr class="thead-light">
              <th colspan="8">
                Summary:
              </th>
            </tr>
            <#if shoppingCart.getAdjustments()?has_content>
              <tr>
                <th colspan="6">${uiLabelMap.CommonSubTotal}:</th>
                <td class="amount" colspan="1">
                  <@ofbizCurrency amount=shoppingCart.getDisplaySubTotal() isoCode=shoppingCart.getCurrency()/>
                </td>
              </tr>
              <#if (shoppingCart.getDisplayTaxIncluded() > 0.0)>
                <tr>
                  <th colspan="6">${uiLabelMap.OrderSalesTaxIncluded}:</th>
                  <td class="amount" colspan="1">
                    <@ofbizCurrency amount=shoppingCart.getDisplayTaxIncluded() isoCode=shoppingCart.getCurrency()/>
                  </td>
                </tr>
              </#if>
              <#list shoppingCart.getAdjustments() as cartAdjustment>
                <#assign adjustmentType = cartAdjustment.getRelatedOne("OrderAdjustmentType", true) />
                <tr>
                  <th colspan="6">
                    ${uiLabelMap.EcommerceAdjustment} - ${adjustmentType.get("description",locale)!}
                    <#if cartAdjustment.productPromoId?has_content>
                      <a href="<@ofbizUrl>showPromotionDetails?productPromoId=${cartAdjustment.productPromoId}</@ofbizUrl>">
                        ${uiLabelMap.CommonDetails}
                      </a>
                    </#if>:
                  </th>
                  <td class="amount" colspan="1">
                    <@ofbizCurrency amount=Static["org.apache.ofbiz.order.order.OrderReadHelper"]
                        .calcOrderAdjustment(cartAdjustment,
                        shoppingCart.getSubTotal()) isoCode=shoppingCart.getCurrency()/>
                  </td>
                </tr>
              </#list>
            </#if>
            <tr>
              <th colspan="6">${uiLabelMap.EcommerceCartTotal}:</th>
              <td class="amount" colspan="1">
                <@ofbizCurrency amount=shoppingCart.getDisplayGrandTotal() isoCode=shoppingCart.getCurrency()/>
              </td>
            </tr>
            <#if itemsFromList>
              <tr>
                <td colspan="8">L - ${uiLabelMap.EcommerceItemsfromShopingList}.</td>
              </tr>
            </#if>
            <#if promoItems>
              <tr>
                <td colspan="8"><span class="badge badge-success">P</span> - ${uiLabelMap.EcommercePromotionalItems}.</td>
              </tr>
            </#if>
            <#if !itemsFromList && !promoItems>
              <tr>
                <td colspan="8">&nbsp;</td>
              </tr>
            </#if>
            <tr>
              <td colspan="8">
                <#if sessionAttributes.userLogin?has_content && sessionAttributes.userLogin.userLoginId != "anonymous">
                  <div class="input-group">
                  <select class="form-control selectBox" name="shoppingListId">
                    <#if shoppingLists?has_content>
                      <#list shoppingLists as shoppingList>
                        <option value="${shoppingList.shoppingListId}">${shoppingList.listName}</option>
                      </#list>
                    </#if>
                    <option value="">---</option>
                    <option value="">${uiLabelMap.OrderNewShoppingList}</option>
                  </select>
                  <span class="input-group-btn">
                  <a href="javascript:addToList();" class="btn btn-outline-secondary">
                    ${uiLabelMap.EcommerceAddSelectedtoList}
                  </a>
                  </span>
                  </div>
                <#else>
                  ${uiLabelMap.OrderYouMust}
                  <a href="<@ofbizUrl>checkLogin/showcart</@ofbizUrl>" class="btn btn-outline-secondary btn-sm">
                    ${uiLabelMap.CommonBeLogged}
                  </a>
                  ${uiLabelMap.OrderToAddSelectedItemsToShoppingList}.&nbsp;
                </#if>
              </td>
            </tr>
            <tr>
              <td colspan="8">
                <#if sessionAttributes.userLogin?has_content && sessionAttributes.userLogin.userLoginId != "anonymous">
                  <a href="<@ofbizUrl>createCustRequestFromCart</@ofbizUrl>" class="btn btn-outline-secondary btn-sm">
                    ${uiLabelMap.OrderCreateCustRequestFromCart}
                  </a>
                <#else>
                  ${uiLabelMap.OrderYouMust}
                  <a href="<@ofbizUrl>checkLogin/showcart</@ofbizUrl>" class="btn btn-outline-secondary btn-sm">
                    ${uiLabelMap.CommonBeLogged}
                  </a>
                  ${uiLabelMap.EcommerceToOrderCreateCustRequestFromCart}.&nbsp;
                </#if>
              </td>
            </tr>
            <tr>
              <td colspan="8">
              <label class="form-check-label">
                <input type="checkbox" onclick="javascript:document.cartform.submit()"
                    name="alwaysShowcart" <#if shoppingCart.viewCartOnAdd()>checked="checked"</#if>/>
                ${uiLabelMap.EcommerceAlwaysViewCartAfterAddingAnItem}.
              </label>
              </td>
            </tr>
          </tfoot>
        </table>
      </form>
    <#else>
      <h3>${uiLabelMap.EcommerceYourShoppingCartEmpty}.</h3>
    </#if>
<#-- Copy link bar to bottom to include a link bar at the bottom too -->
    </div>
</div>

<div class="row">
  <div class="col-xl-6">
  <div>
    <h3>${uiLabelMap.ProductPromoCodes}</h3>
  </div>
  <div>
    <div>
      <form method="post"
          action="<@ofbizUrl>addpromocode<#if requestAttributes._CURRENT_VIEW_?has_content>/${requestAttributes._CURRENT_VIEW_}</#if></@ofbizUrl>" name="addpromocodeform">
        <div class="input-group">
          <input type="text" class="form-control form-control-sm" size="15" name="productPromoCodeId" value="" />
           <span class="input-group-btn">
             <input type="submit" class="btn btn-outline-secondary btn-sm" value="${uiLabelMap.OrderAddCode}" />
           </span>
        </div>
          <#assign productPromoCodeIds = (shoppingCart.getProductPromoCodesEntered())! />
          <#if productPromoCodeIds?has_content>
            ${uiLabelMap.ProductPromoCodesEntered}
            <ul>
              <#list productPromoCodeIds as productPromoCodeId>
                <li>${productPromoCodeId}</li>
              </#list>
            </ul>
          </#if>

      </form>
    </div>
  </div>
  </div>

<#if showPromoText?? && showPromoText>
   <div class="col-xl-6">
    <div>
      <h3>${uiLabelMap.OrderSpecialOffers}</h3>
    </div>
    <div>
      <#-- show promotions text -->
      <ul>
        <#list productPromos as productPromo>
          <li>
            <a href="<@ofbizUrl>showPromotionDetails?productPromoId=${productPromo.productPromoId}</@ofbizUrl>"
                >[${uiLabelMap.CommonDetails}]</a>
              ${StringUtil.wrapString(productPromo.promoText!)}
          </li>
        </#list>
      </ul>
      <div>
        <a href="<@ofbizUrl>showAllPromotions</@ofbizUrl>" class="btn btn-outline-secondary btn-sm">${uiLabelMap.OrderViewAllPromotions}</a>
      </div>
    </div>
  </div>
</#if>
</div>

<#if associatedProducts?has_content>
  <div class="card">
    <div class="card-header">
      <h3>${uiLabelMap.EcommerceYouMightAlsoIntrested}:</h3>
    </div>
    <div class="card-body">
      <#-- random complementary products -->
      <div class="row row-eq-height">
      <#list associatedProducts as assocProduct>

          ${setRequestAttribute("optProduct", assocProduct)}
          ${setRequestAttribute("listIndex", assocProduct_index)}
          ${screens.render("component://ecommerce/widget/CatalogScreens.xml#productsummary")}

      </#list>
        </div>
    </div>
  </div>
</#if>

<#if (shoppingCartSize?default(0) > 0)>
  ${screens.render("component://ecommerce/widget/CartScreens.xml#promoUseDetailsInline")}
</#if>

<!-- Internal cart info: productStoreId=${shoppingCart.getProductStoreId()!}
       locale=${shoppingCart.getLocale()!} currencyUom=${shoppingCart.getCurrency()!}
       userLoginId=${(shoppingCart.getUserLogin().getString("userLoginId"))!}
       autoUserLogin=${(shoppingCart.getAutoUserLogin().getString("userLoginId"))!} -->
