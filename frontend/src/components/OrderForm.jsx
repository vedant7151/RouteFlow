/** Controlled create/edit form; the parent owns the state so a map click can fill lat/lng. */
export default function OrderForm({ form, onChange, onSubmit, onCancel, submitting, editing }) {
  return (
    <form onSubmit={onSubmit}>
      <div className="form-grid">
        <div className="field">
          <label>Reference {editing ? "" : "(optional)"}</label>
          <input
            required={editing}
            value={form.ref}
            onChange={(e) => onChange("ref", e.target.value)}
            placeholder="auto-generated"
          />
        </div>
        <div className="field">
          <label>Customer name *</label>
          <input required value={form.customerName} onChange={(e) => onChange("customerName", e.target.value)} />
        </div>
        <div className="field">
          <label>Customer phone</label>
          <input value={form.customerPhone} onChange={(e) => onChange("customerPhone", e.target.value)} />
        </div>
        <div className="field field-wide">
          <label>Address *</label>
          <input
            required
            value={form.addressText}
            onChange={(e) => onChange("addressText", e.target.value)}
            placeholder="Geocoded automatically if latitude/longitude are left blank"
          />
        </div>
        <div className="field">
          <label>Latitude</label>
          <input value={form.lat} onChange={(e) => onChange("lat", e.target.value)} placeholder="blank = geocode address" />
        </div>
        <div className="field">
          <label>Longitude</label>
          <input value={form.lng} onChange={(e) => onChange("lng", e.target.value)} placeholder="blank = geocode address" />
        </div>
        <div className="field">
          <label>Load</label>
          <input type="number" step="0.1" min="0" value={form.load} onChange={(e) => onChange("load", e.target.value)} />
        </div>
        <div className="field">
          <label>Priority (higher = more urgent)</label>
          <input type="number" value={form.priority} onChange={(e) => onChange("priority", e.target.value)} />
        </div>
        <div className="field">
          <label>Window start</label>
          <input type="datetime-local" value={form.windowStart} onChange={(e) => onChange("windowStart", e.target.value)} />
        </div>
        <div className="field">
          <label>Window end</label>
          <input type="datetime-local" value={form.windowEnd} onChange={(e) => onChange("windowEnd", e.target.value)} />
        </div>
        <div className="field field-wide">
          <label>Notes</label>
          <textarea rows={2} value={form.notes} onChange={(e) => onChange("notes", e.target.value)} />
        </div>
      </div>
      <div className="form-actions">
        <button className="btn btn-primary" type="submit" disabled={submitting}>
          {submitting ? "Saving..." : editing ? "Save changes" : "Create order"}
        </button>
        <button className="btn" type="button" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  );
}
