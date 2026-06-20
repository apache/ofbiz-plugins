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
name: manage-api-integration
description: Define and integrate REST and SOAP APIs in OFBiz.
---

# Manage API Integration

Integration patterns for exposing OFBiz services via REST and SOAP, and handling JSON responses.

## REST API (rest-api)

OFBiz uses the `rest-api` (based on Jersey) to declaratively expose services.

### REST API Definitions

REST endpoints are defined in `*.rest.xml` files located in the `api/` directory of an OFBiz component. These files are automatically discovered and registered by the `rest-api` plugin; no explicit registration in `ofbiz-component.xml` is required.

**Pattern for `*.rest.xml`:**
```xml
<api name="Example API" path="example" ...>
    <resource name="Example" path="examples" ...>
        <operation verb="GET" path="/{exampleId}" service="findExampleById" description="Get Example">
            <service name="findExampleById" />
        </operation>
        <operation verb="POST" path="/create" service="createExample" description="Create Example">
            <service name="createExample" />
        </operation>
    </resource>
</api>
```

### Parameter Handling

Parameter mapping from REST requests to OFBiz services is **implicit**. The `rest-api` plugin automatically extracts parameters from the request and maps them to service IN parameters whose names match.

*   **Path Parameters:** Extracted from the URI based on placeholders in the `path` attributes (e.g., `{exampleId}`).
*   **Query Parameters:** Extracted from the request URL.
*   **Request Body:** For `POST`, `PUT`, and `PATCH` requests with `Content-Type: application/json`, the JSON payload is automatically parsed into a map.

All extracted parameters are aggregated into a single context. The `ServiceRequestHandler` then uses `dispatcher.getDispatchContext().makeValidContext()` to select only those parameters that are defined as IN parameters for the target service.

### Security and Response Handling
*   **Authentication:** Controlled by the `auth="true|false"` attribute on the `<operation>` element.
*   **Responses:** Services return their OUT parameters (excluding internal ones) as a JSON object. The response is automatically prefixed with `&&&START&&&` for XSSI protection.

### Core Components
- `OFBizApiConfig.java`: Scans and registers REST definitions.
- `ServiceRequestHandler.java`: Dispatches REST requests to the OFBiz Service Dispatcher.

## Direct Service REST Export

OFBiz allows services to be exposed as REST APIs directly via attributes in the service definition.

### Pattern:
- **Attributes**: Set `export="true"` and `action="VERB"` (e.g., `action="GET"`).
- **Result**: The service becomes reachable as a REST endpoint (e.g., `/rest/public/findProductById` or similar depending on the rest-api plugin configuration).

```xml
<service name="findProductById" engine="java" auth="true" export="true" action="GET" ...>
    <attribute name="productId" mode="IN" type="String" optional="false"/>
</service>
```

### Key Considerations:
- **Simplicity**: No need for separate `rest.xml` or controller mappings.
- **Contract-first**: The service signature defines the API request/response.
- **Auth**: Inherits service engine authentication rules.

## JSON Responses

For standard controller-based AJAX/API calls, use the `json` response type.

### Controller Configuration
In `controller.xml`:
```xml
<request-map uri="getJsonData">
    <event type="service" invoke="someService"/>
    <response name="success" type="request" value="json"/>
    <response name="error" type="request" value="json"/>
</request-map>
```

### Response Handling
`CommonEvents.jsonResponseFromRequestAttributes` (or `CommonEvents.jsonResponseFromServiceResults` in some versions) processes the request attributes and writes a JSON object to the response.
- **Security**: A `&&&START&&&` prefix is added to GET responses to prevent JSON hijacking (XSSI).

## SOAP Integration

OFBiz provides built-in SOAP support via the `SOAPEventHandler`.

### Exposing a Service as SOAP
1. Set `export="true"` in the service definition.
2. Ensure the `soap` handler is enabled in `handlers-controller.xml`.
3. Access the WSDL at `https://[host]/[webapp]/control/SOAPService/[ServiceName]?wsdl`.

### Invoking a Remote SOAP Service
Use the `soap-engine` or `SOAPClientEngine`.
```xml
<service name="remoteSoapCall" engine="soap" location="http://remote-host/control/SOAPService">
    <implements service="localServiceTemplate"/>
</service>
```

## Security & Data Mapping

- **Authentication**: REST API typically uses JWT or API Keys (configured in `rest-api` common filters).
- **Permissions**: Every service invoked should have proper `permission-service` or `check-permission` logic.
- **Data Mapping**: JSON/XML fields must precisely match service IN attributes for automatic mapping. Use `ServiceUtil.getAndValidateParameters` pattern in custom Java events if manual mapping is needed.
