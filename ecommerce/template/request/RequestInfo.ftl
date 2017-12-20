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
        <strong>${uiLabelMap.OrderRequest}&nbsp;${custRequest.custRequestId}&nbsp;${uiLabelMap.CommonInformation}</strong>
    </div>
    <div class="card-body">
        <div layout="row">
          <div class="col-12">
            <strong>${uiLabelMap.CommonType} :</strong>
            ${(custRequestType.get("description",locale))?default(custRequest.custRequestTypeId!)}
          </div>
        </div>
        <hr/>
        <div layout="row">
          <div class="col-12">
            <strong>${uiLabelMap.CommonStatus} :</strong>
            ${(statusItem.get("description", locale))?default(custRequest.statusId!)}
          </div>
        </div>
        <hr/>
        <div layout="row">
          <div class="col-12">
            <strong>${uiLabelMap.PartyPartyId} :</strong>
            ${custRequest.fromPartyId?default("N/A")}
          </div>
        </div>
        <hr/>
        <div layout="row">
          <div class="col-12">
            <strong>${uiLabelMap.CommonName} :</strong>
            ${custRequest.custRequestName?default("N/A")}
          </div>
        </div>
        <hr/>
        <div layout="row">
          <div class="col-12">
            <strong>${uiLabelMap.CommonDescription} :</strong>
            ${custRequest.description?default("N/A")}
          </div>
        </div>
        <hr/>
        <div layout="row">
          <div class="col-12">
            <strong>${uiLabelMap.CommonCurrency} :</strong>
            <#if currency??>${currency.get("description", locale)?default(custRequest.maximumAmountUomId!)}</#if>
          </div>
        </div>
        <hr/>
        <div layout="row">
          <div class="col-12">
            <strong>${uiLabelMap.CommonInternalComment} :</strong>
            ${custRequest.internalComment?default("N/A")}
          </div>
        </div>
        <hr/>
        <div layout="row">
          <div class="col-12">
            <strong>${uiLabelMap.CommonReason} :</strong>
            ${custRequest.reason?default("N/A")}
          </div>
        </div>
    </div>
</div>