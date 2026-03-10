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
name: manage-java-patterns
description: |-
  - Foundational OFBiz Java coding patterns.
  - Architectural roles (Helper, Worker, Reader) and domain logic structure.
  - Shared standards for logging, security, data retrieval, and validation.
  - Prerequisite for manage-java and manage-java-events.
---

# Skill: manage-java-patterns
## Goal
Establish a consistent foundation for all OFBiz Java development. This skill standardizes cross-cutting concerns—including architectural roles (Helpers, Workers, Readers), logging, exception handling, localization, and high-performance data patterns—to ensure code is secure, maintainable, and idiomatic. It is the primary reference for developing or refactoring any Java-based business logic, data access, or service orchestration.

## Triggers
**ALWAYS** read this skill when:
- Creating or modifying **any** Java code in an OFBiz component, especially architectural roles like **Helpers**, **Workers**, or **Readers**.
- Designing data retrieval, persistence logic, or service orchestration.
- Implementing UI messaging, logging, or localized error handling in Java.

## Core Architectural Roles
Use these patterns when **designing class boundaries** and deciding how to structure your domain logic vs. utility logic:

> [!IMPORTANT]
> **Check and Re-use First**: Before writing new logic, ALWAYS search for existing classes in the component or framework (e.g., `OrderReadHelper`, `ProductWorker`). Re-use or extend existing methodology instead of creating redundant one-offs.

- **Helper Pattern (`Helper`, `ReadHelper`)**:
    - **Purpose**: Stateful domain-specific wrapper around a primary `GenericValue`.
    - **Structure**: Constructor takes primary `GenericValue`. Best practice: Dual constructor with `Delegator` + `id`.
    - **Example**: `OrderReadHelper` (See Examples).
- **Worker Pattern (`Worker`)**:
    - **Purpose**: Static utility class for stateless logic (calculations, checks).
    - **Structure**: Private constructor. All methods are `public static`.
    - **Example**: `ProductWorker` (See Examples).

---

## Rules & Procedures (Shared Patterns)
Use these patterns for **implementing data retrieval**, batch writes, and handling framework types like `GenericValue`, `Delegator`, or `Timestamp`. These are the **"Shared Standards"** for all OFBiz Java code:

### Data & Persistence
- **Standard Retrieval (`EntityQuery`)**: 
    - **Preferred**: `EntityQuery.use(delegator).from("Entity").where(...).queryOne();`
    - **PK Shortcut**: Use `delegator.findOne(entity, pkMap, useCache)` ONLY if you have the full PK map and are explicit about `useCache`.
- **Related Records (`getRelated`)**: Always include a sort list (e.g., `UtilMisc.toList("seqId")`) to avoid nondeterministic results.
- **Optimization**:
    - **Fields**: Use `.select("field1", "field2")` to reduce memory footprint.
    - **Date-Effective**: Use `.filterByDate()` correctly for temporal entities.
    - **Pagination**: Use `EntityFindOptions` (`setMaxRows`, `setOffset`) for large lists.
    - **Caching**: Use `.cache(true)` for reference data (Types/Statuses); `.cache(false)` for transactional freshness.
- **Creation Strategy**:
    - **System IDs**: Use `delegator.createSetNextSeqId(gv)`.
    - **Known PKs**: Use `delegator.makeValue(entity, fieldMap)` + `delegator.create(gv)`.
- **Batch Processing**: Use `delegator.storeAll(List)` in loops. For large sets, use the **Large Set Chunking** pattern (See Examples).

### Framework Utilities
- **Safe List Handling (`EntityUtil`)**: Always use `EntityUtil.getFirst(list)` to avoid `IndexOutOfBoundsException`. Use `EntityUtil.filterByDate()` and `EntityUtil.orderBy()` for in-memory manipulation.
- **Collection Builders (`UtilMisc`)**: Always use `UtilMisc.toMap(...)` and `UtilMisc.toList(...)` for concise, standard instantiation.
- **Date & Timestamps**: Always use `UtilDateTime`. 
    - **Current**: `UtilDateTime.nowTimestamp()`. 
    - **Parsing**: Never use `Timestamp.valueOf(String)`; use locale-aware parsing utilities instead.
- **Mandatory TRIMMING**: Always trim incoming `String` parameters using `UtilValidate.isEmpty()` / `String.trim()` before logic or persistence.

### Communication & Error Handling
- **Logging Standard**: Always define `private static final String MODULE = MyClass.class.getName();`.
    - **PII Warning**: Never log passwords, tokens, or personal identifiers.
- **Localization**: Only localize text at the user boundary (Events/Services). Use `UtilProperties.getMessage(resource, key, locale)`.
- **Exception Strategy**: Helpers/Workers should **throw** checked exceptions. Services/Events should **catch** and map them to localized user-friendly errors. Always catch-all `Exception` at the top level to prevent crashes.
- **Service Invocation**: Always include `userLogin`, `locale`, and `timeZone` in the context. (See Examples).

---

