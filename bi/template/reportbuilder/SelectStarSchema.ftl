<div id="SchemaOverview">
<form name="selectParameters" action="" method="post">
  <input type="checkbox" class="check" id="schemaId" name="schemaId" value="" style="display:none;"/>
  <input type="hidden" name="basisUrl" value="<@ofbizUrl>getAjaxFieldsList?schemaSelected=</@ofbizUrl>"/>
  <input type="hidden" name="selectedSchema" value=""/>
  <table width="100%" space-after="0.3in" cellspacing="0" class="basic-table hover-bar">
    <tr class="header-row">
        <td></td>
        <td><b>${uiLabelMap.Schema}<b></td>
    </tr>
    <#assign alt_row = false>
    <#if starSchemas?has_content>
        <#list starSchemas as schema>
            <tr <#if alt_row> class="alternate-row"</#if>>
                <td><div><input type="radio" class="check" id="schemaId" name="schemaId" value="${schema.name?if_exists}" onClick="checkSelectedSchema(document.SchemaOverview.schemaId)"/> </div> </td>
                <td><div> ${schema.title?if_exists}</div></td>
                <#assign alt_row = !alt_row>
            </tr>
        </#list>
    </#if>
    <tr><td colspan="2"><hr/></td></tr>
    <tr><td colspan="2" align="left">
        <a href="javascript:schemaSelection();" class="buttontext">${uiLabelMap.GetFields}</a>
    </td></tr>
  </table>
</form>
</div>