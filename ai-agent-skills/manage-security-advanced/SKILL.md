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
name: manage-security-advanced
description: |
  Manage OFBiz access control using permissions, security groups, service-level authorization, and business data scoping. OFBiz security is granular and permission-driven, but secure implementations must also protect services, requests, APIs, and record-level access. Use when:
  - Implementing access controls for controllers, services, and screens.
  - Differentiating between module-level and record-level (data scoped/multi-tenant) permissions.
  - Designing security groups, permissions, roles, and enforcing separation of duties.
---

## Goal
Implement a defense-in-depth security model in OFBiz by prioritizing Service-Level validation and strict data scoping, ensuring UI-level checks are treated merely as UX conveniences.

## Triggers
**ALWAYS** read this skill when:
- **New Access Control**: Creating applications requiring authentication or authorization.
- **Roles & Groups**: Defining user roles, security groups, or permissions in seed data.
- **Data Isolation**: Implementing logic that requires multi-tenant or record-level data scoping.
- **Exposing Endpoints**: Adding controller request-maps, APIs, or background jobs.

> [!TIP]
> If you are configuring password strengths, host headers, or moving a server to production, see the [manage-security-deployment](../manage-security-deployment/SKILL.md) skill instead.

## Core Concepts

### 1. The OFBiz Trust Model
- **Administrative Trust Boundary**: OFBiz does **not** sandbox administrators from the host OS JVM. Any user with administrative rights is considered fully trusted.
- **Plugin & Extension Trust**: All custom extensions and plugins (including `ecommerce`) execute with the same privileges as the core framework.
- **OS Privilege Boundary**: The application must be run by a dedicated, restricted OS user account, as OFBiz lacks internal privilege separation.

### 2. Authentication vs Authorization
- **Authentication**: Verifying *who* the user is (`userLogin`). Controllers must require authentication.
- **Authorization**: Verifying *what* the user can do (`hasEntityPermission`).

### 3. The Two Levels of OFBiz Authorization
1. **Functionality/Module-Level Security**: Broad permissions tied purely to the `SecurityGroup` (e.g., `ORDERMGR_VIEW`). It dictates if a user can access a module feature generally.
2. **Record-Level Access (Data Scoping)**: Row-level security combining a module check with a business data query. For example, verifying a user has `ORDERMGR_ROLE_VIEW` *AND* ensuring they are actively associated with a specific Order via `OrderRole`. `hasEntityPermission` does **not** grant safe access to *every* record across the system.

### 4. Auditability
Always preserve the `userLogin` object in the service context so the framework correctly tracks `createdByUserLogin` and `lastModifiedByUserLogin`.

## Procedure

### 1. The Security Hierarchy Checklist
When evaluating access control, **always think in this exact order**:
1. **Who is the user?** Identity validation (`auth="true"`).
2. **What module permission do they have?** Functionality authorization.
3. **Should they access this specific record?** Business Data Scoping (ownership boundary).
4. **Is the service protected?** The absolute core layer of defense.
5. **Is every entry point protected?** Controller endpoints, APIs, and events.

### 2. Service-Level Security (Primary Defense)
The service layer is the absolute core. Even if UI buttons are hidden, if a request event invokes logic, the underlying service MUST enforce the permission. 
- Protect EVERY mutating service and EVERY sensitive read service.
- Centralize permission checks into reusable `<permission-service>` helpers.

### 3. Controller/Request Protection
Prevent anonymous access to internal logic at the HTTP boundary. URLs and direct events should never bypass login (`auth="true"` and `https="true"`).

### 4. Defining Groups & Seed Data
- **Scope Groups**: Create granular groups like `ORDER_ENTRY_CLERK` instead of generic admin buckets.
- **Separate Duties**: Approvals must not be handled by the same broad group that creates the transactions.
- **Seed vs Demo Data**: Foundational security permissions must reside in `seed` data. Never assume demo users (`admin`) exist in production environments.

## Guardrails

- **Service-first security**: Enforce access at the service layer; never rely only on screens or menus.
- **Least privilege**: Grant only the minimum modular permissions required for the job.
- **Scope matters**: Permission to view/update a module does not automatically grant access to all records in that module (Data Scoping).
- **No hardcoded admins**: Never hardcode user IDs, group IDs, or bypasses in business logic.
- **Use reusable checks**: Prefer centralized `<permission-service>` implementations for complex checks.
- **Protect entry points**: Secure controller request-maps, events, services, and APIs consistently.
- **Audit sensitive actions**: Ensure important security-sensitive actions remain attributable to an authenticated `userLogin`.
- **Separate Infrastructure**: Do not configure `security.properties` or Tomcat headers here. Refer to the [manage-security-deployment](../manage-security-deployment/SKILL.md) skill.

