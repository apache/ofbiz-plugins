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
name: manage-eca
description: Manage Service Event Condition Actions (SECA) and Entity Event Condition Actions (EECA) to implement event-driven logic in OFBiz.
---

# Skill: Manage Service and Entity ECAs (SECA / EECA) in Apache OFBiz

## Goal
To implement event-driven logic in OFBiz by triggering services automatically when another service reaches a lifecycle event (SECA) or a database entity is modified (EECA).

ECAs are powerful — but misused ECAs are one of the fastest ways to destabilize a production OFBiz system.

> **Core Principle**: If removing the ECA breaks the main business flow, it should **NOT** be an ECA.

## Triggers
- Implementing cross-cutting concerns (e.g., clearing caches, indexing).
- Triggering side effects (e.g., sending emails after order completion).
- Enforcing business rules that depend on data changes.
- Syncing data between different systems or components.

## Rules & Procedures

### Decision Tree: SECA vs EECA vs Explicit Chaining

**Step 1: Is this logic part of the main business flow?**
Examples: "Order approval must reserve inventory", "Shipment creation must validate allocation", "Invoice must be generated or order is invalid".
- **YES** → **Explicit chaining / orchestrator service**
  - (Call the next service directly, or create a wrapper service like `processOrderApproval` that orchestrates steps using **Groovy** (**Cross-Reference**: `manage-groovy`) or **Service Groups** (**Cross-Reference**: `manage-service-groups`).)
- **NO** → Go to Step 2
*Rule: If removing it breaks the flow, it’s not an ECA.*

**Step 2: What is the trigger source you truly want?**
Ask: "What event should cause this to happen?"
- **2A) Trigger is a service lifecycle event**
  - Example: "After `storeOrder` completes, send an email", "After `createShipment` commits, push to ERP".
  - **Choose SECA**
  - Then go to Step 3 (event + mode)
- **2B) Trigger is a database row change**
  - Example: "Whenever Product changes, reindex", "When OrderHeader.statusId becomes COMPLETED, notify".
  - **Choose EECA** (only on physical entities)
  - Then go to Step 3 (event + mode)
- *If neither is clear → prefer explicit chaining (more traceable).*

**Step 3: Does the triggered work need committed DB state?**
Ask: "Will the triggered service read data written by the triggering work?"
- **YES** → Use post-commit trigger
  - **SECA**: `event="commit"`
  - **EECA**: prefer `event="return"` but ensure the consuming logic doesn’t require uncommitted visibility; if it must read committed state reliably, prefer SECA on commit of the creating/updating service (or pass required data directly).
- **NO** → `return` is usually fine
  - (SECA: `event="return"`, EECA: `event="return"`)
*Key: async is concurrent; commit matters when reads must see written data.*

**Step 4: Is the action heavy / slow / failure-tolerant?**
Examples: external API call, email, indexing, analytics.
- **YES** → `mode="async"`
  - Use `persist="true"` only when job volume is controlled and reliability is required.
  - *(See the [manage-async](../manage-async/SKILL.md) skill before choosing `mode="async"`!)*
- **NO** → `mode="sync"` (keep it lightweight and deterministic)

### Quick Rules of Thumb

**Use Explicit Chaining when:**
- It’s core workflow logic
- Ordering matters strongly across multiple steps
- You need clear traceability and testing
- You want retries/compensation explicitly

**Use SECA when:**
- Trigger is "after service X"
- You want to extend behavior without changing service code
- You can pick `return` vs `commit` intentionally

**Use EECA when:**
- Trigger is "when entity row changes"
- You need cross-cutting reaction to DB writes from multiple entry points
- You can write precise conditions
- You are not targeting view entities and not using `find`

### Implementation Procedures

#### 1. Service ECAs (SECAs)
SECAs trigger a service when another service reaches a specific life-cycle event.

- **Location**: Typically defined in `secas.xml` files within a component's `servicedef` directory.
- **Registration**: Register the SECA file in `ofbiz-component.xml`:
  ```xml
  <service-resource type="eca" loader="main" location="servicedef/secas.xml"/>
  ```

#### SECA Pattern
```xml
<eca service="serviceName" event="event-name">
    <!-- Optional Conditions -->
    <condition field-name="statusId" operator="equals" value="ORDER_COMPLETED"/>
    <!-- Actions -->
    <action service="triggerService" mode="sync|async" persist="true|false"/>
</eca>
```

#### Event Selection Rules
- **return**: Business logic completed, transaction not yet committed (most common).
- **commit**: Use ONLY when the triggered service must see committed data.
- **invoke**: Avoid "invoke" unless modifying input context is required.
- `auth`: Before authentication.
- `in-validate`: Before input validation.
- `out-validate`: Before output validation.

---

