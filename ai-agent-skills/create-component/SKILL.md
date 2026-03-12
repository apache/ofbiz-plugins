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
name: create-component
description: Scaffold a new OFBiz component (plugin) using Gradle. Use when starting a new feature set or isolating custom code.
---

# Skill: create-component
## Goal
Create a new, isolated OFBiz component with the correct directory structure and configuration.

## Triggers
**ALWAYS** read this skill when:
- Creating a new plugin or component.
- Isolating a large feature set into a separate module.
- Understanding plugin discovery vs. manual registration.

## Use when
- Starting a brand new feature (e.g., `my-custom-module`).
- Developing an integration that should not impact core components.

## Procedure
1. **Gradle Command**:
    - Run `./gradlew createPlugin -PpluginId=my-plugin-name`.
    - Optional flags: `-PwebappName=my-app`, `-PbasePermission=MY_PERM`.
2. **Base Structure**:
    - Ensure the following folders are present: `src`, `webapp`, `entitydef`, `servicedef`, `data`, `widget`.
3. **Initial Configuration**:
    - Update `ofbiz-component.xml` with proper titles and descriptions.
    - Add basic security permissions in `data/[Component]SecurityData.xml`.
4. **Registration (Automatic)**:
    - OFBiz automatically discovers any directory in `plugins/` that contains an `ofbiz-component.xml` file.
    - NO command like `./gradlew loadAll` is required for registration.
    - OFBiz must be restarted (or a container reload triggered) to detect new components.

## Post-Scaffold Customization
1. **Directory Structure**: Verify the existence of `entitydef/`, `servicedef/`, `data/`, and `webapp/`.
2. **Component XML**: Update `ofbiz-component.xml` with actual resource locations.
3. **Web Application**: If your component has a UI, ensure the `<webapp>` entry has a unique `mount-point`.
4. **Permissions**: Define base permissions in `data/MyComponentSecurityData.xml`.

## Guardrails
- **Naming**: Use lowercase, kebab-case (e.g., `order-management`).
- **Location**: All custom components MUST reside in the `plugins/` directory.
- **Discovery**: Plugins in `plugins/` are auto-discovered. Do not try to manually register them in framework load files.
- **Independence**: Ensure the new component doesn't break existing ones.
- **Best Practice**: Immediately create a dedicated `SecurityData.xml` to avoid permission conflicts.
- **Cleanliness**: Delete unused scaffolded files (e.g., placeholder entities) after creation.

## Examples
**Example: Creating a new inventory plugin**
```bash
./gradlew createPlugin -PpluginId=inventory-custom -PwebappName=inventoryApp
./gradlew loadAll
```
Then, verify the folder `plugins/inventory-custom` exists.
