import { NavLink } from "react-router-dom";

const links = [
  { to: "/", label: "Dashboard", end: true },
  { to: "/map", label: "Live map" },
  { to: "/orders", label: "Orders" },
  { to: "/drivers", label: "Drivers" },
  { to: "/customers", label: "Customers" },
  { to: "/analytics", label: "Analytics" },
];

export default function Navbar({ user, onLogout }) {
  return (
    <nav className="navbar">
      <span className="navbar-brand">RouteFlow</span>
      {links.map((link) => (
        <NavLink
          key={link.to}
          to={link.to}
          end={link.end}
          className={({ isActive }) => "navbar-link" + (isActive ? " active" : "")}
        >
          {link.label}
        </NavLink>
      ))}
      {user && (
        <div className="navbar-user">
          <span>{user.name} &middot; {user.role}</span>
          <button type="button" className="btn btn-sm" onClick={onLogout}>
            Log out
          </button>
        </div>
      )}
    </nav>
  );
}
