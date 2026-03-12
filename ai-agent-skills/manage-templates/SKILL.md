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
name: manage-templates
description: Define and maintain OFBiz Freemarker (FTL) templates for UI widgets.
---

# Skill: manage-templates
## Goal
Define and maintain complex OFBiz Freemarker (FTL) templates, ensuring correct use of OFBiz directives and context variables.

## Triggers
**ALWAYS** read this skill when:
- Creating or modifying `.ftl` files in `template/` directories.
- Using `<platform-specific><html><html-template ...>` in screens.
- Implementing email templates or custom PDF layouts using FTL.

## Use when
- Creating dynamic HTML components that require logic beyond XML widgets.
- Building custom dashboards or specialized storefront pages.
- Developing macro libraries for reusable UI elements.

## Procedure
1. **Variable Setup & Safety**:
    - Use `<#assign variable = contextVar! />` to safely assign context variables with default values.
    - **Null Safety**: ALWAYS use the `!` operator (e.g., `user.name!`) to prevent template crashes if a variable is missing.
2. **OFBiz Directives (Critical)**:
    - **Links**: Use `<@ofbizUrl>requestName</@ofbizUrl>` for internal links.
    - **Content**: Use `<@ofbizContentUrl>/images/logo.png</@ofbizContentUrl>`.
    - **Currency**: Use `<@ofbizCurrency amount=price isoCode=currency />`.
    - **Labels**: Use `${uiLabelMap.LabelKey}` for internationalization.
3. **Control Flow**:
    - **Lists**: Use `<#list listVar as item> ... <#else> ... </#list>` for iterations.
    - **Conditionals**: Use `<#if condition> ... <#elseif ...> ... <#else> ... </#if>`.
4. **Context & Data Access**:
    - Access `parameters.xyz` for request parameters.
    - Access `requestAttributes.xyz` or `sessionAttributes.xyz` for backend data.
    - **In-template Data Fetching**: Use `EntityQuery` or `dispatcher` for lightweight lookups (e.g., `${Static["org.ofbiz.entity.util.EntityQuery"].use(delegator).from("Product").where("productId", pid).cache(true).queryOne().productName!}` or `${dispatcher.runSync("serviceName", {"param": value}).resultAttr!}`).
    - Use `Static["class.path"].method()` for Java utility calls (use sparingly).
5. **HTML5 Semantic Structure**:
    - **Mandatory**: Use semantic tags (`<header>`, `<main>`, `<section>`, `<article>`, `<footer>`) instead of generic `<div>` wrappers.
    - **Accessibility**: Use `aria-*` attributes and proper label associations.
6. **Encoding & Security**:
    - Use `?html` or `?js_string` when outputting variables to prevent XSS.

## Guardrails
- **Logic Placement**: Keep complex business logic in Groovy/Java; FTL should primarly focus on presentation logic and secondary data lookups.
- **Paths**: Reference templates using `component://` in screen XML.
- **Macros**: Define reusable UI blocks as `<#macro name param> ... </#macro>` to avoid duplication.

## Examples
**Example: Premium Semantic Dashboard Widget**
```ftl
<#-- Initialize data and perform lightweight lookup if needed -->
<#assign productList = productList! />

<section class="card" aria-labelledby="widget-title">
  <header>
    <h2 id="widget-title">${uiLabelMap.ProductFeaturedProducts}</h2>
  </header>
  
  <article class="content">
    <#if productList?has_content>
      <ul class="product-list" role="list">
        <#list productList as product>
          <#-- Data fetching lookup for category name -->
          <#assign category = Static["org.ofbiz.entity.util.EntityQuery"].use(delegator).from("ProductCategory").where("productCategoryId", product.primaryProductCategoryId!).cache(true).queryOne()! />
          <#assign price = productPriceMap[product.productId]! />
          
          <li class="product-item">
            <h3>${product.productName!product.productId}</h3>
            <#if category?has_content>
              <p class="category-tag"><small>${category.categoryName!category.description!}</small></p>
            </#if>
            <div class="price-container">
              <#if price?has_content>
                <@ofbizCurrency amount=price.price isoCode=price.currencyUsed />
              <#else>
                <span class="no-price">${uiLabelMap.ProductNoPriceAvailable}</span>
              </#if>
            </div>
            <footer>
              <a href="<@ofbizUrl>product?product_id=${product.productId}</@ofbizUrl>" class="btn btn-primary">
                ${uiLabelMap.CommonViewDetails}
              </a>
            </footer>
          </li>
        </#list>
      </ul>
    <#else>
      <p class="empty-state">${uiLabelMap.ProductNoProductsFound}</p>
    </#if>
  </article>
</section>
```
