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
      <strong>${uiLabelMap.EcommerceRequestHistory}</strong>
    </div>
    <div class="card-body">
        <table class="table table-responsive-sm">
            <thead class="thead-light">
            <tr>
                <th>
                   ${uiLabelMap.OrderRequest} ${uiLabelMap.CommonNbr}
                </th>
                <th>
                   ${uiLabelMap.CommonType}
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
                  ${uiLabelMap.OrderRequestDate}
                </th>
                <th>
                  ${uiLabelMap.OrderRequestCreatedDate}
                </th>
                <th>
                  ${uiLabelMap.OrderRequestLastModifiedDate}
                </th>
                <th colspan="2"></th>
            </tr>
            </thead>
            <#list requestList as custRequest>
                <#assign status = custRequest.getRelatedOne("StatusItem", true)>
                <#assign type = custRequest.getRelatedOne("CustRequestType", true)>
                <tbody>
                    <tr>
                        <td>
                            ${custRequest.custRequestId?default("N/A")}
                        </td>
                        <td>
                            ${type.get("description",locale)?default("N/A")}
                        </td>
                        <td>
                            ${custRequest.custRequestName?default("N/A")}
                        </td>
                        <td>
                            ${custRequest.description?default("N/A")}
                        </td>
                        <td>
                            ${status.get("description",locale)?default("N/A")}
                        </td>
                        <td>
                            ${custRequest.custRequestDate?default("N/A")}
                        </td>
                        <td>${custRequest.createdDate?default("N/A")}</td>
                        <td>${custRequest.lastModifiedDate?default("N/A")}</td>
                        <td colspan="2">
                            <a href="<@ofbizUrl>/ViewRequest?custRequestId=${custRequest.custRequestId}</@ofbizUrl>" class="buttontext">${uiLabelMap.CommonView}</a>
                        </td>
                    </tr>
                </tbody>
            </#list>
            <#if !requestList?has_content>
                <div class="alert alert-light" role="alert">
                    ${uiLabelMap.OrderNoRequestFound}
                </div>
            </#if>
        </table>
    </div>
</div>
