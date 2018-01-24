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
<#assign searchOptionsHistoryList = Static["org.apache.ofbiz.product.product.ProductSearchSession"].getSearchOptionsHistoryList(session)/>
<#assign currentCatalogId = Static["org.apache.ofbiz.product.catalog.CatalogWorker"].getCurrentCatalogId(request)/>
  <div class="card-header">
    <strong>${uiLabelMap.ProductAdvancedSearchInCategory}</strong>
  </div>
  <div class="card-body">
    <form id="advtokeywordsearchform" method="post" action="<@ofbizUrl>keywordsearch</@ofbizUrl>">
      <fieldset>
        <input type="hidden" name="VIEW_SIZE" value="10"/>
        <input type="hidden" name="PAGING" value="Y"/>
        <input type="hidden" name="SEARCH_CATALOG_ID" value="${currentCatalogId}"/>
      <#if searchCategory?has_content>
        <input type="hidden" name="SEARCH_CATEGORY_ID" value="${searchCategoryId?if_exists}"/>
        <label>${uiLabelMap.ProductCategory}</label>
        <p>${(searchCategory.description)?if_exists}</p>
        <fieldset class="form-group">
          <div class="row">
            <label class="col-form-label col-sm-3 pt-0">${uiLabelMap.ProductIncludeSubCategories}</label>
            <div class="col-sm-9">
              <div class="form-check">
                <input type="radio" name="SEARCH_SUB_CATEGORIES" class="form-check-input" id="SEARCH_SUB_CATEGORIES_YES" value="Y"
                    checked="checked"/>
                <label class="form-check-label" for="SEARCH_SUB_CATEGORIES_YES">${uiLabelMap.CommonYes}</label>
              </div>
              <div class="form-check">
                <input type="radio" name="SEARCH_SUB_CATEGORIES" class="form-check-input" id="SEARCH_SUB_CATEGORIES_NO" value="N"/>
                <label class="form-check-label" for="SEARCH_SUB_CATEGORIES_NO">${uiLabelMap.CommonNo}</label>
              </div>
          </div>
        </fieldset>
      </#if>
      <fieldset class="form-group">
        <div class="row">
          <label class="col-form-label col-sm-3 pt-0">${uiLabelMap.ProductKeywords}</label>
          <div class="col-sm-6">
          <input type="text" class="form-control form-control-sm" name="SEARCH_STRING" id="SEARCH_STRING" size="20"
              value="${requestParameters.SEARCH_STRING?if_exists}"/>
          <div class="form-check">
            <input type="radio" class="form-check-input" name="SEARCH_OPERATOR" id="SEARCH_OPERATOR_ANY"
                value="OR" <#if "OR" == searchOperator>checked="checked"</#if>/>
            <label class="form-check-label" for="SEARCH_OPERATOR_ANY">${uiLabelMap.CommonAny}</label>
          <div>
          <div class="form-check">
            <input type="radio" class="form-check-input" name="SEARCH_OPERATOR" id="SEARCH_OPERATOR_ALL"
                value="AND" <#if "AND" == searchOperator>checked="checked"</#if>/>
            <label class="form-check-label" for="SEARCH_OPERATOR_ALL">${uiLabelMap.CommonAll}</label>
          </div>
        </div>
      </fieldset>
      <#list productFeatureTypeIdsOrdered as productFeatureTypeId>
        <#assign findPftMap =
            Static["org.apache.ofbiz.base.util.UtilMisc"].toMap("productFeatureTypeId", productFeatureTypeId)>
        <#assign productFeatureType = delegator.findOne("ProductFeatureType", findPftMap, true)>
        <#assign productFeatures = productFeaturesByTypeMap[productFeatureTypeId]>
        <div class="form-group row">
          <label for="${productFeatureTypeId}" class="col-sm-3 col-form-label">${(productFeatureType.get("description",locale))?if_exists}</label>
          <div class="col-sm-6">
            <select name="pft_${productFeatureTypeId}" id="pft_${productFeatureTypeId}" class="form-control form-control-sm">
              <option value="">- ${uiLabelMap.CommonSelectAny} -</option>
              <#list productFeatures as productFeature>
                <option value="${productFeature.productFeatureId}">
                  ${productFeature.description?default(productFeature.productFeatureId)}
                </option>
              </#list>
            </select>
          </div>
        </div>
      </#list>
      <fieldset class="form-group">
        <div class="row">
          <label for="sortOrder" class="col-sm-3 col-form-label pt-0">${uiLabelMap.ProductSortedBy}</label>
          <div class="col-sm-6">
            <select name="sortOrder" id="sortOrder" class="form-control form-control-sm">
              <option value="SortKeywordRelevancy">${uiLabelMap.ProductKeywordRelevancy}</option>
              <option value="SortProductField:productName">${uiLabelMap.ProductProductName}</option>
              <option value="SortProductField:totalQuantityOrdered">${uiLabelMap.ProductPopularityByOrders}</option>
              <option value="SortProductField:totalTimesViewed">${uiLabelMap.ProductPopularityByViews}</option>
              <option value="SortProductField:averageCustomerRating">${uiLabelMap.ProductCustomerRating}</option>
              <option value="SortProductPrice:LIST_PRICE">${uiLabelMap.ProductListPrice}</option>
              <option value="SortProductPrice:DEFAULT_PRICE">${uiLabelMap.ProductDefaultPrice}</option>
              <#if productFeatureTypes?? && productFeatureTypes?has_content>
                <#list productFeatureTypes as productFeatureType>
                  <option value="SortProductFeature:${productFeatureType.productFeatureTypeId}">
                    ${productFeatureType.description?default(productFeatureType.productFeatureTypeId)}
                  </option>
                </#list>
              </#if>
            </select>
            <div class="form-check">
              <input type="radio" class="form-check-input" name="sortAscending" id="sortAscendingHigh" value="Y" checked="checked"/>
              <label class="form-check-label" for="sortAscendingHigh">${uiLabelMap.EcommerceLowToHigh}</label>
            </div>
            <div class="form-check">
              <input class="form-check-input" type="radio" name="sortAscending" id="sortAscendingLow" value="N"/>
              <label class="form-check-label" for="sortAscendingLow">${uiLabelMap.EcommerceHighToLow}</label>
            </div>
          </div>
        </div>
      </fieldset>
      <#if searchConstraintStrings?has_content>
        <div>
          <div class="row">
            <label class="col-sm-3">${uiLabelMap.ProductLastSearch}</label>
            <div class="col-sm-7">
              <#list searchConstraintStrings as searchConstraintString>
                <p>${searchConstraintString}</p>
              </#list>
              <p>${uiLabelMap.ProductSortedBy}: ${searchSortOrderString}</p>
            </div>
          </div>
          <div class="form-group row">
            <div class="col-sm-3"></div>
            <div class="col-sm-7">
              <div class="form-check">
                <input class="form-check-input" type="radio" name="clearSearch" id="clearSearchNew" value="Y" checked="checked"/>
                <label class="form-check-label" for="clearSearchNew">${uiLabelMap.ProductNewSearch}</label>
              </div>
              <div class="form-check">
                <input class="form-check-input" type="radio" name="clearSearch" id="clearSearchRefine" value="N"/>
                <label class="form-check-label" for="clearSearchRefine">${uiLabelMap.ProductRefineSearch}</label>
              </div>
            </div>
          </div>
        </div>
      </#if>
        <div>
          <input type="submit" name="submit" class="btn btn-outline-secondary" value="${uiLabelMap.CommonFind}"/>
        </div>

      <#if searchOptionsHistoryList?has_content>
      <div class="card m-3">
        <div class="card-header">
          <strong>${uiLabelMap.OrderLastSearches}...</strong>
        </div>
        <div class="card-body">
          <div>
            <a href="<@ofbizUrl>clearSearchOptionsHistoryList</@ofbizUrl>" class="button">
              ${uiLabelMap.OrderClearSearchHistory}
            </a>
            <h4>${uiLabelMap.OrderClearSearchHistoryNote}</h4>
          </div>
          <#list searchOptionsHistoryList as searchOptions>
          <#-- searchOptions type is ProductSearchSession.ProductSearchOptions -->
            <div>
              <p>${uiLabelMap.EcommerceSearchNumber}${searchOptions_index + 1}</p>
              <a href="<@ofbizUrl>setCurrentSearchFromHistoryAndSearch?searchHistoryIndex=${searchOptions_index}&amp;clearSearch=N</@ofbizUrl>"
                 class="button">${uiLabelMap.CommonSearch}</a>
              <a href="<@ofbizUrl>setCurrentSearchFromHistory?searchHistoryIndex=${searchOptions_index}</@ofbizUrl>"
                 class="button">${uiLabelMap.CommonRefine}</a>
            </div>
            <#assign constraintStrings = searchOptions.searchGetConstraintStrings(false, delegator, locale)>
            <#list constraintStrings as constraintString>
              <p> - ${constraintString}</p>
            </#list>
            <#if searchOptions_has_next>
            </#if>
          </#list>
        </div>
        </div>
      </#if>
      </fieldset>
    </form>
  </div>
</div>
