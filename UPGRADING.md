<!--
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

# Upgrading the OFBiz plugins

This file lists what you need to change when you upgrade the OFBiz plugins to a new major release, plugin by plugin.
Start with the framework and applications notes, which also cover the environment:
[UPGRADING.md](https://github.com/apache/ofbiz-framework/blob/trunk/UPGRADING.md) and
[applications/UPGRADING.md](https://github.com/apache/ofbiz-framework/blob/trunk/applications/UPGRADING.md) in the
ofbiz-framework repository.

The complete list of pull requests that change or remove a public API is in the "API changes" section of the
[release notes](https://github.com/apache/ofbiz-plugins/releases), from the `api-change` label.

When a pull request changes or removes a public API of a plugin, add its entry here in the same pull request.

## Upgrading from 24.09 to the next major release (trunk)

### Removed plugins

These plugins were removed with their entities, services, seed data, screens and requests. A project that still uses
one has to keep its own copy, taken from the release24.09 branch.

| Plugin | Pull request |
|---|---|
| bi, with the OLAP entities (`*Dimension`, `*Fact`, `*StarSchema`) | [#484](https://github.com/apache/ofbiz-plugins/pull/484) |
| birt, which was already disabled in 24.09 | [#482](https://github.com/apache/ofbiz-plugins/pull/482) |
| cmssite, with its multi-site `WebSiteFilter` (see below) | [OFBIZ-13819](https://issues.apache.org/jira/browse/OFBIZ-13819) |
| ebay and ebaystore | [#339](https://github.com/apache/ofbiz-plugins/pull/339) |
| exampleext; the techniques it showed are in the developer manual under "Extending a component without modifying it" | [OFBIZ-13795](https://issues.apache.org/jira/browse/OFBIZ-13795) |
| ldap, with its CAS, LDAP and Active Directory login handlers; LDAP login stays available in the framework through `security.ldap.enable` | [OFBIZ-13823](https://issues.apache.org/jira/browse/OFBIZ-13823) |
| msggateway, with the `Msg91GatewayConfig` entity; the core telecom services stay, so replace any `TG_MSG91` telecom setting with your own gateway service | [OFBIZ-13797](https://issues.apache.org/jira/browse/OFBIZ-13797) |
| multiflex; switch any product store or user still on the `MULTIFLEX` visual theme to `EC_DEFAULT` first | [OFBIZ-13796](https://issues.apache.org/jira/browse/OFBIZ-13796) |
| passport, with the `ThirdPartyLogin`, `OAuth2GitHub`, `GitHubUser`, `OAuth2LinkedIn` and `LinkedInUser` entities | [OFBIZ-13814](https://issues.apache.org/jira/browse/OFBIZ-13814) |
| pricat, with the `ExcelImportHistory` entity | [OFBIZ-13799](https://issues.apache.org/jira/browse/OFBIZ-13799) |
| scrum, with its `/scrum` and `/scrumdemo` webapps | [OFBIZ-13818](https://issues.apache.org/jira/browse/OFBIZ-13818) |
| webpos | [#304](https://github.com/apache/ofbiz-plugins/pull/304) |
| rest-api, which moved to `framework/rest-api` in ofbiz-framework | commit c9eb46469 |

The tables of the removed entities are not dropped; drop them once you no longer need their data.

scrum defined no entities of its own, but it added columns to `CustRequest` (`custEstimatedMilliSeconds`,
`custSequenceNum`, `parentCustRequestId` with the `CUST_REQ_PARENT` foreign key, and `billed`) and to `TimeEntry`
(`planHours`). These columns and the scrum seed and demo rows stay in existing databases; drop the foreign key before
the columns once you no longer need the data.

The cmssite demo web sites (`CmsSite`, `OfbizSite`, `WebSite1` and `WebSite2`) and their content rows stay in databases
that loaded them; delete them once you no longer need them.

A webapp whose `web.xml` names cmssite's `org.apache.ofbiz.cmssite.multisite.WebSiteFilter` fails to start. Serving
several web sites from one webapp through `WebSite.hostedPathAlias` still works with a filter of your own; what it
has to do is under "Custom code" in applications/UPGRADING.md of ofbiz-framework. cmssite's `WebSiteFilter` and
`MultiSiteRequestWrapper` on the release24.09 branch can serve as a starting point.

### All plugins

* The plugins move from Java EE (`javax.*`) to Jakarta EE (`jakarta.*`) with the framework (commit 5590a52e0,
  [OFBIZ-12989](https://issues.apache.org/jira/browse/OFBIZ-12989)). See the framework notes.

### ecommerce

* The PayPal and WorldPay requests (`callPayPal`, `callWorldPay`, `payPal*`, `setPayPalCheckout`, `worldPayNotify`)
  are removed with the payment gateways ([#295](https://github.com/apache/ofbiz-plugins/pull/295)).
* The controller no longer declares the `http` view handler
  ([#426](https://github.com/apache/ofbiz-plugins/pull/426)).

### example

* The `ExampleReportXls` and `ListExampleExport` requests are removed with the `screenxls` view handler
  ([#400](https://github.com/apache/ofbiz-plugins/pull/400)).
* The websocket push notification example (`ExampleServices`, `ExampleWebSockets`) is removed
  ([#466](https://github.com/apache/ofbiz-plugins/pull/466)).

### lucene

* `SearchWorker.getLuceneVersion` is removed (commit 5590a52e0).
* Lucene moves from 9 to 10 ([OFBIZ-13800](https://issues.apache.org/jira/browse/OFBIZ-13800)). Lucene 10 reads
  the existing 9.x indexes. Custom code calling `IndexSearcher.doc` or `TopScoreDocCollector.create` must switch to
  `IndexSearcher.storedFields()` and `TopScoreDocCollectorManager`, and `TotalHits.value` is now the `value()` method.

### projectmgr

* The `mytimesheet` and `mytasks` portlets are checked with the framework's `employeePermissionCheck` service instead
  of `portalPermissionIsEmployee`, and the `ListCustomerProjects` portlet and screen are removed
  ([OFBIZ-13825](https://issues.apache.org/jira/browse/OFBIZ-13825)).
