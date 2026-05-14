# AI Plugin for Apache OFBiz

LangChain4j integration that exposes AI/LLM capabilities as standard OFBiz services.

Apache JIRA: https://issues.apache.org/jira/browse/OFBIZ-13408

## What this plugin does

The AI plugin connects OFBiz to any OpenAI-compatible chat model via LangChain4j 1.8.0.
It provides two callable OFBiz services — `aiGenerate` for free-text responses and
`aiGenerateStructured` for JSON-schema-constrained structured output — that any other
service or Groovy script can call without depending on LangChain4j directly.

## Architecture

| Layer | Class | Role |
|---|---|---|
| Container | `AiContainer` | Reads `ai.properties` at startup, builds the `ChatModel`, calls `AiFactory.setChatModel()` |
| Singleton | `AiFactory` | Holds the live `ChatModel` instance; throws `IllegalStateException` if not initialized |
| Utility | `AiWorker` | Static `generate()` and `generateStructured()` methods; handles message conversion and JSON schema mapping |
| Services | `AiServices` | Thin OFBiz service wrappers that delegate to `AiWorker` and return standard service result maps |

## Prerequisites

- Java 17 or later
- OFBiz trunk
- An API key from OpenAI or a compatible provider (Ollama, Groq, Together, Azure OpenAI)

## Installation

1. The plugin is already placed at `plugins/ai/` inside the OFBiz source tree.
2. Copy the properties template and fill in your values:
   ```
   cp plugins/ai/config/ai.properties.template plugins/ai/config/ai.properties
   ```
   If no template exists, create `plugins/ai/config/ai.properties` from the table below.
3. Start OFBiz normally. The container will log:
   ```
   AI plugin initialized: provider=openai model=gpt-4o-mini
   ```
   If the API key is missing or placeholder, startup continues but the plugin logs an error and skips initialization.

## Configuration

Edit `plugins/ai/config/ai.properties` (this file is gitignored — never commit API keys).

| Property | Description | Default |
|---|---|---|
| `ai.provider` | Provider name. Currently used for logging; the `openai` engine handles all OpenAI-compatible endpoints. | `openai` |
| `ai.model` | Model name passed to the provider. | `gpt-4o-mini` |
| `ai.baseUrl` | Base URL override. Leave empty for the OpenAI default. Set for local or third-party endpoints. | _(empty)_ |
| `ai.apiKey` | Your API key. **Required.** | _(none)_ |
| `ai.timeout` | Request timeout in seconds. | `60` |

## Multiple providers

Setting `ai.baseUrl` makes the plugin work with any OpenAI-compatible endpoint:

| Provider | `ai.baseUrl` |
|---|---|
| OpenAI (default) | _(leave empty)_ |
| Ollama | `http://localhost:11434` |
| Groq | `https://api.groq.com/openai/v1` |
| Together AI | `https://api.together.xyz/v1` |
| Azure OpenAI | your Azure endpoint URL |

## Usage

### Generate free-text (from a Groovy service)

```groovy
import org.apache.ofbiz.ai.AiWorker

def messages = [
    [role: "system",  content: "You are a helpful assistant."],
    [role: "user",    content: "Summarize this order in one sentence."]
]

String response = AiWorker.generate(dctx, messages)
```

### Generate structured output

Schema values can be a plain type string (`"string"`, `"number"`, `"integer"`, `"boolean"`,
`"array"`, `"object"`) or a map with `type` and optional `properties`/`items` for nested shapes.

```groovy
import org.apache.ofbiz.ai.AiWorker

def messages = [
    [role: "user", content: "Extract the product name and price from: 'Widget Pro costs \$49.99'"]
]

def schema = [
    productName: "string",
    price:       "number"
]

Map result = AiWorker.generateStructured(dctx, messages, schema)
// result == [productName: "Widget Pro", price: 49.99]
```

Both methods throw `GeneralException` on failure; callers should catch it and return
`ServiceUtil.returnError()` as appropriate.

## Available services

| Service | IN | OUT | Description |
|---|---|---|---|
| `aiGenerate` | `messages` (List, required)<br>`configName` (String, optional) | `response` (String) | Free-text generation |
| `aiGenerateStructured` | `messages` (List, required)<br>`schema` (Map, required)<br>`configName` (String, optional) | `result` (Map) | Structured JSON output |

Each message in the `messages` list is a `Map` with keys `role` (`system`, `user`, or `assistant`) and `content` (String).

## Smoke test

With OFBiz running and a valid API key configured, invoke `aiSmokeTest` from the
webtools service runner:

```
https://localhost:8443/webtools/control/main
→ Service Engine → Run Service → aiSmokeTest
```

A successful run logs:
```
AI smoke test response: Hello
```

## Adding new providers

To support a provider that is not OpenAI-compatible (e.g., Anthropic native, Amazon Bedrock),
add a new `case` to the `switch (provider)` block in `AiContainer.java`. Instantiate the
provider's LangChain4j `ChatModel` builder, call `AiFactory.setChatModel(chatModel)`, and
add the corresponding `dev.langchain4j:langchain4j-<provider>` dependency to `build.gradle`.
No changes to `AiFactory`, `AiWorker`, or `AiServices` are needed.
