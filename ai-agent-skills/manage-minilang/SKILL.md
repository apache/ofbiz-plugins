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
name: manage-minilang
description: |
  Manage legacy business logic in OFBiz MiniLang (XML). Use when
  - Modifying legacy service workflows defined in XML.
  - Troubleshooting existing MiniLang codebases.
  - Migrating MiniLang logic to Groovy (Highly Recommended).
---

# Skill: manage-minilang (v1.0)

## Goal
Manage legacy business logic in OFBiz MiniLang (XML) while ensuring a path toward modernization.

## Triggers
**ALWAYS** read this skill when:
- User asks to modify `.xml` files in `minilang/` or `script/` directories (excluding services/entities).
- Debugging `engine="simple"` services.

## Use when
- Maintaining existing OFBiz core logic.
- Implementing very simple CRUD sequences (if required by project conventions).

> [!IMPORTANT]
> **MiniLang is DEPRECATED in modern OFBiz versions.**
> - Use Groovy for all new business logic.
> - Only use MiniLang for maintenance of existing legacy code.

## Procedure
1. **Locate**: Find the `<simple-method>` in a `script/.../*.xml` file.
2. **Define**: Define logic using standard tags: `<entity-one>`, `<entity-and>`, `<set>`, `<call-service>`.
3. **Register**: Ensure the script is registered in `servicedef/services.xml`:
   ```xml
   <service name="legacyService" engine="simple" location="component://[comp]/minilang/.../[file].xml" invoke="[methodName]">
   ```

## Guardrails
- **Deprecation**: Never start NEW development in MiniLang.
- **Service Security**: `auth="true"` is required at the service definition, not within MiniLang itself.
- **Complex Logic**: Avoid deeply nested `<if>` or `<loop>` tags in MiniLang; this is a sign it should be migrated to Groovy.
- **Variable Scoping**: Be aware that variables created with `<set>` are local to the `<simple-method>` unless explicitly mapped to `parameters`.

## Examples
**Example: Simple Status Update**
```xml
<simple-method method-name="updateProductStatus">
    <entity-one entity-name="Product" value-field="product"/>
    <set field="product.statusId" from-field="parameters.statusId"/>
    <store-value value-field="product"/>
</simple-method>
```
