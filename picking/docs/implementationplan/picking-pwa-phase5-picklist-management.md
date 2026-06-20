# Picking PWA - Phase 5: Picklist Management (Find, List & Actions)

This phase implements the screens and endpoints for finding, listing, resuming, canceling, and printing active picklists in the warehouse.

## Proposed Changes

### [Backend] Picklist Services

#### [MODIFY] [services.xml](file:///Users/arun/personal/arun/ofbiz_dev/ofbiz-framework/plugins/picking/servicedef/services.xml)
- Define `getPickingPicklists`:
  - Input: `facilityId` (String, required), `statusId` (String, optional).
  - Output: `picklistList` (List of Map).
- Define `cancelPickingPicklist`:
  - Input: `picklistId` (String, required).
  - Output: standard success/error.
- Define `getPickingPicklistPdf`:
  - Input: `picklistId` (String, required).
  - Output: `pdfBase64` (String) representing the Base64-encoded PDF binary data.

#### [MODIFY] [PickingServices.groovy](file:///Users/arun/personal/arun/ofbiz_dev/ofbiz-framework/plugins/picking/src/main/groovy/org/apache/ofbiz/picking/PickingServices.groovy)
- Implement `getPickingPicklists()`:
  - Query all `Picklist` records matching `facilityId`.
  - Filter out `PICKLIST_CANCELLED` and `PICKLIST_PICKED` by default.
  - Calculate counts: `totalOrders` (bins size), `totalItems` (items count), and `pickedItems` (`itemStatusId == 'PICKITEM_COMPLETED'`).
- Implement `cancelPickingPicklist()`:
  - Wrap and invoke standard OFBiz service `cancelPicklistAndItems`.
- Implement `getPickingPicklistPdf()`:
  - Render screen `component://product/widget/facility/FacilityScreens.xml#PicklistReport.fo` using programmatic `ScreenRenderer` with `foScreenStringRenderer` and `FoFormRenderer`.
  - Convert output FO string to PDF byte array via `ApacheFopWorker.createFopInstance` and `ApacheFopWorker.transform`.
  - Base64 encode the resulting byte array and return it.

### [Frontend] PWA Navigation & List Page

#### [NEW] [PicklistList.jsx](file:///Users/arun/personal/arun/ofbiz_dev/picking-app/src/screens/PicklistList.jsx)
- A dashboard screen listing active picklists for the facility.
- Display picklist details in an "Industrial Dark" glass card:
  - Run ID, Date, Status badge.
  - Progress bar (`pickedItems` vs `totalItems`).
  - Action button: **Resume Picking** (links to `/picklist/:picklistId`).
  - Action button: **Print PDF** (shows a spinner, triggers PDF generation, decodes Base64 to Blob, and opens/saves in browser).
  - Action button: **Cancel Run** (shows confirmation dialog, triggers cancellation service, and refreshes the list).
- Toggle filters to view either active runs or all runs.

#### [MODIFY] [App.jsx](file:///Users/arun/personal/arun/picking-app/src/App.jsx)
- Register route `/picklists` pointing to `<PicklistList />`.
- Add a persistent **navigation header menu** or direct-jump button in the top-right header layout (using `FileText` or `ClipboardList` icon) to let users immediately jump to the **Picklists Dashboard** from any screen.

#### [MODIFY] [usePickingApi.js](file:///Users/arun/personal/arun/picking-app/src/hooks/usePickingApi.js)
- Implement API call hooks:
  - `getPickingPicklists(facilityId, statusId)`
  - `cancelPickingPicklist(picklistId)`
  - `getPickingPicklistPdf(picklistId)`
- Include fallback mock implementations for printing and cancellation to allow full offline development and review.

## Verification Plan

### Manual Verification
1. Navigate to the **Picklists Dashboard** using the header's quick-jump icon.
2. Select a picklist card and click **Print PDF**. Confirm that a standard picksheet PDF downloads or opens in a new tab.
3. Click **Cancel Run** on an active picklist, confirm the dialog, and verify it is removed from the active list.
4. Verify that picking progress updates on the dashboard list in real time after resuming and picking some items.
