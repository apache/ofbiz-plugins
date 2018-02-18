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

<div id="keywordsearchbox" class="card">
  <div class="card-header">
    ${uiLabelMap.ProductSearchCatalog}
  </div>
  <div class="card-body">
    <form name="keywordsearchform" id="keywordsearchbox_keywordsearchform" method="post" action="<@ofbizUrl>keywordsearch</@ofbizUrl>">
      <div class="form-group">
        <input type="hidden" name="VIEW_SIZE" value="10" />
        <input type="hidden" name="PAGING" value="Y" />
        <label for="catalogInput">${uiLabelMap.Catalog}</label>
        <input type="text" name="SEARCH_STRING" size="14" class="form-control" id="catalogInput" maxlength="50" value="${requestParameters.SEARCH_STRING!}" />
      </div>
    <#if 0 &lt; otherSearchProdCatalogCategories?size>
      <div class="form-group">
        <select name="SEARCH_CATEGORY_ID" class="form-control" size="1">
          <option value="${searchCategoryId!}">${uiLabelMap.ProductEntireCatalog}</option>
          <#list otherSearchProdCatalogCategories as otherSearchProdCatalogCategory>
            <#assign searchProductCategory = otherSearchProdCatalogCategory.getRelatedOne("ProductCategory", true)>
            <#if searchProductCategory??>
              <option value="${searchProductCategory.productCategoryId}">${searchProductCategory.description?default("No Description " + searchProductCategory.productCategoryId)}</option>
            </#if>
          </#list>
        </select>
      </div>
    <#else>
      <input type="hidden" name="SEARCH_CATEGORY_ID" value="${searchCategoryId!}" />
    </#if>
      <div class="form-group">
        <div class="form-check">
          <label class="form-check-label" for="SEARCH_OPERATOR_OR"><input type="radio" class="form-check-input" name="SEARCH_OPERATOR" id="SEARCH_OPERATOR_OR" value="OR" <#if "OR" == searchOperator>checked="checked"</#if> />${uiLabelMap.CommonAny}</label>
        </div>
        <div class="form-check">
          <label class="form-check-label" for="SEARCH_OPERATOR_AND"><input type="radio" class="form-check-input" name="SEARCH_OPERATOR" id="SEARCH_OPERATOR_AND" value="AND" <#if "AND" == searchOperator>checked="checked"</#if> />${uiLabelMap.CommonAll}</label>
        </div>
      </div>
      <div class="form-group">
        <input type="submit" value="${uiLabelMap.CommonFind}" class="btn btn-outline-secondary" />
      </div>
    </form>
    <form name="advancedsearchform" id="keywordsearchbox_advancedsearchform" method="post" action="<@ofbizUrl>advancedsearch</@ofbizUrl>">
    <#if 0 &lt; otherSearchProdCatalogCategories?size>
      <label for="SEARCH_CATEGORY_ID">${uiLabelMap.ProductAdvancedSearchIn}: </label>
      <select name="SEARCH_CATEGORY_ID" id="SEARCH_CATEGORY_ID" class="form-control" size="1">
        <option value="${searchCategoryId!}">${uiLabelMap.ProductEntireCatalog}</option>
        <#list otherSearchProdCatalogCategories as otherSearchProdCatalogCategory>
          <#assign searchProductCategory = otherSearchProdCatalogCategory.getRelatedOne("ProductCategory", true)>
          <#if searchProductCategory??>
            <option value="${searchProductCategory.productCategoryId}">${searchProductCategory.description?default("No Description " + searchProductCategory.productCategoryId)}</option>
          </#if>
        </#list>
      </select>
    <#else>
      <input type="hidden" name="SEARCH_CATEGORY_ID" value="${searchCategoryId!}" />
    </#if>
      <div class="form-group">
        <input type="submit" value="${uiLabelMap.ProductAdvancedSearch}" class="btn btn-outline-secondary" />
      </div>
    </form>
  </div>
</div>