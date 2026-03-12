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
name: manage-menus
description: Define and maintain navigation menus using the OFBiz Menu Widget XML.
---

# Skill: manage-menus
## Goal
Define and maintain application navigation bars and sub-menus using the OFBiz Menu Widget XML.

## Triggers
**ALWAYS** read this skill when:
- Creating or modifying XML files in `widget/` directory (e.g., `*Menus.xml`).
- Adding new navigation items or modifying menu hierarchies.

## Use when
- Designing the main application header menu.
- Creating contextual sub-menus (e.g., tab bars for specific entities).
- Implementing conditional visibility for menu items based on permissions or data state.

## Procedure
1. **Menu Structure**:
    - Use `<menu>` as the root element with a unique name.
    - Set `default-menu-item-name` to highlight the active tab by default.
2. **Menu Items**:
    - Use `<menu-item>` for each link.
    - Add a `<link>` child element to define the `target` (request URI) and `text` (label).
3. **Conditional Visibility**:
    - Use `<condition>` blocks within `<menu-item>`.
    - Check permissions using `<if-has-permission>` or attribute states using `<if-compare>`.
4. **Integration**:
    - Include menus in screens using `<include-menu name="..." location="..."/>` within a decorator or screen.

## Guardrails
- **Naming**: Menu names should be PascalCase, and item names should be camelCase.
- **Labels**: Reference all menu item text via `${uiLabelMap.LabelName}`.
- **Paths**: Use standard `component://` paths for included menu locations.
- **Styling**: Use `selected-style` to visually distinguish the active menu item.

## Examples
**Example: Menu with Permission-based Items**
```xml
<menu name="ExampleTabBar" extends="CommonTabBarMenu" extends-resource="component://common/widget/CommonMenus.xml">
    <menu-item name="EditExample" title="${uiLabelMap.ExampleExample}">
        <link target="EditExample">
            <parameter param-name="exampleId"/>
        </link>
    </menu-item>
    <menu-item name="EditExampleItems" title="${uiLabelMap.ExampleExampleItems}">
        <condition>
            <if-has-permission permission="EXAMPLE" action="_VIEW"/>
        </condition>
        <link target="EditExampleItems">
            <parameter param-name="exampleId"/>
        </link>
    </menu-item>
</menu>
```
