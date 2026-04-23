# Picking PWA - Phase 2: API Gateway & Security

This phase focuses on making the backend services accessible to the frontend PWA securely.

## Proposed Changes

### [Backend] API Exposure
We will map the services defined in Phase 1 to REST endpoints.

#### [MODIFY] [rest-api config](file:///Users/arun/personal/arun/ofbiz_dev/ofbiz-framework/plugins/rest-api/...) 
(Exact file depends on the `rest-api` plugin implementation, usually a mapping XML or Java configuration).
- Map `picking.getOrdersToPick` -> `GET /api/picking/orders`
- Map `picking.createPicklist` -> `POST /api/picking/picklist/create`
- Map `picking.getPicklistForPicking` -> `GET /api/picking/picklist/{picklistId}`
- Map `picking.recordPick` -> `POST /api/picking/picklist/bin/item/pick`
- Map `picking.completePicklist` -> `POST /api/picking/picklist/{picklistId}/complete`

### [Backend] Security & CORS
#### [MODIFY] [url.properties](file:///Users/arun/personal/arun/ofbiz_dev/ofbiz-framework/framework/webapp/config/url.properties) (or equivalent component config)
- Configure `allowed-origins`: `http://localhost:5173` (Vite Default).
- Set `Access-Control-Allow-Methods` and `Access-Control-Allow-Headers` required for JWT and JSON payloads.

#### [Auth] JWT Integration
- Ensure the `rest-api` uses `generateAuthToken` for session management.
- Validate that the PWA can exchange user credentials for a token.

## Verification Plan

### Automated Tests
- Postman or `curl` test collection verifying that:
    - Root API is accessible.
    - Protected endpoints return `401 Unauthorized` without a token.
    - Valid JWT allows service execution.

### Manual Verification
- Verify CORS "Pre-flight" OPTIONS request from a browser console (simulating the PWA origin).
