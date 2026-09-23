import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  discardRoute,
  dispatchRoute,
  getAnalyticsSummary,
  getOrders,
  getRoutes,
  getVehicles,
  optimizeRoutes,
  resetDemoData,
  todayIso,
} from "../api.js";
import Kpi from "../components/Kpi.jsx";
import { ROUTE_STATUS_BADGE } from "../utils.js";

export default function Dashboard() {
  const date = todayIso();
  const [summary, setSummary] = useState(null);
  const [routes, setRoutes] = useState([]);
  const [orders, setOrders] = useState([]);
  const [vehicles, setVehicles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState(null); // { kind: "success" | "warning" | "info", text, items? }
  const [optimizing, setOptimizing] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [busyRouteId, setBusyRouteId] = useState(null);

  const load = useCallback(async () => {
    setError("");
    try {
      const [summaryData, routesData, vehiclesData, ordersData] = await Promise.all([
        getAnalyticsSummary(date, date),
        getRoutes(date),
        getVehicles(),
        getOrders(),
      ]);
      setSummary(summaryData);
      setRoutes(routesData);
      setVehicles(vehiclesData);
      setOrders(ordersData);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [date]);

  useEffect(() => {
    load();
  }, [load]);

  const activeVehicles = vehicles.filter((v) => v.active);
  const maxCapacity = Math.max(0, ...activeVehicles.map((v) => v.capacity));
  const pending = orders.filter((o) => o.status === "PENDING");
  const routable = pending.filter((o) => o.lat != null && o.lng != null);
  const noLocation = pending.length - routable.length;
  const plannedRoutes = routes.filter((r) => r.status === "PLANNED");
  const liveRoutes = routes.filter((r) => r.status === "DISPATCHED" || r.status === "IN_PROGRESS");
  const doneRoutes = routes.filter((r) => r.status === "COMPLETED");

  async function handleOptimize() {
    setOptimizing(true);
    setError("");
    setNotice(null);
    try {
      const result = await optimizeRoutes(date, null);
      const assigned = result.routes.reduce((sum, r) => sum + r.stops.length, 0);
      const fresh = await getOrders();
      const leftovers = fresh.filter((o) => result.unassignedOrderIds.includes(o.id));

      if (result.routes.length === 0 && leftovers.length === 0) {
        setNotice({ kind: "info", text: "Nothing to plan - there are no pending orders with a location." });
      } else {
        setNotice({
          kind: leftovers.length > 0 ? "warning" : "success",
          text: `Planned ${assigned} order${assigned === 1 ? "" : "s"} across ${result.routes.length} route${result.routes.length === 1 ? "" : "s"}.${
            leftovers.length > 0 ? ` ${leftovers.length} could not be assigned:` : ""
          }`,
          items: leftovers.map((o) =>
            o.load > maxCapacity
              ? `${o.ref} - load ${o.load} exceeds the largest vehicle capacity (${maxCapacity})`
              : `${o.ref} - no vehicle could fit it (capacity, time window or shift end)`
          ),
        });
      }
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setOptimizing(false);
    }
  }

  async function handleReset() {
    const ok = window.confirm(
      "Reset demo data?\n\nThis deletes ALL orders, routes, proofs of delivery, GPS history and vehicles, " +
        "then re-creates the demo scenario (users are kept). Continue?"
    );
    if (!ok) return;
    setResetting(true);
    setError("");
    setNotice(null);
    try {
      const s = await resetDemoData();
      setNotice({
        kind: "success",
        text: `Demo data reset: ${s.vehicles} vehicles, ${s.todaysOrders} orders for today, ${s.historicalRoutes} past routes with ${s.proofsOfDelivery} proofs of delivery.`,
      });
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setResetting(false);
    }
  }

  async function routeAction(routeId, action) {
    setBusyRouteId(routeId);
    setError("");
    try {
      await action(routeId);
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyRouteId(null);
    }
  }

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Dashboard</h1>
          <p>{date} &middot; {activeVehicles.length} active vehicle{activeVehicles.length === 1 ? "" : "s"}</p>
        </div>
        <div className="toolbar">
          <button className="btn" onClick={handleReset} disabled={resetting}>
            {resetting ? "Resetting..." : "Reset demo data"}
          </button>
          <button className="btn btn-primary" onClick={handleOptimize} disabled={optimizing}>
            {optimizing ? "Optimizing..." : "Optimize today's routes"}
          </button>
        </div>
      </div>

      {error && <div className="banner-error">{error}</div>}
      {notice && (
        <div className={`banner-${notice.kind}`}>
          {notice.text}
          {notice.items?.length > 0 && (
            <ul className="banner-list">
              {notice.items.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      {loading ? (
        <div className="empty-state">Loading...</div>
      ) : (
        <>
          <div className="card">
            <h2>What happens next</h2>
            <ol className="steps">
              <li className={orders.length > 0 ? "done" : ""}>
                <strong>Create orders.</strong> <Link to="/orders">Orders</Link> - add one, import a CSV, or drop a pin for
                addresses without coordinates. {pending.length} pending
                {noLocation > 0 && ` (${noLocation} still need a location)`}.
              </li>
              <li className={routes.length > 0 ? "done" : ""}>
                <strong>Optimize.</strong> Click &quot;Optimize today&apos;s routes&quot; - {routable.length} order
                {routable.length === 1 ? "" : "s"} ready to be planned across {activeVehicles.length} vehicle
                {activeVehicles.length === 1 ? "" : "s"}.
              </li>
              <li className={liveRoutes.length + doneRoutes.length > 0 ? "done" : ""}>
                <strong>Review &amp; dispatch.</strong> On the <Link to="/map">Live map</Link>, reorder stops or move them
                between vehicles, then Dispatch ({plannedRoutes.length} planned).
              </li>
              <li className={doneRoutes.length > 0 ? "done" : ""}>
                <strong>Deliver.</strong> Drivers (or the built-in simulator) work through stops - mark them Arrived /
                Delivered with proof of delivery, or Failed with a reason ({liveRoutes.length} in progress).
              </li>
              <li>
                <strong>Measure.</strong> <Link to="/analytics">Analytics</Link> shows on-time %, distance and the driver
                leaderboard.
              </li>
            </ol>
          </div>

          <div className="kpi-grid">
            <Kpi label="Pending orders" value={pending.length} />
            <Kpi label="Delivered (today)" value={summary?.deliveriesCompleted ?? 0} />
            <Kpi label="Failed (today)" value={summary?.deliveriesFailed ?? 0} />
            <Kpi label="On-time %" value={`${summary?.onTimePercentage ?? 0}%`} />
            <Kpi label="Planned distance" value={`${summary?.totalPlannedKm ?? 0} km`} />
            <Kpi label="Actual distance" value={`${summary?.totalActualKm ?? 0} km`} />
          </div>

          <div className="card">
            <h2>Today&apos;s routes</h2>
            {routes.length === 0 ? (
              <div className="empty-state">
                No routes yet for today. Click &quot;Optimize today&apos;s routes&quot; to turn the pending orders into routes.
              </div>
            ) : (
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>Vehicle</th>
                      <th>Stops</th>
                      <th>Distance</th>
                      <th>Duration</th>
                      <th>Status</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {routes.map((route) => (
                      <tr key={route.id}>
                        <td>{route.vehicleLabel}</td>
                        <td>
                          {route.stops.filter((s) => s.status === "DELIVERED").length}/{route.stops.length}
                        </td>
                        <td>{route.plannedDistanceKm.toFixed(1)} km</td>
                        <td>{route.plannedDurationMin.toFixed(0)} min</td>
                        <td>
                          <span className={`badge ${ROUTE_STATUS_BADGE[route.status] ?? ""}`}>{route.status.replace("_", " ")}</span>
                        </td>
                        <td>
                          <div className="row-actions">
                            {route.status === "PLANNED" && (
                              <>
                                <button
                                  className="btn btn-sm btn-primary"
                                  onClick={() => routeAction(route.id, dispatchRoute)}
                                  disabled={busyRouteId === route.id}
                                >
                                  Dispatch
                                </button>
                                <button
                                  className="btn btn-sm"
                                  onClick={() => routeAction(route.id, discardRoute)}
                                  disabled={busyRouteId === route.id}
                                >
                                  Discard
                                </button>
                              </>
                            )}
                            <Link className="btn btn-sm" to="/map">View on map</Link>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}
    </>
  );
}
