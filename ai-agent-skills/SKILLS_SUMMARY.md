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

# OFBiz Agent Skills: Summary of Coverage

This document provides a high-level summary of the specialized skills developed for the OFBiz Agent Toolkit. These skills provide the necessary guardrails, patterns, and procedures for an AI agent to interact with the Apache OFBiz framework effectively.

## 🏗️ Core Abstractions
| Skill | Description |
| :--- | :--- |
| **manage-entities** | Handle Data Model changes, including entity definitions, relations, and primary keys. |
| **manage-services** | Define and implement OFBiz services (Java, Groovy, Script) with proper attributes and parameters. |
| **manage-data** | Manage Seed, Demo, and Setup data using Entity Engine XML artifacts. |
| **manage-dynamic-view-entities**| Create advanced View Entities dynamically for optimized data retrieval without raw SQL. |

## 🎨 UI & Interaction
| Skill | Description |
| :--- | :--- |
| **manage-screens** | Build complex, reusable UI layouts using XML Screen Definitions and decorators. |
| **manage-forms** | Implement declarative data entry and display forms using the OFBiz Form Widget. |
| **manage-menus** | Manage application navigation and menu hierarchies. |
| **manage-templates** | Develop dynamic UI components using Apache FreeMarker (FTL) and macros. |
| **manage-ajax** | Implement seamless UI updates using OFBiz-standard AJAX patterns and event handling. |
| **manage-themes** | Handle visual styling, CSS integration, and theme-specific properties. |
| **manage-labels** | Manage internationalization (i18n) and UI text labels across multiple languages. |
| **manage-content** | Work with the OFBiz Content Management System (DataResource, Content, ElectronicText). |
| **manage-pdf-export** | Design and implement professional documents (Invoices, Labels) using Apache FOP (PDF). |

## ⚙️ Logic & Flows
| Skill | Description |
| :--- | :--- |
| **manage-groovy** | Write business logic using Groovy scripts, leveraging the `run-groovy` engine and DSL. |
| **manage-java** | Implement typed business logic and worker classes in the Java source layer. |
| **manage-java-patterns** | Adhere to OFBiz-specific Java design patterns (e.g., Worker classes, internal service calls). |
| **manage-java-events** | Implement OFBiz Events (web boundaries) and route HTTP requests in Java. |
| **manage-minilang** | Maintain and legacy MiniLang (XML) logic for standard service workflows. |
| **manage-eca** | Orchestrate Event Condition Actions (Service ECA and Entity ECA) for automated flows. |
| **manage-service-groups** | Define and manage aggregate service executions and engine-level groupings. |
| **manage-controller** | Configure web request mappings, security constraints, and view handlers in `controller.xml`. |
| **manage-async** | Manage asynchronous execution, scheduled jobs, and the JobSandbox in OFBiz. |
| **manage-quartz-jobs** | Schedule and manage background tasks, job polling, and temporal expressions. |

## 🔌 Integrations & Communication
| Skill | Description |
| :--- | :--- |
| **manage-api-integration** | Expose services via REST or SOAP and handle JSON/XML data mapping. |
| **manage-email-services** | Configure SMTP, manage email templates, and automate outgoing communications. |

## 🔐 Advanced Management
| Skill | Description |
| :--- | :--- |
| **manage-security-advanced** | Implement complex security permissions, ACLs, and service-level data filtering. |
| **manage-security-deployment** | Harden and secure an OFBiz deployment at the infrastructure limit. |
| **manage-localization-advanced** | Handle advanced i18n, time zones, and locale-specific formatting. |
| **manage-webapps** | Configure web application boundaries, session management, and filter chains. |
| **manage-cache-and-performance** | Optimize performance through Entity Cache, Service Cache, and SQL query tuning. |
| **manage-properties** | Centralize configuration settings in `.properties` files for environment management. |

## 🚀 Project Lifecycle
| Skill | Description |
| :--- | :--- |
| **create-component** | Scaffold new OFBiz plugins using standardized directory structures. |
| **manage-component** | Manage component-level dependencies and `ofbiz-component.xml` registrations. |
| **coding-standards** | General guidelines for clean code, commenting, and OFBiz contribution standards. |
| **precommit-readiness** | Write changes in a hook-ready shape for CodeNarc, Groovy/Java compilation, XML validation, and focused tests before committing. |

## 🧠 Strategic Thinking
| Skill | Description |
| :--- | :--- |
| **manage-strategies** | High-level decision-making guide: XML vs Java, Service vs Event, and Performance tradeoffs. |
