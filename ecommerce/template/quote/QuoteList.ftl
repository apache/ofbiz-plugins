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

<div class="card m-3">
    <div class="card-header">
      <strong>${uiLabelMap.EcommerceQuoteHistory}</strong>
    </div>
    <div class="card-body">
    <#if quoteList?has_content>
        <table class="table table-responsive-sm">
            <thead class="thead-light">
                <tr>
                    <th>
                        ${uiLabelMap.OrderQuote} ${uiLabelMap.CommonNbr}
                    </th>
                    <th>
                        ${uiLabelMap.CommonName}
                    </th>
                    <th>
                        ${uiLabelMap.CommonDescription}
                    </th>
                    <th>
                        ${uiLabelMap.CommonStatus}
                    </th>
                    <th>
                        ${uiLabelMap.OrderOrderQuoteIssueDate}
                    </th>
                    <th>${uiLabelMap.CommonValidFromDate}</th>
                    <th>${uiLabelMap.CommonValidThruDate}</th>
                    <th colspan="2"></th>
                </tr>
            </thead>
            <tbody>
            <#list quoteList as quote>
                <#assign status = quote.getRelatedOne("StatusItem", true)>
                <tr>
                    <td>
                       ${quote.quoteId!}
                    </td>
                    <td>
                       ${quote.quoteName?default("N/A")}
                    </td>
                    <td>
                       ${quote.description?default("N/A")}
                    </td>
                    <td>
                       ${status.get("description",locale)?default("N/A")}
                    </td>
                    <td>
                       ${quote.issueDate?default("N/A")}
                    </td>
                    <td>${quote.validFromDate?default("N/A")}</td>
                    <td>${quote.validThruDate?default("N/A")}</td>
                    <td colspan="2">
                        <a href="<@ofbizUrl>ViewQuote?quoteId=${quote.quoteId}</@ofbizUrl>" class="buttontext">${uiLabelMap.CommonView}</a>
                    </td>
                </tr>
            </#list>
            </tbody>
        </table>
        <#else>
            <div class="alert alert-light" role="alert">
              <h3>${uiLabelMap.OrderNoQuoteFound}</h3>
            </div>
        </#if>
    </div>
</div>
