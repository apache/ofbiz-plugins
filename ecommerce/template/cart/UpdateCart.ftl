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

<#if shoppingCart?has_content && shoppingCart.size() &gt; 0>
  <div class="card">
    <div class="card-header bg-info text-white">${uiLabelMap.EcommerceStep} 1: ${uiLabelMap.PageTitleShoppingCart}</div>
    <div class="card-body">
    <div id="cartSummaryPanel" style="display: none;">
    <table class="table table-responsive-sm" id="cartSummaryPanel_cartItems" summary="This table displays the list of item added into Shopping Cart.">
      <thead class="thead-light">
        <tr>
          <th id="orderItem">${uiLabelMap.OrderItem}</th>
          <th id="description">${uiLabelMap.CommonDescription}</th>
          <th id="unitPrice" class="amount">${uiLabelMap.EcommerceUnitPrice}</th>
          <th id="quantity">${uiLabelMap.OrderQuantity}</th>
          <th id="adjustment" class="amount">${uiLabelMap.EcommerceAdjustments}</th>
          <th id="itemTotal" class="amount">${uiLabelMap.EcommerceItemTotal}</th>
        </tr>
      </thead>
      <tfoot>
        <tr id="completedCartSubtotalRow">
          <td id="subTotal" scope="row" colspan="4">${uiLabelMap.CommonSubtotal}</td>
          <td headers="subTotal" colspan="2" class="amount" id="completedCartSubTotal">
            <@ofbizCurrency amount=shoppingCart.getSubTotal() isoCode=shoppingCart.getCurrency() />
          </td>
        </tr>
        <#assign orderAdjustmentsTotal = 0 />
        <#list shoppingCart.getAdjustments() as cartAdjustment>
          <#assign orderAdjustmentsTotal = orderAdjustmentsTotal +
              Static["org.apache.ofbiz.order.order.OrderReadHelper"]
              .calcOrderAdjustment(cartAdjustment, shoppingCart.getSubTotal()) />
        </#list>
        <tr id="completedCartDiscountRow">
          <td id="productDiscount" scope="row" colspan="4">${uiLabelMap.ProductDiscount}</td>
          <td headers="productDiscount" colspan="2" class="amount text-success" id="completedCartDiscount">
            <input type="hidden" value="${orderAdjustmentsTotal}" id="initializedCompletedCartDiscount" />
            <@ofbizCurrency amount=orderAdjustmentsTotal isoCode=shoppingCart.getCurrency() />
          </td>
        </tr>
        <tr>
          <td id="shippingAndHandling" scope="row" colspan="4">${uiLabelMap.OrderShippingAndHandling}</td>
          <td headers="shippingAndHandling" colspan="2" class="amount" id="completedCartTotalShipping">
            <@ofbizCurrency amount=shoppingCart.getTotalShipping() isoCode=shoppingCart.getCurrency() />
          </td>
        </tr>
        <tr>
          <td id="salesTax" scope="row" colspan="4">${uiLabelMap.OrderSalesTax}</td>
          <td headers="salesTax" colspan="2" class="amount" id="completedCartTotalSalesTax">
            <@ofbizCurrency amount=shoppingCart.getTotalSalesTax() isoCode=shoppingCart.getCurrency() />
          </td>
        </tr>
        <tr>
          <td id="grandTotal" scope="row" colspan="4">${uiLabelMap.OrderGrandTotal}</td>
          <td headers="grandTotal" colspan="2" class="amount" id="completedCartDisplayGrandTotal">
            <strong><@ofbizCurrency amount=shoppingCart.getDisplayGrandTotal() isoCode=shoppingCart.getCurrency() /></strong>
          </td>
        </tr>
      </tfoot>
      <tbody>
        <#list shoppingCart.items() as cartLine>
          <#if cartLine.getProductId()??>
            <#if cartLine.getParentProductId()??>
              <#assign parentProductId = cartLine.getParentProductId() />
            <#else>
              <#assign parentProductId = cartLine.getProductId() />
            </#if>
            <#assign smallImageUrl = Static["org.apache.ofbiz.product.product.ProductContentWrapper"]
                .getProductContentAsText(cartLine.getProduct(), "SMALL_IMAGE_URL", locale, dispatcher, "url")! />
            <#if !smallImageUrl?string?has_content><#assign smallImageUrl = "" /></#if>
          </#if>
          <tr id="cartItemDisplayRow_${cartLine_index}">
            <td headers="orderItem">
              <img src="<@ofbizContentUrl>${requestAttributes.contentPathPrefix!}${smallImageUrl}</@ofbizContentUrl>"
                  alt = "Product Image" /></td>
            <td headers="description">${cartLine.getName(dispatcher)!}</td>
            <td class="amount" headers="unitPrice">${cartLine.getDisplayPrice()}</td>
            <td headers="quantity">
              <span id="completedCartItemQty_${cartLine_index}">${cartLine.getQuantity()?string.number}</span>
            </td>
            <td class="amount" headers="adjustment">
              <span id="completedCartItemAdjustment_${cartLine_index}">
                <@ofbizCurrency amount=cartLine.getOtherAdjustments() isoCode=shoppingCart.getCurrency() />
              </span>
            </td>
            <td class="amount" headers="itemTotal" align="right">
              <span id="completedCartItemSubTotal_${cartLine_index}">
                <@ofbizCurrency amount=cartLine.getDisplayItemSubTotal() isoCode=shoppingCart.getCurrency() />
              </span>
            </td>
          </tr>
        </#list>
      </tbody>
    </table>
    <a href="javascript:void(0);" id="openCartPanel" class="btn btn-outline-secondary">${uiLabelMap.EcommerceClickHereToEdit}</a>
  </div>
  <div id="editCartPanel">
    <form id="cartForm" method="post" action="<@ofbizUrl></@ofbizUrl>">
        <input type="hidden" name="removeSelected" value="false" />
        <div id="cartFormServerError" class="errorMessage"></div>
        <table class="table table-responsive-sm" id="editCartPanel_cartItems">
          <thead class="thead-light">
            <tr>
              <th id="editOrderItem">${uiLabelMap.OrderItem}</th>
              <th id="editDescription">${uiLabelMap.CommonDescription}</th>
              <th id="editUnitPrice" class="amount">${uiLabelMap.EcommerceUnitPrice}</th>
              <th id="editQuantity">${uiLabelMap.OrderQuantity}</th>
              <th id="editAdjustment" class="amount">${uiLabelMap.EcommerceAdjustments}</th>
              <th id="editItemTotal" class="amount">${uiLabelMap.EcommerceItemTotal}</th>
              <th id="removeItem">${uiLabelMap.FormFieldTitle_removeButton}</th>
            </tr>
          </thead>
          <tfoot>
            <tr class="thead-light"><th colspan="7" >Summary</th></tr>
            <tr>
              <td scope="row" colspan="5">${uiLabelMap.CommonSubtotal}</td>
              <td class="amount" id="cartSubTotal" >
                <@ofbizCurrency amount=shoppingCart.getSubTotal() isoCode=shoppingCart.getCurrency() />
              </td>
              <td>&nbsp;</td>
            </tr>
            <tr>
              <td scope="row" colspan="5">${uiLabelMap.ProductDiscount}</td>
              <td id="cartDiscountValue" class="amount text-success">
                <#assign orderAdjustmentsTotal = 0  />
                <#list shoppingCart.getAdjustments() as cartAdjustment>
                  <#assign orderAdjustmentsTotal = orderAdjustmentsTotal +
                      Static["org.apache.ofbiz.order.order.OrderReadHelper"]
                      .calcOrderAdjustment(cartAdjustment, shoppingCart.getSubTotal()) />
                </#list>
                <@ofbizCurrency amount=orderAdjustmentsTotal isoCode=shoppingCart.getCurrency() />
              </td>
              <td>&nbsp;</td>
            </tr>
            <tr>
              <td scope="row" colspan="5">${uiLabelMap.OrderShippingAndHandling}</td>
              <td id="cartTotalShipping" class="amount">
                <@ofbizCurrency amount=shoppingCart.getTotalShipping() isoCode=shoppingCart.getCurrency() />
              </td>
              <td>&nbsp;</td>
            </tr>
            <tr>
              <td scope="row" colspan="5">${uiLabelMap.OrderSalesTax}</td>
              <td id="cartTotalSalesTax" class="amount">
                <@ofbizCurrency amount=shoppingCart.getTotalSalesTax() isoCode=shoppingCart.getCurrency() />
              </td>
              <td>&nbsp;</td>
            </tr>
            <tr>
              <td scope="row" colspan="5">${uiLabelMap.OrderGrandTotal}</td>
              <td id="cartDisplayGrandTotal" class="amount">
                <strong><@ofbizCurrency amount=shoppingCart.getDisplayGrandTotal() isoCode=shoppingCart.getCurrency() /></strong>
              </td>
              <td>&nbsp;</td>
            </tr>
          </tfoot>
          <tbody id="updateBody">
            <#list shoppingCart.items() as cartLine>
              <tr id="cartItemRow_${cartLine_index}">
                <td headers="editOrderItem">
                  <#if cartLine.getProductId()??>
                    <#if cartLine.getParentProductId()??>
                      <#assign parentProductId = cartLine.getParentProductId() />
                    <#else>
                      <#assign parentProductId = cartLine.getProductId() />
                    </#if>
                    <#assign smallImageUrl = Static["org.apache.ofbiz.product.product.ProductContentWrapper"]
                        .getProductContentAsText(cartLine.getProduct(), "SMALL_IMAGE_URL",
                        locale, dispatcher, "url")! />
                    <#if !smallImageUrl?string?has_content><#assign smallImageUrl = "" /></#if>
                    <#if smallImageUrl?string?has_content>
                      <img src="<@ofbizContentUrl>${requestAttributes.contentPathPrefix!}${smallImageUrl}</@ofbizContentUrl>"
                          alt="Product Image" />
                    </#if>
                  </#if>
                </td>
                <td headers="editDescription">${cartLine.getName(dispatcher)!}</td>
                <td class="amount" headers="editUnitPrice" id="itemUnitPrice_${cartLine_index}">
                  <@ofbizCurrency amount=cartLine.getDisplayPrice() isoCode=shoppingCart.getCurrency() />
                </td>
                <td headers="editQuantity">
                  <#if cartLine.getIsPromo()>
                    ${cartLine.getQuantity()?string.number}
                  <#else>
                    <input type="hidden" name="cartLineProductId" id="cartLineProductId_${cartLine_index}"
                        value="${cartLine.getProductId()}" />
                    <input type="text" name="update${cartLine_index}" id="qty_${cartLine_index}"
                        value="${cartLine.getQuantity()?string.number}" class="required validate-number" />
                    <span id="advice-required-qty_${cartLine_index}" style="display:none;" class="errorMessage">
                      (${uiLabelMap.CommonRequired})
                    </span>
                    <span id="advice-validate-number-qty_${cartLine_index}" style="display:none;" class="errorMessage">
                      (${uiLabelMap.CommonPleaseEnterValidNumberInThisField})
                    </span>
                  </#if>
                </td>
                <#if !cartLine.getIsPromo()>
                  <td class="amount" headers="editAdjustment" id="addPromoCode_${cartLine_index}">
                    <@ofbizCurrency amount=cartLine.getOtherAdjustments() isoCode=shoppingCart.getCurrency() />
                  </td>
                <#else>
                  <td class="amount" headers="editAdjustment">
                    <@ofbizCurrency amount=cartLine.getOtherAdjustments() isoCode=shoppingCart.getCurrency() />
                  </td>
                </#if>
                <td class="amount" headers="editItemTotal" id="displayItem_${cartLine_index}">
                  <@ofbizCurrency amount=cartLine.getDisplayItemSubTotal() isoCode=shoppingCart.getCurrency() />
                </td>
                <#if !cartLine.getIsPromo()>
                  <td>
                    <a id="removeItemLink_${cartLine_index}" href="javascript:void(0);">
                      <img id="remove_${cartLine_index}"
                          src="<@ofbizContentUrl>/ecommerce/images/remove.png</@ofbizContentUrl>"
                          alt="Remove Item Image" />
                    </a>
                  </td>
                </#if>
              </tr>
            </#list>
          </tbody>
        </table>
        <div class="form-group">
          <label for="productPromoCode" class="mx-2">${uiLabelMap.EcommerceEnterPromoCode}</label>
          <input id="productPromoCode" class="form-control mb-2" name="productPromoCode" type="text" value="" />
        </div>
        <a href="javascript:void(0);" class="btn btn-primary" id="updateShoppingCart" >
          ${uiLabelMap.EcommerceContinueToStep} 2
        </a>
        <a style="display: none" class="button" href="javascript:void(0);" id="processingShipping">
          ${uiLabelMap.EcommercePleaseWait}....
        </a>
    </form>
  </div>
  </div>
  </div>
</#if>
