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
name: manage-pdf-export
description: Guidelines for generating printable PDF documents in OFBiz using Apache FOP (XSL-FO).
---

# Skill: manage-pdf-export

## Goal
Design and implement professional printable documents (e.g., invoices, packing slips) using Apache FOP (XSL-FO) for PDF generation.

## Triggers
**ALWAYS** read this skill when:
- Creating printable business documents.
- Implementing report-specific screen definitions for PDF output.
- Converting HTML/FTL templates to XSL-FO.

## Use when
- Generating legal or formal documents (PDF).
- Batch printing operations.
- **NOT** for data dumps (CSV/Excel) - use a separate data export skill.

## Procedure
1.  **Output Format**:
    - `application/pdf` (Apache FOP): The standard for printable documents with strict layout requirements.

2.  **Data Preparation (Groovy/Java)**:
    - Create a dedicated service or groovy script to fetch document data.
    - **Optimization**: Use `EntityListIterator` if the document contains large lists.
    - **Structure**: Nested maps/lists are best for Master-Detail PDFs (e.g., Header + Items).

3.  **Template Design (XSL-FO via FTL)**:
    - Use FreeMarker to inject dynamic data into XSL-FO tags.
    - **Root**: `<fo:root>`
    - **Layout**: `<fo:layout-master-set>` defining `simple-page-master` (A4, Letter).
    - **Content**: `<fo:page-sequence>` containing `<fo:flow flow-name="xsl-region-body">`.
    - **Tables**: Use `<fo:table>` and `<fo:table-row>` for structured data.
    - **Example Loop**:
      ```xml
      <#list orderItems as item>
        <fo:table-row>
            <fo:table-cell><fo:block>${item.description}</fo:block></fo:table-cell>
        </fo:table-row>
      </#list>
      ```

4.  **Screen Definition**:
    - Define a screen in `widget/CommonScreens.xml` or component-specific screen file.
    - Use `xsl-fo` as the render-mode/content-type.
    ```xml
    <screen name="OrderPdf">
        <section>
            <actions>
                <script location="component://mycomponent/groovyScripts/documents/OrderPdf.groovy"/>
            </actions>
            <widgets>
                <platform-specific>
                    <xsl-fo><html-template location="component://mycomponent/template/documents/OrderPdf.fo.ftl"/></xsl-fo>
                </platform-specific>
            </widgets>
        </section>
    </screen>
    ```

5.  **View Mapping (`controller.xml`)**:
    - Map the request to the screen with type `screenfop`.
    ```xml
    <view-map name="OrderPdf" type="screenfop" page="component://mycomponent/widget/OrderScreens.xml#OrderPdf" content-type="application/pdf" encoding="none"/>
    ```

## Guardrails
- **Styling**: Styles in FO are verbose. Use FTL macros for common elements (headers, footers) to ensure consistency across documents.
- **Fonts**: Ensure required fonts are available in the FOP configuration if using non-standard typefaces.
- **Async**: For very large documents, generate them asynchronously via Job Scheduler and email the result.
- **Security**: Ensure strict permission checks (`security.hasEntityPermission`) in the data preparation phase, as PDF generation often bypasses standard UI filters.

## Examples
**Example: XSL-FO Template Structure**
```xml
<fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
    <fo:layout-master-set>
        <fo:simple-page-master master-name="main" page-height="11in" page-width="8.5in"
            margin-top="0.5in" margin-bottom="0.5in" margin-left="0.5in" margin-right="0.5in">
            <fo:region-body margin-top="1in"/>
            <fo:region-before extent="1in"/>
            <fo:region-after extent="1in"/>
        </fo:simple-page-master>
    </fo:layout-master-set>

    <fo:page-sequence master-reference="main">
        <fo:flow flow-name="xsl-region-body">
            <fo:block font-size="18pt" font-weight="bold" text-align="center">
                Order Document: ${orderHeader.orderId}
            </fo:block>
            <fo:table table-layout="fixed" width="100%">
                <fo:table-column column-width="50%"/>
                <fo:table-column column-width="50%"/>
                <fo:table-body>
                    <#list orderItems as item>
                    <fo:table-row>
                        <fo:table-cell><fo:block>${item.description}</fo:block></fo:table-cell>
                        <fo:table-cell><fo:block>${item.unitPrice}</fo:block></fo:table-cell>
                    </fo:table-row>
                    </#list>
                </fo:table-body>
            </fo:table>
        </fo:flow>
    </fo:page-sequence>
</fo:root>
```
