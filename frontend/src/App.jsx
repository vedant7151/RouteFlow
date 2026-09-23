import { lazy, Suspense, useEffect, useState } from "react";
import { BrowserRouter, Routes, Route, useLocation } from "react-router-dom";
import { getCurrentUser, logout } from "./api.js";
import Navbar from "./components/Navbar.jsx";
import Login from "./pages/Login.jsx";
import Dashboard from "./pages/Dashboard.jsx";
import Orders from "./pages/Orders.jsx";
import Drivers from "./pages/Drivers.jsx";
import Customers from "./pages/Customers.jsx";
const LiveMap = lazy(() => import("./pages/LiveMap.jsx"));
const Analytics = lazy(() => import("./pages/Analytics.jsx"));
const DriverView = lazy(() => import("./pages/DriverView.jsx"));

// Pages that need the full viewport width (the map) instead of the default centered column.
const WIDE_PAGES = ["/map"];

function PageContainer() {
  const { pathname } = useLocation();
  const wide = WIDE_PAGES.includes(pathname);
  return (
    <div className={`page ${wide ? "page-wide" : ""}`}>
      <Suspense fallback={<div className="empty-state">Loading...</div>}>
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/map" element={<LiveMap />} />
        <Route path="/orders" element={<Orders />} />
        <Route path="/drivers" element={<Drivers />} />
        <Route path="/customers" element={<Customers />} />
        <Route path="/analytics" element={<Analytics />} />
      </Routes>
      </Suspense>
    </div>
  );
}

export default function App() {
  const [user, setUser] = useState(() => getCurrentUser());

  // A 401 from any API call clears the stored session and fires this - drop back to the login
  // screen immediately instead of leaving stale, now-broken pages on screen.
  useEffect(() => {
    function handleExpired() {
      setUser(null);
    }
    window.addEventListener("routeflow:session-expired", handleExpired);
    return () => window.removeEventListener("routeflow:session-expired", handleExpired);
  }, []);

  function handleLogout() {
    logout();
    setUser(null);
  }

  if (!user) {
    return <Login onLogin={setUser} />;
  }

  // Drivers get the mobile driver view, not the dispatcher console (the API would 403 them there anyway).
  if (user.role === "DRIVER") {
    return (
      <Suspense fallback={<div className="empty-state">Loading...</div>}>
        <DriverView user={user} onLogout={handleLogout} />
      </Suspense>
    );
  }

  return (
    <BrowserRouter>
      <div className="app-shell">
        <Navbar user={user} onLogout={handleLogout} />
        <PageContainer />
      </div>
    </BrowserRouter>
  );
}
