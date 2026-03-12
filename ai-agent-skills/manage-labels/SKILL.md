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
name: manage-labels
description: Define and maintain UI label properties for internationalization (i18n).
---

# Skill: manage-labels
## Goal
Define and maintain display text, messages, and titles in a localized manner using OFBiz UI Label files.

## Triggers
**ALWAYS** read this skill when:
- Adding new UI elements (screens, forms, menus) that require descriptive text.
- Modifying XML files in `config/` directory (e.g., `*UiLabels.xml`).

## Use when
- Creating multi-lingual applications.
- Refactoring hardcoded strings into standardized property maps.

## Procedure
1. **File Location**: 
    - UI Labels are typically found in the `config/` directory of a component.
    - Check `ofbiz-component.xml` to ensure the `config/` directory is in the classpath.
2. **Label Definition**:
    - Use `<property key="...">` for each label.
    - **Alphabetical Order**: Maintain property keys in alphabetical order to make searching and sorting easy.
    - Provide values for different languages using `<value xml:lang="...">`.
3. **Usage in Widgets**:
    - Access labels in Screen, Form, or Menu widgets using `${uiLabelMap.LabelKey}`.
4. **Usage in Scripts/Java**:
    - Use `UtilProperties.getMessage(resource, "LabelKey", locale)` in Java.
    - Use `uiLabelMap.LabelKey` directly in Groovy scripts when the map is available in the context.

## Guardrails
- **Naming**: Property keys should be descriptive and use PascalCase (e.g., `PageTitleEditExample`).
- **Resource Loading**: Ensure the resource map is loaded in the screen actions via `<property-map resource="...UiLabels" map-name="uiLabelMap" global="true"/>`.
- **Consistency**: Check `CommonUiLabels` in the `common` component for generic terms (e.g., `CommonSave`, `CommonCancel`) before creating new ones.
- **Ordering**: Always keep labels sorted alphabetically by key within the XML file.

## Examples
**Example: UI Label Definition (Alphabetically Sorted)**
```xml
<resource xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <property key="ExampleExampleId">
        <value xml:lang="en">Example ID</value>
        <value xml:lang="fr">ID d'exemple</value>
    </property>
    <property key="PageTitleEditExample">
        <value xml:lang="en">Edit Example</value>
        <value xml:lang="fr">Modifier l'exemple</value>
    </property>
</resource>
```
