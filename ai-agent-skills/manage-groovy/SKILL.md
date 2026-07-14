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
name: manage-groovy
description: |
  Write Groovy logic for OFBiz services and scripts. Use when
  - Writing business logic for OFBiz services using Groovy.
  - Creating UI/API data preparation scripts for Screen Widgets.
  - Implementing modern entity logic using Groovy DSL.
---

## Goal
Implement business logic, complex data transformations, and data preparation scripts in Groovy using modern OFBiz DSL patterns.

## Triggers
**ALWAYS** read this skill when:
- Creating or modifying `.groovy` files.
- Implementing core business logic for services with `engine="groovy"`.
- Preparing data for screens via `<script>` tags in `widget-screen.xml` or similar presentation files.
- Performing complex data transformations or filtering using Groovy scripts.

## Procedure

### 1. Groovy Services (Business Logic)
- **Definition**: Defined in `services.xml` with `engine="groovy"`. *(See [manage-services](../manage-services/SKILL.md) for defining the service contract and deciding the appropriate service engine).*
  - `location` must point to the script file (e.g., `component://mycomponent/src/main/groovy/...`).
  - `invoke` must point to the specific method name in the script.
- **Method Signature**: Must be defined as a method (e.g., `def myService() { ... }`).
- **Context Variables (Injected)**:
  - `parameters`: Map of all IN/INOUT parameters.
  - `dctx`: DispatchContext object.
  - `dispatcher`: LocalDispatcher object.
  - `delegator`: Delegator object.
  - `userLogin`: GenericValue representing the executing user.
  - `locale` / `timeZone`: User's locale and timezone.
- **Return Value**: Must return a Map using OFBiz utility methods:
  - `return success([key: val])`
  - `return error("Message")` or `return error(label("UiLabels", "MyError"))`

### 2. Groovy Scripts (Data Preparation for Screens)
- **Definition**: Triggered from `<actions>` blocks in screen or form widgets via `<script location="component://..."/>`. Executed natively from top to bottom (no `invoke` needed). *(See [manage-screens](../manage-screens/SKILL.md) for understanding how Groovy data prep scripts integrate with OFBiz screen widgets).*
- **Context Variables (Injected)**:
  - `context`: A Map acting as the global environment. Data assigned here becomes available to Screen/FTL templates.
  - `parameters`: Request parameters (String or String[]).
  - `request` / `response` / `session`: HTTP servlet objects.
  - `dispatcher` / `delegator` / `userLogin`: Standard OFBiz context objects.
- **Passing Data to UI**: Assign directly to the `context` map:
  ```groovy
  context.productList = from("Product").queryList()
  ```

### 3. Entity DSL and Data Manipulation
- **Read Data**: Use the modern OFBiz DSL for readability:
  ```groovy
  // Fetch a list
  List products = from("Product").where("statusId", "PROD_ACTIVE").orderBy("productName").queryList()
  // Fetch one
  GenericValue product = from("Product").where("productId", productId).queryOne()
  ```
- **Domain Helpers First**: Before writing direct entity queries, look for existing OFBiz or component helper methods that encode business semantics. Direct or batched entity queries are still appropriate for enrichment, projections, or avoiding N+1 lookups once the helper behavior is understood.
- **Typed Service Boundaries**: If the service definition already types an IN parameter as `Timestamp`, `BigDecimal`, `Double`, `Long`, or `Integer`, consume `parameters.foo` directly. Do not add redundant `ObjectType.simpleTypeOrObjectConvert(...)`, `as Timestamp`, or similar casts at the service boundary unless the value can also come from a mixed legacy/string source.
- **Advanced Querying**: Use `EntityCondition` for complex filters:
  ```groovy
  import org.apache.ofbiz.entity.condition.EntityCondition
  import org.apache.ofbiz.entity.condition.EntityOperator
  def cond = EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, "ORDER_CREATED")
  ```
- **Groovy Features**: 
  - Leverage native collections: `list.collect { it.name }`, `list.findAll { it.active }`.
  - Use the safe navigation operator (`?.`) to avoid NullPointerExceptions: `result?.orderHeader?.statusId`.
  - Use GStrings for interpolation: `"Processing order ${orderId}"`.

### 4. Service Invocation
```groovy
Map result = runService("updateProduct", [productId: productId, internalName: "New Name"])
if (ServiceUtil.isError(result)) return result // Crucial: Bubble up errors

// Extracting OUT parameters naturally
String newId = result.newProductId
```

