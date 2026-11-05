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
  Write and maintain OFBiz service logic in MiniLang (XML). Use when
  - Writing a new `engine="simple"` service.
  - Modifying or debugging existing MiniLang.
  - Converting MiniLang to Groovy, when asked to.
---

# Skill: manage-minilang (v1.0)

## Goal
Write and maintain OFBiz service logic in MiniLang (XML) that follows what the MiniLang engine actually does.

## Triggers
**ALWAYS** read this skill when:
- User asks to modify `.xml` files in `minilang/` or `script/` directories (excluding services/entities).
- Debugging `engine="simple"` services.

## Use when
- Writing new service logic in a project that uses MiniLang.
- Maintaining existing OFBiz core logic.

> [!IMPORTANT]
> **MiniLang is a supported service engine in trunk. The language is not deprecated.**
> - Some elements and attributes are deprecated. The engine reports them when it parses
>   a file, and fails under `validation.level=strict` (`SimpleMethod.java`,
>   `MiniLangValidate.java`, `simple-methods.xsd`).
> - Groovy and MiniLang are both valid for new logic. Follow the project's choice.
> - Do not convert MiniLang to Groovy unless asked.

## Procedure
1. **Locate**: Find the `<simple-method>` in a `script/.../*.xml` file.
2. **Define**: Define logic using standard tags: `<entity-one>`, `<entity-and>`, `<set>`, `<call-service>`.
3. **Register**: Ensure the script is registered in `servicedef/services.xml`:
   ```xml
   <service name="legacyService" engine="simple" location="component://[comp]/minilang/.../[file].xml" invoke="[methodName]">
   ```

## Guardrails
- **Deprecated Elements**: Never use an element or attribute that `simple-methods.xsd` marks deprecated.
- **Service Security**: `auth="true"` is required at the service definition, not within MiniLang itself.
- **Complex Logic**: Avoid deeply nested `<if>` or `<loop>` tags in MiniLang; split the method by business step instead.
- **Variable Scoping**: Be aware that variables created with `<set>` are local to the `<simple-method>` unless explicitly mapped to `parameters`.
- **Declared Entries Are Copies**: When a `List` or `Map` in-parameter declares child attributes, the dispatcher gives the service a new map for each entry (`ModelService.makeValid`). Set fields on the entry and pass it to `<call-service>` as it is; do not copy it first. A `List` with no child attributes holds the caller's own maps.

## Examples
**Example: Simple Status Update**
```xml
<simple-method method-name="updateProductStatus">
    <entity-one entity-name="Product" value-field="product"/>
    <set field="product.statusId" from-field="parameters.statusId"/>
    <store-value value-field="product"/>
</simple-method>
```
