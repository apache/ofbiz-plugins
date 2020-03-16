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
<div id="FieldsOverview">
<form name="FieldsOverview" action="<@ofbizUrl>report</@ofbizUrl>" target="_BLANK">
	<input type="hidden" name="basisUrl" value="<@ofbizUrl>report?field=</@ofbizUrl>"/>
	<input type="hidden" name="selectedFields" value=""/>
	<input type="hidden" name="fieldCount" value=""/>
	<input type="checkbox" class="check" name="fieldId" value="" style="display:none;"/>
	<input type="hidden" name="selectionWarning" value="${uiLabelMap.SelectionWarning}"/>
    <input type="hidden" name="starSchemaName" value="${starSchemaName}"/>
    <table cellspacing="0" class="basic-table hover-bar">
        <tr class="header-row">
            <td>
                ${uiLabelMap.Select}
            </td>
            <td>
                ${uiLabelMap.CommonName}
            </td>
            <td>
                ${uiLabelMap.CommonDescription}
            </td>
        </tr>
        <#assign alt_row = false>
        <#list starSchemaFields as starSchemaField>
        <tr valign="middle"<#if alt_row> class="alternate-row"</#if>>
            <td>
                <div> <input type="checkbox" class="check" name="fieldId" value="${starSchemaField.name?if_exists}" onClick="checkSelectedFields(document.FieldsOverview.fieldId)"/> </div>
            </td>
            <td>
                ${starSchemaField.name}
            </td>
            <td>
                ${starSchemaField.description?default("")}
            </td>
        </tr>
        <#-- toggle the row color -->
        <#assign alt_row = !alt_row>
        </#list>
        <tr>
            <td colspan="3">
            	<hr/>
            	<a href="javascript:generateReport();" class="buttontext">${uiLabelMap.Report}</a>
            </td>
        </tr>
    </table>
</form>
</div>