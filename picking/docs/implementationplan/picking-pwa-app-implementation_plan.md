# PWA Picking Application for OFBiz

The goal is to provide a modern, mobile-first Progressive Web App (PWA) for warehouse operators to manage the picking process efficiently. This includes creating picklists from approved orders, performing the physical picking, and completing the picklists.

## User Review Required

> [!IMPORTANT]
> The PWA will be built as a **standalone Vite + React project** located in `/Users/arun/personal/arun/ofbiz_dev/picking-app`. It will not be hosted as an OFBiz webapp.

> [!NOTE]
> We will leverage the existing `rest-api` plugin in OFBiz to provide the backend services. We may need to configure CORS in OFBiz to allow requests from the standalone frontend.

## Proposed Changes

### [Backend] Product Component Extensions

We will extend the standard `product` application component to support the picking PWA:
- Add wrapper/data-formatting services to standard `services_picklist.xml` and `PicklistServices.groovy` in `applications/product`.
- Expose the necessary standard services directly on the REST API.

#### [MODIFY] [services_picklist.xml](file:///Users/arun/personal/arun/ofbiz_dev/ofbiz-framework/applications/product/servicedef/services_picklist.xml)
- **`createPicklistFromOrders`**: Standard service exposed via `export="true"` and `action="POST"`.
- **`setPicklistItemToComplete`**: Standard service exposed via `export="true"`, `action="POST"`, and `itemStatusId` set to optional (internally sets it to `PICKITEM_COMPLETED`).
- **`getOrdersToPick`**: Custom wrapper service added with `engine="groovy"`, `export="true"`, `action="GET"`.
- **`getPicklistDetails`**: Custom wrapper service added with `engine="groovy"`, `export="true"`, `action="GET"`.

#### [MODIFY] [PicklistServices.groovy](file:///Users/arun/personal/arun/ofbiz_dev/ofbiz-framework/applications/product/src/main/groovy/org/apache/ofbiz/product/shipment/PicklistServices.groovy)
Implementation of `getOrdersToPick` and `getPicklistDetails` to format/flatten the outputs of standard `findOrdersToPickMove` and `getPickAndPackReportInfo` services.

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
3. **Picklist Management**: Browse and search active/completed picklists, with progress indicators.
4. **Active Picklist**: Sorted list of items by location (Picklist Detail).
5. **Picking Interface**: Advanced scanning support:
    - **Camera Scan**: Integrated via `html5-qrcode` or similar for mobile camera use.
    - **Hardware Scan**: Global listener for keyboard emulation (Bluetooth/USB scanners) with automated input focus.
    - Manual quantity entry and confirmation.
6. **Success Summary**: Confirmation and picklist completion.

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
- end-to-end flow: Find Orders -> Create Picking Picklist -> Pick All Items -> Complete.
- Verify inventory updates (if applicable) and picklist status changes in the backend.
