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
<div class="card">
<#if productCategory?has_content>
<div class="card-header">
  <strong>${productCategory.categoryName!}</strong>
</div>
<div class="card-body">
<form name="choosequickaddform" method="post" action="<@ofbizUrl>quickadd</@ofbizUrl>">
  <select name='category_id' class="form-control">
    <option value='${productCategory.productCategoryId}'>${productCategory.categoryName!}</option>
    <option value='${productCategory.productCategoryId}'>--</option>
    <#list quickAddCats as quickAddCatalogId>
    <#assign loopCategory = delegator.findOne("ProductCategory", Static["org.apache.ofbiz.base.util.UtilMisc"].toMap("productCategoryId", quickAddCatalogId), true)>
    <#if loopCategory?has_content>
    <option value='${quickAddCatalogId}'>${loopCategory.categoryName!}</option>
  </#if>
</#list>
</select>
<div class="pt-2 pb-1"><a href="javascript:document.choosequickaddform.submit()" class="buttontext">${uiLabelMap.ProductChooseQuickAddCategory}</a></div>
</form>
<#if productCategory.categoryImageUrl?? || productCategory.longDescription??>
<div>
  <#if productCategory.categoryImageUrl??>
  <img src="<@ofbizContentUrl>${productCategory.categoryImageUrl}</@ofbizContentUrl>" vspace="5" hspace="5" class="cssImgLarge" alt="" />
</#if>
<div class="pt-2 pb-1">
${productCategory.longDescription!}
</div>
</div>
</#if>
</#if>

<#if productCategoryMembers?? && 0 < productCategoryMembers?size>
<form method="post" action="<@ofbizUrl>addtocartbulk</@ofbizUrl>" name="bulkaddform">
  <fieldset>
    <input type='hidden' name='category_id' value='${categoryId}' />
    <div class="quickaddall">
      <a href="javascript:document.bulkaddform.submit()" class="btn btn-outline-secondary ml-3">${uiLabelMap.OrderAddAllToCart}</a>
    </div>
    <div class="quickaddtable">
      <#list productCategoryMembers as productCategoryMember>
      <#assign product = productCategoryMember.getRelatedOne("Product", true)>
      <p>
        ${setRequestAttribute("optProductId", productCategoryMember.productId)}
        ${screens.render(quickaddsummaryScreen)}
      </p>
    </#list>
    </div>
    <div class="ml-3">
      <a href="javascript:document.bulkaddform.submit()" class="btn btn-outline-secondary">${uiLabelMap.OrderAddAllToCart}</a>
    </div>
  </fieldset>
</form>
<#else>
<label>${uiLabelMap.ProductNoProductsInThisCategory}.</label>
</#if>
</div>
</div>

