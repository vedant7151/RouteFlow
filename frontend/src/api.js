import { Client } from "@stomp/stompjs";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

const SESSION_KEY = "routeflow_session";

/** The whole LoginResponse (tokens + user identity) is kept together as one JSON blob. */
function getSession() {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function setSession(session) {
  if (session) sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
  else sessionStorage.removeItem(SESSION_KEY);
}

/** So App.jsx can drop back to the login screen the moment the session is truly dead. */
function expireSession() {
  setSession(null);
  window.dispatchEvent(new Event("routeflow:session-expired"));
}

/** One session at a time: logging in replaces whatever was there, no role-switching mid-session. */
export async function login(email, password) {
  const res = await fetch(`${API_BASE_URL}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) {
    if (res.status === 401 || res.status === 403) throw new Error("Invalid email or password.");
    throw new Error(`Login failed (${res.status}). Is the backend running?`);
  }
  const session = await res.json();
  setSession(session);
  return session;
}

export function logout() {
  setSession(null);
}

export function getCurrentUser() {
  return getSession();
}

let refreshPromise = null;

/** Trades the refresh token for a new pair. Concurrent 401s share one refresh call. Returns the new session or null. */
function refreshSession() {
  const session = getSession();
  if (!session?.refreshToken) return Promise.resolve(null);
  if (!refreshPromise) {
    refreshPromise = fetch(`${API_BASE_URL}/api/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: session.refreshToken }),
    })
      .then(async (res) => {
        if (!res.ok) return null;
        const fresh = await res.json();
        setSession(fresh);
        return fresh;
      })
      .catch(() => null)
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

/** fetch() with the bearer token; on a 401 it refreshes once and retries before giving up on the session. */
async function authedFetch(path, init = {}) {
  const session = getSession();
  if (!session) throw new Error("Not logged in.");

  const send = (s) =>
    fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers: { ...(init.headers ?? {}), Authorization: `Bearer ${s.accessToken}` },
    });

  let res = await send(session);
  if (res.status === 401) {
    const fresh = await refreshSession();
    if (fresh) res = await send(fresh);
  }
  if (res.status === 401) {
    expireSession();
    throw new Error("Session expired - please log in again.");
  }
  return res;
}

