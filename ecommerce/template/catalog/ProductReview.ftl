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

<#if requestParameters.product_id??>
  <form id="reviewProduct" method="post" action="<@ofbizUrl>createProductReview</@ofbizUrl>">
    <fieldset class="inline">
      <input type="hidden" name="productStoreId" value="${(productStore.productStoreId)!}" />
      <input type="hidden" name="productId" value="${requestParameters.product_id}" />
      <input type="hidden" name="product_id" value="${requestParameters.product_id}" />
      <input type="hidden" name="category_id" value="${(requestParameters.category_id)!}" />
      <div>
        <label for="one">${uiLabelMap.EcommerceRating}:</label>
        <div class="form-check form-check-inline">
          <input type="radio" class="form-check-input" id="one" name="productRating" value="1.0" />
          <label class="form-check-label" for="one">1</label>
        </div>
        <div class="form-check form-check-inline">
          <input type="radio" class="form-check-input" id="two" name="productRating" value="2.0" />
          <label class="form-check-label" for="two">2</label>
        </div>
        <div class="form-check form-check-inline">
          <input type="radio" class="form-check-input" id="three" name="productRating" value="3.0" />
          <label class="form-check-label" for="three">3</label>
          </div>
        <div class="form-check form-check-inline">
          <input type="radio" class="form-check-input" id="four" name="productRating" value="4.0" />
          <label class="form-check-label" for="four">4</label>
        </div>
        <div class="form-check form-check-inline">
          <input type="radio" class="form-check-input" id="five" name="productRating" value="5.0" />
          <label class="form-check-label" for="five">5</label>
        </div>
      </div>
      <div>
        <label for="yes">${uiLabelMap.EcommercePostAnonymous}:</label>
        <div class="form-check form-check-inline">
          <input type="radio" class="form-check-input" id="yes" name="postedAnonymous" value="Y" />
          <label class="form-check-label" for="yes">${uiLabelMap.CommonYes}</label>
        </div>
        <div class="form-check form-check-inline">
          <input type="radio" class="form-check-input" id="no" name="postedAnonymous" value="N" checked="checked" />
          <label class="form-check-label" for="no">${uiLabelMap.CommonNo}</label>
        </div>
      </div>
      <div>
        <label for="review">${uiLabelMap.CommonReview}:</label>
        <textarea class="textAreaBox" name="productReview" cols="40"></textarea>
      </div>
      <div>
        <a href="javascript:document.getElementById('reviewProduct').submit();" class="button">
          [${uiLabelMap.CommonSave}]
        </a>
        <a href="<@ofbizUrl>product?product_id=${requestParameters.product_id}</@ofbizUrl>" class="button">
          [${uiLabelMap.CommonCancel}]
        </a>
      </div>
    </fieldset>
  </form>
<#else>
  <h2>${uiLabelMap.ProductCannotReviewUnKnownProduct}.</h2>
</#if>
