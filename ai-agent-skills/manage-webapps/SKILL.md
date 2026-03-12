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
name: manage-webapps
description: Configure and manage OFBiz web applications, defining component webapp properties, servlet environments, and request routing through controller.xml.
---

# Manage Webapps Skill

Manage the registration, bootstrapping, and request routing of OFBiz web applications. This skill covers how webapps link to components, how the servlet environment is initialized, and how requests flow through the framework.

## Triggers
- Creating a new module or UI plugin.
- Exposing a set of screens or services to the web.
- Modifying servlet filters, listeners, or security settings.
- Debugging request routing or dispatcher lookup issues.

## Component Webapp Definition (`ofbiz-component.xml`)

Webapps are registered in the `ofbiz-component.xml` file. A single component can contain multiple web applications (e.g., `ordermgr`, `facility`, `catalog`).

### Key Attributes
- **name**: Unique identifier for the webapp.
- **title**: Display name; also used in headers when no UI Label is found.
- **mount-point**: The context path (e.g., `/mywebapp`).
- **app-bar-display**: `true`/`false`. Controls if the app appears in the top navigation bar.
- **base-permission**: Defines the **minimum security permission(s)** required to access the webapp. The user must have ALL listed permissions to enter.
- **access-permission**: (Preferred) Modern replacement for `base-permission`. Takes precedence if both are defined.
- **server**: Usually `default-server`.
- **location**: Path to the webapp directory relative to the component root.
- **menu-name**: Determines which application menu it belongs to (e.g., `main`, `secondary`).
- **client-proxy-server**: Optional proxy server name for the webapp.
- **description**: Internal documentation of the webapp's purpose.
- **position**: Order of the app in the application bar or menu.
- **use-autologin-cookie**: `true`/`false`. Enables the "remember me" feature.
- **session-cookie-max-age**: Duration in seconds for the session cookie.

## The Request Pipeline

Understanding the flow is critical for debugging and routing.

### 1. Request Routing vs. Bootstrapping
- **`web.xml`**: Bootstraps the servlet environment (Filters, Servlets, Listeners).
- **`controller.xml`**: Handles the actual **Request Routing** logic.

### 2. Standard Flow
1. **Browser** sends request (e.g., `/ordermgr/control/viewOrder`).
2. **ControlServlet** (defined in `web.xml`) processes all controller requests.
3. **ControlServlet** delegates to **`controller.xml`** by matching the request URI to a `<request-map>`.
4. **`controller.xml` Mapping**:
    - **Request-Map**: Checks security and invokes events.
    - **Event**: Logic-level execution (Service, Java, Groovy).
    - **View-Map**: Determines the response type (view, redirect, request).
    - **Screen**: If response type is `view`, a screen definition is called (e.g., `widget/OrderScreens.xml`).
    - **Widget**: The screen renders UI components (Form, Menu, Tree).

## Web Application Configuration (`web.xml`)

Located in `webapp/<webapp-name>/WEB-INF/web.xml`.

### The Filter Chain
OFBiz uses a specific sequence of filters for security and context management. **Order is critical.**

1. **ContextFilter**: Initializes the delegator and local dispatcher.
2. **ControlFilter**: Handles URL redirections and allowed paths.
3. **SameSiteFilter**: Manages session cookie flags.
4. **SecurityFilter**: Handles request-level authentication/authorization.
5. **ResponseFilter**: Manages response headers (caching, security headers).

### Servlet Configuration
- **servlet-name**: Usually `ControlServlet`.
- **servlet-class**: `org.apache.ofbiz.webapp.control.ControlServlet`.
- **servlet-mapping**: Maps a URL pattern (standardly `/control/*`) to the `ControlServlet`.

### Context Parameters (`context-param`)
- **delegatorName**: The name of the entity delegator to use (e.g., `default`).
- **localDispatcherName**: Connects the webapp to a unique service engine instance.
    - **Guardrail**: It MUST match the webapp name (e.g., `ordermgr`) to ensure reliable service execution and logging.