async function parseResponse(res, method, path) {
  if (!res.ok) {
    let message = `${method} ${path} failed (${res.status})`;
    try {
      const errBody = await res.json();
      if (errBody?.error) message = errBody.error;
      if (errBody?.fields) message += `: ${JSON.stringify(errBody.fields)}`;
    } catch {
      // response wasn't JSON - keep the generic message
    }
    throw new Error(message);
  }
  if (res.status === 204) return null;
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

async function request(path, { method = "GET", body } = {}) {
  const res = await authedFetch(path, {
    method,
    headers: body !== undefined ? { "Content-Type": "application/json" } : {},
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  return parseResponse(res, method, path);
}

/** Like request(), but sends a FormData body (multipart) instead of JSON - used for POD upload and CSV import. */
async function requestMultipart(path, formData) {
  const res = await authedFetch(path, { method: "POST", body: formData });
  return parseResponse(res, "POST", path);
}

// --- Orders ---
export const getOrders = (date) => request(`/api/orders${date ? `?date=${date}` : ""}`);
export const getOrder = (id) => request(`/api/orders/${id}`);
export const createOrder = (order) => request("/api/orders", { method: "POST", body: order });
export const updateOrder = (id, order) => request(`/api/orders/${id}`, { method: "PUT", body: order });
export const deleteOrder = (id) => request(`/api/orders/${id}`, { method: "DELETE" });
export const importOrdersCsv = (file) => {
  const formData = new FormData();
  formData.append("file", file);
  return requestMultipart("/api/orders/import", formData);
};

// --- Vehicles ---
export const getVehicles = () => request("/api/vehicles");
export const createVehicle = (vehicle) => request("/api/vehicles", { method: "POST", body: vehicle });
export const updateVehicle = (id, vehicle) => request(`/api/vehicles/${id}`, { method: "PUT", body: vehicle });
export const deactivateVehicle = (id) => request(`/api/vehicles/${id}`, { method: "DELETE" });

// --- Users / Drivers ---
export const getUsers = (role) => request(`/api/users${role ? `?role=${role}` : ""}`);
export const registerUser = (user) => request("/api/auth/register", { method: "POST", body: user });
export const deactivateUser = (id) => request(`/api/users/${id}`, { method: "DELETE" });
export const reactivateUser = (id) => request(`/api/users/${id}/reactivate`, { method: "POST" });

// --- Routes ---
export const getRoutes = (date) => request(`/api/routes?date=${date}`);
export const getRoute = (id) => request(`/api/routes/${id}`);
export const optimizeRoutes = (date, vehicleIds) =>
  request("/api/routes/optimize", { method: "POST", body: { date, vehicleIds } });
export const dispatchRoute = (id) => request(`/api/routes/${id}/dispatch`, { method: "POST" });
export const discardRoute = (id) => request(`/api/routes/${id}`, { method: "DELETE" });
export const reorderRoute = (id, orderedStopIds) =>
  request(`/api/routes/${id}/reorder`, { method: "PATCH", body: { orderedStopIds } });
export const reassignStop = (stopId, targetRouteId, sequence = null) =>
  request(`/api/stops/${stopId}/reassign`, { method: "PATCH", body: { targetRouteId, sequence } });

// --- Stops (delivery lifecycle) ---
export const updateStopStatus = (stopId, { status, exceptionReason, lat, lng }) =>
  request(`/api/stops/${stopId}/status`, {
    method: "PATCH",
    body: { status, exceptionReason: exceptionReason ?? null, lat: lat ?? null, lng: lng ?? null },
  });

// --- Driver web view ---
export const getDriverRoutes = (date) => request(`/api/driver/routes?date=${date}`);
export const startDriverRoute = (routeId) => request(`/api/driver/routes/${routeId}/start`, { method: "POST" });
export const postLocation = (vehicleId, lat, lng, speed) =>
  request("/api/tracking/location", { method: "POST", body: { vehicleId, lat, lng, speed: speed ?? null } });

// --- Proof of delivery ---
export const capturePod = (orderId, { photo, signatureData, notes, lat, lng }) => {
  const formData = new FormData();
  if (photo) formData.append("photo", photo);
  if (signatureData) formData.append("signatureData", signatureData);
  if (notes) formData.append("notes", notes);
  if (lat != null) formData.append("lat", lat);
  if (lng != null) formData.append("lng", lng);
  return requestMultipart(`/api/orders/${orderId}/pod`, formData);
};
export const getPod = (orderId) => request(`/api/orders/${orderId}/pod`);

/** Resolves a POD photoUrl ("/uploads/pod/...") returned by the backend to a fetchable absolute URL. */
export const resolveUploadUrl = (path) => (path ? `${API_BASE_URL}${path}` : null);

// --- Analytics ---
export const getAnalyticsSummary = (from, to) => request(`/api/analytics/summary?from=${from}&to=${to}`);

// --- Demo tooling ---
export const resetDemoData = () => request("/api/admin/demo/reset", { method: "POST" });

/** Local calendar date (YYYY-MM-DD) - the operating "today", not the UTC date. */
export const todayIso = () => new Date().toLocaleDateString("en-CA");

// --- Live tracking (STOMP over WebSocket) ---
function createClient(onConnect, onStatus) {
  const client = new Client({
    brokerURL: `${API_BASE_URL.replace(/^http/, "ws")}/ws`,
    reconnectDelay: 4000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    // The CONNECT frame is authenticated, so re-read the (possibly refreshed) token on every (re)connect.
    beforeConnect: () => {
      client.connectHeaders = { Authorization: `Bearer ${getSession()?.accessToken ?? ""}` };
    },
    onConnect: () => {
      onStatus?.("connected");
      onConnect?.(client);
    },
    onWebSocketClose: () => onStatus?.("disconnected"),
    onStompError: () => onStatus?.("error"),
  });
  client.activate();
  return client;
}

/**
 * Opens one authenticated STOMP connection and subscribes to /topic/vehicles/{id} for each vehicle id.
 * onLocation receives a VehicleLocationEvent per message; onStatus ("connected" | "disconnected" | "error")
 * lets the UI show connection health. Returns a disconnect function for effect cleanup.
 */
export function watchVehicles(vehicleIds, onLocation, onStatus) {
  if (!vehicleIds || vehicleIds.length === 0) return () => {};

  const client = createClient((c) => {
    for (const vehicleId of vehicleIds) {
      c.subscribe(`/topic/vehicles/${vehicleId}`, (message) => {
        try {
          onLocation(JSON.parse(message.body));
        } catch {
          // ignore malformed frame
        }
      });
    }
  }, onStatus);

  return () => {
    client.deactivate();
  };
}

/**
 * Driver-side location streaming: publishes to /app/location over the authenticated WebSocket, and
 * falls back to the REST endpoint while the socket is (re)connecting. Returns { send, stop }.
 */
export function createLocationPublisher(vehicleId, onStatus) {
  const client = createClient(null, onStatus);

  return {
    send(lat, lng, speed) {
      const payload = { vehicleId, lat, lng, speed: speed ?? null, timestamp: new Date().toISOString() };
      if (client.connected) {
        client.publish({ destination: "/app/location", body: JSON.stringify(payload) });
      } else {
        postLocation(vehicleId, lat, lng, speed).catch(() => {});
      }
    },
    stop() {
      client.deactivate();
    },
  };
}
