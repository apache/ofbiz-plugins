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
<#-- Thread content id equals contentId if contentAssocTypeId equals PUBLISH_LINK, else threadContentId = ownerContentId -->
<#assign threadContentId = forumMessage.contentId/>
<#if "RESPONSE" == forumMessage.caContentAssocTypeId>
  <#assign threadContentId = forumMessage.ownerContentId!/>
</#if>
<#if rsp??>
  <#assign contentId = rsp.contentId>
<#else>
  <#assign contentId = forumMessage.contentId>
</#if>
<p>
  ${uiLabelMap.CommonTitle} : ${forumMessage.contentName!} ${uiLabelMap.CommonBy}:${forumMessage.createdByUserLogin!}
  ${uiLabelMap.CommonAt}: ${(forumMessage.createdDate.toString().substring(0,16))!}
  <a href="ViewForumMessage?forumId=${parameters.forumId}&amp;responseContentId=${forumMessage.contentId}&amp;threadContentId=${threadContentId!}"
      class="buttontext">${uiLabelMap.CommonView}</a>
</p>
<p>
  ${forumMessage.description!}
</p>