### 5. Logging
- **ALWAYS** use built-in injected logging methods: `logInfo("...")`, `logWarning("...")`, `logError("...")`.
- **NEVER** use `println` or `System.out`.

## Guardrails

### For Services:
- **Input Validation**: Check mandatory criteria early and return `error(...)` if preconditions aren't met.
- **Security**: Default to `auth="true"` in the `services.xml` definition. If `auth="false"` is used (e.g., for internal services), you **must** include explicit permission checks inside the service. Public services might use `auth="true"` along with permission checks.
- **Transaction Management**: Rely on the Service Engine's transaction configuration (`require-new-transaction`, etc.) in `services.xml`. Do not manually manage transactions in Groovy unless absolutely necessary. *(See [manage-services](../manage-services/SKILL.md) for more details).*

### For Scripts:
- **Null Checks**: Perform strict null checks on `parameters` before using them in database queries to avoid accidental full-table scans.

## Anti-Patterns (Bad Practices)

### General:
- **Direct SQL**: **NEVER** write raw JDBC/SQL in Groovy. Always use the Entity Engine (DSL or Delegator).
- **Hardcoding Strings**: Hardcoding error messages instead of UI Labels, or hardcoding IDs (like `partyId`).
- **Legacy Entity APIs**: **DO NOT** use legacy Delegator APIs like `delegator.findList(...)`. Always use the modern Entity DSL `from("Entity").where(...).queryList()`.
- **Custom Conversion Helpers**: Avoid local helper methods for conversions that OFBiz already provides. Prefer existing utilities such as `ObjectType.simpleTypeOrObjectConvert(...)` at service/input boundaries and `UtilMisc.toIntegerObject(...)` for integer conversion.
- **Redundant Boundary Conversion**: Do not manually re-parse values that the Service Engine has already coerced from `services.xml`. Reserve `ObjectType.simpleTypeOrObjectConvert(...)` for legacy string-typed services, mixed-source fallback logic, or decoding composite REST IDs.

### Database Querying:
- **Unconstrained Queries**: **NEVER** query a table without a `.where(...)` condition unless explicitly fetching a tiny bounded list (like enumerated `StatusItem`).
- **N+1 Queries**: **AVOID** querying the database inside a loop. Instead, gather all IDs, perform a single `IN` query using `EntityOperator.IN`, and map the results in memory.
- **Over-fetching**: **ALWAYS** use `.select("field1", "field2")` on `EntityQuery` when you only need a few fields from a wide or heavy table. This saves memory and JDBC overhead.
- **Fetching Full Records for Existence Checks**: **DO NOT** use `queryList()` or `queryOne()` if you only need to know if a record exists. Use `.queryCount()` instead.
- **Date-Effective Existence Checks**: For date-effective entities, push the date filtering into the query itself with `.filterByDate()` or `.filterByDate("fromField", "thruField")` before calling `.queryCount()`. Do not fetch rows and then filter them in memory just to answer "does one exist?".
- **Large Result Sets**: **NEVER** use `queryList()` for queries that could return thousands of rows (e.g., all orders). Use `queryIterator()` to get an `EntityListIterator` and **must** wrap it in a `try-finally` block to ensure `.close()` is called.
- **Missing Cache Usage**: For highly-read, rarely-changed data (e.g., `StatusItem`, `Geo`), missing `.cache(true)` bypasses the entity cache and impacts database performance unnecessarily.

### Services & Orchestration:
- **Top-Level Logic**: **NEVER** write business logic outside of a method block in a service file. It causes unpredictable shared-state issues.
- **Ignoring Results**: **ALWAYS** check returning maps for errors using `ServiceUtil.isError(...)`. Ignoring child service failures causes silent cascading data corruption.
- **Overusing Groovy**: Implementing highly transactional, complex financial/math invariants in Groovy. Use Java for heavy core logic. *(See [manage-java](../manage-java/SKILL.md) for when core business logic requires strict typing and performance).*
- **Blocking the UI (Sync vs Async)**: **AVOID** running slow, non-critical background services synchronously (e.g., sending emails) within a UI request thread. Use `dispatcher.runAsync(...)` instead.
- **Heavy Payloads**: **NEVER** pass massive objects (like `List<GenericValue>` or heavy byte streams) through service parameters (IN or OUT). Services should pass lightweight data like IDs, IDs maps, or minimal POJOs.

