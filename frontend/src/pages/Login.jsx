import { useState } from "react";
import { login } from "../api.js";

// Seeded by the backend's demo data; shown only in dev builds so testing different roles is one click.
const DEMO_ACCOUNTS = [
  { label: "Dispatcher", email: "dispatcher@routeflow.dev", password: "Dispatcher@123" },
  { label: "Manager", email: "manager@routeflow.dev", password: "Manager@123" },
  { label: "Driver (Van-1)", email: "driver1@routeflow.dev", password: "Driver@123" },
  { label: "Driver (Van-2)", email: "driver2@routeflow.dev", password: "Driver@123" },
  { label: "Driver (Bike-1)", email: "driver3@routeflow.dev", password: "Driver@123" },
];

export default function Login({ onLogin }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    setError("");
    try {
      const session = await login(email, password);
      onLogin(session);
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="login-screen">
      <form className="card login-card" onSubmit={handleSubmit}>
        <div className="login-brand">RouteFlow</div>
        <p className="text-muted" style={{ marginTop: 0 }}>Sign in to the dispatcher console</p>

        {error && <div className="banner-error">{error}</div>}

        <div className="field">
          <label>Email</label>
          <input
            type="email"
            required
            autoFocus
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="dispatcher@routeflow.dev"
          />
        </div>
        <div className="field" style={{ marginTop: 12 }}>
          <label>Password</label>
          <input
            type="password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>

        <div className="form-actions">
          <button type="submit" className="btn btn-primary" disabled={submitting} style={{ width: "100%" }}>
            {submitting ? "Signing in..." : "Sign in"}
          </button>
        </div>

        {import.meta.env.DEV && (
          <div className="demo-accounts">
            <div className="text-muted">Demo accounts (dev only) - click to fill:</div>
            <div className="demo-account-list">
              {DEMO_ACCOUNTS.map((a) => (
                <button
                  key={a.email}
                  type="button"
                  className="chip"
                  onClick={() => {
                    setEmail(a.email);
                    setPassword(a.password);
                  }}
                >
                  {a.label}
                </button>
              ))}
            </div>
          </div>
        )}
      </form>
    </div>
  );
}
