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
name: manage-controller
description: Manage OFBiz Web Controller definitions. Use when defining requests, redirects, and view mappings.
---

# Skill: manage-controller
## Goal
Define and maintain request mappings and view definitions within the OFBiz `controller.xml`.

## Triggers
**ALWAYS** read this skill when:
- Modifying `WEB-INF/controller.xml`.
- Registering a web application in `ofbiz-component.xml`.
- Adding new UI pages (views) or form submission targets (requests).

## Use when
- Designing URL structures for the web application.
- Mapping requests to Java/Groovy events or OFBiz services.
- Configuring security (HTTPS, Auth) for specific URLs.

## Procedure
1. **Security & Authentication**:
    - Set `uri` to a unique identifier.
    - Set `https="true"` for sensitive data.
    - Set `auth="true"` to require user login.
2. **Register**: Ensure the webapp is registered in `ofbiz-component.xml`:
   ```xml
   <webapp name="myApp" title="My App" server="default-server" location="webapp/myApp" mount-point="/myapp"/>
   ```
3. **Advanced Request Routing (Events before URIs)**:
    - **`<preprocessor>`**: Runs unconditionally on *every single request* to this webapp before the regular `<request-map>` logic. Use for global checks, request logging, setting up tenant databases, or enforcing global security rules.
    - **`<firstvisit>`**: Runs only once per user session (on their very first request). Perfect for initializing analytics tracking, setting up an empty shopping cart, or calculating initial geographical settings based on IP.
4. **Request Mapping**:
    - Define `<event type="..." path="..." invoke="..."/>` for processing logic.
    - **Architectural Decision: Which Event Type to Use?**
        - `type="service"`: **Preferred for 90% of requests**. Use this when the request maps directly to a standard OFBiz service (which handles transactions, permissions, and IN/OUT parameter mapping automatically).
        - `type="service-multi"`: Use to handle multi-form submissions natively. It extracts tabular POST data (like multiple input rows), loops over them in the framework, and calls a standard single-record service repeatedly. No custom Java/Groovy iteration required.
        - `type="groovy"`: Use this for orchestrating multiple services, formatting/pre-processing HTTP Request parameters before calling a service, or writing custom endpoints (like streaming files or returning JSON).
        - `type="java"`: **Use sparingly for HTTP Controllers**. Only use this for core framework events (like login/logout, JWT validation, or low-level HTTP session manipulation). Standard business logic belongs in Services, not Java HTTP events.
4. **Response Handling**:
    - **`type="view"`**: Use to render a UI page (Screen/Template). This is the final step in a request lifecycle where the response HTML is sent back. Do not use this if you need to run another event first.
    - **`type="request"`**: Use for an internal server-side forward to another `<request-map>`. **Crucial:** Request attributes (like error messages, form data, or queried entities) are kept and passed forward. The browser URL does *not* change.
    - **`type="request-redirect"`**: Use for an HTTP 302 Redirect to another `<request-map>`. The browser URL *does* change. **Crucial:** All request attributes are lost because it's a completely new HTTP request. Form parameters are appended to the URL as query params. Use this after a successful database update (PRG pattern: Post/Redirect/Get) to prevent double-submissions on browser refresh.
    - **`type="request-redirect-noparam"`**: Like `request-redirect`, but explicitly strips all form parameters so they are not leaked in the resulting URL's query string. Essential for URLs containing sensitive data (e.g., passwords or tokens) after a form submission.
    - **`name="error"` Handling**: Always include a `<response name="error" type="view" value="..."/>` to return the user to the input page (with their submitted data intact) if the event fails or throws validation errors.
5. **RESTful APIs, JSON, & AJAX**:
    - To build API endpoints or handle AJAX requests, return pure JSON instead of an HTML view.
    - Use `<response name="success" type="request" value="json"/>` (assuming a global `json` request exists in `common-controller.xml`) to serialize the `requestAttributes` into a JSON response.
    - Alternatively, use `<response name="success" type="none"/>`. Use this when the Groovy/Java event explicitly writes to the `HttpServletResponse` output stream (e.g., when generating a custom JSON string or streaming a file download) so OFBiz does not attempt to render a view.
