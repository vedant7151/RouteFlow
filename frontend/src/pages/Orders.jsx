import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createOrder, deleteOrder, getOrders, getVehicles, importOrdersCsv, updateOrder } from "../api.js";
import OrderForm from "../components/OrderForm.jsx";
import OrderMap from "../components/OrderMap.jsx";
import PodViewModal from "../components/PodViewModal.jsx";
import { CSV_TEMPLATE, emptyOrderForm, formatTime, ORDER_STATUS_BADGE, toDateTimeLocal } from "../utils.js";

const STATUSES = ["PENDING", "ASSIGNED", "EN_ROUTE", "ARRIVED", "DELIVERED", "FAILED"];

function orderToForm(order) {
  return {
    ref: order.ref ?? "",
    customerName: order.customerName ?? "",
    customerPhone: order.customerPhone ?? "",
    addressText: order.addressText ?? "",
    lat: order.lat ?? "",
    lng: order.lng ?? "",
    load: String(order.load ?? 1),
    priority: String(order.priority ?? 0),
    notes: order.notes ?? "",
    windowStart: toDateTimeLocal(order.timeWindowStart),
    windowEnd: toDateTimeLocal(order.timeWindowEnd),
  };
}

function formToPayload(form) {
  return {
    ref: form.ref || `ORD-${Date.now().toString().slice(-6)}`,
    customerName: form.customerName,
    customerPhone: form.customerPhone || null,
    addressText: form.addressText,
    lat: form.lat !== "" ? Number(form.lat) : null,
    lng: form.lng !== "" ? Number(form.lng) : null,
    load: form.load ? Number(form.load) : 1,
    priority: form.priority ? Number(form.priority) : 0,
    notes: form.notes || null,
    timeWindowStart: form.windowStart ? new Date(form.windowStart).toISOString() : null,
    timeWindowEnd: form.windowEnd ? new Date(form.windowEnd).toISOString() : null,
  };
}

function downloadTemplate() {
  const url = URL.createObjectURL(new Blob([CSV_TEMPLATE], { type: "text/csv" }));
  const a = document.createElement("a");
  a.href = url;
  a.download = "routeflow-orders-template.csv";
  a.click();
  URL.revokeObjectURL(url);
}

