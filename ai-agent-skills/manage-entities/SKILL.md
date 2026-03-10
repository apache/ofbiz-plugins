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
name: manage-entities
description: Manage OFBiz entity definitions (Data Model). Use when creating/modifying entities, debugging DB errors, or adding views.
---

# Skill: manage-entities
## Goal
Define and maintain the OFBiz Data Model using XML entity definitions. This includes creating new database tables (entities), defining joins (view-entities), adding indexes, and extending core framework entities to support custom business requirements safely.

> [!TIP]
> Use this skill in conjunction with [manage-data](../manage-data/SKILL.md) and [manage-component](../manage-component/SKILL.md).

## Triggers
**ALWAYS** read this skill when:
- Creating or modifying `servicedef/entitymodel.xml` (entities) or `servicedef/entitymodel_view.xml` (views).
- Extending core OFBiz entities using `<extend-entity>`.
- Troubleshooting database sync errors, foreign key violations, or SQL performance issues.
- Designing complex data relationships and aggregations.

## Rules & Procedures

### 1. Entity Definition
- **Location**: Defined in `entitydef/entitymodel.xml`.
- **Requirements**: Always include a descriptive `title` and a `package-name` (e.g., `org.apache.ofbiz.order`).
- **Primary Keys**: Every entity MUST have at least one `<prim-key field="..."/>`.
- **Optimistic Locking**: Use `enable-lock="true"` for entities with high concurrency (e.g., `OrderHeader`, `Shipment`).
- **Dependent Ordering**: Use `dependent-on="[EntityName]"` to ensure correct load and delete ordering for related entities.

### 2. Field Types & Constraints
- **Standard Types**: Use standard OFBiz field types defined in `fieldtypemodel.xml` to ensure database independence:
    - `id`: For primary/foreign keys (usually VARCHAR(20)).
    - `id-long`: For longer IDs.
    - `name`: For short names/labels.
    - `description`: For longer descriptive text.
    - `currency-amount`: For financial values (fixed precision).
    - `date-time`: For full timestamps.
    - `indicator`: For boolean-style flags ('Y' or 'N').
- **Sequencing**: Primary keys of type `id` or `id-long` are typically generated via the OFBiz Sequencer.
- **Default Values**: Use `default-value="..."` for fields that should auto-initialize (e.g., `statusId="ACTIVE"`).
- **Hard Constraints**: Use `not-null="true"` for mandatory database-level fields.

### 3. Relationships & Foreign Keys
- **type="one"**: Represents a **Database Foreign Key** constraint. Target must exist.
    - Syntax: `<relation type="one" fk-name="[FK_NAME]" rel-entity-name="[EntityName]">`
- **type="one-nofk"**: Logical relationship without a physical database constraint. Use when target might be missing or managed externally.
- **type="many"**: Represents a **Logical Relationship** for navigation (no database FK created).
    - Syntax: `<relation type="many" rel-entity-name="[EntityName]">`
- **Naming**: Use a consistent naming convention for `fk-name` (e.g., `[EntityShort]_[FieldShort]`). Use `title` if multiple links to the same entity exist.
- **Entity Verification**: **ALWAYS** use the `entity-name` (as defined in `entitymodel.xml`), NOT the physical database table name, for `rel-entity-name`.
- **Keys**: Ensure all primary key fields of the related entity are mapped in the `<key-map>`.

### 4. Registration
Every entity and view-entity resource MUST be registered in `ofbiz-component.xml`:
```xml
<entity-resource type="model" reader-name="main" loader="main" location="entitydef/entitymodel.xml"/>
```

### 5. View Entities (Joins & Functions)
- **Goal**: Define database joins and aggregations directly in XML.
- **Alias Strategy**: Use `<alias-all>` for convenience, but define explicit `<alias>` entries when joining entities with overlapping fields.
- **Join Direction**: Usually defined from the **Child** (containing FK) to the **Parent** (containing PK).
- **Conditions**: Use `<entity-condition>` and `<condition-expr>` for pre-filtered views (e.g., only active records).
- **Functions**: Use `function="..."` (e.g., `sum`, `count`) and `group-by="true"` for aggregations.

### 6. Entity Extensions
- **Strategy**: NEVER modify framework entities directly. Use `<extend-entity entity-name="...">`.
- **Namespacing**: **ALWAYS** prefix custom fields (e.g., `extField` or `customField`) to avoid naming collisions with future framework updates.

### 7. Temporal Data & History
- **Effective Dates**: Always use `fromDate` (often part of Primary Key) and `thruDate` to manage history.
- **Audit Fields**: OFBiz automatically adds `createdStamp` and `lastUpdatedStamp`. Don't define these manually.

### 8. Indexes
- **Rule**: Add indexes for fields frequently used in search/joins (e.g., `partyId`, `statusId`) if they aren't part of a PK.
- **When to Avoid**: Do not index low cardinality fields (unless part of a composite index) or high-write tables where overhead is costly.
- **Judicious Use**: Avoid redundant indexes on PKs. Don't add indexes during initial design; wait for query performance patterns.

### 9. Status View Entities
- **Join**: Always join with `StatusItem` on `statusId`.
- **Aliasing**: Alias `StatusItem.description` to `statusDescription` to avoid name collisions.

## Guardrails
- **Naming**: Use **CamelCase** for entities and fields (e.g., `ProductPrice`, `productId`).
- **Check Existing**: **ALWAYS** check existing entity definitions (via `entitymodel.xml` or WebTools Entity Engine) before creating new entities to avoid duplication.
- **Complexity**: Avoid view-entities with more than **5-6 joins**. If complexity grows, consider service-level logic.
- **Audit**: Enable `enable-audit-log="true"` for sensitive PII or financial fields.
- **Integrity**: Parental entities MUST exist before children (both in data load and relation definition).
- **Cache**: Use `useCache=true` in find operations for static data (Types, Enums) to boost performance.

