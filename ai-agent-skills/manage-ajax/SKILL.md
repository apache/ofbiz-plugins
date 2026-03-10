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
name: manage-ajax
description: Implement AJAX-based UI interactions and partial updates in OFBiz.
---

# Skill: manage-ajax
## Goal
Configure and implement AJAX interactions to provide a seamless UI experience with partial page updates and JSON data exchange.

## Triggers
**ALWAYS** read this skill when:
- Adding `<on-event-update-area>` to forms, grids, or screens.
- Configuring controller responses for AJAX (JSON or partial HTML).
- Implementing autocomplete or dynamic dropdowns.
- Working with `CommonEvents.xml` for JSON responses.

## Use when
- Updating a list/grid without reloading the entire page (e.g., pagination).
- Submitting a form and updating only the relevant section of the screen.
- Fetching data dynamically for autocompletes.
- Bridging OFBiz backend data with modern frontend frameworks (React/Vue).

## Procedure
1. **Triggering from Widgets**:
    - Use `<on-event-update-area>` inside a `<form>` or `<grid>`.
    - **Attributes**:
        - `event-type`: `submit` (for buttons), `paginate` (for list navigation), `change` (for input fields).
        - `area-id`: The target HTML `id` of the `div` to be replaced.
        - `area-target`: The request URI (from `controller.xml`) to execute.
2. **Controller Configuration**:
    - **JSON Response**:
      ```xml
      <request-map uri="myServiceAjax">
          <security https="true" auth="true"/>
          <event type="service" invoke="myService"/>
          <response name="success" type="request" value="json"/>
          <response name="error" type="request" value="json"/>
      </request-map>
      ```
4. **Common Events**: Ensure your controller includes the standard JSON event handler (usually inherited or via `<include name="CommonEvents" location="component://common/webtools/webapp/webtools/WEB-INF/controller.xml"/>`).
    - **Partial HTML Response**:
      ```xml
      <request-map uri="myPartialScreen">
          <security https="true" auth="true"/>
          <response name="success" type="view" value="MyPartialView"/>
      </request-map>
      <view-map name="MyPartialView" type="screen" page="component://.../AjaxScreens.xml#MyPartialContent"/>
      ```
3. **Ajax-Specific Screens**:
    - Create a screen that only renders the specific widget (form/grid) without the global header/footer.
    - Use `<decorator-screen name="AjaxVisualThemeChild" location="component://common/widget/CommonScreens.xml">` if some layout is still needed, or no decorator for pure fragments.
4. **Passing State**:
    - Ensure all necessary parameters (e.g., `exampleId`, `viewIndex`) are passed in the AJAX target request to maintain context.

## Guardrails
- **ID Collisions**: Ensure `area-id` matches the actual HTML ID rendered on the page.
- **Security**: AJAX requests must still respect `<security auth="true"/>` unless explicitly intended for public access.
- **Error Handling**: Always handle the `error` response in `controller.xml` to prevent silent failures.
- **Performance**: Avoid over-using AJAX for simple transitions; use it where partial updates significantly enhance UX.

## Examples
**Example: AJAX Pagination for a List**
```xml
<grid name="ListExamplesAjax" extends="ListExamples" paginate-target="findExampleAjax">
    <on-event-update-area event-type="paginate" area-id="ExampleListDiv" area-target="ListExampleFragment"/>
</grid>
```

**Example: Auto-saving a field**
```xml
<field name="exampleName">
    <text>
        <on-event-update-area event-type="change" area-id="StatusBar" area-target="saveExampleNameAjax"/>
    </text>
</field>
```
