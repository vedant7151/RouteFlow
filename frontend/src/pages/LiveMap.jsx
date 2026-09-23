import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import {
  discardRoute,
  dispatchRoute,
  getRoutes,
  getVehicles,
  reassignStop,
  reorderRoute,
  todayIso,
  updateStopStatus,
  watchVehicles,
} from "../api.js";
import FailStopModal from "../components/FailStopModal.jsx";
import PodModal from "../components/PodModal.jsx";
import {
  escapeHtml,
  formatTime,
  isTerminalStop,
  markerClassForStatus,
  parsePolyline,
  ROUTE_STATUS_BADGE,
  STOP_STATUS_BADGE,
} from "../utils.js";

const DEFAULT_CENTER = [19.076, 72.8777]; // Mumbai - matches the seeded demo depots
const POLL_MS = 4000;
const LATE_ETA_MS = 10 * 60 * 1000;

/** Reads a --chart-N token from theme.css so route colors stay in sync with the rest of the UI. */
function routeColor(index) {
  const value = getComputedStyle(document.documentElement).getPropertyValue(`--chart-${(index % 6) + 1}`);
  return value.trim() || "#2563eb";
}

function markerIcon(className, size = 16) {
  return L.divIcon({ className: "", html: `<span class="${className}"></span>`, iconSize: [size, size] });
}

