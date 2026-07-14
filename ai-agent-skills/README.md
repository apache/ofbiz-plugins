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

# OFBiz Agent Skills Plugin

## Overall Goal
The `ai-agent-skills` plugin is a repository of standardized knowledge, best practices, and implementation patterns for Apache OFBiz. Its primary purpose is to empower AI agents (like Antigravity) to perform complex OFBiz development tasks—such as creating entities, defining services, building screens, and managing business logic—with high accuracy and adherence to framework-specific guardrails.

By providing these "skills" in a structured format, we ensure that agents follow the correct design patterns (e.g., favoring View Entities over manual iteration, using Worker classes correctly, adhering to security standards) without manual intervention for every step.

## Version Compatibility
These skills work best with Apache OFBiz `trunk`, where the guidance is maintained and updated first.

If you use them against older release branches or customized codebases, verify helper availability, service contracts, and framework behavior before applying the guidance verbatim.

## Agent Setup & Activation

For an AI agent (like Antigravity, Cursor, or GitHub Copilot) to effectively use these skills, they need to be linked or aggregated into the specific directories those agents expect at the root of your workspace.

### 1. Framework Location (Plugin)
Keep the source of these skills in your OFBiz plugins directory so they remain version-controlled alongside your project:
`~/ofbiz-framework/plugins/ai-agent-skills/`

### 2. Synchronization via Gradle
To make these skills available and properly formatted for your specific AI agents, run the automated Gradle synchronization tasks from your OFBiz root directory.

```bash
# Navigate to your OFBiz framework root
cd ~/ofbiz-framework

# Option 1: Sync to the default generic agents directory (.agents/skills)
./gradlew syncAgentSkills
```

### Updating Skills
If you modify or add any content inside `plugins/ai-agent-skills`, you must re-run the sync command for the changes to take effect in your AI agents:

```bash
./gradlew syncAgentSkills
```
This command recopies the structured files into the `.agents/` folder by default.

```bash
# Option 2: Sync targets individually using the -Pagent parameter
./gradlew syncAgentSkills -Pagent=agents    # Copies folder to .agents/skills/
./gradlew syncAgentSkills -Pagent=gemini    # Copies folder to .gemini/skills/
./gradlew syncAgentSkills -Pagent=claude    # Copies folder to .claude/skills/
./gradlew syncAgentSkills -Pagent=github    # Copies folder to .github/skills/
./gradlew syncAgentSkills -Pagent=cursor    # Copies folder to .cursor/skills/

# You can also use "all" to copy to all supported agents at once:
./gradlew syncAgentSkills -Pagent=all

# Or pass separated agents to target a subset explicitly:
./gradlew syncAgentSkills -Pagent=claude,github
```

Using these tasks ensures that as the skills in the plugin are updated, your agents automatically inherit the updates locally without manual intervention.

## How to Use with an Agent
Once synced, the agents will automatically discover these skills when working in your OFBiz project directory.

### Integration Patterns
- **Generic Agents**: Point them to the `.agents/skills` directory.
- **Gemini (Antigravity)**: Automatically reads from `.gemini/skills/`.
- **Claude**: Automatically reads contexts from `.claude/skills/`.
- **Cursor**: Automatically reads contexts from `.cursor/skills/`.
- **GitHub Copilot**: Automatically incorporates capabilities from `.github/skills/`.

## Directory Structure
The master source of these skills should be placed within the `plugins` directory of your OFBiz framework:
`/path/to/ofbiz/plugins/ai-agent-skills/`

Each directory contains:
- `SKILL.md`: The core knowledge file containing the goal, procedures, and guardrails for that specific skill.

## Available Skills
For a detailed list of all skills with short descriptions, see [SKILLS_SUMMARY.md](~/ai-agent-skills/SKILLS_SUMMARY.md).

The toolkit currently covers:
- **Core Abstractions**: `manage-entities`, `manage-services`, `manage-data`.
- **UI & Interaction**: `manage-screens`, `manage-forms`, `manage-menus`, `manage-templates`, `manage-ajax`.
- **Logic & Flows**: `manage-groovy`, `manage-java`, `manage-java-patterns`, `manage-eca`, `manage-service-groups`.
- **Integrations**: `manage-api-integration`, `manage-email-services`.
- **Advanced Management**: `manage-security-advanced`, `manage-localization-advanced`, `manage-webapps`, `manage-cache-and-performance`.
- **Strategies**: `manage-strategies` (Xml vs Java/Groovy, Dos and Donts).
- **Quality Gates**: `precommit-readiness` (pre-commit hooks, CodeNarc, Groovy/Java compilation, XML validation, and focused tests).

## Deployment and Updates
This plugin is linked to the GitHub repository. To update the skills available to your agent, pull the latest changes from the repository.
