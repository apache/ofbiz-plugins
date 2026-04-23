# Picking PWA - Phase 3: Frontend Core & Design System

This phase bootstraps the PWA and establishes the premium design system.

## Proposed Changes

### [Frontend] Project Initialization
Located in `/Users/arun/personal/arun/ofbiz_dev/picking-app`.

#### [NEW] [Vite + React Setup]
- Initialize project: `npx -y create-vite@latest ./ --template react-swc`.
- Install dependencies: `react-router-dom`, `lucide-react` (icons), `jwt-decode`.

### [Frontend] Design System
#### [NEW] [index.css](file:///Users/arun/personal/arun/ofbiz_dev/picking-app/src/index.css)
- Implement **Industrial Dark** theme using CSS variables:
    ```css
    :root {
      --bg-primary: #0a0a0c;
      --bg-secondary: #161618;
      --accent: #f59e0b; /* Amber for industrial feel */
      --glass-bg: rgba(255, 255, 255, 0.05);
      --glass-border: rgba(255, 255, 255, 0.1);
    }
    ```
- Utility classes for glassmorphism effects and transitions.

#### [NEW] [App.jsx](file:///Users/arun/personal/arun/ofbiz_dev/picking-app/src/App.jsx)
- Set up routing and layout wrappers.
- Global state for authentication and facility selection.

## Verification Plan

### Manual Verification
- Verify the dev server starts (`npm run dev`).
- Check responsiveness on various screen sizes via Chrome DevTools.
- Verify fonts and color tokens render correctly according to the mockup.
