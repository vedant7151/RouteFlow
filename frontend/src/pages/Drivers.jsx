import { useCallback, useEffect, useState } from "react";
import {
  createVehicle,
  deactivateUser,
  deactivateVehicle,
  getUsers,
  getVehicles,
  reactivateUser,
  registerUser,
  updateVehicle,
} from "../api.js";

const emptyDriverForm = { name: "", email: "", password: "" };
const emptyVehicleForm = {
  label: "",
  capacity: "50",
  startDepotLat: "19.076",
  startDepotLng: "72.8777",
  shiftStart: "09:00",
  shiftEnd: "18:00",
  vehicleType: "van",
  driverUserId: "",
};

export default function Drivers() {
  const [drivers, setDrivers] = useState([]);
  const [vehicles, setVehicles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [showDriverForm, setShowDriverForm] = useState(false);
  const [driverForm, setDriverForm] = useState(emptyDriverForm);
  const [savingDriver, setSavingDriver] = useState(false);

  const [showVehicleForm, setShowVehicleForm] = useState(false);
  const [editingVehicleId, setEditingVehicleId] = useState(null);
  const [vehicleForm, setVehicleForm] = useState(emptyVehicleForm);
  const [savingVehicle, setSavingVehicle] = useState(false);

  const load = useCallback(async () => {
    setError("");
    try {
      const [driversData, vehiclesData] = await Promise.all([
        getUsers("DRIVER"),
        getVehicles(),
      ]);
      setDrivers(driversData);
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

  async function handleAddDriver(e) {
    e.preventDefault();
    setSavingDriver(true);
    setError("");
    try {
      await registerUser({ ...driverForm, role: "DRIVER" });
      setDriverForm(emptyDriverForm);
      setShowDriverForm(false);
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setSavingDriver(false);
    }
  }

  function openVehicleForm(vehicle) {
    if (vehicle) {
      setEditingVehicleId(vehicle.id);
      setVehicleForm({
        label: vehicle.label,
        capacity: String(vehicle.capacity),
        startDepotLat: String(vehicle.startDepotLat),
        startDepotLng: String(vehicle.startDepotLng),
        shiftStart: (vehicle.shiftStart ?? "09:00").slice(0, 5),
        shiftEnd: (vehicle.shiftEnd ?? "18:00").slice(0, 5),
        vehicleType: vehicle.vehicleType ?? "",
        driverUserId: vehicle.driverUserId ?? "",
      });
    } else {
      setEditingVehicleId(null);
      setVehicleForm(emptyVehicleForm);
    }
    setShowVehicleForm(true);
  }

  function closeVehicleForm() {
    setShowVehicleForm(false);
    setEditingVehicleId(null);
    setVehicleForm(emptyVehicleForm);
  }

  async function handleSaveVehicle(e) {
    e.preventDefault();
    setSavingVehicle(true);
    setError("");
    try {
      const payload = {
        label: vehicleForm.label,
        capacity: Number(vehicleForm.capacity),
        startDepotLat: Number(vehicleForm.startDepotLat),
        startDepotLng: Number(vehicleForm.startDepotLng),
        shiftStart: vehicleForm.shiftStart,
        shiftEnd: vehicleForm.shiftEnd,
        vehicleType: vehicleForm.vehicleType || null,
        driverUserId: vehicleForm.driverUserId || null,
      };
      if (editingVehicleId) await updateVehicle(editingVehicleId, payload);
      else await createVehicle(payload);
      closeVehicleForm();
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setSavingVehicle(false);
    }
  }

  async function handleDeactivate(id) {
    setError("");
    try {
      await deactivateVehicle(id);
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleToggleDriver(driver) {
    setError("");
    try {
      if (driver.enabled) await deactivateUser(driver.id);
      else await reactivateUser(driver.id);
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  const vehicleByDriver = Object.fromEntries(
    vehicles.filter((v) => v.active && v.driverUserId).map((v) => [v.driverUserId, v])
  );

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Drivers</h1>
          <p>{drivers.length} driver{drivers.length === 1 ? "" : "s"} &middot; {vehicles.length} vehicle{vehicles.length === 1 ? "" : "s"}</p>
        </div>
      </div>

      {error && <div className="banner-error">{error}</div>}

      <div className="page-header" style={{ marginBottom: 8 }}>
        <h2 className="section-title" style={{ margin: 0 }}>Driver accounts</h2>
        <button className="btn" onClick={() => setShowDriverForm((s) => !s)}>
          {showDriverForm ? "Cancel" : "Add driver"}
        </button>
      </div>

      {showDriverForm && (
        <div className="card">
          <form onSubmit={handleAddDriver}>
            <div className="form-grid">
              <div className="field">
                <label>Name *</label>
                <input
                  required
                  value={driverForm.name}
                  onChange={(e) => setDriverForm((f) => ({ ...f, name: e.target.value }))}
                />
              </div>
              <div className="field">
                <label>Email *</label>
                <input
                  required
                  type="email"
                  value={driverForm.email}
                  onChange={(e) => setDriverForm((f) => ({ ...f, email: e.target.value }))}
                />
              </div>
              <div className="field">
                <label>Password *</label>
                <input
                  required
                  type="password"
                  minLength={8}
                  value={driverForm.password}
                  onChange={(e) => setDriverForm((f) => ({ ...f, password: e.target.value }))}
                />
              </div>
            </div>
            <div className="form-actions">
              <button className="btn btn-primary" type="submit" disabled={savingDriver}>
                {savingDriver ? "Saving..." : "Create driver"}
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="card">
        {loading ? (
          <div className="empty-state">Loading...</div>
        ) : drivers.length === 0 ? (
          <div className="empty-state">No drivers yet. Click "Add driver" to create one.</div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Vehicle</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {drivers.map((d) => (
                <tr key={d.id}>
                  <td>{d.name}</td>
                  <td>{d.email}</td>
                  <td>{vehicleByDriver[d.id]?.label ?? <span className="text-muted">-</span>}</td>
                  <td>
                    <span className={`badge ${d.enabled ? "badge-green" : "badge-red"}`}>
                      {d.enabled ? "Active" : "Disabled"}
                    </span>
                  </td>
                  <td>
                    <button
                      className={`btn btn-sm ${d.enabled ? "btn-danger" : ""}`}
                      onClick={() => handleToggleDriver(d)}
                    >
                      {d.enabled ? "Deactivate" : "Reactivate"}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="page-header" style={{ marginBottom: 8, marginTop: 28 }}>
        <h2 className="section-title" style={{ margin: 0 }}>Vehicles</h2>
        <button className="btn" onClick={() => (showVehicleForm ? closeVehicleForm() : openVehicleForm(null))}>
          {showVehicleForm ? "Cancel" : "Add vehicle"}
        </button>
      </div>

      {showVehicleForm && (
        <div className="card">
          <form onSubmit={handleSaveVehicle}>
            <div className="form-grid">
              <div className="field">
                <label>Label *</label>
                <input
                  required
                  value={vehicleForm.label}
                  onChange={(e) => setVehicleForm((f) => ({ ...f, label: e.target.value }))}
                  placeholder="e.g. Van-2"
                />
              </div>
              <div className="field">
                <label>Type</label>
                <input
                  value={vehicleForm.vehicleType}
                  onChange={(e) => setVehicleForm((f) => ({ ...f, vehicleType: e.target.value }))}
                />
              </div>
              <div className="field">
                <label>Capacity</label>
                <input
                  type="number"
                  step="0.1"
                  value={vehicleForm.capacity}
                  onChange={(e) => setVehicleForm((f) => ({ ...f, capacity: e.target.value }))}
                />
              </div>
              <div className="field">
                <label>Depot latitude</label>
                <input
                  value={vehicleForm.startDepotLat}
                  onChange={(e) => setVehicleForm((f) => ({ ...f, startDepotLat: e.target.value }))}
                />
              </div>
              <div className="field">
                <label>Depot longitude</label>
                <input
                  value={vehicleForm.startDepotLng}
                  onChange={(e) => setVehicleForm((f) => ({ ...f, startDepotLng: e.target.value }))}
                />
              </div>
              <div className="field">
                <label>Shift start</label>
                <input
                  type="time"
                  value={vehicleForm.shiftStart}
                  onChange={(e) => setVehicleForm((f) => ({ ...f, shiftStart: e.target.value }))}
                />
              </div>
              <div className="field">
                <label>Shift end</label>
                <input
                  type="time"
                  value={vehicleForm.shiftEnd}
                  onChange={(e) => setVehicleForm((f) => ({ ...f, shiftEnd: e.target.value }))}
                />
              </div>
              <div className="field">
                <label>Driver</label>
                <select
                  value={vehicleForm.driverUserId}
                  onChange={(e) => setVehicleForm((f) => ({ ...f, driverUserId: e.target.value }))}
                >
                  <option value="">Unassigned</option>
                  {drivers.map((d) => (
                    <option key={d.id} value={d.id}>{d.name}</option>
                  ))}
                </select>
              </div>
            </div>
            <div className="form-actions">
              <button className="btn btn-primary" type="submit" disabled={savingVehicle}>
                {savingVehicle ? "Saving..." : editingVehicleId ? "Save vehicle" : "Create vehicle"}
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="card">
        {loading ? (
          <div className="empty-state">Loading...</div>
        ) : vehicles.length === 0 ? (
          <div className="empty-state">No vehicles yet. Click "Add vehicle" to create one.</div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Label</th>
                <th>Type</th>
                <th>Capacity</th>
                <th>Shift</th>
                <th>Depot</th>
                <th>Driver</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {vehicles.map((v) => (
                <tr key={v.id}>
                  <td>{v.label}</td>
                  <td>{v.vehicleType || "-"}</td>
                  <td>{v.capacity}</td>
                  <td>{(v.shiftStart ?? "").slice(0, 5)} - {(v.shiftEnd ?? "").slice(0, 5)}</td>
                  <td className="text-muted">{v.startDepotLat.toFixed(3)}, {v.startDepotLng.toFixed(3)}</td>
                  <td>{v.driverName || "Unassigned"}</td>
                  <td>
                    <span className={`badge ${v.active ? "badge-green" : "badge-red"}`}>
                      {v.active ? "Active" : "Inactive"}
                    </span>
                  </td>
                  <td>
                    <div className="row-actions">
                      {v.active && (
                        <button className="btn btn-sm" onClick={() => openVehicleForm(v)}>Edit</button>
                      )}
                      {v.active && (
                        <button className="btn btn-sm btn-danger" onClick={() => handleDeactivate(v.id)}>
                          Deactivate
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
