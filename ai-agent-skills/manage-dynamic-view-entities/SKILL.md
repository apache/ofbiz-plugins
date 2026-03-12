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
name: manage-dynamic-view-entities
description: Programmatically build complex queries using the DynamicViewEntity API for runtime query variability.
---

# manage-dynamic-view-entities

Use `DynamicViewEntity` when the join structure, selected aliases, or aggregation must be built at runtime. Prefer explicit aliases, add conditions only for aliases that exist in the dynamic view, and use group-by correctly when aggregate functions are present.

Programmatically build complex queries using the `DynamicViewEntity` API. This is essential when query structures (joins, aliases) depend on runtime parameters that cannot be statically defined in `entitymodel.xml`.

## Triggers
- Dynamic search screens with optional joins based on user input.
- Reporting tools that allow selecting arbitrary fields or grouping criteria at runtime.
- Scenarios requiring "self-joins" (aliasing the same entity multiple times).
- Projections or aggregations that vary based on logic.

## Static vs. Dynamic View Entities

| Feature | Static View Entity (XML) | Dynamic View Entity (Java/Groovy) |
| :--- | :--- | :--- |
| **Visibility** | Visible in Webtools; pre-parsed; cached | Scoped to logic; minor runtime overhead |
| **Flexibility** | Fixed join structure | Conditional joins/aliases based on input |
| **Reuse** | Highly reusable across services/screens | Ad-hoc, used and thrown away |
| **Preference** | **Primary Choice** for stable/reusable logic | Use only for **actual runtime variability** |

## Best Practices
- **Resource Scoping**: Keep `DynamicViewEntity` objects within the method scope; they are designed for immediate use.
- **Explicit Aliases**: Prefer explicit `addAlias` calls over `addAliasAll` when joining multiple entities to avoid field name collisions and over-selection.
- **Minimal Joins**: Only join tables strictly necessary for the active logic path.
- **Alias Safety**: Always ensure that `EntityCondition` and `orderBy` clauses reference the **aliases** exposed by the view, not the underlying entity field names.

## Procedure

### 1. Initialize DynamicViewEntity
Instantiate the container.

### 2. Add Member Entities
Use unique aliases for each member. You can add the same entity multiple times with different aliases (Self-Join).

### 3. Add Aliases (Projections)
Explicitly select fields. Use `addAliasAll` only for the base entity when field overlap is not a concern.

### 4. Create View Links (Joins)
The `relOptional` (Boolean) argument determines the join type:
- `Boolean.FALSE`: Inner Join (default behavior).
- `Boolean.TRUE`: Left Outer Join (preserves parent rows if related row is missing).

Use `ModelKeyMap.makeKeyMapList("fieldId")` as a convenience for standard field-to-field mapping.

### 5. Execute Query
Choose the correct result set handler:
- `queryList()`: For small to medium result sets.
- `queryIterator()`: For large results (streaming). **Must be closed** in a `finally` block or try-with-resources.

## Guardrails
1. **Alias Safety**: Only reference active aliases in conditions and sorting. Reference by the name exposed in `addAlias`.
2. **Explicit Aliases**: Always prefer explicit aliases over `addAliasAll` when joining multiple entities.
3. **Aggregation Grouping**: When using aggregate functions (sum, count, etc.), every non-aggregated field must have `groupBy="true"`.
4. **Static Preference**: If the query structure is stable and reused, prefer a static `<view-entity>` in XML.
5. **Unique Member Aliases**: Use unique aliases for every member entity, especially for self-joins.
6. **Resource Management**: Always close `EntityListIterator` properly.
7. **No Caching**: Dynamic views are NOT cached by the entity engine.
8. **Distinct**: Use `.distinct()` on `EntityQuery` if your join results in duplicates.
9. **PK Mapping**: Ensure the `ModelKeyMap` accurately reflects the PK/FK relationship.

## Anti-Patterns
- **❌ Cartesian Products**: Forgetting `addViewLink` between members causes a cross-join.
- **❌ Infinite Joins**: Adding too many member entities leads to massive, unperformant SQL.
- **❌ Dead Conditions**: Applying an `EntityCondition` on an alias that was not added to the `DynamicViewEntity`.
- **❌ Field Overwrites**: Using the same alias name for different fields without unique `name` parameters.

## Examples

### Positive Patterns (Good)

#### 1. Standard Join with Alias Safety and Distinct
Covers: Procedure 1-5, Guardrails 1, 2, 8, 9.
```java
DynamicViewEntity dve = new DynamicViewEntity();
dve.addMemberEntity("OH", "OrderHeader");
dve.addMemberEntity("OT", "OrderType");

// Join using convenience method and proper PK/FK mapping
dve.addViewLink("OH", "OT", Boolean.FALSE, ModelKeyMap.makeKeyMapList("orderTypeId"));

// Explicit aliases to avoid over-selection
dve.addAlias("OH", "orderId", "id", null, null, null, null);
dve.addAlias("OT", "description", "orderTypeDesc", null, null, null, null);

// Condition references exposed ALIAS "id", not "orderId"
EntityCondition cond = EntityCondition.makeCondition("id", "10000");

List<GenericValue> list = EntityQuery.use(delegator)
    .from(dve)
    .where(cond)
    .distinct(true) 
    .queryList();
```

