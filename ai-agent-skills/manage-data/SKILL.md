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

---
name: manage-data
description: Create and manage OFBiz data files (Entity Engine XML).
---

# Skill: manage-data
## Goal
Create and manage OFBiz data files (Entity Engine XML) to populate the database with valid seed, demo, or configuration data. This includes adding mandatory setup records, common type definitions (Seed/Seed-Initial), and optional transactional records for development environments (Demo). 

> [!TIP]
> Use this skill in conjunction with [manage-entities](../manage-entities/SKILL.md) and [manage-component](../manage-component/SKILL.md).

## Triggers
**ALWAYS** read this skill when:
- Creating or updating XML files intended for data loading.
- Troubleshooting data-related startup errors or foreign key violations.
- Configuring entity data resources in `ofbiz-component.xml`.

## Rules & Procedures

### 1. File Structure & Validation
- **Encoding**: ALWAYS use `UTF-8` encoding.
- **Granularity**: Prefer small, logically focused data files (e.g., `ProductPriceData.xml`) over monolithic files for better maintainability.
- **Header**: ALWAYS include the correct XSI schema for IDE validation and framework parsing.
```xml
<?xml version="1.0" encoding="UTF-8"?>
<entity-engine-xml xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:noNamespaceSchemaLocation="http://ofbiz.apache.org/dtds/entity-engine-xml.xsd">
    <!-- Entries -->
</entity-engine-xml>
```

### 2. Data Readers & Loading Order
Data files **must** be registered in `ofbiz-component.xml` using `<entity-resource type="data" .../>`.
| Reader | Technical Purpose | Content Type |
| :--- | :--- | :--- |
| `seed` | Mandatory configuration | Enums, StatusItems, UOMs, System Settings. |
| `seed-initial` | Loading during first install | Baseline setup, administrative users. |
| `ext` | External/Custom seed | Plugin-specific configuration data. |
| `demo` | Development/Testing | Orders, Invoices, Shipments, Sample Products. |

**Loading Order**: Ensure foreign keys are satisfied. If `Product` refers to `ProductType`, the `ProductType` data must be loaded first (or be in the same file earlier). Standard reader sequence: `seed` -> `seed-initial` -> `ext` -> `demo`.

### 3. Record Definition Patterns
- **Upsert Behavior**: Data loading is an "upsert" (create-or-store). Re-loading a file updates existing records based on Primary Keys.
- **Null Handling**: Omitted attributes are treated as `null`. Attributes with empty quotes (e.g., `attr=""`) are treated as **Empty Strings**.
- **Entity Grouping**: Group related entities using XML comments as headers (e.g., `<!-- Products -->`) for readability in large files.

## Guardrails
- **Security**: **NEVER** include sensitive data (PII, actual passwords, PCI data) in seed or demo files. Use standard "ofbiz" hashes for demo users.
- **Entity Verification**: **NEVER** invent entity names. Always verify the entity exists in `entitymodel.xml` before creating data.
- **Unique Primary Keys**: Ensure all records have unique primary keys. For Seed/Ext data, use descriptive string IDs if possible.
- **Ordering**: Define parent entities BEFORE child entities to avoid foreign key violations. Referenced records (e.g., `statusId`) must exist in the same file or earlier seed files.
- **Composite Primary Keys**: Always populate **ALL** primary key fields defined in the model (e.g., `ProductPrice` requires `productId`, `productPriceTypeId`, etc.).
- **Temporal Patterns**: For entities using `fromDate`/`thruDate`, ensure `fromDate` is populated (often part of PK) and date ranges do not overlap for the same logical record.
- **Dates**: Use ISO timestamp: `YYYY-MM-DD HH:MM:SS` (milliseconds are optional and typically not required).
- **ID Generation**: Prefer descriptive/readable IDs for seed/demo data (e.g., `DEMO_STORE`, `PROD_100`) instead of arbitrary numeric sequences.
- **CDATA**: Wrap text containing XML special characters (e.g., descriptions with HTML) in `<![CDATA[ ... ]]>`.
- **Determinism**: Seed data must be **deterministic and environment independent**. Do not rely on environment-specific system variables.

## Bad Practices & Anti-patterns

### Anti-pattern: FK Ordering Trap
**Pitfall**: Defining a child record (like `OrderItem`) before its parent (`OrderHeader`). This triggers immediate loading failures.
- **❌ Bad**: `OrderItem` followed by `OrderHeader`.
- **✅ Good**: Parent first, then children. Use `dependent-on` in the entity model and ensure referenced records (e.g., `statusId`) exist in the same XML file or in earlier reader files (e.g., `seed` before `demo`).

### Anti-pattern: Reader Misuse
**Pitfall**: Putting transactional demo data (like Orders) into a `seed` file. This forces bloat into production environments.
- **❌ Bad**: Placing `<OrderHeader .../>` in a file registered as `reader-name="seed"`.
- **✅ Good**: Use `reader-name="demo"` for transactional samples.

### Anti-pattern: Timestamp Fragility
**Pitfall**: Using non-standard or localized date formats that fail on different server locales.
- **❌ Bad**: `Jan 1 2023 10:00 AM`
- **✅ Good**: `2023-01-01 10:00:00`

---

## Common Code Patterns (Examples)

**Example: Comprehensive Seed Data (Types, Products, Relationships)**
```xml
<entity-engine-xml xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:noNamespaceSchemaLocation="http://ofbiz.apache.org/dtds/entity-engine-xml.xsd">

    <!-- Product Metadata -->
    <ProductType productTypeId="FINISHED_GOOD" isPhysical="Y" isDigital="N" description="Finished Good"/>

    <!-- Core Product Data -->
    <Product productId="PROD_MASTER" productTypeId="FINISHED_GOOD" internalName="Master Product"
        description="A standard physical product sample." createdDate="2023-01-01 00:00:00"/>

    <!-- Temporal Relationship (Price) -->
    <ProductPrice productId="PROD_MASTER" productPriceTypeId="DEFAULT_PRICE"
        productPricePurposeId="PURCHASE" currencyUomId="USD" productStoreGroupId="_NA_"
        price="19.99" fromDate="2023-01-01 00:00:00"/>

    <!-- Batch Job Scheduling -->
    <RecurrenceInfo recurrenceInfoId="BATCH_DAILY" startDateTime="2023-01-01 00:00:00" recurrenceInfoTypeId="DAILY"/>
    <!-- Note: runtimeDataId omitted (null) as per best practice -->
    <JobSandbox jobId="BATCH_DAILY_JOB" jobName="Daily Sync" serviceName="syncExternalData"
        poolId="pool" runTime="2023-01-01 00:00:00" recurrenceInfoId="BATCH_DAILY" statusId="SERVICE_PENDING"/>
</entity-engine-xml>
```
