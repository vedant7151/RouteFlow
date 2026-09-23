import { useCallback, useEffect, useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { getAnalyticsSummary, todayIso } from "../api.js";
import Kpi from "../components/Kpi.jsx";

const CHART_COLORS = ["--chart-1", "--chart-2", "--chart-3", "--chart-4", "--chart-5", "--chart-6"];

const PRESETS = [
  { label: "Last 7 days", days: 7 },
  { label: "Last 30 days", days: 30 },
  { label: "Last 90 days", days: 90 },
];

function daysAgoIso(days) {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toLocaleDateString("en-CA");
}

const shortDate = (iso) => iso?.slice(5) ?? "";

function EmptyChart() {
  return <div className="empty-state">No data for this range.</div>;
}

export default function Analytics() {
  const [preset, setPreset] = useState(7);
  const [from, setFrom] = useState(daysAgoIso(7));
  const [to, setTo] = useState(todayIso());
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setError("");
    setLoading(true);
    try {
      setSummary(await getAnalyticsSummary(from, to));
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [from, to]);

  useEffect(() => {
    load();
  }, [load]);

  function applyPreset(days) {
    setPreset(days);
    setFrom(daysAgoIso(days));
    setTo(todayIso());
  }

  const statusData = summary ? Object.entries(summary.ordersByStatus ?? {}).map(([status, count]) => ({ status, count })) : [];
  const planned = summary?.totalPlannedKm ?? 0;
  const actual = summary?.totalActualKm ?? 0;
  const distanceVsPlan = planned > 0 ? `${(((actual - planned) / planned) * 100).toFixed(0)}%` : "-";
  const perDay = summary?.deliveriesPerDay ?? [];
  const trend = summary?.onTimeTrend ?? [];
  const distance = summary?.distancePerDay ?? [];
  const failures = summary?.failureReasons ?? [];

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Analytics</h1>
          <p>{from} &rarr; {to}</p>
        </div>
        <div className="toolbar">
          <div className="tabs">
            {PRESETS.map((p) => (
              <button key={p.days} className={`tab ${preset === p.days ? "active" : ""}`} onClick={() => applyPreset(p.days)}>
                {p.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {error && <div className="banner-error">{error}</div>}

      {loading ? (
        <div className="empty-state">Loading...</div>
      ) : (
        <>
          <div className="kpi-grid">
            <Kpi label="Delivered" value={summary?.deliveriesCompleted ?? 0} />
            <Kpi label="Failed" value={summary?.deliveriesFailed ?? 0} />
            <Kpi label="On-time %" value={`${summary?.onTimePercentage ?? 0}%`} />
            <Kpi label="Avg delivery time" value={`${(summary?.avgDeliveryTimeMinutes ?? 0).toFixed(0)} min`} />
            <Kpi label="Planned distance" value={`${planned} km`} />
            <Kpi label="Actual distance" value={`${actual} km`} />
            <Kpi label="Actual vs plan" value={distanceVsPlan} />
          </div>

          <div className="chart-grid">
            <div className="card chart-card">
              <h2>Deliveries per day</h2>
              {perDay.length === 0 ? (
                <EmptyChart />
              ) : (
                <ResponsiveContainer width="100%" height="85%">
                  <BarChart data={perDay}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                    <XAxis dataKey="date" tickFormatter={shortDate} tick={{ fontSize: 11 }} />
                    <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                    <Tooltip />
                    <Bar dataKey="count" name="Delivered" fill="var(--chart-1)" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </div>

            <div className="card chart-card">
              <h2>On-time trend</h2>
              {trend.length === 0 ? (
                <EmptyChart />
              ) : (
                <ResponsiveContainer width="100%" height="85%">
                  <LineChart data={trend}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                    <XAxis dataKey="date" tickFormatter={shortDate} tick={{ fontSize: 11 }} />
                    <YAxis domain={[0, 100]} unit="%" tick={{ fontSize: 11 }} />
                    <Tooltip formatter={(v) => `${v}%`} />
                    <Line type="monotone" dataKey="percentage" name="On-time" stroke="var(--chart-2)" strokeWidth={2} dot={{ r: 3 }} />
                  </LineChart>
                </ResponsiveContainer>
              )}
            </div>

            <div className="card chart-card">
              <h2>Planned vs actual distance (km)</h2>
              {distance.length === 0 ? (
                <EmptyChart />
              ) : (
                <ResponsiveContainer width="100%" height="85%">
                  <BarChart data={distance}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                    <XAxis dataKey="date" tickFormatter={shortDate} tick={{ fontSize: 11 }} />
                    <YAxis tick={{ fontSize: 11 }} />
                    <Tooltip />
                    <Legend />
                    <Bar dataKey="plannedKm" name="Planned" fill="var(--chart-1)" radius={[4, 4, 0, 0]} />
                    <Bar dataKey="actualKm" name="Actual" fill="var(--chart-3)" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </div>

            <div className="card chart-card">
              <h2>Orders by status (all time)</h2>
              {statusData.length === 0 ? (
                <EmptyChart />
              ) : (
                <ResponsiveContainer width="100%" height="85%">
                  <PieChart>
                    <Pie data={statusData} dataKey="count" nameKey="status" outerRadius={80} label>
                      {statusData.map((entry, i) => (
                        <Cell key={entry.status} fill={`var(${CHART_COLORS[i % CHART_COLORS.length]})`} />
                      ))}
                    </Pie>
                    <Legend />
                    <Tooltip />
                  </PieChart>
                </ResponsiveContainer>
              )}
            </div>
          </div>

          <div className="chart-grid">
            <div className="card">
              <h2>Driver leaderboard</h2>
              {(summary?.driverLeaderboard ?? []).length === 0 ? (
                <div className="empty-state">No completed deliveries in this range yet.</div>
              ) : (
                summary.driverLeaderboard.map((entry, i) => (
                  <div className="leaderboard-row" key={entry.vehicleId}>
                    <div>
                      <span className="rank">{i + 1}</span>
                      <strong>{entry.driverName || "Unassigned"}</strong>
                      <span className="text-muted"> &middot; {entry.label}</span>
                    </div>
                    <div className="text-muted">{entry.delivered} delivered &middot; {entry.km.toFixed(1)} km</div>
                  </div>
                ))
              )}
            </div>

            <div className="card">
              <h2>Why deliveries failed</h2>
              {failures.length === 0 ? (
                <div className="empty-state">No failed deliveries in this range.</div>
              ) : (
                failures.map((f) => (
                  <div className="leaderboard-row" key={f.reason}>
                    <div>{f.reason}</div>
                    <div className="text-muted">{f.count}</div>
                  </div>
                ))
              )}
            </div>
          </div>
        </>
      )}
    </>
  );
}
