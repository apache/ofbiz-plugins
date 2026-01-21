<#-- <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd"> -->
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

<div style="position:relative" class="gantt" id="GanttChartDIV"></div>

<input id="ofbizGantItemsJson" type="hidden" value="${phaseTaskListJson}"/>

<#-- Commented out because qs.js has a transitive vulnerability due to request.js. See https://issues.apache.org/jira/browse/OFBIZ-13339 for details
<script type="text/javascript" src="/projectmgr/node_modules/jsgantt-improved/dist/jsgantt.js"></script>
<script type="text/javascript" src="/projectmgr/static/projectmgr.js"></script>
-->
This has for now been Commented out because qs.js has a transitive vulnerability due to request.js.
<br>
See <a href="https://issues.apache.org/jira/browse/OFBIZ-13339 for details">https://issues.apache.org/jira/browse/OFBIZ-13339 for details</a>
<br><br>
The latest possible version that can be installed is 6.5.3 because of the following conflicting dependencies:
<br>
jsgantt-improved@2.8.9 requires qs@~6.5.2 via a transitive dependency on request@2.88.2
<br>
No patched version available for qs
<br>
The earliest fixed version is 6.14.1.
<br><br>
For details see.
<br>
<a href="https://github.com/advisories/GHSA-6rw7-vpxm-498p">https://github.com/advisories/GHSA-6rw7-vpxm-498p</a>
<br>
<a href="https://github.com/apache/ofbiz-plugins/network/updates/1194761905">https://github.com/apache/ofbiz-plugins/network/updates/1194761905</a>
<br>
<a href="https://github.com/jsGanttImproved/jsgantt-improved/issues/384">https://github.com/jsGanttImproved/jsgantt-improved/issues/384</a>
<br>
<br>
If you feel it's ok with you (e.g. totally secured Internet access, or rather no access at all which is safer!) you may uncomment and use.
