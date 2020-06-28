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
  ${virtualJavaScript!}
  <script type="text/javascript">
<!--
    function displayProductVirtualId(variantId, virtualProductId, pForm) {
        if(variantId){
            pForm.product_id.value = variantId;
        }else{
            pForm.product_id.value = '';
            variantId = '';
        }
        var elem = document.getElementById('product_id_display');
        var txt = document.createTextNode(variantId);
        if(elem.hasChildNodes()) {
            elem.replaceChild(txt, elem.firstChild);
        } else {
            elem.appendChild(txt);
        }

        var priceElem = document.getElementById('variant_price_display');
        var price = getVariantPrice(variantId);
        var priceTxt = null;
        if(price){
            priceTxt = document.createTextNode(price);
        }else{
            priceTxt = document.createTextNode('');
        }

        if(priceElem.hasChildNodes()) {
            priceElem.replaceChild(priceTxt, priceElem.firstChild);
        } else {
            priceElem.appendChild(priceTxt);
        }
    }
//-->
</script>
  ${virtualJavaScript!}
  <script type="text/javascript">
<!--
    function displayProductPrice(a, tag) {
        if (typeof(tag) == 'undefined') {
            tag = 'h4';
        }
        var div = a.closest('div');
        var secs = [
            'aggregated', 'compare', 'list', 'your', 'saved', 'details'];
        var title = '';
        for (var c in secs) {
            var obj = $('.product-price-' + secs[c], div);
            if (obj.length) {
                if (title) {
                    title += '<br />';
                }
                var wrapped = $("<div>" + obj.html() + "</div>");
                wrapped.find('.product-price-expand').remove();
                title += wrapped.html();
            }
        }
        return '<' + tag + '>' + title + '</' + tag + '>';
    }
    $( document ).ready(function() {
        $(".product-price-expand").popover({
            content: function() {return displayProductPrice(this, 'h6')},
            title: function() {
                return $('.product-id', this.closest('div')).html();
            },
            html: true,
            trigger: 'focus',
            placement: 'auto'
        });
    });
