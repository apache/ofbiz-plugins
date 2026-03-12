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
name: coding-standards
description: Guidelines for writing production-quality code, focusing on commenting style, cleanliness, and professionalism.
---

# Skill: coding-standards
## Goal
Ensure all OFBiz contributions meet production-grade standards for clarity, maintainability, and architectural alignment.

## Triggers
**ALWAYS** read this skill when:
- Writing any code (Java, Groovy, XML, CSS).
- Reviewing PRs or auditing existing code for OFBiz alignment.
- Using OFBiz utility libraries for validation and map creation.
- Preparing a pull request or implementing new features.

## General Rules
1. **Explain the WHY**: Code shows *what* happens. Comments must explain *why* (e.g., explaining a workaround for a legacy constraint).
2. **No Internal Monologue**: Delete "thinking out loud" or "maybe this works" comments.
3. **Guard against Technical Debt**: Avoid `// TODO` without a tracking reference.
4. **Remove Debug Code**: Clean up `System.out.println`, `println`, or excessive `Debug.logInfo` before completion.

## OFBiz Specific Standards
1. **XML Actions**: Keep logic flat. If it requires complex loops or nesting, move to Groovy.
2. **Naming Conventions**:
    - **Components**: `kebab-case` (e.g., `order-management`).
    - **Entities/Services**: `UpperCamelCase` for entities, `lowerCamelCase` for services.
    - **Java/Groovy**: `lowerCamelCase` for methods/variables, `UpperCamelCase` for classes.
3. **Documentation**:
    - **Services**: Every service MUST have a `<description>` in `services.xml`.
    - **Java/Groovy**: Use Javadoc/Groovydoc for public methods and complex logic.
4. **Utility Libraries**:
    - **Validation**: ALWAYS use `UtilValidate.isEmpty()` or `isNotEmpty()`.
    - **Maps/Lists**: Use `UtilMisc.toMap(...)` and `UtilMisc.toList(...)` for concise creation.
    - **Logging**: Use `Debug.logInfo(...)`, `Debug.logError(...)` with a `MODULE` tag.
    - Use `.filterByDate()` for all date-effective entities (e.g., `ProductPrice`, `StatusItem`).
    - Prefer `EntityQuery` over `delegator.findOne(...)`.
5. **Internationalization (i18n)**:
    - Never hardcode user-facing strings. Use `uiLabelMap.LabelKey`.

## Guardrails
- **Consistency**: Follow the existing indentation and naming conventions of the component you are editing.
- **Transactional Integrity**: Respect OFBiz service boundaries; don't commit transactions manually.

## Examples
**Example: Clean documentation**
```java
// Check if the product is active based on current system time.
// We use filterByDate to ensure we only get valid price records.
GenericValue price = EntityQuery.use(delegator).from("ProductPrice")
        .where("productId", productId)
        .filterByDate()
        .queryFirst();
```