#### 2. Entity ECAs (EECAs)
EECAs trigger a service when a database operation occurs on a specific entity.

- **Location**: Typically defined in `eecas.xml` files within a component's `entitydef` directory.
- **Registration**: Register the EECA file in `ofbiz-component.xml`:
  ```xml
  <entity-resource type="eca" loader="main" location="entitydef/eecas.xml"/>
  ```

#### EECA Pattern
```xml
<eca entity="EntityName" operation="create|store|remove|find" event="validate|run|return|status-change">
    <condition field-name="fieldName" operator="not-equals" value="N"/>
    <action service="triggerService" mode="sync" value-attr="currentValue"/>
</eca>
```

#### Critical Constraints
- NEVER attach EECA to view entities.
- Avoid `operation="find"` (high performance risk).
- ✔ Prefer `store` + condition over `status-change` when possible.

#### Event Rules
- **run**: Before DB operation (rare).
- **return**: After DB operation (most common).
- **status-change**: Use ONLY for explicit status-driven logic.

**Common Operations**: `create`, `store` (update), `remove`.

---

## Guardrails
- **Avoid Recursion**: Be extremely careful not to trigger a SECA/EECA that eventually calls the original service/entity operation, leading to an infinite loop (see Examples).
- **Transactional Impact**: Synchronous (`mode="sync"`) actions run in the same transaction. If the action fails, the main operation fails.
- **Condition Precision**: Always use conditions to ensure the action only triggers when strictly necessary.
- **Async Execution Warning**: Before configuring `mode="async"`, you **MUST** review the explicit rules in the `manage-async` skill to prevent JobSandbox explosions and system instability.

## Examples: Good Patterns vs Anti-Patterns

### 1. Order Completion SECA (Good Pattern for Live Traffic)
Triggering an email and resetting totals after an order is stored.
```xml
<eca service="storeOrder" event="return">
    <condition field-name="orderTypeId" operator="equals" value="SALES_ORDER"/>
    <action service="resetOrderGrandTotal" mode="sync"/>
    <action service="sendOrderConfirmationEmail" mode="async" persist="true"/>
</eca>
```
**Why this is good (for live traffic)**: It decouples the heavy network call (email) from the transaction, avoiding blocking the user during checkout.

**ANTI-PATTERN: Leaving this enabled for bulk historical imports.**
This async + persist strategy is **WRONG** if:
- You are **bulk-importing historical orders** (JobSandbox explosion).
- The email must be sent **before** order completion is considered successful.
- The service modifies `OrderHeader` again (leading to transaction timing collisions).
- You are firing this for **every minor order update** (missing `<condition>` guard).

### 2. Infinite Loop Recursion (EECA ANTI-PATTERN)
**NEVER** trigger an action that updates the exact same entity that triggered it, unless guarded by iron-clad status conditions.
```xml
<eca entity="OrderHeader" operation="store" event="return">
    <action service="recomputeOrderHeader" mode="sync"/>
</eca>
```
**Why this is dangerous**: When `recomputeOrderHeader` executes, it internally stores `<OrderHeader>`. Because the operation is `store`, the EECA immediately fires a *second* time. This creates an infinite loop that instantly crashes the transaction thread.

### 3. View Entity trigger (EECA ANTI-PATTERN)
**NEVER** attach an EECA to a View Entity.
```xml
<!-- WRONG: OrderHeaderAndItems is a View Entity -->
<eca entity="OrderHeaderAndItems" operation="store" event="return">
    <action service="triggerCustomLogic" mode="sync"/>
</eca>
```
**Why this is wrong**: View Entities are virtual joins. The OFBiz Entity Engine does not fire database events (`create`, `store`, `remove`) on views—it fires them on the underlying physical tables. An EECA on a View Entity will either never trigger, or will trigger inconsistently and cause deep system confusion.

### 4. Find Operation (EECA ANTI-PATTERN)
**NEVER** attach an EECA to a `find` operation unless absolutely unavoidable and closely monitored.
```xml
<!-- WRONG: High performance risk -->
<eca entity="Product" operation="find" event="return">
    <action service="triggerExpensiveCheck" mode="sync"/>
</eca>
```
**Why this is dangerous**: Find operations are executed continuously by the system (UI lookups, internal service logic, batch processing). Intercepting every `find` operation on a core entity like `Product` adds massive latency to the system and will destroy database throughput.

### 5. Product Keyword Indexing EECA
Automatically indexing keywords whenever a Product is created or updated.
```xml
<eca entity="Product" operation="create" event="return">
    <action service="indexProductKeywords" mode="sync" value-attr="productInstance"/>
</eca>
<eca entity="Product" operation="store" event="return">
    <action service="indexProductKeywords" mode="sync" value-attr="productInstance"/>
</eca>
```
