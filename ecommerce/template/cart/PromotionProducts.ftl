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

<#if productIds?has_content>
<div class="card">
    <div class="card-header">
        ${uiLabelMap.OrderProductsForPromotion}
    </div>
    <div class="card-body">
        <table class="table table-responsive-sm">
          <thead>
          <tr>
            <th>${uiLabelMap.CommonQualifier}</th>
            <th>${uiLabelMap.CommonBenefit}</th>
            <th class="text-right">
              <#if (listSize > 0)>
                <#if (viewIndex > 0)>
                <a href="<@ofbizUrl>showPromotionDetails?productPromoId=${productPromoId!}&amp;VIEW_SIZE=${viewSize}&amp;VIEW_INDEX=${viewIndex-1}</@ofbizUrl>" class="buttontext">${uiLabelMap.CommonPrevious}</a> |
                </#if>
                ${lowIndex+1} - ${highIndex} ${uiLabelMap.CommonOf} ${listSize}
                <#if (listSize > highIndex)>
                | <a href="<@ofbizUrl>showPromotionDetails?productPromoId=${productPromoId!}&amp;VIEW_SIZE=${viewSize}&amp;VIEW_INDEX=${viewIndex+1}</@ofbizUrl>" class="buttontext">${uiLabelMap.CommonNext}</a>
                </#if>
                </b>
              </#if>
            </th>
          </tr>
          </thead>
          <tbody>
        <#if (listSize > 0)>
          <#list productIds[lowIndex..highIndex-1] as productId>
              <tr>
                <td>[<#if productIdsCond.contains(productId)>x<#else>&nbsp;</#if>]</td>
                <td>[<#if productIdsAction.contains(productId)>x<#else>&nbsp;</#if>]</td>
                <td>
                  ${setRequestAttribute("optProductId", productId)}
                  ${setRequestAttribute("listIndex", productId_index)}
                  ${screens.render(productsummaryScreen)}
                </td>
              </tr>
          </#list>
        </#if>
        </tbody>
        </table>
    </div>
</div>
</#if>