#### 2. Conditional Query with Optional Joins
Covers: Guardrail 1 (logic safety) and Procedure 4 (`relOptional`).
```java
if (UtilValidate.isNotEmpty(partyId)) {
    // Left Outer Join: Boolean.TRUE
    dve.addMemberEntity("BILL_TO", "OrderRole");
    dve.addViewLink("OH", "BILL_TO", Boolean.TRUE, ModelKeyMap.makeKeyMapList("orderId"));
    dve.addAlias("BILL_TO", "partyId", "billToPartyId", null, null, null, null);
}

List<EntityCondition> conds = new ArrayList<>();
// Best Practice: Only apply condition if the alias was actually added to the DVE
if (dve.getMemberModelMemberEntities().containsKey("BILL_TO")) {
    conds.add(EntityCondition.makeCondition("billToPartyId", partyId));
    conds.add(EntityCondition.makeCondition("roleTypeId", "BILL_TO_CUSTOMER"));
}
```

#### 3. Self-Join (Multiple Aliases for Same Entity)
Covers: Guardrail 5 and Procedure 2.
```java
// Join OrderRole twice for different purposes
dve.addMemberEntity("SHIP_TO_ROLE", "OrderRole");
dve.addMemberEntity("BILL_TO_ROLE", "OrderRole");

dve.addViewLink("OH", "SHIP_TO_ROLE", Boolean.FALSE, ModelKeyMap.makeKeyMapList("orderId"));
dve.addViewLink("OH", "BILL_TO_ROLE", Boolean.FALSE, ModelKeyMap.makeKeyMapList("orderId"));

dve.addAlias("SHIP_TO_ROLE", "partyId", "shipToPartyId", null, null, null, null);
dve.addAlias("BILL_TO_ROLE", "partyId", "billToPartyId", null, null, null, null);
```

#### 4. Aggregation and Grouping
Covers: Guardrail 3.
```java
DynamicViewEntity dve = new DynamicViewEntity();
dve.addMemberEntity("OI", "OrderItem");

// Every non-aggregated field MUST have groupBy=true
dve.addAlias("OI", "productId", "productId", null, null, Boolean.TRUE, null);
dve.addAlias("OI", "quantity", "totalQuantity", null, null, null, "sum");

List<GenericValue> summary = EntityQuery.use(delegator).from(dve).queryList();
```

#### 5. Complex Expressions (ComplexAlias)
```java
import org.apache.ofbiz.entity.model.ModelViewEntity.ComplexAlias;
import org.apache.ofbiz.entity.model.ModelViewEntity.ComplexAliasField;

ComplexAlias lineTotal = new ComplexAlias("*");
lineTotal.addComplexAliasMember(new ComplexAliasField("OI", "unitPrice", null, null));
lineTotal.addComplexAliasMember(new ComplexAliasField("OI", "quantity", null, null));
dve.addAlias(null, "lineTotal", null, null, null, null, null, lineTotal);
```

#### 6. Resource Management (Iterator)
Covers: Guardrail 6 and Procedure 5.
```java
try (EntityListIterator eli = EntityQuery.use(delegator).from(dve).queryIterator()) {
    GenericValue value;
    while ((value = eli.next()) != null) {
        // process streaming results
    }
}
```

### Negative Patterns (Bad)

#### ❌ Cartesian Product (Missing Join)
```java
dve.addMemberEntity("OH", "OrderHeader");
dve.addMemberEntity("OI", "OrderItem");
// BUG: Missing addViewLink("OH", "OI", ...) causes DB crash on large tables
```

#### ❌ Dead Condition (Missing Member)
```java
// CRASH PROBABLE: "productId" alias doesn't exist if someFlag is false
if (someFlag) {
    dve.addMemberEntity("OI", "OrderItem");
    dve.addAlias("OI", "productId");
}
EntityCondition cond = EntityCondition.makeCondition("productId", "foo"); 
List<GenericValue> list = EntityQuery.use(delegator).from(dve).where(cond).queryList();
```

#### ❌ Infinite Joins (Over-Joining)
```java
// BUG: Joining too many entities (e.g., 10+) leads to timeouts
dve.addMemberEntity("E1", "Table1");
// ... add 9 more member entities ...
dve.addMemberEntity("E10", "Table10");
// RECOMMENDATION: Materialize as Database View or break into multiple queries
```

#### ❌ Field Overwrite (Duplicate Alias)
```java
dve.addAlias("OH", "statusId");
dve.addAlias("OT", "statusId"); // Overwrites OH.statusId; OH value becomes inaccessible
```