## Bad Practices & Anti-patterns

### Anti-pattern: Snake_Case Fields
- **❌ Bad**: `<field name="product_id" type="id"/>`
- **✅ Good**: `<field name="productId" type="id"/>` (OFBiz standard is CamelCase).

### Anti-pattern: Missing Namespacing in Extensions
- **❌ Bad**: Extending `Product` with a field named `internalId` (high collision risk).
- **✅ Good**: Use `extInternalId` or `customInternalId`.

### Anti-pattern: Over-Indexing
- **❌ Bad**: Creating an index on every field "just in case." This kills `INSERT`/`UPDATE` performance.
- **✅ Good**: Index only after identifying performance bottlenecks.

### Anti-pattern: Physical Table Name Reference
- **❌ Bad**: Using `rel-entity-name="product_price"` or table names in code.
- **✅ Good**: ALWAYS use the entity name: `rel-entity-name="ProductPrice"`.

### Anti-pattern: Missing Primary Key
- **❌ Bad**: Defining an entity without a `<prim-key>`. This causes persistent caching and loading errors.
- **✅ Good**: Every entity MUST have at least one primary key field.

### Anti-pattern: Hardcoded/Nondescript FK Names
- **❌ Bad**: `<relation ... fk-name="FK_001">`.
- **✅ Good**: Use a descriptive name: `<relation ... fk-name="PROD_PRICE_TYPE">`.

### Anti-pattern: Hardcoding IDs
- **❌ Bad**: Literal strings like "COMPLETED" in code.
- **✅ Good**: Use `Enumeration` or `StatusItem` and refer to constant IDs.

### Anti-pattern: Business Logic in Entity Definitions
- **❌ Bad**: Adding calculation or validation logic in the entity model.
- **✅ Good**: Entities should be pure data structures; logic belongs in Services.

### Anti-pattern: Redundant Fields
- **❌ Bad**: Duplicating data that can be retrieved via a join.
- **✅ Good**: Keep data normalized unless strictly required for a performance optimization.

### Anti-pattern: Circular View Entities
- **❌ Bad**: View A joins View B, which joins View A.
- **✅ Good**: Maintain a strict hierarchy; avoid cycles which cause stack overflows during initialization.

## Common Code Patterns (Examples)

**Example: Standard Entity with Constraints & Locking**
```xml
<entity entity-name="CustomOrderTask" package-name="com.myplugin.order" title="Custom Order Management Task" enable-lock="true">
    <field name="taskId" type="id" not-null="true"/>
    <field name="orderId" type="id" not-null="true"/>
    <field name="statusId" type="id" default-value="TASK_OPEN"/>
    <field name="taskDescription" type="description"/>
    <prim-key field="taskId"/>
    <relation type="one" fk-name="CUST_TASK_ORDR" rel-entity-name="OrderHeader">
        <key-map field-name="orderId"/>
    </relation>
</entity>
```

**Example: Filtered View Entity (with Conditions)**
```xml
<view-entity entity-name="ActiveOrderSummary" package-name="com.myplugin.order">
    <member-entity entity-alias="OH" entity-name="OrderHeader"/>
    <member-entity entity-alias="SI" entity-name="StatusItem"/>
    <alias-all entity-alias="OH"/>
    <alias entity-alias="SI" name="statusDescription" field="description"/>
    <view-link entity-alias="OH" rel-entity-alias="SI">
        <key-map field-name="statusId"/>
    </view-link>
    <entity-condition>
        <condition-expr entity-alias="OH" field-name="statusId" operator="not-equals" value="ORDER_COMPLETED"/>
    </entity-condition>
</view-entity>
```

**Example: Status View Entity (Aliasing Pattern)**
```xml
<view-entity entity-name="OrderHeaderAndStatus" package-name="org.apache.ofbiz.order">
    <member-entity entity-alias="OH" entity-name="OrderHeader"/>
    <member-entity entity-alias="SI" entity-name="StatusItem"/>
    <alias-all entity-alias="OH"/>
    <alias entity-alias="SI" name="statusDescription" field="description"/>
    <view-link entity-alias="OH" rel-entity-alias="SI">
        <key-map field-name="statusId"/>
    </view-link>
</view-entity>
```

**Example: Advanced View Entity with Aggregation**
```xml
<view-entity entity-name="OrderHeaderAndRoleSummary" package-name="org.apache.ofbiz.order">
    <member-entity entity-alias="ORLE" entity-name="OrderRole"/>
    <member-entity entity-alias="OH" entity-name="OrderHeader"/>
    <alias entity-alias="ORLE" name="partyId" group-by="true"/>
    <alias entity-alias="ORLE" name="roleTypeId" group-by="true"/>
    <alias entity-alias="OH" name="totalGrandAmount" field="grandTotal" function="sum"/>
    <alias entity-alias="OH" name="totalOrders" field="orderId" function="count"/>
    <view-link entity-alias="ORLE" rel-entity-alias="OH">
        <key-map field-name="orderId"/>
    </view-link>
</view-entity>
```

**Example: Entity Extension with Namespacing**
```xml
<extend-entity entity-name="Product">
    <field name="extBrandLoyaltyScore" type="numeric"/>
    <field name="extIsWholesaleOnly" type="indicator" default-value="N"/>
</extend-entity>
```

