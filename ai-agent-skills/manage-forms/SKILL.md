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
name: manage-forms
description: Define and maintain Form and Grid widgets using the OFBiz Form Widget XML.
---

# Skill: manage-forms
## Goal
Define and maintain data input forms and data display grids using the OFBiz Form Widget XML.

## Triggers
**ALWAYS** read this skill when:
- Creating or modifying XML files in `widget/` directory (e.g., `*Forms.xml`).
- Registering form resources in `ofbiz-component.xml`.
- Defining new forms/grids or extending existing ones.

## Use when
- Creating single-record update forms or multi-record list grids.
- Implementing AJAX-based UI updates using `on-event-update-area`.
- Reusing form structures via component inheritance.

## Procedure
1. **Inheritance & Reuse**:
    - Use `extends="..."` to inherit fields from another form.
    - Use `extends-resource="..."` to specify the location of the parent form.
2. **Form Types**:
    - `single`: For single record input/edit.
    - `list` or `grid`: For multiple records. Use `grid` (modern) over `form type="list"`.
3. **Register**: Ensure the widget XML is registered in `ofbiz-component.xml`:
   ```xml
   <form-resource type="model" loader="main" location="component://[comp]/widget/[File]Forms.xml"/>
   ```
3. **Data Binding**:
    - Use `auto-fields-service` or `auto-fields-entity` to generate fields automatically.
    - Use `<actions>` within the grid to perform complex finds (e.g., calling `performFind` service).
4. **AJAX & Events**:
    - Use `<on-event-update-area event-type="submit" area-id="..." area-target="..."/>` for partial page updates.
5. **Field Elements (Advanced)**:
    - **Drop-downs**: Use `<drop-down><entity-options ...></drop-down>`.
    - **Lookups**: Use `<lookup target-form-name="..."/>`.
    - **Displays**: Use `<display-entity entity-name="..."/>`.
    - **Actions**: Use `<hyperlink>` for navigation and `<submit>` for form submission.
6. **Styling**:
    - Use `odd-row-style`, `header-row-style`, and `default-table-style` for grids.
    - Use `alt-row-style` for row-level conditional styling.
7. **Autocompleters**:
    - Use `<lookup target-form-name="..."/>` for standard autocompleting lookups.
    - For multi-select autocompleters, use `<drop-down allow-multiple="true">` with `asmselect` CSS class if available in theme.
8. **Pagination**:
    - Use `paginate-target` on `<grid>` to specify the request for fetching next pages.
    - Set `view-size` to control items per page.
    - Use `<on-event-update-area event-type="paginate" .../>` for AJAX pagination.

## Guardrails
- **Naming**: Form names should be PascalCase (e.g., `EditExample`).
- **Separation**: Keep labels in UI label files, referenced via `${uiLabelMap.LabelName}`.
- **DTD Compliance**: Ensure compliance with `widget-form.xsd`.
- **Targeting**: Specify the `target` attribute for forms to define the submission request.

## Examples
**Example: Grid with Service Search and AJAX Pagination**
```xml
<grid name="ListExamples" list-name="listIt" paginate-target="FindExample" default-entity-name="Example">
    <actions>
        <service service-name="performFind" result-map="result" result-map-list="listIt">
            <field-map field-name="inputFields" from-field="exampleCtx"/>
            <field-map field-name="entityName" value="Example"/>
        </actions>
    <field name="exampleId" title="${uiLabelMap.ExampleExampleId}">
        <hyperlink description="${exampleId}" target="EditExample">
            <parameter param-name="exampleId"/>
        </hyperlink>
    </field>
    <field name="statusId" title="${uiLabelMap.CommonStatus}">
        <display-entity entity-name="StatusItem"/>
    </field>
    <on-event-update-area event-type="paginate" area-id="ListArea" area-target="ListOnly"/>
</grid>
```

**Example: AJAX Autocompleter (Dependent Drop-down)**
```xml
<form name="EditEx" type="single" target="updateEx">
    <field name="countryGeoId">
        <drop-down>
            <entity-options entity-name="Geo" key-field-name="geoId">
                <entity-constraint name="geoTypeId" value="COUNTRY"/>
            </entity-options>
        </drop-down>
        <on-client-event-update-area event-type="change" area-id="stateGeoId_area" area-target="GetStatesByCountry"/>
    </field>
    <field name="stateGeoId" id-name="stateGeoId_area">
        <drop-down>
            <entity-options entity-name="Geo" key-field-name="geoId">
                <entity-constraint name="geoTypeId" value="STATE"/>
                <entity-constraint name="geoId" from-field="countryGeoId"/>
            </entity-options>
        </drop-down>
    </field>
</form>
```

