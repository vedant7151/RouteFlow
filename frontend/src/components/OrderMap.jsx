import { useEffect, useRef } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { escapeHtml, markerClassForStatus } from "../utils.js";

const DEFAULT_CENTER = [19.076, 72.8777];

function divIcon(className, size = 16) {
  return L.divIcon({ className: "", html: `<span class="${className}"></span>`, iconSize: [size, size] });
}

/**
 * Map of order pins (coloured by status) and vehicle depots. In pick mode a click on the map reports
 * the coordinates via onPick - the "manual pin-drop fallback" for addresses geocoding can't resolve.
 */
export default function OrderMap({ orders, depots = [], pickMode = false, picked = null, onPick }) {
  const elRef = useRef(null);
  const mapRef = useRef(null);
  const layerRef = useRef(null);
  const pickedMarkerRef = useRef(null);
  const fittedRef = useRef(false);
  const pickModeRef = useRef(pickMode);
  const onPickRef = useRef(onPick);

  useEffect(() => {
    pickModeRef.current = pickMode;
    onPickRef.current = onPick;
    elRef.current?.classList.toggle("map-picking", pickMode);
  }, [pickMode, onPick]);

  useEffect(() => {
    const map = L.map(elRef.current).setView(DEFAULT_CENTER, 11);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: "&copy; OpenStreetMap contributors",
      maxZoom: 19,
    }).addTo(map);
    layerRef.current = L.layerGroup().addTo(map);
    map.on("click", (e) => {
      if (pickModeRef.current) onPickRef.current?.(e.latlng.lat, e.latlng.lng);
    });
    mapRef.current = map;
    // The container changes width when the order form opens beside it; Leaflet must be told.
    const observer = new ResizeObserver(() => map.invalidateSize());
    observer.observe(elRef.current);
    return () => {
      observer.disconnect();
      map.remove();
      mapRef.current = null;
      pickedMarkerRef.current = null;
      fittedRef.current = false;
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    const layer = layerRef.current;
    if (!map || !layer) return;

    layer.clearLayers();
    const bounds = [];

    depots.forEach((depot) => {
      L.marker([depot.lat, depot.lng], { icon: divIcon("map-marker map-marker-depot", 18), zIndexOffset: -100 })
        .bindTooltip(`${escapeHtml(depot.label)} depot`)
        .addTo(layer);
      bounds.push([depot.lat, depot.lng]);
    });

    orders.forEach((order) => {
      if (order.lat == null || order.lng == null) return;
      L.marker([order.lat, order.lng], { icon: divIcon(markerClassForStatus(order.status)) })
        .bindPopup(
          `<div class="map-popup">
            <div class="map-popup-title">${escapeHtml(order.ref)}</div>
            <div>${escapeHtml(order.addressText)}</div>
            <div class="text-muted">${escapeHtml(order.customerName)} &middot; ${escapeHtml(order.status)}</div>
          </div>`
        )
        .addTo(layer);
      bounds.push([order.lat, order.lng]);
    });

    // Only auto-fit the first time there is something to show, so the map does not jump while you work.
    if (!fittedRef.current && bounds.length > 0) {
      map.fitBounds(bounds, { padding: [32, 32], maxZoom: 13 });
      fittedRef.current = true;
    }
  }, [orders, depots]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    pickedMarkerRef.current?.remove();
    pickedMarkerRef.current = null;
    if (picked && Number.isFinite(picked.lat) && Number.isFinite(picked.lng)) {
      pickedMarkerRef.current = L.marker([picked.lat, picked.lng], {
        icon: divIcon("map-marker map-marker-picked", 20),
        zIndexOffset: 1000,
      }).addTo(map);
    }
  }, [picked]);

  return <div ref={elRef} className="map-canvas" />;
}
