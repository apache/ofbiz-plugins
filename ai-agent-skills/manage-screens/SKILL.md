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
name: manage-screens
description: Define and maintain UI screens using the OFBiz Screen Widget XML.
---

# Skill: manage-screens
## Goal
Define and maintain complex UI screens using the OFBiz Screen Widget XML.

## Triggers
**ALWAYS** read this skill when:
- Creating or modifying XML files in `widget/` directory (e.g., `*Screens.xml`).
- Registering screen resources in `ofbiz-component.xml`.
- Defining new request-to-view mappings in `controller.xml`.

## Use when
- Developing new UI features or custom dashboards.
- Modifying existing layouts or adding new containers/sections.
- Integrating decorators for consistent styling and sub-menu logic.

## Procedure
1. **Research & Inheritance**: 
    - Identify existing screens to extend or use as templates.
    - Check `CommonScreens.xml` for standard decorators (`main-decorator`, `CommonExampleDecorator`).
2. **Decorator Pattern**: 
    - Use `<decorator-screen>` to wrap screens. 
    - Populate sections like `body`, `pre-body`, or `menu-bar` using `<decorator-section name="...">`.
    - Use `<decorator-section-include>` in the decorator definition itself to mark insertion points.
3. **Register**: Ensure the widget XML is registered in `ofbiz-component.xml`:
   ```xml
   <screen-resource type="model" loader="main" location="component://[comp]/widget/[File]Screens.xml"/>
   ```
3. **Action Section (Complex)**: 
    - **Data Fetching**: Use `<entity-one>`, `<entity-and>`, `<entity-condition>`.
    - **Logic**: Use `<script location="..."/>` for complex Groovy logic.
    - **Service Calls**: Use `<service service-name="..." result-map="..."/>`.
    - **Parameters**: Use `<set field="..." from-field="parameters.xyz"/>`.
4. **Widget Section (Fully Loaded)**:
    - **Conditional Logic**: Use `<section><condition><if-compare ...>` or `<if-has-permission ...>`.
    - **Layout**: Use `<container>`, `<screenlet>`, `<horizontal-separator>`.
    - **Links & Icons**: Use `<link target="..." text="..." style="..." image-location="..."/>`.
    - **Platform Specific**: Use `<platform-specific><xsl-fo>` for PDF or `<html-template>` for raw FTL.
5. **Styles and Scripts (UI Enhancement)**:
    - **Global/Header Inclusion**: Use `<set field="layoutSettings.javaScripts[]" value="..." global="true"/>` and `<set field="layoutSettings.styleSheets[]" value="..." global="true"/>` in the `<actions>` section.
    - **In-page/Late Inclusion**: Use `<platform-specific><html><html-template location="..."/></html></platform-specific>` to include FTL files that contain inline `<script>` or `<style>` tags.
    - **External Libraries**: Always prefer the `layoutSettings` mechanism for standard JS/CSS files to ensure they are properly managed by the decorator's `<head>`.
6. **Pagination & Context**: Pass `viewIndex` and `viewSize` to included forms/grids.

## Guardrails
- **Permissions**: ALWAYS include a `<condition>` block with `<if-has-permission>` at the start of the primary `<section>`.
- **Naming Conventions**: Screen names should be PascalCase (e.g., `EditExampleItems`).
- **Reuse**: Prefer `<include-screen name="..." location="..."/>` over duplicating widget blocks.
- **Paths**: ALWAYS use the `component://` protocol for locations.

## Examples
**Example: Screen with Decorator and Conditional Sub-menus**
```xml
<screen name="EditExample">
    <section>
        <actions>
            <set field="titleProperty" value="PageTitleEditExample"/>
            <set field="tabButtonItem" value="EditExample"/>
            <set field="exampleId" from-field="parameters.exampleId"/>
            <entity-one entity-name="Example" value-field="example"/>
            <!-- Include custom script for push notifications -->
            <set field="layoutSettings.javaScripts[]" value="/example/js/ExamplePushNotifications.js" global="true"/>
        </actions>
        <widgets>
            <decorator-screen name="CommonExampleDecorator" location="${parameters.mainDecoratorLocation}">
                <decorator-section name="body">
                    <section>
                        <condition>
                            <if-has-permission permission="EXAMPLE" action="_UPDATE"/>
                        </condition>
                        <widgets>
                            <screenlet title="${uiLabelMap.ExampleDetails}">
                                <include-form name="EditExample" location="component://example/widget/example/ExampleForms.xml"/>
                            </screenlet>
                        </widgets>
                        <fail-widgets>
                            <label style="h3">${uiLabelMap.ExampleUpdatePermissionError}</label>
                        </fail-widgets>
                    </section>
                </decorator-section>
            </decorator-screen>
        </widgets>
    </section>
</screen>
```
