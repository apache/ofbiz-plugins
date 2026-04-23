# Picking PWA - Phase 5: Scanning & Recording

This phase implements the high-performance picking interface with scanning support.

## Proposed Changes

### [Frontend] Picking Interface
#### [NEW] [PickingDetail.jsx](file:///Users/arun/personal/arun/ofbiz_dev/picking-app/src/screens/PickingDetail.jsx)
- Interactive view for the current target item.
- Visual display of: Location, Product Name, SKU, and Quantity to pick.

### [Frontend] Scanning Integration
#### [NEW] [ScannerManager.js](file:///Users/arun/personal/arun/ofbiz_dev/picking-app/src/utils/ScannerManager.js)
- **Camera Scan**: Wrapper for `html5-qrcode` to enable camera-based scanning.
- **Hardware Scan**: Global `keydown` listener to capture input from Bluetooth/HID scanners.
- Automatic focus management to ensure scanning works without manual tap.

### [Integration] Pick Recording
Implement the link to the `POST /api/picking/picklist/bin/item/pick` API to update the backend as items are scanned or manually confirmed.
- Handle optimistic UI updates for a "fast feel".

## Verification Plan

### Manual Verification
- Test camera scanning with a mock QR code/Barcode on a smartphone.
- Simulate hardware scanning by typing a SKU into the browser while the listener is active.
- Verify status changes in the UI after a successful scan.
