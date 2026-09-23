import { createPortal } from "react-dom";

/**
 * Generic overlay + card shell, rendered into document.body via a portal.
 * Without the portal, this would mount inline under the Live Map page - and Leaflet assigns its
 * own internal panes/controls z-index values up to 1000, which would then render on top of a
 * modal mounted in the normal DOM position (whatever z-index it has). Portaling to <body> plus
 * .overlay's z-index (see index.css) sidesteps that entirely.
 * All positioning/visual styling lives in .overlay/.modal in index.css.
 */
export default function Modal({ title, onClose, children }) {
  return createPortal(
    <div className="overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>{title}</h2>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close">
            &times;
          </button>
        </div>
        {children}
      </div>
    </div>,
    document.body
  );
}
