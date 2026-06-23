# Picking PWA - Phase 1: Backend Foundation

This phase focuses on extending the standard `product` component and implementing/exposing the services required for the picking process.

## Proposed Changes

### [Backend] Product Component Extension

We will modify standard files to implement and expose the necessary endpoints:

#### [MODIFY] [services_picklist.xml](file:///Users/arun/personal/arun/ofbiz_dev/ofbiz-framework/applications/product/servicedef/services_picklist.xml)
REST-enable and define services:
- **`createPicklistFromOrders`**: Standard service exposed via `export="true"`, `action="POST"`.
- **`setPicklistItemToComplete`**: Standard service exposed via `export="true"`, `action="POST"`, with `itemStatusId` overridden to be optional.
- **`getOrdersToPick`**: Custom wrapper service added. `action="GET"`, `export="true"`.
- **`getPicklistDetails`**: Custom wrapper service added. `action="GET"`, `export="true"`.

> [!IMPORTANT]
> **Status Transitions**: The transition of the `Picklist` to `PICKLIST_PICKED` status is handled by the **existing SECA** (`checkPicklistBinItemStatuses`) which triggers automatically when all items in a bin are marked completed (via `setPicklistItemToComplete`).

#### [MODIFY] [PicklistServices.groovy](file:///Users/arun/personal/arun/ofbiz_dev/ofbiz-framework/applications/product/src/main/groovy/org/apache/ofbiz/product/shipment/PicklistServices.groovy)
Append the two wrapper services:
- **`getOrdersToPick`**: Transforms `pickMoveInfoList` structure from standard `findOrdersToPickMove` to a flat, simple `orderList` for the PWA.
- **`getPicklistDetails`**: Flattens `picklistInfo.picklistBinInfoList` structure from standard `getPickAndPackReportInfo` to a flat `items` list for the PWA.


## Verification Plan

### Automated Tests
- Integration tests using the OFBiz Service Engine (`test-run` command).
- Validate that `createPicklist` correctly associates all items from selected orders.
- Confirm `recordPick` correctly updates items and that the overall Picklist transitions to `PICKLIST_PICKED` (via SECA).

### Manual Verification
- Use the OFBiz Web Tools (Service Engine) to manually trigger the services with mock data and verify the entity state in the database.
