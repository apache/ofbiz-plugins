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
name: manage-services
description: Define and implement OFBiz services. Use when creating new services in services.xml, modifying attributes, orchestrating execution, or debugging dispatcher.
---

# Skill: manage-services

## Goal
Define and implement business logic as reusable, transactional, and securely enforced OFBiz services.

## Triggers
**ALWAYS** read this skill when:
- Defining or modifying `servicedef/services.xml`.
- Registering services in `ofbiz-component.xml`.
- Implementing service logic in Java, Groovy, or MiniLang.
- Troubleshooting service execution, validation, transactions, or asynchronous behavior.
- Exposing business logic to the UI or API.
- Orchestrating complex data workflows. (**Cross-Reference**: See the `manage-service-groups` skill for orchestrating sequential or concurrent flows without complex branching)
- Handling transactional operations (e.g., database commit/rollback boundaries).
- Dispatching asynchronous jobs or delayed callbacks.

## Rules & Procedures

### 1. Engine Selection
- **`entity-auto`**: Use for standard CRUD (Create, Update, Delete). Highly efficient; eliminates manual boilerplate.
- **`groovy`**: Use for logic requiring entity queries, map manipulation, parameter adaptations, or UI preparation. Fast to write, minimal boilerplate.
- **`java`**: Use for core domain invariants, strict compile-time typing, performance-heavy transformations, heavy loops, or integrating with 3rd-party libraries.
- **`group`**: Use as a simple orchestration wrapper for multiple independent services sequentially. Replaces the need to write an orchestration script. (**Cross-Reference**: See the `manage-service-groups` skill for group definitions, execution modes (`send-mode`), and orchestration anti-patterns.)

### 2. Service Definition & Registration
- **Naming**: Use `verbNoun` format for all service names (e.g., `updateExample`, `createOrder`).
- **In/Out Parameters**: Use `<attribute name="..." mode="IN|OUT|INOUT" type="..." optional="true|false"/>`. NEVER bypass attribute validation by casually omitting them or making them wrongly optional.
- **Native Contract Types**: Backend/data services should keep OFBiz-native service types such as `Timestamp` and `BigDecimal` in IN/OUT attributes and returned maps.
- **Type In XML First**: If a service consumes dates, numbers, or booleans, declare them with native OFBiz/Java types in `services.xml` first. Prefer fixing the service contract over keeping Groovy-side parsing code forever.
- **Auth & Security**: Set `auth="true"` for protected services. ALWAYS implement permission checks (e.g., `<permission-service>`).
- **Export & REST**: Set `export="true"` to expose the service to external callers. Set `action="GET|POST|PUT|DELETE"` to automatically export the service as a REST API endpoint.
- **Service Overrides**: Use `<implements service="..." />` to inherit attributes from an existing service.
- **Registration**: Ensure `services.xml` is registered in `ofbiz-component.xml` via `<service-resource type="model" loader="main" location="servicedef/services.xml"/>`.

### 3. Execution & Handling
- **Request Processing**: Access parameters via `context` (Map). All services MUST return success/error maps via `ServiceUtil` (Java) or DSL shorthands (Groovy).
- **Synchronous (`runSync`)**: Use when the system depends on an immediate computation result or necessitates atomicity and immediate data consistency.
- **Asynchronous (`runAsync`)**: Use for heavy processing operations or external API integrations spanning across latency. **Cross-Reference**: See the `manage-async` skill for detailed guidelines.
- **Asynchronous Callbacks**: For long-running operations where you want to react without polling, use `dispatcher.registerCallback`.

