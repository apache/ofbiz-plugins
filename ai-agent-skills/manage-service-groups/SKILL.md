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
name: manage-service-groups
description: Orchestrate multiple services using the OFBiz Group Engine for sequential or concurrent execution.
---

# Manage Service Groups

Service Groups allow you to define a single service that acts as a wrapper for multiple child services. This is ideal for orchestrating complex workflows where several independent services need to run together. (**Cross-Reference**: See the [manage-services](../manage-services/SKILL.md) skill for the foundational rules on service implementation).

## Triggers
- Executing multiple services in a specific sequence.
- Running services concurrently to improve performance.
- Wrapping multiple operations in a single transactional unit.
- Scaffolding complex integration flows (e.g., "Complete Purchase Order" which might involve receiving items, creating invoices, and posting to GL).

## Procedures

### 1. Engine vs Workflow Definitions
- **Group Engine (`engine="group"`)**: Use exclusively for straight-line sequential or concurrent fire-and-forget flows that do not have complex conditional branching or data dependencies between steps.
- **Groovy Engine (`engine="groovy"`)**: Use if your workflow requires conditional `if/else` logic, try/catch error handling, complex iteration, or complex data transformations between service steps.

### 2. Define the Group Service (`services.xml`)
In your `services.xml`, define a service using `engine="group"`. Ensure you include the `invoke` and `use-transaction` attributes. (**Cross-Reference**: See the [manage-services](../manage-services/SKILL.md) skill for comprehensive rules on service definitions, authentication, and REST exports.)

```xml
<service name="myServiceGroup" engine="group" auth="true">
    <description>Orchestrates multiple services</description>
    <attribute name="orderId" mode="IN" type="String"/>
</service>
```

### 3. Define the Group Members
Create a `groups.xml` (or similar) file to define which services belong to the group. (**Cross-Reference**: Ensure each individual member service is defined according to the rules in [manage-services](../manage-services/SKILL.md)).

- **Location**: Typically `servicedef/groups.xml`.
- **Registration**: Register in `ofbiz-component.xml`:
  ```xml
  <service-resource type="group" loader="main" location="servicedef/groups.xml"/>
  ```

#### Group Definition Pattern
```xml
<service-group name="myServiceGroup">
    <invoke name="firstService" mode="sync"/>
    <invoke name="secondService" mode="async" pause="1000"/>
    <invoke name="thirdService" mode="sync" optional="true"/>
</service-group>
```

**Attributes for `<invoke>`**:
- `name`: The service to call.
- `mode`: `sync` (sequential) or `async` (parallel/background).
- `optional`: If `true`, the group continues even if this service fails.
- `result-to-context`: If `true`, the results of this service are added to the context for subsequent services in the group.

## Guardrails
- **Transaction Behavior**: By default, a group service runs in a single transaction. If a `sync` member fails and isn't `optional="true"`, the entire transaction rolls back. (**Cross-Reference**: See the [manage-services](../manage-services/SKILL.md) skill for deep-dives into `use-transaction` versus `require-new-transaction` mapping).
- **Context collisions**: Context passing only happens when `result-to-context="true"` for `sync` steps. Context collisions are a risk here; encourage prefixing output keys or mapping results in wrapper services.
- **Async Pitfalls**: If you use `mode="async"` within a group, that service runs in its own thread/transaction. The group won't wait for it to finish unless specifically architected otherwise.
- **Service Engine**: Use `engine="group"` only for orchestration. Don't put business logic in the group definition itself.

## Anti-Patterns

### Anti-Pattern 1: Hidden Branching Logic
**Bad:** Using `optional="true"` to simulate an `if/else` block.
```xml
<group name="processPayment" send-mode="all">
    <invoke name="chargeStripe" mode="sync" optional="true"/>
    <invoke name="logPaymentSuccess" mode="sync"/>
</group>
```

**Good:** Using a Groovy orchestrator service for real conditional logic. (**Cross-Reference**: See the [manage-groovy](../manage-groovy/SKILL.md) skill)
```groovy
def stripeResult = run service: "chargeStripe", with: context
if (ServiceUtil.isError(stripeResult)) {
    logWarning("Stripe failed, trying alternative...")
} else {
    run service: "logPaymentSuccess", with: context
}
```

### Anti-Pattern 2: The `result-to-context` Trap
**Bad:** Overwriting generic context variables (e.g., `statusId`) across multiple services.
```xml
<group name="createOrderAndInvoice" send-mode="all">
    <invoke name="createOrder" mode="sync" result-to-context="true"/>
    <invoke name="createInvoice" mode="sync" result-to-context="true"/>
</group>
```

**Good:** Explicitly mapping values in a Groovy service instead of blindly sharing context.
```groovy
def orderMap = run service: "createOrder", with: orderCtx
def invoiceCtx = [orderId: orderMap.orderId, partyId: context.partyId]
def invoiceMap = run service: "createInvoice", with: invoiceCtx
```

### Anti-Pattern 3: Async Race Conditions
**Bad:** Expecting a `sync` step 2 to wait for an `async` step 1.
```xml
<group name="reportFlow" send-mode="all">
    <invoke name="generateReport" mode="async"/>
    <invoke name="emailReport" mode="sync"/>
</group>
```

**Good:** Run both as `sync` in a fire-and-forget orchestrator, OR use a SECA on `generateReport` success. (**Cross-Reference**: See the [manage-eca](../manage-eca/SKILL.md) skill)

### Anti-Pattern 4: Thread Exhaustion with `pause`
**Bad:** Putting the Tomcat web thread to sleep using `pause`.
```xml
<group name="waitAndRetry" send-mode="all">
    <invoke name="waitForExternalSystem" mode="sync" pause="5000"/>
</group>
```

**Good:** Use explicit asynchronous scheduled jobs instead of pausing the current thread. (**Cross-Reference**: See the [manage-quartz-jobs](../manage-quartz-jobs/SKILL.md) skill)

### Anti-Pattern 5: Distributed Transaction Failures
**Bad:** Mixing local database commits with external ERP REST calls where compensation is impossible.
```xml
<group name="fulfillOrder" send-mode="all">
    <invoke name="updateInventoryDB" mode="sync"/>
    <invoke name="notifyERP" mode="sync"/>
</group>
```

**Good:** Avoid doing this in Service Groups. Use manual Groovy transaction catch blocks or `global-rollback` SECAs.

### Anti-Pattern 6: Output Parameter Masking
**Bad:** Relying on Service Groups to blindly gather context and assuming the caller gets specific `OUT` parameters back correctly.

**Good:** Use a Groovy wrapper service to explicitly construct and return results.

## Examples

### Sequential Purchase Order Completion
```xml
<service-group name="completePurchaseOrder">
    <invoke name="receiveInventory" mode="sync"/>
    <invoke name="createPurchaseInvoice" mode="sync"/>
    <invoke name="postToGeneralLedger" mode="sync" optional="true"/>
</service-group>
```

### Concurrent Notifications
```xml
<service-group name="sendMassNotifications">
    <invoke name="sendEmailNotification" mode="async"/>
    <invoke name="sendSMSNotification" mode="async"/>
    <invoke name="logNotificationStats" mode="sync"/>
</service-group>
```