- **mainDecoratorLocation**: Path to the main decorator screen used by the webapp (e.g., `component://order/widget/CommonScreens.xml`).

## Three-Layer Security Model

Access control happens at three distinct levels:
1. **Component Level**: `base-permission` in `ofbiz-component.xml` (The gatekeeper).
2. **Controller Level**: `<security auth="true"/>` in `request-map` (Path protection).
3. **Service Level**: `auth="true"` in `services.xml` (Logic protection).

## Static Resource Handling
Static assets (images, CSS, JS) reside in `webapp/<app>/`.
- **Rule**: These are served directly by the servlet container. **Do not route static assets through the controller** via a request-map.

## Procedure
1. Define the webapp in `ofbiz-component.xml` with proper permissions and `app-bar-display`.
2. Create the folder structure: `webapp/<app>/WEB-INF/` and `webapp/<app>/widget/`.
3. Configure `web.xml` for bootstrapping (Filters, `ControlServlet`, `localDispatcherName`).
4. Define routing in `controller.xml` (`request-map`, `view-map`).
5. Verify global framework settings in `url.properties` (affects all webapps).

## Best Practices
1. **Unique Mount Points**: Ensure mount points do not overlap across the framework.
2. **Permission Consistency**: Align `base-permission` in `ofbiz-component.xml` with request-level security checks in `controller.xml`.
3. **Dispatcher Naming Symmetry**: Always match `localDispatcherName` in `web.xml` to the webapp name defined in `ofbiz-component.xml`.
4. **Context Path Safety**: Avoid hardcoding context roots (e.g., `/ordermgr`) in URLs; use dynamic path lookups (e.g., `request.getContextPath()`).

## Guardrails
1. **Filter Order**: Never reorder filters in `web.xml` without deep framework knowledge.
2. **Dispatcher Symmetry**: Always match `localDispatcherName` to the webapp name.
3. **Servlet Mappings**: Do not modify `ControlServlet` mappings (`/control/*`) unless extending the core engine.
4. **Context Safety**: Never expose sensitive logic or files outside of the `ControlServlet` routing.
5. **Mount Point Uniqueness**: Ensure mount points do not overlap across the framework.

## Anti-Patterns
- **❌ Shared Dispatchers**: Multiple apps sharing one dispatcher name, leading to context collisions.
- **❌ Controller-Routed Assets**: Creating request-maps for simple `.js` or `.css` files.
- **❌ Hardcoded Context Paths**: Using `/ordermgr` in HTML instead of dynamic context lookup.

## Examples

### 1. Minimal Webapp Setup Structure
```text
my-component/
    ofbiz-component.xml
    webapp/
        example/               <-- Webapp Location
            images/            <-- Static Assets
            WEB-INF/
                web.xml        <-- Bootstrapping
                controller.xml <-- Request Routing
            widget/
                ExampleScreens.xml <-- UI Definitions
```

### 2. Webapp Registration (`ofbiz-component.xml`)
```xml
<webapp name="example"
    title="Example App"
    server="default-server"
    location="webapp/example"
    mount-point="/example"
    app-bar-display="true"
    base-permission="OFBTOOLS,EXAMPLE_VIEW"/>
```

### 3. Request Routing (`controller.xml`)
```xml
<request-map uri="viewOrder">
    <security auth="true" https="true"/>
    <event type="service" invoke="getOrderDetails"/>
    <response name="success" type="view" value="OrderView"/>
</request-map>

<view-map name="OrderView" type="screen" page="component://order/widget/OrderScreens.xml#OrderView"/>
```

### 4. Global URL Settings (`url.properties`)
> [!IMPORTANT]
> Settings in `framework/webapp/config/url.properties` apply **globally** to all webapps in the instance.
- `no.http=Y`: Force HTTPS instance-wide.
- `port.https=8443`: Global secure port.
- **`content.url.prefix`**: Global prefix for media/content like images, JS, CSS, and URL (e.g., CDN or static server).

