# AI Plugin for Apache OFBiz

An optional plugin that brings LLM agent capabilities to OFBiz using only framework-native patterns — no external AI SDK, no framework modifications.

Apache JIRA: https://issues.apache.org/jira/browse/OFBIZ-13408  
Documentation: https://cwiki.apache.org/confluence/pages/viewpage.action?pageId=430407963

## What it does

Exposes a single OFBiz service — `agentRun` — that runs a named AI agent. Agents and tools are declared in XML. The plugin handles the LLM loop, tool dispatch, observability, permission enforcement, conversation memory, and human approval gating.

No third-party AI SDK. Uses `java.net.http.HttpClient` and Jackson, both already present in the OFBiz runtime.

## Prerequisites

- OFBiz trunk (Java 17+)
- An API key from OpenAI, Anthropic, or any OpenAI-compatible provider (Ollama, Groq, etc.)

## Installation

```bash
cp plugins/ai/config/ai.properties.template plugins/ai/config/ai.properties
```

Edit `ai.properties` — set your provider block and API key (this file is gitignored, never commit it).

## Configuration

Named provider blocks — add as many as needed:

```properties
ai.provider.openai-default.model=gpt-4o-mini
ai.provider.openai-default.apiKey=sk-...

ai.provider.anthropic-default.baseUrl=https://api.anthropic.com/v1
ai.provider.anthropic-default.model=claude-sonnet-4-6
ai.provider.anthropic-default.apiKey=sk-ant-...
ai.provider.anthropic-default.extraHeaders=anthropic-version:2023-06-01

ai.provider.ollama-default.baseUrl=http://localhost:11434
ai.provider.ollama-default.model=llama3
```

## Declaring tools and agents

Tools are OFBiz services exposed to the LLM, declared in `ai/*.tools.xml` inside any component:

```xml
<tools xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <tool name="getProductionRunStatus"
          service-name="getProductionRunStatus"
          requires-approval="false">
        <description>Returns the current status of a production run given its ID.</description>
    </tool>
    <tool name="updateProductionRunStatus"
          service-name="updateProductionRunStatus"
          requires-approval="true">
        <description>Updates the status of a production run. Requires human approval.</description>
    </tool>
</tools>
```

Agents are declared in `ai/*.agent.xml`:

```xml
<agent name="mrp-assistant" provider="anthropic-default" max-iterations="8">
    <system-prompt>You are an MRP analyst. Use the tools available to answer manufacturing queries.</system-prompt>
    <tool-allow-list>
        <tool name="getProductionRunStatus"/>
        <tool name="updateProductionRunStatus"/>
    </tool-allow-list>
</agent>
```

The plugin scans all components' `ai/` directories at startup.

## Usage

Call `agentRun` from any Groovy script, service, or ECA:

```groovy
Map result = dispatcher.runSync("agentRun", [
    agentName:   "mrp-assistant",
    userMessage: "Which production runs are at risk this week?",
    userLogin:   userLogin
])
String answer = result.assistantMessage
```

Multi-turn conversation — pass a `threadId`:

```groovy
Map result = dispatcher.runSync("agentRun", [
    agentName:   "mrp-assistant",
    userMessage: "What about next week?",
    threadId:    "thread-abc123",
    userLogin:   userLogin
])
// result.threadId — pass this back on the next call
```

Human approval — pass `approvalRequired: true` or mark individual tools with `requires-approval="true"`:

```groovy
Map result = dispatcher.runSync("agentRun", [
    agentName:        "mrp-assistant",
    userMessage:      "Update all at-risk runs to ON_HOLD.",
    approvalRequired: true,
    userLogin:        userLogin
])
if (result.stopReason == "approval_required") {
    String proposalId = result.proposalId
    // store proposalId — a reviewer calls approveAgentProposal or rejectAgentProposal
}
```

## Services

| Service | Purpose |
|---|---|
| `agentRun` | Run a named agent |
| `approveAgentProposal` | Execute pending tools and resume the agent loop |
| `rejectAgentProposal` | Reject a proposal; returns LLM acknowledgment |
| `getUsageSummary` | Token usage and estimated cost, filterable by agent/user/date |
| `getConversationHistory` | Messages for a thread in sequence order |
| `archiveConversationThread` | Mark thread archived |
| `aiGenerate` | Direct single-turn LLM call, no agent loop |
| `aiGenerateStructured` | Structured JSON output constrained by schema |

## Architecture

```
AiContainer (startup)
    ├── ProviderRegistry  — reads ai.properties named blocks
    ├── ToolCatalog       — scans all components' ai/*.tools.xml
    └── AgentRegistry     — scans all components' ai/*.agent.xml

agentRun
    └── AgentRunner
            ├── load thread history (if threadId)
            ├── loop: AiHttpClient → LLM → tool dispatch → repeat
            ├── persist AiAgentRun + AiAgentToolCall
            └── save conversation messages (if threadId)
```

## Admin UI

Mounted at `/ai` — requires `OFBTOOLS` permission.

| Screen | URL |
|---|---|
| Run History | `/ai/control/FindAiAgentRun` |
| Run Detail | `/ai/control/AiAgentRunDetail` |
| Usage Dashboard | `/ai/control/AiUsageDashboard` |
| Proposal Review | `/ai/control/FindAiAgentProposal` |
| Thread Explorer | `/ai/control/FindAiConversationThread` |

## Smoke test

Start OFBiz, then: webtools → Service Engine → Run Service → `aiSmokeTest`