### 4. Transaction Management (`use-transaction` & `require-new-transaction`)
- **Same Transaction (Default)**: Default is `use-transaction="true"`. Suitable for parent-child updates where all must succeed or fail together.
- **Different Transaction**: Use `require-new-transaction="true"` to isolate failures from the parent (e.g., Audit Logging so errors aren't wiped, Sequence Counters, Massive Batches).

### 5. Business Transactions (Rollback vs. Commit Services)
- **Commit Services**: Use `dispatcher.addCommitService` or SECAs on `global-commit` to run a service *only after* the transaction commits (e.g., sending emails). **Cross-Reference**: See the `manage-eca` skill for clear distinctions between `commit` and `global-commit`.
- **Rollback Services**: Use `dispatcher.addRollbackService` or SECAs on `global-rollback` to run a compensating service *only if* the target transaction rolls back entirely (e.g., releasing external payment holds).

### 6. Architecture & Composition
- OFBiz services can cleanly be invoked via Internal Dispatch, the Controller web layer, Scheduled Jobs, Form/Widget layers, or External API integration.
- **Cross-Reference**: See the `manage-eca` skill for deep dives into event-driven architecture mapping (SECAs/EECAs) configurations and patterns.

## Guardrails

### 1. View vs. Business Logic Separation
- Services must remain completely agnostic of the View layer. NEVER put HTML encoding, FreeMarker syntax, or UI formatting into a core backend service.
- Do not stringify dates, timestamps, quantities, or money in backend data services unless the service is explicitly a presentation/export formatting service.
- Error Messages: Use properties from `uiLabelMap` for user-facing error messages to support internationalization.

### 2. Error Handling Integrity
- **Swallowing Errors via Dispatcher**: When invoking a sub-service with `dispatcher.runSync()`, ALWAYS check manually using `ServiceUtil.isError(result)`. Failing to check will mask business failures and block rollbacks.
- **Null Safety**: Always check for mandatory attributes in implementation if not strictly enforced by XML validation.

### 3. Transaction Boundaries
- **Hidden Dangers of Async Services**: Never dispatch async services that rely on uncommitted data from the current transaction. They run concurrently and might trigger an intermittent "record not found". Use SECAs on `commit` or `global-commit` instead. **Cross-Reference**: See `manage-eca` on how to configure this correctly.
- **Programmatic Transactions**: NEVER handle `TransactionUtil.begin()` or `TransactionUtil.commit()` manually within Java code. Configure transactions strictly using `require-new-transaction` in `services.xml`. Let the service engine manage persistence and rollbacks.

### 4. Boilerplate CRUD Avoidance
- Do NOT use a Java or Groovy service simply to write `delegator.create()`. Rely on the `entity-auto` engine.

### 5. API Wrapper Services
- Thin API wrappers may adapt naming, shape, or multi-call orchestration for a PWA or REST endpoint, but they should still preserve OFBiz-native types in the service contract.
- Do not stringify timestamps, quantities, or money in wrapper services unless the service is explicitly a presentation/export formatter.
- For multi-column primary keys exposed through a single REST path segment, use a stable composite ID mapping in the API wrapper rather than flattening the underlying entity model.

## Anti-Patterns

### Anti-Pattern 1: Swallowing Errors in runSync
If you do not return the error from a sub-service, the parent service thinks it succeeded and commits the transaction, leaving your system in an inconsistent state.

**Bad:**
```groovy
def processOrder() {
    // Calling sub-service but ignoring the result!
    dispatcher.runSync("chargeCreditCard", [orderId: context.orderId]) 
    
    // This will run even if the credit card was declined!
    dispatcher.runSync("approveOrder", [orderId: context.orderId])
    return ServiceUtil.returnSuccess()
}
```

**Good:**
```groovy
def processOrder() {
    Map chargeResult = dispatcher.runSync("chargeCreditCard", [orderId: context.orderId])
    if (ServiceUtil.isError(chargeResult)) {
        return chargeResult // Returns error upwards, triggers parent rollback!
    }
    
    dispatcher.runSync("approveOrder", [orderId: context.orderId])
    return ServiceUtil.returnSuccess()
}
```

### Anti-Pattern 2: Manual Transaction Management
Manually managing database connections and transactions bypasses the service engine's ability to trigger SECAs, coordinate distributed transactions, and handle rollbacks consistently.

**Bad:**
```java
public static Map<String, Object> myBadService(DispatchContext dctx, Map<String, ? extends Object> context) {
    boolean beganTransaction = false;
    try {
        beganTransaction = TransactionUtil.begin();
        // ... DB updates
        TransactionUtil.commit(beganTransaction);
    } catch (Exception e) {
        TransactionUtil.rollback(beganTransaction, "Error", e);
    }
    return ServiceUtil.returnSuccess();
}
```

**Good:**
Configure it in `services.xml` instead and let the engine handle it automatically.
```xml
<!-- In services.xml -->
<service name="myGoodService" engine="java" ... use-transaction="true" require-new-transaction="true">
</service>
```

### Anti-Pattern 3: Writing Boilerplate CRUD
Writing custom Groovy/Java code just to perform basic CRUD operations is a waste of time and introduces potential bugs.

**Bad:** (Groovy file)
```groovy
def createExample() {
    GenericValue example = delegator.makeValue("Example")
    example.setPKFields(context)
    example.setNonPKFields(context)
    example.create()
    return ServiceUtil.returnSuccess()
}
```
*(Plus the XML definition using engine="groovy")*

**Good:** (Only needs XML definition)
```xml
<service name="createExample" engine="entity-auto" invoke="create" default-entity-name="Example" auth="true">
    <auto-attributes mode="IN" include="nonpk" optional="true"/>
    <auto-attributes mode="OUT" include="pk" optional="false"/>
</service>
```

### Anti-Pattern 4: Async Execution on Uncommitted Data
Dispatching an async service before the data it needs is committed leads to intermittent "record not found" errors because the async thread might start querying before the main thread commits.

**Bad:**
```groovy
def createOrder() {
    // 1. Create order in DB (uncommitted in current transaction)
    delegator.create(orderHeader) 
    
    // 2. DANGER: Async thread starts running NOW, might fetch orderHeader before step 1 commits
    dispatcher.runAsync("sendOrderConfirmationEmail", [orderId: orderHeader.orderId]) 
    
    return ServiceUtil.returnSuccess()
}
```

**Good:**
Use a SECA triggered on `global-commit` so it only runs *after* the data is safely in the database and the parent transaction has entirely succeeded. See the **`manage-eca`** skill for detailed examples of `global-commit` vs `commit`.
```xml
<!-- In secas.xml -->
<eca service="createOrder" event="global-commit">
    <action service="sendOrderConfirmationEmail" mode="async"/>
</eca>
```

## Examples

**Example: Service implementation in Groovy**
```groovy
def updateExample() {
    Map result = ServiceUtil.returnSuccess()
    GenericValue example = from("Example").where("exampleId", context.exampleId).queryOne()
    if (example) {
        example.setNonPKFields(context)
        example.store()
    }
    return result
}
```

**Example: Asynchronous Callback Configuration (Java)**
Instead of hard-wiring logic, react to long-running services:
```java
dispatcher.registerCallback("getTrackingNumberFromCarrier", new GenericServiceCallback() {
    @Override
    public void receiveEvent(Map<String,Object> ctx, Map<String,Object> res) {
        Debug.logInfo("Carrier response received. Tracking ID: " + res.get("trackingNumber"), "MyModule");
    }
    @Override
    public boolean isEnabled() { return true; }
});
```

**Example: Compensating Rollback Service (Java)**
If the database rolls back, execute logic to cancel an external hold:
```java
dispatcher.addRollbackService("releaseAuthorization", context, true);
```

**Example: REST Export in Service Definition**
```xml
<service name="findProductById" engine="java" auth="true" export="true" action="GET"
         location="org.apache.ofbiz.product.product.ProductServices" invoke="findProductById">
    <description>Automatically exported as a REST API</description>
    <attribute name="productId" mode="IN" type="String" optional="false"/>
</service>
```
