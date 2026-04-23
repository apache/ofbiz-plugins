# PWA Picking Application for OFBiz

The goal is to provide a modern, mobile-first Progressive Web App (PWA) for warehouse operators to manage the picking process efficiently. This includes creating picklists from approved orders, performing the physical picking, and completing the picklists.

## User Review Required

> [!IMPORTANT]
> The PWA will be built as a **standalone Vite + React project** located in `/Users/arun/personal/arun/ofbiz_dev/picking-app`. It will not be hosted as an OFBiz webapp.

> [!NOTE]
> We will leverage the existing `rest-api` plugin in OFBiz to provide the backend services. We may need to configure CORS in OFBiz to allow requests from the standalone frontend.

## Proposed Changes

### [Backend] Picking Component

We will create a new component `plugins/picking` to house all picking-related logic and the PWA itself.

#### [NEW] [ofbiz-component.xml](file:///Users/arun/personal/arun/ofbiz_dev/ofbiz-framework/plugins/picking/ofbiz-component.xml)
Defines the component and its services. No `webapp` will be defined here for the picking app.

#### [NEW] [services.xml](file:///Users/arun/personal/arun/ofbiz_dev/ofbiz-framework/plugins/picking/servicedef/services.xml)
Definition of business services for the picking process:
- `picking.getOrdersToPick`: Returns a list of orders ready for picking.
- `picking.createPicklist`: Groups selected orders into a new Picklist.
- `picking.getPicklistForPicking`: Returns detailed picklist data (items, locations, quantities) optimized for the PWA.
- `picking.recordPick`: Updates the status and quantity of a picklist item.
- `picking.completePicklist`: Finalizes the picklist.

#### [NEW] [PickingServices.groovy](file:///Users/arun/personal/arun/ofbiz_dev/ofbiz-framework/plugins/picking/src/main/groovy/org/apache/ofbiz/picking/PickingServices.groovy)
Implementation of the above services using standard OFBiz entity and service patterns.

---

### [Frontend] Standalone PWA Application

A modern React-based PWA located in `/Users/arun/personal/arun/ofbiz_dev/picking-app`.

#### [NEW] [Project Structure]
- **Vite**: Build tool for the standalone project.
- **PWA Features**: Service worker for offline capability and manifest for "Add to Home Screen".
- **Design System**: Premium "Industrial Dark" theme using Vanilla CSS and glassmorphism.

#### [NEW] [UI Screens]
1. **Facility Selection**: Choose target warehouse.
2. **Order Picking Queue**: List and select orders to create a picklist.
3. **Active Picklist**: Sorted list of items by location.
4. **Picking Interface**: Advanced scanning support:
    - **Camera Scan**: Integrated via `html5-qrcode` or similar for mobile camera use.
    - **Hardware Scan**: Global listener for keyboard emulation (Bluetooth/USB scanners) with automated input focus.
    - Manual quantity entry and confirmation.
5. **Success Summary**: Confirmation and picklist completion.

### [Design Mockup]

![PWA Picking App Mockup](/Users/arun/.gemini/antigravity/brain/dbf793e9-6761-453f-81b1-33ebf9205a7a/pwa_picking_app_mockup_1775804263452.png)

---

### [Integration] API & Security

- **CORS**: Configure the `rest-api` or global OFBiz settings to allow origin `http://localhost:5173` (default Vite port) for development.
- **Auth**: Use `generateAuthToken` for JWT-based session management.
- **Permissions**: Standard OFBiz permissions (`FACILITY_VIEW`, `FACILITY_CREATE`) will be required.

## Open Questions

> [!NOTE]
> All design and technical requirements have been addressed. Ready for execution.

## Verification Plan

### Automated Tests
- OFBiz Service Unit Tests for picking logic.
- Mocked API tests for frontend components.

### Manual Verification
- end-to-end flow: Find Orders -> Create Picklist -> Pick All Items -> Complete.
- Verify inventory updates (if applicable) and picklist status changes in the backend.
