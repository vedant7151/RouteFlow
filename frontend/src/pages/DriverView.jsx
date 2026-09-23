import { useCallback, useEffect, useRef, useState } from "react";
import {
  createLocationPublisher,
  getDriverRoutes,
  postLocation,
  startDriverRoute,
  todayIso,
  updateStopStatus,
} from "../api.js";
import FailStopModal from "../components/FailStopModal.jsx";
import PodModal from "../components/PodModal.jsx";
import { formatTime, isTerminalStop, pointNear, ROUTE_STATUS_BADGE, STOP_STATUS_BADGE } from "../utils.js";

const POLL_MS = 5000;
const SEND_INTERVAL_MS = 4000; // PRD: a location every 3-5s

const GPS_TEXT = {
  off: "Location sharing is off",
  waiting: "Waiting for a GPS fix...",
  ok: "Sharing live location",
  denied: "Location permission denied - allow it in your browser settings",
  unsupported: "This browser has no geolocation support",
  error: "Could not get a GPS fix",
};

/** What a DRIVER sees instead of the dispatcher console: today's stops, start route, status + POD, live GPS. */
export default function DriverView({ user, onLogout }) {
  const date = todayIso();
  const [vehicle, setVehicle] = useState(null);
  const [routes, setRoutes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [podStop, setPodStop] = useState(null);
  const [failStop, setFailStop] = useState(null);

  const [sharing, setSharing] = useState(false);
  const [gps, setGps] = useState({ state: "off" });
  const [feed, setFeed] = useState("");
  const [lastPosition, setLastPosition] = useState(null);
  const lastSentRef = useRef(0);

  const load = useCallback(async () => {
    try {
      const data = await getDriverRoutes(date);
      setVehicle(data.vehicleId ? { id: data.vehicleId, label: data.vehicleLabel } : null);
      setRoutes(data.routes);
      setError("");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [date]);

  useEffect(() => {
    load();
    const id = setInterval(() => {
      if (!document.hidden) load();
    }, POLL_MS);
    return () => clearInterval(id);
  }, [load]);

  const vehicleId = vehicle?.id;

  // Stream the browser's GPS position to the backend over the authenticated WebSocket while sharing is on.
  useEffect(() => {
    if (!sharing || !vehicleId) return undefined;
    if (!("geolocation" in navigator)) {
      setGps({ state: "unsupported" });
      return undefined;
    }

    const publisher = createLocationPublisher(vehicleId, setFeed);
    setGps({ state: "waiting" });

    const watchId = navigator.geolocation.watchPosition(
      (pos) => {
        const { latitude, longitude, speed, accuracy } = pos.coords;
        setLastPosition({ lat: latitude, lng: longitude });
        setGps({ state: "ok", accuracy: Math.round(accuracy ?? 0) });
        const now = Date.now();
        if (now - lastSentRef.current >= SEND_INTERVAL_MS) {
          lastSentRef.current = now;
          publisher.send(latitude, longitude, speed != null ? speed * 3.6 : null);
        }
      },
      (err) => setGps({ state: err.code === 1 ? "denied" : "error" }),
      { enableHighAccuracy: true, maximumAge: 2000, timeout: 15000 }
    );

    return () => {
      navigator.geolocation.clearWatch(watchId);
      publisher.stop();
      setGps({ state: "off" });
    };
  }, [sharing, vehicleId]);

  const activeRoute =
    routes.find((r) => r.status === "IN_PROGRESS") ?? routes.find((r) => r.status === "DISPATCHED") ?? routes[0];
  const stops = activeRoute?.stops ?? [];
  const nextStop = stops.find((s) => !isTerminalStop(s.status));
  const doneCount = stops.filter((s) => isTerminalStop(s.status)).length;
  const upcoming = stops.filter((s) => !isTerminalStop(s.status) && s.id !== nextStop?.id);
  const finished = stops.filter((s) => isTerminalStop(s.status));

  async function act(action) {
    setBusy(true);
    setError("");
    try {
      await action();
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function handleStart() {
    await act(() => startDriverRoute(activeRoute.id));
    setSharing(true);
  }

  // Desktop browsers have no GPS - lets you exercise the "real driver supersedes the simulator" path.
  function sendDemoLocation() {
    const target = nextStop ?? stops[0];
    if (!target || !vehicleId) return;
    const p = pointNear(target.lat, target.lng);
    setLastPosition(p);
    act(() => postLocation(vehicleId, p.lat, p.lng, 25));
  }

  return (
    <div className="driver-shell">
      <header className="driver-header">
        <div>
          <div className="driver-brand">RouteFlow Driver</div>
          <div className="text-muted">{user.name}{vehicle && ` · ${vehicle.label}`}</div>
        </div>
        <button className="btn btn-sm" onClick={onLogout}>Log out</button>
      </header>

      <main className="driver-main">
        {error && <div className="banner-error">{error}</div>}

        {loading ? (
          <div className="empty-state">Loading...</div>
        ) : !vehicle ? (
          <div className="card empty-state">No vehicle is assigned to you yet. Ask your dispatcher to assign you one.</div>
        ) : !activeRoute ? (
          <div className="card empty-state">
            No dispatched route for today yet. Your dispatcher will push one to you - this page refreshes by itself.
          </div>
        ) : (
          <>
            <div className="card driver-route">
              <div className="driver-route-head">
                <strong>{activeRoute.vehicleLabel} &middot; {date}</strong>
                <span className={`badge ${ROUTE_STATUS_BADGE[activeRoute.status] ?? ""}`}>{activeRoute.status.replace("_", " ")}</span>
              </div>
              <div className="progress" aria-label="Route progress">
                <div className="progress-bar" style={{ width: `${stops.length ? (100 * doneCount) / stops.length : 0}%` }} />
              </div>
              <div className="text-muted">
                {doneCount}/{stops.length} stops done &middot; {activeRoute.plannedDistanceKm.toFixed(1)} km planned
              </div>

              {activeRoute.status === "DISPATCHED" && (
                <button className="btn btn-primary driver-big-btn" onClick={handleStart} disabled={busy}>
                  Start route
                </button>
              )}

              <label className="switch-row">
                <input type="checkbox" checked={sharing} onChange={(e) => setSharing(e.target.checked)} />
                <span>
                  Share my live location <span className={`gps-state gps-${gps.state}`}>{GPS_TEXT[gps.state]}
                  {gps.state === "ok" && gps.accuracy ? ` (±${gps.accuracy} m)` : ""}</span>
                </span>
              </label>
              {sharing && feed === "error" && <div className="banner-error">Live feed was rejected - falling back to HTTP updates.</div>}
              <button className="link-btn" onClick={sendDemoLocation} disabled={busy || !nextStop}>
                Demo: send a test location near the next stop
              </button>
            </div>

            {nextStop ? (
              <div className="card next-stop">
                <div className="next-stop-label">Next stop</div>
                <div className="next-stop-title">
                  {nextStop.sequence}. {nextStop.orderRef}{" "}
                  <span className={`badge ${STOP_STATUS_BADGE[nextStop.status] ?? ""}`}>{nextStop.status}</span>
                </div>
                <div className="next-stop-address">{nextStop.addressText}</div>
                <div className="text-muted">
                  ETA {formatTime(nextStop.plannedEta)} &middot; load {nextStop.load}
                </div>
                {(nextStop.customerName || nextStop.customerPhone) && (
                  <div>
                    {nextStop.customerName}{" "}
                    {nextStop.customerPhone && <a className="link-btn" href={`tel:${nextStop.customerPhone}`}>{nextStop.customerPhone}</a>}
                  </div>
                )}
                {nextStop.notes && <div className="banner-info">{nextStop.notes}</div>}

                <a
                  className="btn driver-big-btn"
                  target="_blank"
                  rel="noreferrer"
                  href={`https://www.google.com/maps/dir/?api=1&destination=${nextStop.lat},${nextStop.lng}`}
                >
                  Navigate
                </a>
                <div className="driver-actions">
                  {nextStop.status !== "ARRIVED" && (
                    <button
                      className="btn driver-big-btn"
                      disabled={busy}
                      onClick={() =>
                        act(() => updateStopStatus(nextStop.id, { status: "ARRIVED", lat: lastPosition?.lat ?? nextStop.lat, lng: lastPosition?.lng ?? nextStop.lng }))
                      }
                    >
                      I have arrived
                    </button>
                  )}
                  <button className="btn btn-primary driver-big-btn" onClick={() => setPodStop(nextStop)}>Delivered</button>
                  <button className="btn btn-danger driver-big-btn" onClick={() => setFailStop(nextStop)}>Couldn&apos;t deliver</button>
                </div>
              </div>
            ) : (
              <div className="card banner-success">All stops are done - nice work!</div>
            )}

            {upcoming.length > 0 && (
              <div className="card">
                <h2>Upcoming</h2>
                {upcoming.map((s) => (
                  <div className="driver-stop" key={s.id}>
                    <div><strong>{s.sequence}. {s.orderRef}</strong> <span className="text-muted">ETA {formatTime(s.plannedEta)}</span></div>
                    <div className="text-muted">{s.addressText}</div>
                  </div>
                ))}
              </div>
            )}

            {finished.length > 0 && (
              <div className="card">
                <h2>Done</h2>
                {finished.map((s) => (
                  <div className="driver-stop" key={s.id}>
                    <div>
                      <strong>{s.sequence}. {s.orderRef}</strong>{" "}
                      <span className={`badge ${STOP_STATUS_BADGE[s.status] ?? ""}`}>{s.status}</span>
                    </div>
                    <div className="text-muted">{s.addressText}</div>
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </main>

      {podStop && (
        <PodModal
          stop={podStop}
          position={lastPosition}
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
    </div>
  );
}
