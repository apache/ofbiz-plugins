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
| ebay and ebaystore | [#339](https://github.com/apache/ofbiz-plugins/pull/339) |
| webpos | [#304](https://github.com/apache/ofbiz-plugins/pull/304) |
| rest-api, which moved to `framework/rest-api` in ofbiz-framework | commit c9eb46469 |

The tables of the removed entities are not dropped; drop them once you no longer need their data.

### All plugins

* The plugins move from Java EE (`javax.*`) to Jakarta EE (`jakarta.*`) with the framework (commit 5590a52e0,
  [OFBIZ-12989](https://issues.apache.org/jira/browse/OFBIZ-12989)). See the framework notes.

### cmssite

* `MultiSiteRequestWrapper` no longer overrides `isRequestedSessionIdFromUrl` and `getRealPath`, which Jakarta EE
  removed (commit 5590a52e0).

### ecommerce

* The PayPal and WorldPay requests (`callPayPal`, `callWorldPay`, `payPal*`, `setPayPalCheckout`, `worldPayNotify`)
  are removed with the payment gateways ([#295](https://github.com/apache/ofbiz-plugins/pull/295)).
* The controller no longer declares the `http` view handler
  ([#426](https://github.com/apache/ofbiz-plugins/pull/426)).

### example and exampleext

* The `ExampleReportXls` and `ListExampleExport` requests are removed with the `screenxls` view handler
  ([#400](https://github.com/apache/ofbiz-plugins/pull/400)).
* The websocket push notification example (`ExampleServices`, `ExampleWebSockets`) is removed
  ([#466](https://github.com/apache/ofbiz-plugins/pull/466)).
* The exampleext `CommonBirtDecorator` is removed with the birt plugin
  ([#482](https://github.com/apache/ofbiz-plugins/pull/482)).

### lucene

* `SearchWorker.getLuceneVersion` is removed (commit 5590a52e0).

### pricat

* `SampleHtmlThread` and `PricatParseExcelHtmlThread` are final, and the `AbstractReport` constructor is protected
  ([#487](https://github.com/apache/ofbiz-plugins/pull/487)).

### scrum

* The `ProductStatistics` request, screen and form are removed with the birt plugin
  ([#482](https://github.com/apache/ofbiz-plugins/pull/482)).
