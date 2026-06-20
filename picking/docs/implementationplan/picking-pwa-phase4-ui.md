# Picking PWA - Phase 4: Workflow UI

This phase implements the screens for browsing orders and managing the active picklist.

## Proposed Changes

### [Frontend] Workflow Screens
Implementation of core components in `picking-app/src/screens/`.

#### [NEW] [OrderQueue.jsx](file:///Users/arun/personal/arun/ofbiz_dev/picking-app/src/screens/OrderQueue.jsx)
- Display a list of orders ready for picking (using `GET /rest/services/getOrdersToPick`).
- Multi-select functionality to group orders.
- "Create Picklist" button triggering `POST /rest/services/createPicklist`.

#### [NEW] [ActivePicklist.jsx](file:///Users/arun/personal/arun/ofbiz_dev/picking-app/src/screens/ActivePicklist.jsx)
- Displays the items of a specific picklist (using `GET /rest/services/getPicklistDetails`).
- Grouping by Warehouse Aisle/Location for optimal picking.
- Visual status indicators (To Pick, In Progress, Completed).
- "Complete Run" button (appears when all items picked) navigating to Phase 6 flow.

### [Frontend] Integration Hooks
#### [NEW] [usePickingApi.js](file:///Users/arun/personal/arun/ofbiz_dev/picking-app/src/hooks/usePickingApi.js)
- Custom hook to handle API calls with the JWT token.
- Centralized error handling for the picking workflow.

## Verification Plan

### Manual Verification
- Verify that clicking "Create Picklist" navigates the user to the Active Picklist for that specific ID.
- Check that the sorting logic (by location) is visible in the UI.
