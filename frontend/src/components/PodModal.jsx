import { useState } from "react";
import { capturePod, updateStopStatus } from "../api.js";
import Modal from "./Modal.jsx";
import SignaturePad from "./SignaturePad.jsx";

/**
 * Marks a stop DELIVERED and captures proof of delivery (photo/signature/notes) in one flow.
 * `position` is the vehicle's last known GPS fix, used as the geo-stamp; without one the stop's own coordinates are used.
 */
export default function PodModal({ stop, position, onClose, onSaved }) {
  const [photo, setPhoto] = useState(null);
  const [signatureData, setSignatureData] = useState(null);
  const [notes, setNotes] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  async function handleSubmit(e) {
    e.preventDefault();
    setSaving(true);
    setError("");
    try {
      const lat = position?.lat ?? stop.lat;
      const lng = position?.lng ?? stop.lng;
      await updateStopStatus(stop.id, { status: "DELIVERED", lat, lng });
      await capturePod(stop.orderId, { photo, signatureData, notes, lat, lng });
      onSaved();
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal title={`Deliver ${stop.orderRef}`} onClose={onClose}>
      {error && <div className="banner-error">{error}</div>}
      <form onSubmit={handleSubmit}>
        <div className="field">
          <label>Photo (optional)</label>
          <input type="file" accept="image/*" capture="environment" onChange={(e) => setPhoto(e.target.files?.[0] ?? null)} />
        </div>

        <div className="field" style={{ marginTop: 12 }}>
          <label>Signature (optional)</label>
          <SignaturePad onChange={setSignatureData} />
        </div>

        <div className="field" style={{ marginTop: 12 }}>
          <label>Notes</label>
          <textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </div>

        <div className="form-actions">
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? "Saving..." : "Mark delivered"}
          </button>
          <button type="button" className="btn" onClick={onClose} disabled={saving}>
            Cancel
          </button>
        </div>
      </form>
    </Modal>
  );
}
