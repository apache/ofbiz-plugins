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
    <div class="card-header">
        <strong>${uiLabelMap.OrderRequestItems}</strong>
    </div>
    <div class="card-body">
        <table class="table table-responsive-sm">
            <thead class="thead-light">
                <tr>
                    <th>${uiLabelMap.ProductItem}</th>
                    <th>${uiLabelMap.OrderProduct}</th>
                    <th>${uiLabelMap.ProductQuantity}</th>
                    <th>${uiLabelMap.OrderAmount}</th>
                    <th>${uiLabelMap.OrderRequestMaximumAmount}</th>
                    <th></th>
                </tr>
            </thead>
            <#assign alt_row = false>
            <tbody>
            <#list requestItems as requestItem>
                <#if requestItem.productId??>
                    <#assign product = requestItem.getRelatedOne("Product", false)>
                </#if>
                <tr>
                    <td>
                        <#if showRequestManagementLinks??>
                            <a href="<@ofbizUrl>EditRequestItem?custRequestId=${requestItem.custRequestId}&amp;custRequestItemSeqId=${requestItem.custRequestItemSeqId}</@ofbizUrl>" class="buttontext">${requestItem.custRequestItemSeqId}</a>
                        <#else>
                            ${requestItem.custRequestItemSeqId}
                        </#if>
                    </td>
                    <td>
                        <div>
                            ${(product.internalName)!}&nbsp;
                            <#if showRequestManagementLinks??>
                                <a href="/catalog/control/EditProduct?productId=${requestItem.productId!}" class="buttontext">${requestItem.productId!}</a>
                            <#else>
                                <a href="<@ofbizUrl>product?product_id=${requestItem.productId!}</@ofbizUrl>" class="buttontext">${requestItem.productId!}</a>
                            </#if>
                        </div>
                    </td>
                    <td>${requestItem.quantity?default("N/A")}</td>
                    <td>${requestItem.selectedAmount?default("N/A")}</td>
                    <td><@ofbizCurrency amount=requestItem.maximumAmount isoCode=request.maximumAmountUomId/></td>
                </tr>
            </#list>
            </tbody>
        </table>
    </div>
</div>