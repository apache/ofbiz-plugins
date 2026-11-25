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
name: manage-properties
description: Manage OFBiz application properties. Use for static .properties files or dynamic SystemProperty entity configurations.
---

# Skill: manage-properties
## Goal
Define and access configuration settings in OFBiz using static property files or dynamic database records.

## Triggers
**ALWAYS** read this skill when:
- Adding new configuration keys to a component.
- Overriding core OFBiz settings.
- Accessing properties from Java, Groovy, or XML.

## Use when
- Defining static defaults in `config/` directory.
- Creating runtime-configurable settings in `SystemProperty` entity.
- Using `EntityUtilProperties` or `UtilProperties` to retrieve values.
- Configuring settings a container ships with.

## Procedure
1. **Static Properties (`.properties` files)**:
    - Create/Edit files in component's `config/` directory.
    - Path: `plugins/mycomponent/config/mycomponent.properties`.
    - Overridable value: `EntityUtilProperties.getPropertyValue("mycomponent", "my.key", "default", delegator)` checks `SystemProperty` first, then falls back to the file.
    - File-only value (no delegator, or must not be changed at runtime): `UtilProperties.getPropertyValue("mycomponent", "my.key", "default")`. It never reads `SystemProperty`.
2. **Dynamic Properties (`SystemProperty` entity)**:
    - Define in data XML files.
    - Fields: `systemResourceId` (matches file basename), `systemPropertyId` (key), `systemPropertyValue` (value), `systemPropertyLookup` (secret key, resolved like `LOOKUP(key)`).
    - Advantage: Can be updated via DB/UI without restart, but only for code that reads through `EntityUtilProperties`.
3. **Overriding Core Properties**:
    - Avoid editing core framework property files.
    - Use `SystemProperty` records to override static values only where the code reads them with `EntityUtilProperties`.
4. **Container Settings (`ofbiz-component.xml`)**:
    - Settings a container ships with go as `<property>` children of its `<container>` element, not in a `.properties` file.
    - Read them with `ContainerConfig.getConfiguration(name)` and `ContainerConfig.getPropertyValue(cfg, "key", default)` (see the commented `javamail-container` in `framework/service/ofbiz-component.xml`).

## Guardrails
- **Uniqueness**: Prefix property keys with component name to avoid collisions (e.g., `shop.order.limit`).
- **Basename**: `systemResourceId` must match the `.properties` file name if providing dynamic overrides for static defaults.
- **`LOOKUP(key)`**: Resolved in `.properties` files, but not in `systemPropertyValue`. For a `SystemProperty`, put the key in `systemPropertyLookup` instead.
- **Secrets**: Never store passwords, tokens or keys as a `systemPropertyValue`: it is plain text in the DB and visible in Webtools. Use `LOOKUP(key)` in the file or `systemPropertyLookup`.

## Examples
**Example: Static Property File (`myplugin.properties`)**
```properties
# Cache setting for the plugin
myplugin.cache.enabled=true
myplugin.max.items=50
```

**Example: Dynamic Overrides (Data XML)**
```xml
<entity-engine-xml>
    <!-- Override the static default at runtime -->
    <SystemProperty systemResourceId="myplugin" systemPropertyId="myplugin.max.items" 
                    systemPropertyValue="100" description="Maximum items override"/>
</entity-engine-xml>
```

**Example: Accessing in Java**
```java
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.entity.util.EntityUtilProperties;

// SystemProperty (myplugin / myplugin.max.items) if it exists, else myplugin.properties, else "50"
String maxItems = EntityUtilProperties.getPropertyValue("myplugin", "myplugin.max.items", "50", delegator);

// myplugin.properties only; SystemProperty is never consulted
String cacheEnabled = UtilProperties.getPropertyValue("myplugin", "myplugin.cache.enabled", "true");
```