6. **View Definitions**:
    - Map the `value` in the response to a `<view-map>`.
    - **`type="screen"`**: **Preferred for UI**. Renders an OFBiz Screen Widget (`page` points to a `component://.../Screens.xml#Name`).
    - **`type="json"`**: Used inherently for API responses when returning data (though often handled implicitly by `request` chaining to a global `json` handler rather than an explicit view).
    - **`type="url"`**: Direct redirect to an external URL or static asset. Set `page="https://..."` or an absolute server path.

## Guardrails
- **Naming**: Request URIs should be camelCase (e.g., `updateExample`).
- **Chaining**: Avoid deep request chains; prefer direct redirects (`type="request-redirect"`).
- **Redundancy**: Check for global-requests and global-responses in standard OFBiz controllers to avoid duplication.
- **Event Anti-Pattern**: Do NOT use `type="java"` events to write standard business logic that extracts parameters from the `HttpServletRequest`. That logic belongs in a `Service` (which automatically maps parameters) or a `Groovy` script (if HTTP orchestration is required).
- **View Anti-Pattern**: Do NOT use `type="ftl"` in view-maps for new code. Always route UI rendering through Screen Widgets (`type="screen"`) to maintain consistent decorators, security, and context setup.
- **Security Check**: Be careful with `https="true"`. In modern OFBiz deployments behind an SSL-terminating proxy (like Nginx), setting this can cause infinite 302 redirect loops if `port.https.enabled` is not configured correctly in `url.properties`.
- **CSRF Protection**: For form submissions (POST requests), always ensure the request map is protected or utilizes OFBiz's built-in token mechanisms to prevent Cross-Site Request Forgery.

## Examples
**Example: Standard Request with Service Event and View Mapping**
```xml
<request-map uri="updateExample">
    <security https="true" auth="true"/>
    <event type="service" invoke="updateExample"/>
    <response name="success" type="view" value="EditExample"/>
    <response name="error" type="view" value="EditExample"/>
</request-map>

<view-map name="EditExample" type="screen" page="component://example/widget/example/ExampleScreens.xml#EditExample"/>
```

### JSON/AJAX API Endpoint (Standard OFBiz Way)
```xml
<!-- This will invoke the service, and the 'json' request handler will serialize the result to the browser -->
<request-map uri="getExampleDetailsJson">
    <security https="false" auth="true"/>
    <event type="service" invoke="getExampleDetails"/>
    <response name="success" type="request" value="json"/>
    <response name="error" type="request" value="json"/>
</request-map>
```

### Advanced Routing: Preprocessors and Firstvisit
```xml
<!-- This block is placed inside the <site-conf> tag, usually near the top -->

<!-- Runs on EVERY request to this webapp -->
<preprocessor>
    <!-- Example: Check for suspicious activity or validate tenant ID -->
    <event name="checkBlacklist" type="java" path="org.apache.ofbiz.security.SecurityEvents" invoke="checkBlacklist"/>
</preprocessor>

<!-- Runs ONLY ONCE per user session -->
<firstvisit>
    <!-- Example: Initialize analytics tracking or an empty shopping cart -->
    <event name="initVisitor" type="java" path="org.apache.ofbiz.webapp.control.LoginWorker" invoke="checkExternalLoginKey"/>
</firstvisit>
```

### Custom Response Writing (Streaming or Custom JSON)
```xml
<!-- The Groovy event directly writes to response.getWriter() and returns "success", so we use type="none" to stop further rendering -->
<request-map uri="downloadCustomData">
    <security https="true" auth="true"/>
    <event type="groovy" path="component://example/groovyScripts/StreamCustomData.groovy"/>
    <response name="success" type="none"/>
    <response name="error" type="view" value="main"/>
</request-map>
```
