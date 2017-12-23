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
<#if person??>
  <h2>${uiLabelMap.PartyEditPersonalInformation}</h2>&nbsp;
  <form id="editpersonform1" class="mr-2" method="post" action="<@ofbizUrl>updatePerson</@ofbizUrl>" name="editpersonform">
<#else>
  <h2>${uiLabelMap.PartyAddNewPersonalInformation}</h2>&nbsp;
  <form id="editpersonform2" class="mr-2" method="post" action="<@ofbizUrl>createPerson/${donePage}</@ofbizUrl>"
      name="editpersonform">
</#if>
    <div class="form-group">
      <a href='<@ofbizUrl>${donePage}</@ofbizUrl>' class="btn btn-outline-secondary">${uiLabelMap.CommonGoBack}</a>
      <a href="javascript:document.editpersonform.submit()" class="btn btn-outline-secondary">${uiLabelMap.CommonSave}</a>
    </div>
      <input type="hidden" name="partyId" value="${person.partyId!}"/>
        <div class="row">
          <div class="col-sm-6">
            <label>${uiLabelMap.CommonTitle}</label>
            <select name="personalTitle" class="form-control custom-select">
            <#if personData.personalTitle?has_content >
              <option>${personData.personalTitle}</option>
              <option value="${personData.personalTitle}"> --</option>
            <#else>
              <option value="">${uiLabelMap.CommonSelectOne}</option>
            </#if>
              <option>${uiLabelMap.CommonTitleMr}</option>
              <option>${uiLabelMap.CommonTitleMrs}</option>
              <option>${uiLabelMap.CommonTitleMs}</option>
              <option>${uiLabelMap.CommonTitleDr}</option>
            </select>
          </div>
          <div class="col-sm-6">
            <label>${uiLabelMap.PartyHeight}</label>
            <input type="text" class="form-control" name="height" value="${personData.height!}"/>
          </div>
        </div>
        <div class="row">
          <div class="col-sm-6">
          <label>${uiLabelMap.PartyFirstName}</label>
            <input type="text" class="form-control" name="firstName"
                value="${personData.firstName!}"/>
          </div>
          <div class="col-sm-6">
            <label>${uiLabelMap.PartyWeight}</label>
            <input type="text" class="form-control" name="weight" value="${personData.weight!}"/>
          </div>
        </div>
        <div class="row">
          <div class="col-sm-6">
            <label>${uiLabelMap.PartyMiddleInitial}</label>
            <input type="text" class="form-control" name="middleName"
                value="${personData.middleName!}"/>
          </div>
          <div class="col-sm-6">
            <label>${uiLabelMap.PartyMaidenName}</label>
            <input type="text" class="form-control" name="mothersMaidenName"
                   value="${personData.mothersMaidenName!}"/>
          </div>
        </div>
        <div class="row">
          <div class="col-sm-6">
            <label>${uiLabelMap.PartyLastName}</label>
            <input type="text" class='form-control' name="lastName" value="${personData.lastName!}"/>
          </div>
          <div class="col-sm-6">
            <label>${uiLabelMap.PartyMaritalStatus}</label>
            <select name="maritalStatus" class="form-control custom-select">
            <#if personData.maritalStatus?has_content>
              <option value="${personData.maritalStatus}">
                <#if "S" == personData.maritalStatus>${uiLabelMap.PartySingle}</#if>
                 <#if "M" == personData.maritalStatus>${uiLabelMap.PartyMarried}</#if>
                 <#if "D" == personData.maritalStatus>${uiLabelMap.PartyDivorced}</#if>
              </option>
              <option value="${personData.maritalStatus}"> --</option>
            <#else>
              <option></option>
            </#if>
              <option value="S">${uiLabelMap.PartySingle}</option>
              <option value="M">${uiLabelMap.PartyMarried}</option>
              <option value="D">${uiLabelMap.PartyDivorced}</option>
            </select>
          </div>
        </div>
        <div class="row">
          <div class="col-sm-6">
          <label>${uiLabelMap.PartySuffix}</label>
          <input type="text" class="form-control" name="suffix" value="${personData.suffix!}"/>
          </div>
          <div class="col-sm-6">
            <label>${uiLabelMap.PartyPassportNumber}</label>
            <input type="text" class="form-control" name="passportNumber"
                   value="${personData.passportNumber!}"/>
          </div>
        </div>
        <div class="row">
          <div class="col-sm-6">
            <label>${uiLabelMap.PartyNickName}</label>
            <input type="text" class='form-control' name="nickname" value="${personData.nickname!}"/>
          </div>
          <div class="col-sm-6">
            <label>${uiLabelMap.PartyPassportExpireDate}</label>
            <input type="text" class="form-control" name="passportExpireDate"
                   value="${personData.passportExpireDate!}"/>
            <div>${uiLabelMap.CommonFormatDate}</div>
          </div>
        </div>
        <div class="row">
          <div class="col-sm-6">
            <label>${uiLabelMap.PartyGender}</label>
            <select name="gender" class="form-control custom-select">
            <#if personData.gender?has_content >
              <option value="${personData.gender}">
                <#if "M" == personData.gender >${uiLabelMap.CommonMale}</#if>
                  <#if "F" == personData.gender >${uiLabelMap.CommonFemale}</#if>
              </option>
              <option value="${personData.gender}"> --</option>
            <#else>
              <option value="">${uiLabelMap.CommonSelectOne}</option>
            </#if>
              <option value="M">${uiLabelMap.CommonMale}</option>
              <option value="F">${uiLabelMap.CommonFemale}</option>
            </select>
          </div>
          <div class="col-sm-6">
            <label>${uiLabelMap.PartyTotalYearsWorkExperience}</label>
            <input type="text" class="form-control" name="totalYearsWorkExperience"
                   value="${personData.totalYearsWorkExperience!}"/>
          </div>
        </div>
        <div class="row">
          <div class="col-sm-6">
            <label>${uiLabelMap.PartyBirthDate}</label>
            <input type="text" class="form-control" name="birthDate"
                value="${(personData.birthDate.toString())!}"/>
            <div>${uiLabelMap.CommonFormatDate}</div>
          </div>
          <div class="col-sm-6">
            <label>${uiLabelMap.CommonComment}</label>
            <input type="text" class="form-control" name="comments" value="${personData.comments!}"/>
          </div>
        </div>
  </form>
  <div class="form-group">
    <a href='<@ofbizUrl>${donePage}</@ofbizUrl>' class="btn btn-outline-secondary">${uiLabelMap.CommonGoBack}</a>
    <a id="editpersonform3" href="javascript:document.editpersonform.submit()" class="btn btn-outline-secondary">${uiLabelMap.CommonSave}</a>
  </div>