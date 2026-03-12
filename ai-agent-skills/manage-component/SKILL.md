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
name: manage-component
description: Maintain OFBiz component configuration, dependencies, and resource boundaries.
---

# Skill: manage-component
## Goal
Configure and maintain the structure, resources, and dependencies of an OFBiz component (plugin). This includes registering business resources (entities, services, ECAs), defining web applications, and managing inter-component dependencies to ensure classpath and resource availability across the project.

## Triggers
**ALWAYS** read this skill when:
- Creating a new component or modifying `ofbiz-component.xml`.
- Adding new service, entity, or ECA resource files.
- Managing project dependencies in `build.gradle` or inter-component dependencies.
- Troubleshooting component loading, resource visibility, or classpath issues.

## Rules & Procedures

### 1. Resource Registration
Every XML resource must be explicitly registered in `ofbiz-component.xml` to be recognized by the framework.
- **Entities**: `<entity-resource type="model" reader-name="main" loader="main" location="entitydef/entitymodel.xml"/>`
- **EECA**: `<entity-resource type="eca" loader="main" location="entitydef/entityeca.xml"/>`
- **Services**: `<service-resource type="model" loader="main" location="servicedef/services.xml"/>`
- **SECA**: `<service-resource type="eca" loader="main" location="servicedef/serviceeca.xml"/>`
- **Group Services**: `<service-resource type="group" loader="main" location="servicedef/groups.xml"/>`
- **Seed/Demo Data**: `<entity-resource type="data" reader-name="seed" loader="main" location="data/MySeedData.xml"/>`
- **Web Applications**: Register the webapp using `<webapp name="..." title="..." server="default-server" location="webapp/..." mount-point="/..."/>`.

> [!IMPORTANT]
> **Screens are NOT registered** in `ofbiz-component.xml`. They are referenced directly via URIs or rendered by controllers.

### 2. Dependency Management
- **Inter-Component**: Use `<depends-on component-name="xxx"/>` in `ofbiz-component.xml`. This ensures that component `xxx` is loaded first and its **classpath and resources** (services, entities) are available to the current component.
- **External Libraries**: **Always prefer Gradle** for dependency management. Add `dependencies { implementation 'group:name:version' }` to `build.gradle`.
- **Legacy Classpath**: Avoid using the `<classpath>` tag in `ofbiz-component.xml`. OFBiz automatically loads all JAR files located in a component's `/lib` directory.

### 3. Resource Locations & Properties
- **UI Labels**: Store localized text in `config/[Component]UiLabels.xml`.
- **System Settings**: Store technical/business configurations in `config/[component].properties`.
- **Entity Definitions**: Place in `entitydef/`.
- **Service Definitions**: Place in `servicedef/`.

## Guardrails
- **URI Enforcement**: **NEVER** use relative paths. ALWAYS use `component://[componentName]/path/to/file` for all resource references across components.
- **Edit Discipline**: Do **NOT** modify `ofbiz-component.xml` unless new resources are actually being introduced. Avoid redundant edits.
- **Circular Dependencies**: Avoid circular `<depends-on>` relationships between components; they lead to unpredictable loading failures.
- **Mount Points**: Ensure a unique `mount-point` for every `<webapp>` entry to prevent URL collisions.
- **Resource Loaders**: Avoid redefining `<resource-loader>` entries unless your component specifically requires a custom implementation. Standard components inherit loaders from core.
- **Registry Completeness**: If it's an entity, service, or ECA XML, it **must** be in `ofbiz-component.xml` or it will not be loaded.

## Bad Practices & Anti-patterns

### Anti-pattern: Pathing Traps
**Pitfall**: Hardcoding file system paths or using simple relative paths (e.g., `entitydef/model.xml`). While sometimes working locally, this breaks portability and leads to resource resolution failures in different environments.
- **❌ Bad**: `location="entitydef/model.xml"`
- **✅ Good**: `location="component://mycomponent/entitydef/model.xml"`

### Anti-pattern: Redundant Classpath Tagging
**Pitfall**: Explicitly adding `<classpath type="jar" location="lib/*"/>` when JARs in `/lib` are already auto-loaded. This clutters the configuration.
- **❌ Bad**: `<classpath type="jar" location="lib/mysql-connector.jar"/>`
- **✅ Good**: Rely on `/lib` auto-loading or use Gradle `dependencies`.

### Anti-pattern: Mount Point Collisions
**Pitfall**: Defining duplicate `mount-point` values for different `<webapp>` entries (within the same component or across different components). This causes the web server to fail to start or leads to unpredictable URL routing.
- **❌ Bad (Duplicate Mounts)**:
  ```xml
  <webapp name="app1" mount-point="/app" .../>
  <webapp name="app2" mount-point="/app" .../>
  ```
- **✅ Good (Unique Mounts)**: 
  ```xml
  <webapp name="app1" mount-point="/app1" .../>
  <webapp name="app2" mount-point="/app2" .../>
  ```

---

## Common Code Patterns (Examples)

**Example: Comprehensive ofbiz-component.xml**
```xml
<ofbiz-component name="myplugin" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:noNamespaceSchemaLocation="http://ofbiz.apache.org/dtds/ofbiz-component.xsd">
    <!-- Inherits resource-loader from core if not defined -->

    <!-- Components we depend on for classpath/resource availability -->
    <depends-on component-name="common"/>
    <depends-on component-name="party"/>

    <!-- Entity Definitions & ECAs -->
    <entity-resource type="model" reader-name="main" loader="main" location="entitydef/entitymodel.xml"/>
    <entity-resource type="eca" loader="main" location="entitydef/entityeca.xml"/>

    <!-- Service Definitions & ECAs -->
    <service-resource type="model" loader="main" location="servicedef/services.xml"/>
    <service-resource type="eca" loader="main" location="servicedef/serviceeca.xml"/>
    <service-resource type="group" loader="main" location="servicedef/groups.xml"/>

    <!-- Data Resources -->
    <entity-resource type="data" reader-name="seed" loader="main" location="data/MySeedData.xml"/>
    <entity-resource type="data" reader-name="demo" loader="main" location="data/MyDemoData.xml"/>

    <!-- Web Applications -->
    <webapp name="myportal" title="My Portal" server="default-server" location="webapp/myportal" mount-point="/myportal" app-bar-display="true"/>
</ofbiz-component>
```