### Groovy Typing & Safety:
- **Groovy Truth Traps**: **BEWARE** that Groovy evaluates `0`, `""` (empty string), and `[]` (empty list) as `false`. When evaluating numeric variables (e.g., order amount of `0.0`), use strict null checks (`if (amount != null)`) instead of `if (amount)` to avoid accidentally skipping valid zero values.
- **Implicit BigDecimal Arithmetic**: Groovy defaults decimals to `BigDecimal`, but careless division can throw `ArithmeticException` for non-terminating decimal expansions. Explicitly handle math precision or use standard Java rounding logic for financial data.

### Scripts & Data Prep:
- **Modifying State**: **NEVER** perform DB inserts/updates/deletes or call mutating services (`create...`, `update...`) inside a screen script. Screen scripts are strictly **read-only**. State mutation happens in Services/Events, not UI data prep.
- **Complex Logic**: Putting massive business logic in a screen script instead of delegating to a reusable Service.

## Examples

**Example 1: Groovy Service (The Right Way)**
```groovy
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.service.ServiceUtil

def updateInternalName() {
    String productId = parameters.productId
    if (!productId) return error("Product ID is required")

    // Transaction Management: Prefer the service engine's transaction boundary setup in services.xml.
    // By relying on the container, returning error() automatically triggers a rollback.
    GenericValue product = from("Product").where("productId", productId).queryOne()
    if (!product) return error("Product not found")

    try {
        product.internalName = parameters.internalName
        product.store()
        
        // Note on multi-record updates: Ensure the service itself is transactional. 
        // If an exception occurs or you return error() mid-loop, it prevents partial writes.
    } catch (Exception e) {
        logError(e, "Failed to update internal name for product ${productId}")
        return error("Error updating product: ${e.getMessage()}")
    }
    
    logInfo("Successfully updated internal name for product ${productId}")
    return success([updatedName: product.internalName])
}
```

**Example 1b: Typed service boundary (The Right Way)**
```groovy
def createAssociation() {
    Timestamp fromDate = parameters.fromDate ?: UtilDateTime.nowTimestamp()
    BigDecimal quantity = parameters.quantity ?: BigDecimal.ONE
    // services.xml already typed fromDate and quantity
    ...
}
```

**Example 1c: Date-effective existence check (The Right Way)**
```groovy
long supplierProductCount = from("SupplierProduct")
        .where(productId: productId, currencyUomId: currencyUomId)
        .filterByDate("availableFromDate", "availableThruDate")
        .queryCount()
boolean hasSupplierPrice = supplierProductCount > 0L
```

**Example 2: Data Prep Screen Script (The Right Way)**
```groovy
// Simple top-to-bottom read-only script for a screen
String statusId = parameters.statusId

if (statusId) {
    // Only querying if parameter exists and using context for UI
    context.invoices = from("Invoice")
                        .where("statusId", statusId)
                        .orderBy("-invoiceDate")
                        .queryList()
} else {
    context.invoices = []
}

context.pageTitle = "Invoice Review"
```

**Anti-Pattern Example 1: Modifying State in a Screen Script (DO NOT DO THIS)**
```groovy
// Bad Practice: This is a screen script but it mutates the database!
GenericValue newRecord = delegator.makeValue("CustomEntity")
newRecord.customId = delegator.getNextSeqId("CustomEntity")
newRecord.description = "Created during screen render"
newRecord.create() // WRONG! Screen scripts should be read-only.
```

**Anti-Pattern Example 2: Top-Level Logic in a Service File (DO NOT DO THIS)**
```groovy
// Bad Practice: Logic executed outside of a method block in a service script.
// This causes unpredictable shared-state and caching issues!
GenericValue user = from("UserLogin").where("userLoginId", parameters.userLoginId).queryOne()
user.lastAccessed = org.apache.ofbiz.base.util.UtilDateTime.nowTimestamp()
user.store()

def myServiceMethod() {
    // Only logic inside here is safe for repeated service invocation
    return success()
}
```

**Anti-Pattern Example 3: Ignoring Child Service Errors (DO NOT DO THIS)**
```groovy
def processComplexOrder() {
    // Bad Practice: Fails to check if the child service returned an error!
    // If the child service fails, this continues silently and likely causes a crash later.
    Map result = runService("createOrderHeader", [partyId: parameters.partyId])
    
    String orderId = result.orderId 
    runService("createOrderItem", [orderId: orderId, productId: "PROD_1"]) // Fails if orderId is null
    
    return success()
}
```