## Guardrails
- **Naming**: Ensure suffixes `Helper` or `Worker` are used appropriately.
- **Statelessness**: Workers and Service/Event methods **MUST** be stateless.
- **Thread Safety**: Never use non-final static variables for request/service state.
- **Helper Scope**: Helpers should not hold `LocalDispatcher` references unless orchestrating services.
- **Null Safety**: `UtilValidate.isEmpty()` is for Strings/Collections; use `null` checks for `GenericValue`.

---

## Bad Practices & Anti-patterns
Avoid these common pitfalls that lead to memory leaks, performance degradation, or data inconsistency.

### Anti-pattern: The "Loop-of-Death"
**Pitfall**: Calling `delegator.create()` or `gv.store()` inside a loop for a large number of records. This creates a massive transaction overhead and can lead to timeouts.
- **❌ Bad (Sequential Writes)**:
  ```java
  for (GenericValue item : items) {
      item.set("statusId", "COMPLETED");
      item.store(); // Individual DB hit per iteration
  }
  ```
- **✅ Good (Batch Write)**: 
  ```java
  // Collect and store all at once
  delegator.storeAll(items); 
  ```

### Anti-pattern: Silent Mutation (Missing `.store()`)
**Pitfall**: Mutating a `GenericValue` object retrieved from the database and assuming it auto-saves. Unlike Hibernate, OFBiz `GenericValue` objects are snapshots.
- **❌ Bad**:
  ```java
  GenericValue product = EntityQuery.use(delegator).from("Product").where("productId", id).queryOne();
  product.set("productName", newName);
  // Missing product.store() - change is lost!
  ```
- **✅ Good**: 
  ```java
  product.set("productName", newName);
  product.store();
  ```

### Anti-pattern: The Unclosed `EntityListIterator`
**Pitfall**: Failing to close an `EntityListIterator` (ELI). This leaves database cursors open, eventually exhausting the connection pool.
- **❌ Bad**:
  ```java
  EntityListIterator eli = EntityQuery.use(delegator).from("OrderHeader").queryIterator();
  while (eli.next() != null) { ... }
  // ELI never closed!
  ```
- **✅ Good (Try-with-resources)**: 
  ```java
  try (EntityListIterator eli = EntityQuery.use(delegator).from("OrderHeader").queryIterator()) {
      while (eli.next() != null) { ... }
  } // Automatically closed
  ```

### Anti-pattern: In-Memory Filtering Overkill
**Pitfall**: Fetching a massive list into memory using `queryList()` and then filtering it using Java streams or loops, instead of letting the database do its job.
- **❌ Bad**:
  ```java
  List<GenericValue> allOrders = EntityQuery.use(delegator).from("OrderHeader").queryList();
  List<GenericValue> activeOrders = allOrders.stream()
      .filter(o -> "ORDER_APPROVED".equals(o.getString("statusId")))
      .collect(Collectors.toList());
  ```
- **✅ Good**: 
  ```java
  List<GenericValue> activeOrders = EntityQuery.use(delegator).from("OrderHeader")
          .where("statusId", "ORDER_APPROVED").queryList();
  ```

---

## Common Code Patterns (Examples)

### Helper Pattern (Dual Constructors)
```java
public class MyOrderHelper {
    private GenericValue orderHeader;
    private Delegator delegator;

    public MyOrderHelper(GenericValue orderHeader) { 
        this.orderHeader = orderHeader; 
        this.delegator = orderHeader.getDelegator();
    }

    public MyOrderHelper(Delegator delegator, String orderId) throws GenericEntityException {
        this.delegator = delegator;
        this.orderHeader = EntityQuery.use(delegator).from("OrderHeader")
                .where("orderId", orderId).queryOne();
    }

    public List<GenericValue> getValidItems(boolean useCache) throws GenericEntityException {
        if (orderHeader == null) return Collections.emptyList();
        return orderHeader.getRelated("OrderItem", null, UtilMisc.toList("itemSeqId"), useCache);
    }
}
```

### Batch Writes & Large Set Chunking
```java
final int CHUNK = 500;
List<GenericValue> buffer = new ArrayList<>(CHUNK);

try (EntityListIterator eli = EntityQuery.use(delegator)
        .from("OrderItem")
        .where("statusId", "ITEM_APPROVED")
        .queryIterator()) {

    GenericValue gv;
    while ((gv = eli.next()) != null) {
        // mutate gv
        buffer.add(gv);

        if (buffer.size() >= CHUNK) {
            delegator.storeAll(buffer);
            buffer.clear();
        }
    }
    if (!buffer.isEmpty()) delegator.storeAll(buffer);
}
```

### Standard Service Invocation Context
```java
Map<String, Object> ctx = UtilMisc.toMap(
  "userLogin", userLogin,
  "locale", locale,
  "timeZone", timeZone,
  "productId", productId
);
Map<String, Object> res = dispatcher.runSync("myService", ctx);
if (ServiceUtil.isError(res)) {
    return ServiceUtil.returnError(ServiceUtil.getErrorMessage(res));
}
```