//-->
</script>
  <#if product??>
    <#-- variable setup -->
      <#if "Y" == backendPath?default("N")>
        <#assign productUrl><@ofbizCatalogUrl productId=product.productId productCategoryId=categoryId/></#assign>
        <#else>
          <#assign productUrl><@ofbizCatalogAltUrl productId=product.productId productCategoryId=categoryId/></#assign>
      </#if>

      <#if requestAttributes.productCategoryMember??>
        <#assign prodCatMem = requestAttributes.productCategoryMember>
      </#if>
      <#assign smallImageUrl = productContentWrapper.get("SMALL_IMAGE_URL", "url")!>
        <#assign largeImageUrl = productContentWrapper.get("LARGE_IMAGE_URL", "url")!>
          <#if !smallImageUrl?string?has_content><#assign smallImageUrl = "/images/defaultImage.jpg"></#if>
          <#if !largeImageUrl?string?has_content><#assign largeImageUrl = "/images/defaultImage.jpg"></#if>
          <#-- end variable setup -->
            <#assign productInfoLinkId = "productInfoLink">
              <#assign productInfoLinkId = productInfoLinkId + product.productId/>
              <#assign productDetailId = "productDetailId"/>
              <#assign productDetailId = productDetailId + product.productId/>

              <div class="col-md-3 products-card">
                <div class="card text-center">
                  <a href="${productUrl}">
                    <img class="card-img-top" src="<@ofbizContentUrl>${contentPathPrefix!}${smallImageUrl}</@ofbizContentUrl>" alt="Small Image">
                  </a>
                  <div class="card-body">
                    <h4 class="card-title"><a href="${productUrl}" class="btn btn-link">${productContentWrapper.get("PRODUCT_NAME", "html")!}</a></h4>
                    <div class="card-text">
                      <#assign textNDivs = 1?int/>
                      <#if prodCatMem?? && prodCatMem.comments?has_content>
                        <#assign textNDivs = textNDivs + 1/>
                      </#if>
                      <#if sizeProductFeatureAndAppls?has_content>
                        <#-- assign textNDivs = textNDivs + 1 + sizeProductFeatureAndAppls?size/ -->
                        <#assign textNDivs = textNDivs + 1/>
                      </#if>
                      <#assign textDivPercent = 50 / textNDivs/>
                      <div class="product-description percent-${textDivPercent?int}">${productContentWrapper.get("DESCRIPTION", "html")!}<#if daysToShip??>&nbsp;-&nbsp;${uiLabelMap.ProductUsuallyShipsIn} <b>${daysToShip}</b> ${uiLabelMap.CommonDays}!</#if></div>

                      <#-- Display category-specific product comments -->
                        <#if prodCatMem?? && prodCatMem.comments?has_content>
                          <p class="product-category-comment percent-${textDivPercent?int}">${prodCatMem.comments}</p>
                        </#if>

                        <#-- example of showing a certain type of feature with the product -->
                          <#if sizeProductFeatureAndAppls?has_content>
                            <div class="product-exploded-feature percent-${textDivPercent?int}">
                              <#if (sizeProductFeatureAndAppls?size == 1)>
                                <p>${uiLabelMap.OrderSizeAvailableSingle}:</p>
                                <#else>
                                  ${uiLabelMap.OrderSizeAvailableMultiple}:
                              </#if>
                              <div>
                                <#list sizeProductFeatureAndAppls as sizeProductFeatureAndAppl>
                                  ${sizeProductFeatureAndAppl.abbrev?default(sizeProductFeatureAndAppl.description?default(sizeProductFeatureAndAppl.productFeatureId))}<#if sizeProductFeatureAndAppl_has_next>,</#if>
                                </#list>
                              </div>
                            </div>
                          </#if>
                          <div class="product-price">
                            <p class="product-id"><strong>${product.productId!}</strong></p>
                            <dl>
                              <dt></dt>
                              <dd></dd>
                            </dl>
                            <#if totalPrice??>
                              <p class="product-price-aggregated">${uiLabelMap.ProductAggregatedPrice}: <span class='basePrice'><@ofbizCurrency amount=totalPrice isoCode=totalPrice.currencyUsed/></span></p>
                            <#else>
                              <#if price.competitivePrice?? && price.price?? && price.price?double < price.competitivePrice?double>
                                <p class="product-price-compare">
                                ${uiLabelMap.ProductCompareAtPrice}: <span class='basePrice'><@ofbizCurrency amount=price.competitivePrice isoCode=price.currencyUsed/></span>
                                </p>
                              </#if>
                              <#if price.listPrice?? && price.price?? && price.price?double < price.listPrice?double>
                                <p class="product-price-list">
                                  ${uiLabelMap.ProductListPrice}: <span class="basePrice"><@ofbizCurrency amount=price.listPrice isoCode=price.currencyUsed/></span>
                                </p>
                              </#if>
                              <b class="product-price-main">
                                <#if price.isSale?? && price.isSale>
                                  <p class="badge badge-info">${uiLabelMap.OrderOnSale}!</p>
                                  <#assign priceStyle = "salePrice">
                                    <#else>
                                      <#assign priceStyle = "regularPrice">
                                </#if>
                                <a class="product-price-expand" href="javascript:void(0);">?</a>
                                <#if (price.price?default(0) > 0 && "N" == product.requireAmount?default("N"))>
                                  <p class="product-price-your">${uiLabelMap.OrderYourPrice}: <#if "Y" = product.isVirtual!> ${uiLabelMap.CommonFrom} </#if><span class="${priceStyle}"><@ofbizCurrency amount=price.price isoCode=price.currencyUsed/></span></p>
                                </#if>
                              </b>
                              <#if price.listPrice?? && price.price?? && price.price?double < price.listPrice?double>
                                <#assign priceSaved = price.listPrice?double - price.price?double>
                                <#assign percentSaved = (priceSaved?double / price.listPrice?double) * 100>
                                  <p class="product-price-saved">
                                    ${uiLabelMap.OrderSave}: <span class="basePrice"><@ofbizCurrency amount=priceSaved isoCode=price.currencyUsed/> (${percentSaved?int}%)</span>
                                  </p>
                              </#if>
                            </#if>
                          <#-- show price details ("showPriceDetails" field can be set in the screen definition) -->
                            <#if (showPriceDetails?? && "Y" == showPriceDetails?default("N"))>
                              <#if price.orderItemPriceInfos??>
                                <#list price.orderItemPriceInfos as orderItemPriceInfo>
                                  <div class="product-price-details">${orderItemPriceInfo.description!}</div>
                                </#list>
                              </#if>
                            </#if>
                          </div>
        <#if averageRating?? && (averageRating?double > 0) && numRatings?? && (numRatings?long > 2)>
          <div class="product-rating">${uiLabelMap.OrderAverageRating}: ${averageRating} (${uiLabelMap.CommonFrom} ${numRatings} ${uiLabelMap.OrderRatings})</div>
        </#if>
        <form class="product-compare" method="post" action="<@ofbizUrl secure="${request.isSecure()?string}">addToCompare</@ofbizUrl>" name="addToCompare${requestAttributes.listIndex!}form">
          <input type="hidden" name="productId" value="${product.productId}"/>
          <input type="hidden" name="mainSubmitted" value="Y"/>
        </form>
                    </div>
      <div id="${productDetailId}" style="display:none;">
        <img src="<@ofbizContentUrl>${contentPathPrefix!}${largeImageUrl}</@ofbizContentUrl>" alt="Large Image"/>
        ${uiLabelMap.ProductProductId} ${product.productId!}
        ${uiLabelMap.ProductProductName} ${productContentWrapper.get("PRODUCT_NAME", "html")!}
        ${uiLabelMap.CommonDescription} ${productContentWrapper.get("DESCRIPTION", "html")!}
      </div>
      <div class="productbuy">
        <#-- check to see if introductionDate hasn't passed yet -->
          <#if product.introductionDate?? && nowTimestamp.before(product.introductionDate)>
            <div style="color: red;">${uiLabelMap.ProductNotYetAvailable}</div>
            <#-- check to see if salesDiscontinuationDate has passed -->
              <#elseif product.salesDiscontinuationDate?? && nowTimestamp.after(product.salesDiscontinuationDate)>
                <div style="color: red;">${uiLabelMap.ProductNoLongerAvailable}</div>
                <#-- check to see if it is a rental item; will enter parameters on the detail screen-->
                  <#elseif "ASSET_USAGE" == product.productTypeId!>
                    <a href="${productUrl}" class="btn btn-outline-secondary btn-sm">${uiLabelMap.OrderMakeBooking}...</a>
                    <#-- check to see if it is an aggregated or configurable product; will enter parameters on the detail screen-->
                      <#elseif "AGGREGATED" == product.productTypeId! || "AGGREGATED_SERVICE" == product.productTypeId!>
                        <a href="${productUrl}" class="btn btn-outline-secondary btn-sm">${uiLabelMap.OrderConfigure}...</a>
                        <#-- check to see if the product is a virtual product -->
                          <#elseif product.isVirtual?? && "Y" == product.isVirtual>
                            <a href="${productUrl}" class="btn btn-outline-secondary btn-sm">${uiLabelMap.OrderChooseVariations}...</a>
                            <#-- check to see if the product requires an amount -->
                              <#elseif product.requireAmount?? && "Y" == product.requireAmount>
                                <a href="${productUrl}" class="btn btn-outline-secondary btn-sm">${uiLabelMap.OrderChooseAmount}...</a>
                                <#elseif "ASSET_USAGE_OUT_IN" == product.productTypeId!>
                                  <a href="${productUrl}" class="btn btn-outline-secondary btn-sm">${uiLabelMap.OrderRent}...</a>
                                  <#else>
                                    <#assign defaultQuantity = 1/>
                                    <#if prodCatMem?? && prodCatMem.quantity?? && 0.00 < prodCatMem.quantity?double>
                                      <#assign defaultQuantity = prodCatMem.quantity?double/>
                                    </#if>
                                    <form method="post" action="<@ofbizUrl>additem</@ofbizUrl>" name="the${requestAttributes.formNamePrefix!}${requestAttributes.listIndex!}form" style="margin: 0;">
                                      <div class="form-group" style="margin: 0;">
                                        <input type="hidden" name="add_product_id" value="${product.productId}"/>
                                        <input type="hidden" name="clearSearch" value="N"/>
                                        <input type="hidden" name="mainSubmitted" value="Y"/>
                                        <div class="input-group">
                                          <input type="text" class="form-control form-control-sm" name="quantity" value="${defaultQuantity}"/>
                                          <a href="javascript:document.the${requestAttributes.formNamePrefix!}${requestAttributes.listIndex!}form.submit()" class="btn btn-outline-secondary btn-sm">${uiLabelMap.OrderAddToCart}</a>
                                        </div>
                                        <#if mainProducts?has_content>
                                          <input type="hidden" name="product_id" value=""/>
                                          <select name="productVariantId" onchange="javascript:displayProductVirtualId(this.value, '${product.productId}', this.form);" style="width: 100%;">
                                            <option value="">Select Unit Of Measure</option>
                                            <#list mainProducts as mainProduct>
                                              <option value="${mainProduct.productId}">${mainProduct.uomDesc} : ${mainProduct.piecesIncluded}</option>
                                            </#list>
                                          </select>
                                          <div style="display: inline-block;">
                                            <strong><span id="product_id_display"> </span></strong>
                                            <strong><span id="variant_price_display"> </span></strong>
                                          </div>
                                        </#if>
                                      </div>
                                    </form>
                                    <a href="javascript:document.addToCompare${requestAttributes.listIndex!}form.submit()" class="btn btn-link btn-sm">${uiLabelMap.ProductAddToCompare}</a>
                                    <#if prodCatMem?? && prodCatMem.quantity?? && 0.00 < prodCatMem.quantity?double>
                                    <form method="post" action="<@ofbizUrl>additem</@ofbizUrl>" name="the${requestAttributes.formNamePrefix!}${requestAttributes.listIndex!}defaultform" style="margin: 0;">
                                      <input type="hidden" name="add_product_id" value="${prodCatMem.productId!}"/>
                                      <input type="hidden" name="quantity" value="${prodCatMem.quantity!}"/>
                                      <input type="hidden" name="clearSearch" value="N"/>
                                      <input type="hidden" name="mainSubmitted" value="Y"/>
                                      <a href="javascript:document.the${requestAttributes.formNamePrefix!}${requestAttributes.listIndex!}defaultform.submit()" class="btn btn-outline-secondary btn-sm">${uiLabelMap.CommonAddDefault}(${prodCatMem.quantity?string.number}) ${uiLabelMap.OrderToCart}</a>
                                    </form>
                                    <#assign productCategory = delegator.findOne("ProductCategory", Static["org.apache.ofbiz.base.util.UtilMisc"].toMap("productCategoryId", prodCatMem.productCategoryId), false)/>
                                    <#if productCategory.productCategoryTypeId != "BEST_SELL_CATEGORY">
                                      <form method="post" action="<@ofbizUrl>additem</@ofbizUrl>" name="the${requestAttributes.formNamePrefix!}${requestAttributes.listIndex!}defaultform" style="margin: 0;">
                                        <input type="hidden" name="add_product_id" value="${prodCatMem.productId!}"/>
                                        <input type="hidden" name="quantity" value="${prodCatMem.quantity!}"/>
                                        <input type="hidden" name="clearSearch" value="N"/>
                                        <input type="hidden" name="mainSubmitted" value="Y"/>
                                        <a href="javascript:document.the${requestAttributes.formNamePrefix!}${requestAttributes.listIndex!}defaultform.submit()" class="btn btn-outline-secondary btn-sm">${uiLabelMap.CommonAddDefault}(${prodCatMem.quantity?string.number}) ${uiLabelMap.OrderToCart}</a>
                                      </form>
                                    </#if>
          </#if>
          </#if>
      </div>
      <#else>
        &nbsp;${uiLabelMap.ProductErrorProductNotFound}.<br />
        </#if>
    </div>
  </div>
</div>


