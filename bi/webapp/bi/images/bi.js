/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

function schemaSelection(){
    var schemas = document.getElementsByName('schemaId');
    if(!schemas){
        alert('Selected schema: ' + schema_value)
    }
    var schema_value;
    for(var i = 0; i < schemas.length; i++){
        if(schemas[i].checked){
            schema_value = schemas[i].value
        }
    }
    getAssocFieldList(schema_value)
}

function getAssocFieldList(schemaId){
    if(!schemaId){
        alert('No Schema Selected!')
    } else{
        var param='starSchemaName='+ schemaId
        jQuery.ajax({
            url: 'getAssociatedFieldList',
            data: param,
            type: 'get',
            async: false,
            success: function(data) {
                jQuery('#fieldSelector').html(data)
            },
            error: function(data) {
                alert("Error during schema selection")
            }
        })
    }
    
}

function checkSelectedFields(chk)
{
    document.FieldsOverview.selectedFields.value = ""
    var strValue = ""
    var fieldCount = 0
    var i = 0;
    for (i = 0; i < chk.length; i++)
        if(chk[i].checked == true){
            if (strValue == "") {
                strValue = chk[i].value
            }
            else {
                strValue = strValue +"&field=" + chk[i].value
            }
            fieldCount = fieldCount +1
        }
    document.FieldsOverview.selectedFields.value = strValue
    document.FieldsOverview.fieldCount.value = fieldCount
}

function generateReport() {
    var schemaQuery = '&starSchemaName='+ document.FieldsOverview.starSchemaName.value
    var queryString = document.FieldsOverview.selectedFields.value
    var fieldCount = document.FieldsOverview.fieldCount.value
    if (!document.FieldsOverview.selectedFields.value){
        alert("No Field(s) selected!")
    }
    else {
        var param= schemaQuery + '&field=' + queryString + '&fieldCount=' + fieldCount
        jQuery.ajax({
            url: 'reportStarSchema',
            data: param,
            type: 'get',
            async: false,
            success: function(data) {
                jQuery('#report').html(data)
            },
            error: function(data) {
                alert("Error during schema selection")
            }
        })
    }
}