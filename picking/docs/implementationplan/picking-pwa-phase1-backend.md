# Picking PWA - Phase 1: Backend Foundation

This phase focuses on setting up the `picking` component and implementing the core business services required for the picking process.

## Proposed Changes

### [Backend] Picking Component Setup
We will initialize the component structure and define the necessary services.

#### [NEW] [ofbiz-component.xml](file:///Users/arun/personal/arun/ofbiz_dev/ofbiz-framework/plugins/picking/ofbiz-component.xml)
Defines the component metadata and resource paths for services.

#### [NEW] [services.xml](file:///Users/arun/personal/arun/ofbiz_dev/ofbiz-framework/plugins/picking/servicedef/services.xml)
Definition of business services:
- **`getOrdersToPick`**: Wraps `findOrdersToPickMove`. `action="GET"`, `export="true"`.
- **`createPicklist`**: Wraps `createPicklistFromOrders`. `action="POST"`, `export="true"`.
- **`getPicklistDetails`**: Wraps `getPickAndPackReportInfo`. `action="GET"`, `export="true"`.
- **`recordPick`**: Wraps `setPicklistItemToComplete`. `action="POST"`, `export="true"`.

> [!IMPORTANT]
> **Status Transitions**: We are not creating a custom `completePicklist` service. The transition of the `Picklist` to `PICKLIST_PICKED` status will be handled by the **existing SECA** (`checkPicklistBinItemStatuses`) which triggers automatically when all items in a bin are marked completed.

#### [NEW] [PickingServices.groovy](file:///Users/arun/personal/arun/ofbiz_dev/ofbiz-framework/plugins/picking/src/main/groovy/org/apache/ofbiz/picking/PickingServices.groovy)
Implementation of the logic using Groovy DSL:
- **Logic Consistency**: Every custom service MUST call the corresponding `runService("existingService", ...)` to ensure transactions, reservations, and business rules remain unchanged.
- **Data Transformation**: The main responsibility of these Groovy services is to "flatten" the complex Map structures returned by standard OFBiz into simple, flat JSON-serializable Lists and Maps for the PWA.
- **Business Validation**: Ensuring orders aren't already on a picklist and validating picked quantities against available reservations.

## Verification Plan

### Automated Tests
- Integration tests using the OFBiz Service Engine (`test-run` command).
- Validate that `createPicklist` correctly associates all items from selected orders.
- Confirm `recordPick` correctly updates items and that the overall Picklist transitions to `PICKLIST_PICKED` (via SECA).

### Manual Verification
- Use the OFBiz Web Tools (Service Engine) to manually trigger the services with mock data and verify the entity state in the database.