## Examples

### 1. Centralized Service Protection (XML)
Modularize permission logic into reusable helper services to protect business logic.

```xml
<!-- Reusable permission service -->
<service name="orderManagerPermissionCheck" engine="java" ...> ... </service>

<!-- Protecting a mutating service -->
<service name="createOrder" engine="java" ...>
    <permission-service service-name="orderManagerPermissionCheck" main-action="CREATE"/>
</service>
```

### 2. Programmatic Module Check (Groovy/Java)
For ad-hoc logic or when checking permissions manually inside a service implementation.

```java
// Check basic module functionality permission
if (!security.hasEntityPermission("ORDERMGR", "_CREATE", userLogin)) {
    return ServiceUtil.returnError("You do not have permission to create orders.");
}
```

### 3. Data Scoping / Record-Level Security (Java)
Combine a broad module-level check with a strict business data query to enforce multi-tenant or ownership boundaries.

```java
public Map<String, Object> viewVendorOrder(DispatchContext dctx, Map<String, ?> context) {
    GenericValue userLogin = (GenericValue) context.get("userLogin");
    String orderId = (String) context.get("orderId");

    // 1. Module-Level Check
    if (!security.hasEntityPermission("ORDERMGR", "_VIEW", userLogin)) {
        return ServiceUtil.returnError("Permission denied.");
    }

    // 2. Data Scoping Check (Business Ownership Boundary)
    long relatedOrderCount = EntityQuery.use(delegator).from("OrderRole")
        .where("orderId", orderId, "partyId", userLogin.getString("partyId"), "roleTypeId", "VENDOR")
        .queryCount();

    if (relatedOrderCount == 0) {
        return ServiceUtil.returnError("Security Violation: You do not have organization access to this order.");
    }
    // Proceed...
}
```

### 4. Controller Protection (XML)
Ensure public/unauthenticated requests cannot trigger internal logic.

```xml
<!-- Enforces login and SSL -->
<request-map uri="createOrderEndpoint">
    <security https="true" auth="true"/>
    <event type="service" invoke="createOrder"/>
    <response name="success" type="view" value="OrderSummary"/>
</request-map>
```

### 5. UI Visibility as UX Convenience (XML)
Use `<if-has-permission>` strictly to prevent showing dead links. **This is not a security boundary.**

```xml
<screen name="ViewOrder">
    <section>
        <condition>
            <!-- Check permission before rendering the link -->
            <if-has-permission permission="ORDERMGR" action="_VIEW"/>
        </condition>
        <widgets>
            <!-- Render the link -->
        </widgets>
    </section>
</screen>
```

## Anti-Patterns

- **❌ Hardcoding User IDs or Roles**: Never grant bypasses based on specific user IDs. Permissions must come from the defined security model.
  ```java
  // BAD: Hardcoding bypassing security logic
  if ("admin".equals(userLogin.getString("userLoginId"))) { ... }

  // GOOD: Using the OFBiz Security engine
  if (security.hasEntityPermission("SYSTEM", "_ADMIN", userLogin)) { ... }
  ```

- **❌ Unprotected Mutating Services**: Exposing `java`/`groovy` services without a `<permission-service>` mapping or an internal `security` check.
  ```xml
  <!-- BAD: Mutating service without permission boundary -->
  <service name="deleteDangerousData" engine="java" default-entity-name="SystemData" .../>
  ```

- **❌ Relying solely on UI Hiding**: Assuming an action is secure just because the screen `<link>` requires a permission. A malicious user can directly POST to the `auth="false"` controller endpoint mapped to the underlying service.

- **❌ Accidental System Context Execution**: Wrapping untrusted input in a block where `userLogin` is specifically swapped to the `system` account. Elevated actions must never be casually triggered by normal user requests.
  ```java
  // BAD: Executing logic with an elevated 'system' user context without extreme care
  GenericValue systemUser = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", "system").queryOne();
  dispatcher.runSync("someSensitiveService", UtilMisc.toMap("userLogin", systemUser));
  ```

- **❌ Overuse of FULLADMIN**: Assigning standard users to `FULLADMIN` (except for true system administrators) causes uncontrolled security sprawl. Always prefer job-scoped groups (e.g., `ORDER_ENTRY_CLERK`).
