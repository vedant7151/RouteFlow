/** Pure helpers shared by pages/components (kept free of React so they are trivially unit-testable). */

/** Backend polylines are "lat,lng;lat,lng;..." strings. */
export function parsePolyline(encoded) {
  if (!encoded) return [];
  return encoded
    .split(";")
    .map((pair) => pair.split(",").map(Number))
    .filter(([lat, lng]) => Number.isFinite(lat) && Number.isFinite(lng));
}

/** Order/stop text is user-supplied and ends up in Leaflet popups as HTML, so it must be escaped. */
export function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (ch) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[ch]);
}

export function formatTime(iso) {
  if (!iso) return "-";
  return new Date(iso).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

export function formatDateTime(iso) {
  if (!iso) return "-";
  return new Date(iso).toLocaleString([], { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" });
}

/** ISO instant -> the "YYYY-MM-DDTHH:mm" (local time) string a datetime-local input expects. */
export function toDateTimeLocal(iso) {
  if (!iso) return "";
  const d = new Date(iso);
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export const ROUTE_STATUS_BADGE = {
  PLANNED: "badge-blue",
  DISPATCHED: "badge-amber",
  IN_PROGRESS: "badge-amber",
  COMPLETED: "badge-green",
  CANCELLED: "badge-red",
};

export const STOP_STATUS_BADGE = {
  PENDING: "",
  EN_ROUTE: "badge-blue",
  ARRIVED: "badge-amber",
  DELIVERED: "badge-green",
  FAILED: "badge-red",
  SKIPPED: "badge-red",
};

export const ORDER_STATUS_BADGE = {
  PENDING: "",
  ASSIGNED: "badge-blue",
  EN_ROUTE: "badge-blue",
  ARRIVED: "badge-amber",
  DELIVERED: "badge-green",
  FAILED: "badge-red",
};

export function isTerminalStop(status) {
  return status === "DELIVERED" || status === "FAILED" || status === "SKIPPED";
}

/** CSS class (see --marker-* tokens in theme.css) for a stop/order pin, by lifecycle status. */
export function markerClassForStatus(status) {
  if (status === "DELIVERED") return "map-marker map-marker-delivered";
  if (status === "FAILED" || status === "SKIPPED") return "map-marker map-marker-failed";
  if (status === "EN_ROUTE" || status === "ARRIVED" || status === "ASSIGNED") return "map-marker map-marker-active";
  return "map-marker map-marker-pending";
}

export const CSV_TEMPLATE =
  "ref,addressText,lat,lng,timeWindowStart,timeWindowEnd,load,priority,notes,customerName,customerPhone\n" +
  'CSV-001,"Bandra Fort, Mumbai",19.0421,72.8189,,,2,1,Fragile,Asha Rao,+91 98200 00001\n' +
  'CSV-002,"Powai Lake, Mumbai",,,2026-09-21T05:30:00Z,2026-09-21T09:30:00Z,1,0,Leave at gate,Vikram Sen,\n';

/** Half-way point between a coordinate and a target, nudged ~100 m short of it - for the driver "demo" GPS button. */
export function pointNear(lat, lng, offsetDeg = 0.001) {
  return { lat: lat + offsetDeg, lng: lng + offsetDeg };
}

/** Blank order form values (all strings - they are bound to inputs). */
export const emptyOrderForm = {
  ref: "",
  customerName: "",
  customerPhone: "",
  addressText: "",
  lat: "",
  lng: "",
  load: "1",
  priority: "0",
  notes: "",
  windowStart: "",
  windowEnd: "",
};