export default function LiveMap() {
  const [date, setDate] = useState(todayIso());
  const [routes, setRoutes] = useState([]);
  const [vehicles, setVehicles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [expandedIds, setExpandedIds] = useState(() => new Set());
  const [hiddenIds, setHiddenIds] = useState(() => new Set());
  const [vehiclePositions, setVehiclePositions] = useState({});
  const [wsStatus, setWsStatus] = useState("connecting");
  const [podStop, setPodStop] = useState(null);
  const [failStop, setFailStop] = useState(null);
  const [busyId, setBusyId] = useState(null);

  const mapElRef = useRef(null);
  const mapRef = useRef(null);
  const routeLayerRef = useRef(null);
  const stopMarkersRef = useRef({});
  const vehicleMarkersRef = useRef({});
  const fittedKeyRef = useRef("");

  const load = useCallback(async () => {
    try {
      const [routesData, vehiclesData] = await Promise.all([getRoutes(date), getVehicles()]);
      setRoutes(routesData);
      setVehicles(vehiclesData);
      setExpandedIds((prev) => (prev.size ? prev : new Set(routesData.slice(0, 1).map((r) => r.id))));
      setError("");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [date]);

  useEffect(() => {
    load();
    // Stop statuses change on their own (driver simulator / real drivers), so keep the sidebar fresh.
    const id = setInterval(() => {
      if (!document.hidden) load();
    }, POLL_MS);
    return () => clearInterval(id);
  }, [load]);

  // Init the Leaflet map once.
  useEffect(() => {
    const map = L.map(mapElRef.current).setView(DEFAULT_CENTER, 11);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: "&copy; OpenStreetMap contributors",
      maxZoom: 19,
    }).addTo(map);
    routeLayerRef.current = L.layerGroup().addTo(map);
    mapRef.current = map;
    const vehicleMarkers = vehicleMarkersRef.current;
    return () => {
      map.remove();
      mapRef.current = null;
      Object.keys(vehicleMarkers).forEach((k) => delete vehicleMarkers[k]);
    };
  }, []);

  // Redraw only when something the map shows actually changed - not on every poll - so open popups survive.
  const drawKey = useMemo(
    () =>
      JSON.stringify([
        routes.map((r) => [r.id, r.polyline?.length, r.stops.map((s) => [s.id, s.status, s.sequence])]),
        vehicles.map((v) => [v.id, v.active, v.startDepotLat, v.startDepotLng]),
      ]),
    [routes, vehicles]
  );

  useEffect(() => {
    const map = mapRef.current;
    const layer = routeLayerRef.current;
    if (!map || !layer) return;

    layer.clearLayers();
    stopMarkersRef.current = {};
    const bounds = [];

    vehicles
      .filter((v) => v.active)
      .forEach((v) => {
        L.marker([v.startDepotLat, v.startDepotLng], { icon: markerIcon("map-marker map-marker-depot", 18), zIndexOffset: -100 })
          .bindTooltip(`${escapeHtml(v.label)} depot`)
          .addTo(layer);
      });

    routes.forEach((route, index) => {
      if (hiddenIds.has(route.id)) return;
      const color = routeColor(index);

      const path = parsePolyline(route.polyline);
      if (path.length > 1) {
        L.polyline(path, { color, weight: 4, opacity: 0.75 }).addTo(layer);
        path.forEach((p) => bounds.push(p));
      }

      route.stops.forEach((stop) => {
        if (stop.lat == null || stop.lng == null) return;
        const marker = L.marker([stop.lat, stop.lng], { icon: markerIcon(markerClassForStatus(stop.status)) });
        marker.bindPopup(
          `<div class="map-popup">
            <div class="map-popup-title">${stop.sequence}. ${escapeHtml(stop.orderRef)}</div>
            <div>${escapeHtml(stop.addressText)}</div>
            <div class="text-muted">${escapeHtml(route.vehicleLabel)} &middot; ${escapeHtml(stop.status)} &middot; planned ${escapeHtml(formatTime(stop.plannedEta))}</div>
          </div>`
        );
        marker.addTo(layer);
        stopMarkersRef.current[stop.id] = marker;
        bounds.push([stop.lat, stop.lng]);
      });
    });

    // Re-fit only when the set of routes (or the date) changes, never on a status refresh.
    const fitKey = `${date}|${routes.map((r) => r.id).join(",")}`;
    if (bounds.length > 0 && fittedKeyRef.current !== fitKey) {
      map.fitBounds(bounds, { padding: [32, 32], maxZoom: 14 });
      fittedKeyRef.current = fitKey;
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- drawKey is the change signal for routes/vehicles
  }, [drawKey, hiddenIds, date]);

  // One WebSocket subscription per set of vehicles - not per poll.
  const vehicleIdsKey = useMemo(() => [...new Set(routes.map((r) => r.vehicleId))].sort().join(","), [routes]);

  useEffect(() => {
    setVehiclePositions({});
    if (!vehicleIdsKey) return undefined;
    return watchVehicles(
      vehicleIdsKey.split(","),
      (event) => setVehiclePositions((prev) => ({ ...prev, [event.vehicleId]: event })),
      setWsStatus
    );
  }, [vehicleIdsKey]);

  // Keep vehicle markers on the map in sync with the live positions.
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const markers = vehicleMarkersRef.current;

    for (const [vehicleId, position] of Object.entries(vehiclePositions)) {
      const route = routes.find((r) => r.vehicleId === vehicleId);
      if (!route || hiddenIds.has(route.id)) {
        markers[vehicleId]?.remove();
        delete markers[vehicleId];
        continue;
      }
      const latLng = [position.lat, position.lng];
      const tooltip = `${escapeHtml(route.vehicleLabel)} &middot; ${position.simulated ? "simulated" : "driver GPS"}`;
      if (markers[vehicleId]) {
        markers[vehicleId].setLatLng(latLng).setTooltipContent(tooltip);
      } else {
        markers[vehicleId] = L.marker(latLng, { icon: markerIcon("map-marker-vehicle", 20), zIndexOffset: 1000 })
          .bindTooltip(tooltip)
          .addTo(map);
      }
    }
  }, [vehiclePositions, hiddenIds, routes]);

  // Rolling ETAs pushed with every location ping, keyed by stop id.
  const liveEtas = useMemo(() => {
    const byStop = {};
    Object.values(vehiclePositions).forEach((p) => (p.etas ?? []).forEach((e) => (byStop[e.stopId] = e.eta)));
    return byStop;
  }, [vehiclePositions]);

  const plannedRoutes = routes.filter((r) => r.status === "PLANNED");

  function toggle(setter, id) {
    setter((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function focusStop(stop) {
    const map = mapRef.current;
    const marker = stopMarkersRef.current[stop.id];
    if (!map || !marker) return;
    map.setView([stop.lat, stop.lng], Math.max(map.getZoom(), 15));
    marker.openPopup();
  }

  async function run(id, action) {
    setBusyId(id);
    setError("");
    try {
      await action();
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyId(null);
    }
  }

  function moveStop(route, stopIndex, delta) {
    const ids = route.stops.map((s) => s.id);
    const target = stopIndex + delta;
    if (target < 0 || target >= ids.length) return;
    [ids[stopIndex], ids[target]] = [ids[target], ids[stopIndex]];
    run(route.stops[stopIndex].id, () => reorderRoute(route.id, ids));
  }

  function renderStopRow(route, stop, index) {
    const liveEta = liveEtas[stop.id];
    const showLive = liveEta && !isTerminalStop(stop.status);
    const lateBy = showLive && stop.plannedEta ? new Date(liveEta) - new Date(stop.plannedEta) : 0;
    const isPlanned = route.status === "PLANNED";

    return (
      <li className="stop-row" key={stop.id}>
        <div className="stop-row-main">
          <div>
            <button className="link-btn stop-title" onClick={() => focusStop(stop)} title="Show on map">
              {stop.sequence}. {stop.orderRef}
            </button>{" "}
            <span className={`badge ${STOP_STATUS_BADGE[stop.status] ?? ""}`}>{stop.status}</span>
          </div>
          <div className="stop-row-address" title={stop.addressText}>{stop.addressText}</div>
          <div className="stop-row-times">
            {showLive ? (
              <span className={lateBy > LATE_ETA_MS ? "eta-late" : "eta-live"} title={`Planned ${formatTime(stop.plannedEta)}`}>
                ETA {formatTime(liveEta)}
              </span>
            ) : stop.actualArrival ? (
              <span className="text-muted">Arrived {formatTime(stop.actualArrival)}</span>
            ) : (
              <span className="text-muted">Planned {formatTime(stop.plannedEta)}</span>
            )}
          </div>
        </div>

        {isPlanned ? (
          <div className="stop-row-actions">
            <button className="btn btn-sm" title="Move earlier" disabled={index === 0 || busyId !== null} onClick={() => moveStop(route, index, -1)}>&uarr;</button>
            <button className="btn btn-sm" title="Move later" disabled={index === route.stops.length - 1 || busyId !== null} onClick={() => moveStop(route, index, 1)}>&darr;</button>
            {plannedRoutes.length > 1 && (
              <select
                className="select-sm"
                value=""
                disabled={busyId !== null}
                onChange={(e) => e.target.value && run(stop.id, () => reassignStop(stop.id, e.target.value))}
                title="Move this stop to another vehicle"
              >
                <option value="">Move to...</option>
                {plannedRoutes.filter((r) => r.id !== route.id).map((r) => (
                  <option key={r.id} value={r.id}>{r.vehicleLabel}</option>
                ))}
              </select>
            )}
          </div>
        ) : (
          !isTerminalStop(stop.status) && (
            <div className="stop-row-actions">
              {stop.status !== "ARRIVED" && (
                <button
                  className="btn btn-sm"
                  disabled={busyId === stop.id}
                  onClick={() => run(stop.id, () => updateStopStatus(stop.id, { status: "ARRIVED", lat: stop.lat, lng: stop.lng }))}
                >
                  Arrived
                </button>
              )}
              <button className="btn btn-sm btn-primary" onClick={() => setPodStop({ stop, vehicleId: route.vehicleId })}>Deliver</button>
              <button className="btn btn-sm btn-danger" onClick={() => setFailStop(stop)}>Failed</button>
            </div>
          )
        )}
      </li>
    );
  }

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Live map</h1>
          <p>
            Routes, live driver positions and delivery status.{" "}
            <span className={`ws-status ws-${wsStatus}`}>
              <i className="live-dot" /> {wsStatus === "connected" ? "Live" : wsStatus === "error" ? "Live feed rejected" : "Connecting..."}
            </span>
          </p>
        </div>
        <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
      </div>

      {error && <div className="banner-error">{error}</div>}

      <div className="map-layout">
        <div className="map-sidebar">
          {loading ? (
            <div className="empty-state">Loading...</div>
          ) : routes.length === 0 ? (
            <div className="empty-state">
              No routes for this date yet. Go to the Dashboard and click &quot;Optimize today&apos;s routes&quot;.
            </div>
          ) : (
            routes.map((route, index) => {
              const delivered = route.stops.filter((s) => s.status === "DELIVERED").length;
              return (
                <div className="route-card" key={route.id}>
                  <div className="route-card-header" onClick={() => toggle(setExpandedIds, route.id)}>
                    <div>
                      <div className="route-card-title">
                        <span
                          className="route-color-dot"
                          style={{ background: routeColor(index), opacity: hiddenIds.has(route.id) ? 0.25 : 1 }}
                          onClick={(e) => {
                            e.stopPropagation();
                            toggle(setHiddenIds, route.id);
                          }}
                          title={hiddenIds.has(route.id) ? "Hidden - click to show on map" : "Click to hide from map"}
                        />
                        {route.vehicleLabel}
                        {vehiclePositions[route.vehicleId] && <i className="live-dot" title="Live" />}
                      </div>
                      <div className="route-card-meta">
                        {route.status === "PLANNED"
                          ? `${route.stops.length} stops`
                          : `${delivered}/${route.stops.length} done`}{" "}
                        &middot; {route.plannedDistanceKm.toFixed(1)} km &middot; {route.plannedDurationMin.toFixed(0)} min
                      </div>
                    </div>
                    <div className="route-card-actions">
                      <span className={`badge ${ROUTE_STATUS_BADGE[route.status] ?? ""}`}>{route.status.replace("_", " ")}</span>
                      {route.status === "PLANNED" && (
                        <>
                          <button
                            className="btn btn-sm btn-primary"
                            disabled={busyId !== null}
                            onClick={(e) => {
                              e.stopPropagation();
                              run(route.id, () => dispatchRoute(route.id));
                            }}
                          >
                            Dispatch
                          </button>
                          <button
                            className="btn btn-sm"
                            title="Discard this plan - its orders return to Pending"
                            disabled={busyId !== null}
                            onClick={(e) => {
                              e.stopPropagation();
                              run(route.id, () => discardRoute(route.id));
                            }}
                          >
                            Discard
                          </button>
                        </>
                      )}
                    </div>
                  </div>

                  {expandedIds.has(route.id) && (
                    <ul className="stop-list">{route.stops.map((stop, i) => renderStopRow(route, stop, i))}</ul>
                  )}
                </div>
              );
            })
          )}
        </div>

        <div className="card map-canvas-card">
          <div ref={mapElRef} className="map-canvas" />
        </div>
      </div>

      {podStop && (
        <PodModal
          stop={podStop.stop}
          position={vehiclePositions[podStop.vehicleId]}
          onClose={() => setPodStop(null)}
          onSaved={() => {
            setPodStop(null);
            load();
          }}
        />
      )}
      {failStop && (
        <FailStopModal
          stop={failStop}
          onClose={() => setFailStop(null)}
          onSaved={() => {
            setFailStop(null);
            load();
          }}
        />
      )}
    </>
  );
}
