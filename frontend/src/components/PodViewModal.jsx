import { useEffect, useState } from "react";
import { getPod, resolveUploadUrl } from "../api.js";
import { formatDateTime } from "../utils.js";
import Modal from "./Modal.jsx";

/** Audit view for an order: exception reason for failed orders, full proof of delivery for delivered ones. */
export default function PodViewModal({ order, onClose }) {
  const [pod, setPod] = useState(null);
  const [loading, setLoading] = useState(order.status === "DELIVERED");
  const [error, setError] = useState("");

  useEffect(() => {
    if (order.status !== "DELIVERED") return;
    (async () => {
      try {
        setPod(await getPod(order.id));
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    })();
  }, [order.id, order.status]);

  return (
    <Modal title={`${order.status === "DELIVERED" ? "Proof of delivery" : "Order details"} - ${order.ref}`} onClose={onClose}>
      <div className="field">
        <label>Customer</label>
        <div>{order.customerName} {order.customerPhone && <span className="text-muted">&middot; {order.customerPhone}</span>}</div>
      </div>
      <div className="field" style={{ marginTop: 12 }}>
        <label>Address</label>
        <div>{order.addressText}</div>
      </div>

      {order.status === "FAILED" && (
        <div className="field" style={{ marginTop: 12 }}>
          <label>Failure reason</label>
          <div>{order.exceptionReason || "Unspecified"}</div>
        </div>
      )}

      {loading && <div className="empty-state">Loading...</div>}
      {error && <div className="banner-error" style={{ marginTop: 12 }}>{error}</div>}

      {pod && (
        <>
          <div className="field" style={{ marginTop: 12 }}>
            <label>Captured</label>
            <div>{formatDateTime(pod.capturedAt)}</div>
          </div>
          {pod.capturedLat != null && (
            <div className="field" style={{ marginTop: 12 }}>
              <label>Geo-stamp</label>
              <div>
                {pod.capturedLat.toFixed(5)}, {pod.capturedLng.toFixed(5)}{" "}
                <a
                  className="link-btn"
                  href={`https://www.openstreetmap.org/?mlat=${pod.capturedLat}&mlon=${pod.capturedLng}#map=17/${pod.capturedLat}/${pod.capturedLng}`}
                  target="_blank"
                  rel="noreferrer"
                >
                  view on map
                </a>
              </div>
            </div>
          )}
          {pod.photoUrl && (
            <div className="field" style={{ marginTop: 12 }}>
              <label>Photo</label>
              <img className="pod-photo-preview" src={resolveUploadUrl(pod.photoUrl)} alt="Proof of delivery" />
            </div>
          )}
          {pod.hasSignature && (
            <div className="field" style={{ marginTop: 12 }}>
              <label>Signature</label>
              <img className="pod-signature-preview" src={pod.signatureData} alt="Customer signature" />
            </div>
          )}
          {pod.notes && (
            <div className="field" style={{ marginTop: 12 }}>
              <label>Notes</label>
              <div>{pod.notes}</div>
            </div>
          )}
          {!pod.photoUrl && !pod.hasSignature && !pod.notes && (
            <div className="empty-state">No photo, signature or notes were captured.</div>
          )}
        </>
      )}
    </Modal>
  );
}
