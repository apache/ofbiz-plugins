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
name: manage-java
description: |-
  - Implementing OFBiz services (business logic) in Java.
  - Handling transactions and service contracts.
  - Requires manage-java-patterns for shared standards.
---

# Skill: manage-java
## Goal
Implement robust, transaction-aware business logic in Java for OFBiz services. This is recommended for core domain logic, performance-critical integrations, or where strict typing and deep 3rd-party library integration are required.

## Triggers
**ALWAYS** read this skill when:
- Creating or modifying Java services in an OFBiz component.
- Implementing business logic that requires transaction management.
- Debugging service execution or runtime reflection errors.

> [!TIP]
> **See [manage-java-patterns](../manage-java-patterns/SKILL.md)** for shared Java standards (logging constants, localization utilities, and basic exception handling).


## Rules & Procedures
### 1. Class Structure & Worker Patterns
- Create service classes under the component’s Java source directory so they’re on the OFBiz runtime classpath (FQCN must match `services.xml` location).
- Keep worker methods stateless and side-effect controlled. This ensures testability and safe reuse across services, events, and screens.
- Pass dependencies like `Delegator`, `LocalDispatcher`, `GenericValue userLogin`, `Locale`, and `TimeZone` explicitly to worker methods to ensure thread-safety and avoid implicit dependencies.

### 2. Standard Method Signature
- A Java service must be a `public static` method.
- It receives a `DispatchContext` and a `Map<String, ? extends Object>` of input parameters.
- It returns a `Map<String, Object>` containing the output parameters.
```java
public static Map<String, Object> myService(DispatchContext dctx, Map<String, ? extends Object> context)
```

### 3. Core Tools and Parameters
- **Delegator/Dispatcher**: Always retrieve these from the `DispatchContext`.
  ```java
  Delegator delegator = dctx.getDelegator();
  LocalDispatcher dispatcher = dctx.getDispatcher();
  GenericValue userLogin = (GenericValue) context.get("userLogin");
  ```
- **Data Access**: Use `dctx.getDelegator()` to perform entity lookups.
- **Service Parameters**: Inputs are received via the `context` Map.
  - **Rule**: Consult **[manage-java-patterns](../manage-java-patterns/SKILL.md)** for `EntityQuery` performance, `UtilValidate`, normalization, and batch-write patterns.

### 4. Locale and Timezone Awareness in Services
- Always pass context-provided `Locale` and `TimeZone` objects to sub-services and worker methods.
- **Rule**: Follow the "Date & Timestamp Handling" and "Localization" patterns in **[manage-java-patterns](../manage-java-patterns/SKILL.md)**. Never interpret user-entered local date/time without an explicit `timeZone`.

### 5. Exposing Service in XML
- The service must be explicitly registered in `services.xml` specifying `engine="java"`.
```xml
<service name="myService" engine="java" location="com.example.ofbiz.MyJavaServices" invoke="myService" auth="true">
    <attribute name="myInputParam" type="String" mode="IN"/>
</service>
```

## Guardrails
- **Service Registration Match**: Ensure the Java method name and parameters match exactly what is defined in the `<service>` definition in `services.xml`.
- **Service Return Values**: **ALWAYS** use `ServiceUtil.returnSuccess()`, `ServiceUtil.returnError()`, or `ServiceUtil.returnFailure()` to ensure the dispatcher natively understands the transaction outcome.
- **Child Service Verification**: When invoking sub-services, immediately check if they failed and safely bubble the error using `ServiceUtil.isError()`.
  - **Rule**: Follow the "Service Invocation & Context" pattern in **[manage-java-patterns](../manage-java-patterns/SKILL.md)** to ensure consistent error handling and context propagation.
- **Resource Management (ELI)**: If using `EntityListIterator`, you **MUST** ensure it is safely closed. Refer to the "Batch Writes & Chunking" section in **[manage-java-patterns](../manage-java-patterns/SKILL.md)** for the `try-with-resources` pattern.
- **Authentication/userLogin**: If service is `auth="true"`, treat missing or invalid `userLogin` as a user error; don't wait for a NullPointerException (NPE) to crash the system. Validate `userLogin` exists when required and return a clean error if missing.

## Bad Practices & Anti-patterns

### Anti-pattern: Static State Mutation (Race Conditions)
**Pitfall**: Using non-final static variables to store state or temporary data. Since OFBiz services are executed concurrently by the service engine, this leads to unpredictable race conditions and memory leaks.
- **❌ Bad (Shared Static State)**:
  ```java
  private static Map<String, Object> tempCache = new HashMap<>(); // Global state!
  public static Map<String, Object> myService(DispatchContext dctx, Map<String, ?> context) {
      tempCache.put("userId", context.get("userId")); // Overwritten by other threads
      ...
  }
  ```
- **✅ Good (Thread-Safe Context)**: 
  ```java
  public static Map<String, Object> myService(DispatchContext dctx, Map<String, ?> context) {
      // Keep state local to the method/stack
      String userId = (String) context.get("userId");
      ...
  }
  ```

