# RouteFlow frontend

React 19 + Vite. Dispatcher console (dashboard, orders, live map, drivers, customers, analytics) and a
mobile driver view. See the top-level [README](../README.md) for the full walkthrough.

```powershell
npm install
npm run dev        # http://localhost:5173  (needs the backend on http://localhost:8080)
npm test           # vitest - pure helpers in src/utils.js
npm run lint       # oxlint
npm run build
```

Set `VITE_API_BASE_URL` (see `.env.example`) if the backend is not on `http://localhost:8080`.

## Layout

| Path | What |
|---|---|
| `src/api.js` | every backend call; session in `sessionStorage`, transparent token refresh, authenticated STOMP |
| `src/utils.js` | pure helpers (polyline parsing, HTML escaping, status -> badge/marker maps) |
| `src/pages/` | `Dashboard`, `Orders`, `LiveMap`, `Drivers`, `Customers`, `Analytics`, `DriverView`, `Login` |
| `src/components/` | `OrderMap` (pins + pin-drop), `OrderForm`, `Modal` (portaled), `PodModal`, `SignaturePad`, ... |
| `src/theme.css` | **all design tokens** |
| `src/index.css` | component styles - only reference tokens |

## Restyling

Edit `src/theme.css` only. Colours (incl. chart palette and map-marker colours), spacing, radius,
shadows, font and layout widths are CSS variables; no component hardcodes them. To add a dark skin,
duplicate the `:root` block under `[data-theme="dark"]` and set `document.documentElement.dataset.theme`.

## Notes

- Leaflet markers are `<span>`s styled by `.map-marker*` (they must be `display: block`).
- Map popups are HTML strings - anything user-supplied goes through `escapeHtml`.
- Modals render into `document.body` (portal) with a z-index above Leaflet's panes/controls.
