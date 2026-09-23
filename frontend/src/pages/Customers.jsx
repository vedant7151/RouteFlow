import { useEffect, useState } from "react";
import { getOrders } from "../api.js";

/**
 * There's no separate Customer entity in the backend - a "customer" here is just the
 * name+phone attached to orders, grouped client-side. Read-only: add/edit customer info
 * via the Orders page instead.
 */
export default function Customers() {
  const [customers, setCustomers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    (async () => {
      try {
        const orders = await getOrders();
        const byKey = new Map();
        for (const order of orders) {
          const key = `${order.customerName || "Unknown"}|${order.customerPhone || ""}`;
          if (!byKey.has(key)) {
            byKey.set(key, {
              name: order.customerName || "Unknown",
              phone: order.customerPhone || "",
              orders: [],
            });
          }
          byKey.get(key).orders.push(order);
        }
        setCustomers(
          [...byKey.values()].sort((a, b) => b.orders.length - a.orders.length)
        );
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Customers</h1>
          <p>Derived from order history &middot; {customers.length} customer{customers.length === 1 ? "" : "s"}</p>
        </div>
      </div>

      {error && <div className="banner-error">{error}</div>}

      <div className="card">
        {loading ? (
          <div className="empty-state">Loading...</div>
        ) : customers.length === 0 ? (
          <div className="empty-state">No customers yet - they appear here once you add orders.</div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Phone</th>
                <th>Orders</th>
                <th>Last address</th>
              </tr>
            </thead>
            <tbody>
              {customers.map((c) => (
                <tr key={`${c.name}|${c.phone}`}>
                  <td>{c.name}</td>
                  <td>{c.phone || "-"}</td>
                  <td>{c.orders.length}</td>
                  <td>{c.orders[c.orders.length - 1]?.addressText}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
