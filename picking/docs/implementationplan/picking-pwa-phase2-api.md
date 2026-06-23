# Picking PWA - Phase 2: API Gateway & Security

This phase focuses on making the backend services accessible to the frontend PWA securely.

## Proposed Changes

### [Backend] API Exposure
Instead of creating custom REST route mappings, the services defined in Phase 1 will be automatically exposed via OFBiz's generic dynamic REST API by setting `export="true"` and the correct `action` attribute on their service definitions in [services_picklist.xml](file:///Users/arun/personal/arun/ofbiz_dev/ofbiz-framework/applications/product/servicedef/services_picklist.xml).

The corresponding endpoints will be:
- **Get Orders to Pick**: `GET /rest/services/getOrdersToPick` (wrapper service)
- **Create Picklist**: `POST /rest/services/createPicklistFromOrders` (standard service)
- **Get Picklist Details**: `GET /rest/services/getPicklistDetails` (wrapper service)
- **Record Pick**: `POST /rest/services/setPicklistItemToComplete` (standard service)


### [Backend] Security & CORS
#### [MODIFY] [security.properties](file:///Users/arun/personal/arun/ofbiz_dev/ofbiz-framework/framework/security/config/security.properties)
Configure the allowed origins list to support cross-origin requests from the standalone frontend PWA (running on Vite):
- Add `cors.origins.allowed=http://localhost:5173`

#### [Auth] JWT Integration
- Authenticate requests using the standard `rest-api` JWT session mechanism.
- Validate that the PWA can fetch and exchange user credentials for a token via the `POST /rest/auth/token` endpoint.

## Verification Plan

### Automated Tests
- Postman or `curl` test collection verifying that:
    - Root API endpoints (e.g. `GET /rest/services/getOrdersToPick`) are accessible when authorized.
    - Protected endpoints return `401 Unauthorized` without a valid token.
    - Valid JWT allows successful service execution.

### Manual Verification
- Verify CORS "Pre-flight" OPTIONS request from a browser console (simulating the PWA origin `http://localhost:5173`).
