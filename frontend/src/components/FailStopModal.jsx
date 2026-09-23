import { useState } from "react";
import { updateStopStatus } from "../api.js";
import Modal from "./Modal.jsx";

const REASONS = ["Customer absent", "Wrong address", "Damaged goods", "Refused", "Other"];

export default function FailStopModal({ stop, onClose, onSaved }) {
  const [reason, setReason] = useState(REASONS[0]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  async function handleSubmit(e) {
    e.preventDefault();
    setSaving(true);
    setError("");
    try {
      await updateStopStatus(stop.id, { status: "FAILED", exceptionReason: reason, lat: stop.lat, lng: stop.lng });
      onSaved();
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal title={`Mark ${stop.orderRef} failed`} onClose={onClose}>
      {error && <div className="banner-error">{error}</div>}
      <form onSubmit={handleSubmit}>
        <div className="field">
          <label>Reason</label>
          <select value={reason} onChange={(e) => setReason(e.target.value)}>
            {REASONS.map((r) => (
              <option key={r} value={r}>{r}</option>
            ))}
          </select>
        </div>
        <div className="form-actions">
          <button type="submit" className="btn btn-danger" disabled={saving}>
            {saving ? "Saving..." : "Mark failed"}
          </button>
          <button type="button" className="btn" onClick={onClose} disabled={saving}>
            Cancel
          </button>
        </div>
      </form>
    </Modal>
  );
}