### Anti-pattern: Manual Transaction Overkill
**Pitfall**: Manually starting and committing JDBC transactions inside a service. This is error-prone and interferes with the service engine's automatic transaction handling.
- **❌ Bad (Manual JDBC/TransactionUtil)**:
  ```java
  TransactionUtil.begin();
  try {
      // logic
      TransactionUtil.commit();
  } catch (Exception e) {
      TransactionUtil.rollback();
  }
  ```
- **✅ Good (Declarative Transactions)**: 
  ```xml
  <!-- Set requirement in services.xml -->
  <service name="myService" engine="java" require-new-transaction="true" ... />
  ```

### Anti-pattern: Result Map Pollution
**Pitfall**: Returning parameters in the result map that are NOT defined in the `services.xml` contract. This makes the service hard to test, debug, and maintain.
- **❌ Bad**:
  ```java
  Map<String, Object> result = ServiceUtil.returnSuccess();
  result.put("undeclaredFlag", true); // Not in XML, confusing for callers
  return result;
  ```
- **✅ Good**: 
  ```java
  // Only return what is defined in <attribute mode="OUT" />
  Map<String, Object> result = ServiceUtil.returnSuccess();
  result.put("orderId", orderId); 
  return result;
  ```

### Anti-pattern: Silent Failure (Swallowing Exceptions)
**Pitfall**: Catching exceptions and returning a success message without actually handling or reporting the failure.
- **❌ Bad**:
  ```java
  try {
      processOrder();
  } catch (Exception e) {
      // Swallowed! Caller thinks it worked.
  }
  return ServiceUtil.returnSuccess();
  ```
- **✅ Good**: 
  ```java
  try {
      processOrder();
  } catch (Exception e) {
      Debug.logError(e, MODULE);
      return ServiceUtil.returnError("Processing Failed: " + e.getMessage());
  }
  ```

### Anti-pattern: Direct JDBC Queries
**Pitfall**: Bypassing the `Delegator` for raw JDBC (`java.sql.Connection`). This breaks caching, service triggers (ECAs), database independence, and standard OFBiz transaction synchronization.
- **❌ Bad (Raw SQL)**:
  ```java
  // Bypasses framework; breaks caching and portability
  Connection conn = DriverManager.getConnection(url); 
  Statement stmt = conn.createStatement();
  stmt.executeUpdate("UPDATE PRODUCT SET PRODUCT_NAME='...' WHERE PRODUCT_ID='...' ");
  ```
- **✅ Good (Use Delegator)**: 
  ```java
  // Maintains caching, security, and transaction integrity
  GenericValue product = EntityQuery.use(delegator).from("Product")
          .where("productId", id).queryOne();
  product.set("productName", name);
  product.store();
  ```

### Other Bad Practices
- **Java for simple CRUD**: Use the `entity-auto` engine instead for simple create/update operations.
- **Hardcoding UI Strings**: Returning inline string literals prevents internationalization. Use `UtilProperties.getMessage`.
- **Assuming Optional Parameter Types**: Never assume `context.get("param")` exists without an `isEmpty()` validation just because it is `optional="true"`.
- **Mixing Loop-of-Death**: Refer to **[manage-java-patterns](../manage-java-patterns/SKILL.md)** for batching and background job deferral.

## Common Code Patterns (Examples)
**Example: Advanced Java Service Implementation**
```java
public static Map<String, Object> updateExampleStatus(DispatchContext dctx, Map<String, ? extends Object> context) {
    Delegator delegator = dctx.getDelegator();
    LocalDispatcher dispatcher = dctx.getDispatcher();
    String exampleId = (String) context.get("exampleId");
    String statusId = (String) context.get("statusId");
    Locale locale = (Locale) context.get("locale");
    Map<String, Object> result = ServiceUtil.returnSuccess();
    
    // Explicit Validation instead of just relying on exceptions
    if (UtilValidate.isEmpty(exampleId)) {
        return ServiceUtil.returnError(UtilProperties.getMessage("MyUiLabels", "ExampleIdMissing", locale));
    }
    
    try {
        GenericValue example = EntityQuery.use(delegator).from("Example")
                .where("exampleId", exampleId).queryOne();
        if (example == null) {
            return ServiceUtil.returnError(UtilProperties.getMessage("MyUiLabels", "NotFound", locale));
        }
        
        example.set("statusId", statusId);
        example.store();
        
        // Hypothetical explicit sub-service invocation verification
        Map<String, Object> subCtx = UtilMisc.toMap(
            "userLogin", context.get("userLogin"),
            "locale", locale,
            "timeZone", context.get("timeZone"),
            "exampleId", exampleId,
            "statusId", statusId
        );
        Map<String, Object> notifyResult = dispatcher.runSync("notifyStatusChange", subCtx);
        if (ServiceUtil.isError(notifyResult)) {
            return ServiceUtil.returnError(ServiceUtil.getErrorMessage(notifyResult));
        }
        
    } catch (GenericEntityException | GenericServiceException e) {
        Debug.logError(e, "Error updating status for Example: " + exampleId, MODULE);
        String errMsg = UtilProperties.getMessage("CommonUiLabels", "CommonSystemError", locale);
        return ServiceUtil.returnError(errMsg);
    }
    
    return result;
}
```
