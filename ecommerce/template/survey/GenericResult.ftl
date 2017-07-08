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

<#assign uiLabelMap = Static["org.apache.ofbiz.base.util.UtilProperties"].getResourceBundleMap("CommonUiLabels", locale)>

<h1>${survey.description!}</h1>
<br />

<table width="100%" border="0" cellpadding="2" cellspacing="0">
  <#list surveyQuestionAndAppls as surveyQuestionAndAppl>

    <#-- special formatting for select boxes -->
    <#assign align = "left">
    <#if ("BOOLEAN" == surveyQuestionAndAppl.surveyQuestionTypeId || "OPTION" == surveyQuestionAndAppl.surveyQuestionTypeId)>
      <#assign align = "right">
    </#if>

    <#-- get an answer from the answerMap -->
    <#if surveyAnswers?has_content>
      <#assign answer = surveyAnswers.get(surveyQuestionAndAppl.surveyQuestionId)!>
    </#if>

    <#-- get the question results -->
    <#if surveyResults?has_content>
      <#assign results = surveyResults.get(surveyQuestionAndAppl.surveyQuestionId)!>
    </#if>

    <tr>

      <#-- seperator options -->
      <#if "SEPERATOR_TEXT" == surveyQuestionAndAppl.surveyQuestionTypeId>
        <td colspan="5"><div>${surveyQuestionAndAppl.question!}</div></td>
      <#elseif "SEPERATOR_LINE" == surveyQuestionAndAppl.surveyQuestionTypeId>
        <td colspan="5"><hr /></td>
      <#else>

        <#-- standard question options -->
        <td align='right' nowrap="nowrap">
          <#assign answerString = "answers">
          <#if (results._total?default(0) == 1)>
             <#assign answerString = "answer">
          </#if>
          <div>${surveyQuestionAndAppl.question!} (${results._total?default(0)?string.number} ${answerString})</div>
          <#if surveyQuestionAndAppl.hint?has_content>
            <div>${surveyQuestionAndAppl.hint}</div>
          </#if>
        </td>
        <td width='1'>&nbsp;</td>

        <td align="${align}">
          <#if "BOOLEAN" == surveyQuestionAndAppl.surveyQuestionTypeId>
            <#assign selectedOption = (answer.booleanResponse)?default("Y")>
            <div><span style="white-space: nowrap;">
              <#if "Y" == selectedOption><b>==>&nbsp;<font color="red"></#if>${uiLabelMap.CommonY}<#if "Y" == selectedOption></font></b></#if>&nbsp;[${results._yes_total?default(0)?string("#")} / ${results._yes_percent?default(0)?string("#")}%]
            </span></div>
            <div><span style="white-space: nowrap;">
              <#if "N" == selectedOption><b>==>&nbsp;<font color="red"></#if>N<#if "N" == selectedOption></font></b></#if>&nbsp;[${results._no_total?default(0)?string("#")} / ${results._no_percent?default(0)?string("#")}%]
            </span></div>
          <#elseif "TEXTAREA" == surveyQuestionAndAppl.surveyQuestionTypeId>
            <div>${(answer.textResponse)!}</div>
          <#elseif "TEXT_SHORT" == surveyQuestionAndAppl.surveyQuestionTypeId>
            <div>${(answer.textResponse)!}</div>
          <#elseif "TEXT_LONG" == surveyQuestionAndAppl.surveyQuestionTypeId>
            <div>${(answer.textResponse)!}</div>
          <#elseif "EMAIL" == surveyQuestionAndAppl.surveyQuestionTypeId>
            <div>${(answer.textResponse)!}</div>
          <#elseif "URL" == surveyQuestionAndAppl.surveyQuestionTypeId>
            <div>${(answer.textResponse)!}</div>
          <#elseif "DATE" == surveyQuestionAndAppl.surveyQuestionTypeId>
            <div>${(answer.textResponse)!}</div>
          <#elseif "CREDIT_CARD" == surveyQuestionAndAppl.surveyQuestionTypeId>
            <div>${(answer.textResponse)!}</div>
          <#elseif "GIFT_CARD" == surveyQuestionAndAppl.surveyQuestionTypeId>
            <div>${(answer.textResponse)!}</div>
          <#elseif "NUMBER_CURRENCY" == surveyQuestionAndAppl.surveyQuestionTypeId>
            <div>${answer.currencyResponse?number?default(0)}</div>
          <#elseif "NUMBER_FLOAT" == surveyQuestionAndAppl.surveyQuestionTypeId>
            <div>${answer.floatResponse?number?default(0)?string("#")}</div>
          <#elseif "NUMBER_LONG" == surveyQuestionAndAppl.surveyQuestionTypeId>
            <div>${answer.numericResponse?number?default(0)?string("#")}&nbsp;[${uiLabelMap.CommonTally}: ${results._tally?default(0)?string("#")} / ${uiLabelMap.CommonAverage}: ${results._average?default(0)?string("#")}]</div>
          <#elseif "PASSWORD" == surveyQuestionAndAppl.surveyQuestionTypeId>
            <div>[${uiLabelMap.CommonNotShown}]</div>
          <#elseif "CONTENT" == surveyQuestionAndAppl.surveyQuestionTypeId>
            <#if answer.contentId?has_content>
              <#assign content = answer.getRelatedOne("Content", false)>
              <a href="/content/control/img?imgId=${content.dataResourceId}" class="buttontext">${answer.contentId}</a>&nbsp;-&nbsp;${content.contentName!}
            </#if>
          <#elseif "OPTION" == surveyQuestionAndAppl.surveyQuestionTypeId>
            <#assign options = surveyQuestionAndAppl.getRelated("SurveyQuestionOption", null, sequenceSort, false)!>
            <#assign selectedOption = (answer.surveyOptionSeqId)?default("_NA_")>
            <#if options?has_content>
              <#list options as option>
                <#assign optionResults = results.get(option.surveyOptionSeqId)!>
                  <div><span style="white-space: nowrap;">
                    <#if option.surveyOptionSeqId == selectedOption><b>==>&nbsp;<font color="red"></#if>
                    ${option.description!}
                    <#if option.surveyOptionSeqId == selectedOption></font></b></#if>
                    &nbsp;[${optionResults._total?default(0)?string("#")} / ${optionResults._percent?default(0?string("#"))}%]
                  </span></div>
              </#list>
            </#if>
          <#else>
            <div>${uiLabelMap.EcommerceUnsupportedQuestionType}: ${surveyQuestionAndAppl.surveyQuestionTypeId}</div>
          </#if>
        </td>
        <td width="90%">&nbsp;</td>
      </#if>
    </tr>
  </#list>
</table>
