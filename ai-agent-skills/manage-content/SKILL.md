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
name: manage-content
description: Create and manage OFBiz content data. Use when defining Content, DataResource, or linking content to products/categories.
---

# Skill: manage-content
## Goal
Manage the OFBiz Content Management System (CMS) data, including data resources and their associations.

## Triggers
**ALWAYS** read this skill when:
- Creating content-related data files.
- Linking images, documents, or text to products or orders.
- Troubleshooting content visibility or retrieval issues.

## Use when
- Creating `DataResource` for files or electronic text.
- Defining `Content` metadata.
- Setting up `ProductContent`, `CategoryContent`, or `OrderContent` associations.

## Procedure
1. **Define DataResource**:
    - The foundational record for actual content (text, URL, or reference to file).
    - Fields: `dataResourceId`, `dataResourceTypeId` (e.g., `ELECTRONIC_TEXT`, `LOCAL_FILE`), `dataTemplateTypeId`.
2. **Define Content**:
    - The metadata layer over the data resource.
    - Fields: `contentId`, `dataResourceId`, `contentTypeId`, `statusId`, `contentName`, `description`.
3. **Link Content (Associations)**:
    - Use association entities to link content to business objects.
    - Examples: `ProductContent` (links to Product), `ContentAssoc` (links content together).
4. **Electronic Text**:
    - For text stored in the database, use `ElectronicText` entity linked to the `DataResource`.

## Guardrails
- **Naming**: Use clear, descriptive names for `contentId`.
- **Status**: Always set `statusId` (e.g., `CTNT_PUBLISHED`) to ensure visibility if logic filters by status.
- **Paths**: For `LOCAL_FILE`, ensure paths are relative or managed via system properties.

## Examples
**Example: Product Image Content**
```xml
<entity-engine-xml>
    <!-- Data Resource pointing to the image file -->
    <DataResource dataResourceId="MY_PROD_IMAGE" dataResourceTypeId="LOCAL_FILE" 
                  objectInfo="/images/products/myproduct-large.jpg" statusId="DR_AVAILABLE"/>
    
    <!-- Content metadata linking to the resource -->
    <Content contentId="MY_PROD_IMAGE_CNT" contentTypeId="DOCUMENT" 
             dataResourceId="MY_PROD_IMAGE" statusId="CTNT_PUBLISHED" contentName="Large Image"/>
    
    <!-- Linking to a specific product -->
    <ProductContent productId="PROD_1001" contentId="MY_PROD_IMAGE_CNT" 
                    productContentTypeId="LARGE_IMAGE" fromDate="2026-01-01 00:00:00.000"/>
</entity-engine-xml>
```

**Example: Electronic Text Content**
```xml
<entity-engine-xml>
    <DataResource dataResourceId="MY_PROD_DESC" dataResourceTypeId="ELECTRONIC_TEXT"/>
    <ElectronicText dataResourceId="MY_PROD_DESC" textData="Detailed product description here..."/>
    <Content contentId="MY_PROD_DESC_CNT" contentTypeId="DOCUMENT" 
             dataResourceId="MY_PROD_DESC" statusId="CTNT_PUBLISHED"/>
</entity-engine-xml>
```