export default function Orders() {
  const [orders, setOrders] = useState([]);
  const [vehicles, setVehicles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");

  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState(emptyOrderForm);
  const [submitting, setSubmitting] = useState(false);

  const [podOrder, setPodOrder] = useState(null);
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState(null);
  const fileInputRef = useRef(null);

  const load = useCallback(async () => {
    setError("");
    try {
      const [ordersData, vehiclesData] = await Promise.all([getOrders(), getVehicles()]);
      setOrders(ordersData);
      setVehicles(vehiclesData);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const depots = useMemo(
    () =>
      vehicles
        .filter((v) => v.active)
        .map((v) => ({ label: v.label, lat: v.startDepotLat, lng: v.startDepotLng })),
    [vehicles]
  );

  const counts = useMemo(() => {
    const byStatus = Object.fromEntries(STATUSES.map((s) => [s, 0]));
    orders.forEach((o) => {
      byStatus[o.status] = (byStatus[o.status] ?? 0) + 1;
    });
    return byStatus;
  }, [orders]);

  const visibleOrders = useMemo(
    () => (statusFilter === "ALL" ? orders : orders.filter((o) => o.status === statusFilter)),
    [orders, statusFilter]
  );
  const missingLocation = orders.filter((o) => o.lat == null || o.lng == null).length;

  const picked = useMemo(() => {
    const lat = Number(form.lat);
    const lng = Number(form.lng);
    return showForm && form.lat !== "" && form.lng !== "" && Number.isFinite(lat) && Number.isFinite(lng)
      ? { lat, lng }
      : null;
  }, [showForm, form.lat, form.lng]);

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  const handlePick = useCallback((lat, lng) => {
    setForm((f) => ({ ...f, lat: lat.toFixed(6), lng: lng.toFixed(6) }));
  }, []);

  function openCreate() {
    setEditingId(null);
    setForm(emptyOrderForm);
    setShowForm(true);
  }

  function openEdit(order) {
    setEditingId(order.id);
    setForm(orderToForm(order));
    setShowForm(true);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function closeForm() {
    setShowForm(false);
    setEditingId(null);
    setForm(emptyOrderForm);
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    setError("");
    try {
      const payload = formToPayload(form);
      if (editingId) await updateOrder(editingId, payload);
      else await createOrder(payload);
      closeForm();
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(id) {
    setError("");
    try {
      await deleteOrder(id);
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleImport(e) {
    const file = e.target.files?.[0];
    if (!file) return;
    setImporting(true);
    setImportResult(null);
    setError("");
    try {
      setImportResult(await importOrdersCsv(file));
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setImporting(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  }

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Orders</h1>
          <p>
            {orders.length} order{orders.length === 1 ? "" : "s"} &middot; {counts.PENDING} waiting to be planned
            {missingLocation > 0 && ` · ${missingLocation} without a location`}
          </p>
        </div>
        <div className="toolbar">
          <input ref={fileInputRef} type="file" accept=".csv,text/csv" hidden onChange={handleImport} />
          <button className="btn" onClick={() => fileInputRef.current?.click()} disabled={importing}>
            {importing ? "Importing..." : "Import CSV"}
          </button>
          <button className="btn btn-primary" onClick={showForm ? closeForm : openCreate}>
            {showForm ? "Cancel" : "Add order"}
          </button>
        </div>
      </div>

      {error && <div className="banner-error">{error}</div>}

      {importResult && (
        <div className={importResult.failedCount > 0 ? "banner-warning" : "banner-success"}>
          Imported {importResult.importedCount} order{importResult.importedCount === 1 ? "" : "s"}
          {importResult.failedCount > 0 && `, ${importResult.failedCount} failed`}.
          {importResult.errors?.length > 0 && (
            <ul className="banner-list">
              {importResult.errors.slice(0, 5).map((msg) => (
                <li key={msg}>{msg}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      <div className={`orders-top ${showForm ? "with-form" : ""}`}>
      {showForm && (
        <div className="card">
          <h2>{editingId ? "Edit order" : "New order"}</h2>
          <div className="banner-info">
            No coordinates? Click the map beside this form to drop a pin - it fills latitude/longitude for you.
          </div>
          <OrderForm
            form={form}
            onChange={update}
            onSubmit={handleSubmit}
            onCancel={closeForm}
            submitting={submitting}
            editing={Boolean(editingId)}
          />
        </div>
      )}
      <div className="card map-card">
        <OrderMap orders={showForm ? orders : visibleOrders} depots={depots} pickMode={showForm} picked={picked} onPick={handlePick} />
      </div>
      </div>
      <div className="legend">
        <span><i className="legend-dot legend-pending" /> Pending</span>
        <span><i className="legend-dot legend-active" /> Assigned / in progress</span>
        <span><i className="legend-dot legend-delivered" /> Delivered</span>
        <span><i className="legend-dot legend-failed" /> Failed</span>
        <span><i className="legend-dot legend-depot" /> Vehicle depot</span>
      </div>

      <div className="card">
        <div className="filters">
          <button className={`chip ${statusFilter === "ALL" ? "active" : ""}`} onClick={() => setStatusFilter("ALL")}>
            All ({orders.length})
          </button>
          {STATUSES.map((s) => (
            <button
              key={s}
              className={`chip ${statusFilter === s ? "active" : ""}`}
              onClick={() => setStatusFilter(s)}
            >
              {s.replace("_", " ")} ({counts[s] ?? 0})
            </button>
          ))}
        </div>

        {loading ? (
          <div className="empty-state">Loading...</div>
        ) : visibleOrders.length === 0 ? (
          <div className="empty-state">
            {orders.length === 0 ? 'No orders yet. Click "Add order" or "Import CSV".' : "No orders with this status."}
          </div>
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Ref</th>
                  <th>Customer</th>
                  <th>Address</th>
                  <th>Window</th>
                  <th>Load / prio</th>
                  <th>Status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {visibleOrders.map((order) => (
                  <tr key={order.id}>
                    <td>{order.ref}</td>
                    <td>
                      {order.customerName}
                      {order.customerPhone && <div className="text-muted nowrap">{order.customerPhone}</div>}
                    </td>
                    <td>
                      {order.addressText}
                      {(order.lat == null || order.lng == null) && (
                        <span className="badge badge-red" style={{ marginLeft: 6 }}>No location</span>
                      )}
                      {order.notes && <div className="text-muted">{order.notes}</div>}
                    </td>
                    <td className="nowrap">
                      {order.timeWindowStart || order.timeWindowEnd
                        ? `${formatTime(order.timeWindowStart)} - ${formatTime(order.timeWindowEnd)}`
                        : "Anytime"}
                    </td>
                    <td>
                      {order.load} / {order.priority}
                      {order.priority > 0 && <span className="badge badge-amber" style={{ marginLeft: 6 }}>Urgent</span>}
                    </td>
                    <td>
                      <span className={`badge ${ORDER_STATUS_BADGE[order.status] ?? ""}`}>{order.status}</span>
                      {order.exceptionReason && <div className="text-muted">{order.exceptionReason}</div>}
                    </td>
                    <td>
                      <div className="row-actions">
                        {order.status === "PENDING" && (
                          <button className="btn btn-sm" onClick={() => openEdit(order)}>Edit</button>
                        )}
                        {(order.status === "DELIVERED" || order.status === "FAILED") && (
                          <button className="btn btn-sm" onClick={() => setPodOrder(order)}>
                            {order.status === "DELIVERED" ? "View POD" : "Details"}
                          </button>
                        )}
                        <button className="btn btn-sm btn-danger" onClick={() => handleDelete(order.id)}>Delete</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <p className="text-muted" style={{ marginBottom: 0 }}>
          Need a CSV format? <button className="link-btn" onClick={downloadTemplate}>Download the template</button>.
        </p>
      </div>

      {podOrder && <PodViewModal order={podOrder} onClose={() => setPodOrder(null)} />}
    </>
  );
}
