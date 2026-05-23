import { useState, useEffect, useCallback } from "react";

// ─── API CONFIG ──────────────────────────────────────────────────────────────
const API_BASE = "http://localhost:8000/api";
const getHeaders = () => ({
  "Content-Type": "application/json",
  Authorization: `Bearer ${localStorage.getItem("scms_token") || ""}`,
});

const api = {
  get: (path) => 
    fetch(`${API_BASE}${path}`, { headers: getHeaders() })
      .then(async (r) => {
        const data = await r.json();
        if (!r.ok) throw new Error(data.message || "Fetch failed");
        return data;
      }),

  post: (path, body) =>
    fetch(`${API_BASE}${path}`, { method: "POST", headers: getHeaders(), body: JSON.stringify(body) })
      .then(async (r) => {
        const data = await r.json();
        if (!r.ok) throw new Error(data.message || "Submission failed");
        return data;
      }),

  put: (path, body) =>
    fetch(`${API_BASE}${path}`, { method: "PUT", headers: getHeaders(), body: JSON.stringify(body) })
      .then(async (r) => {
        const data = await r.json();
        if (!r.ok) throw new Error(data.message || "Update failed");
        return data;
      }),

  patch: (path, body) =>
    fetch(`${API_BASE}${path}`, { method: "PATCH", headers: getHeaders(), body: JSON.stringify(body) })
      .then(async (r) => {
        const data = await r.json();
        if (!r.ok) throw new Error(data.message || "Update failed");
        return data;
      }),

  delete: (path) =>
    fetch(`${API_BASE}${path}`, { method: "DELETE", headers: getHeaders() })
      .then(async (r) => {
        const data = await r.json();
        if (!r.ok) throw new Error(data.message || "Delete failed");
        return data;
      }),
};
          
// ─── CONSTANTS ───────────────────────────────────────────────────────────────
const NAV_ITEMS = [
  { key: "dashboard", label: "Dashboard", icon: "ti-layout-dashboard" },
  { key: "inventory", label: "Inventory", icon: "ti-package" },
  { key: "orders", label: "Orders", icon: "ti-file-invoice" },
  { key: "deliveries", label: "Deliveries", icon: "ti-truck" },
  { key: "suppliers", label: "Suppliers", icon: "ti-building-factory-2" },
  { key: "reports", label: "Reports & Analytics", icon: "ti-chart-bar" },
];

const STATUS_STYLES = {
  pending:    { bg: "#FEF3C7", color: "#92400E", label: "Pending" },
  approved:   { bg: "#D1FAE5", color: "#065F46", label: "Approved" },
  shipped:    { bg: "#DBEAFE", color: "#1E40AF", label: "Shipped" },
  delivered:  { bg: "#F3F4F6", color: "#374151", label: "Delivered" },
  rejected:   { bg: "#FEE2E2", color: "#991B1B", label: "Rejected" },
  in_transit: { bg: "#DBEAFE", color: "#1E40AF", label: "In Transit" },
  out_for_delivery: { bg: "#D1FAE5", color: "#065F46", label: "Out for Delivery" },
  // Role badges for User Management
  admin:      { bg: "#EEF2FF", color: "#4338CA", label: "Admin" },
  manager:    { bg: "#FDF2F7", color: "#9D174D", label: "Manager" },
  field_personnel: { bg: "#F3F4F6", color: "#374151", label: "Personnel" },
};

const ACTIVITY_ICONS = {
  order:    { icon: "ti-file-invoice", bg: "#EEF2FF", color: "#4338CA" },
  delivery: { icon: "ti-truck",        bg: "#ECFDF5", color: "#059669" },
  stock:    { icon: "ti-alert-triangle", bg: "#FEF3C7", color: "#D97706" },
  delivery_complete: { icon: "ti-package", bg: "#D1FAE5", color: "#065F46" },
  user: { icon: "ti-user", bg: "#F3F4F6", color: "#374151" },
};


// ─── BADGE ────────────────────────────────────────────────────────────────────
function StatusBadge({ status }) {
  const s = STATUS_STYLES[status] || { bg: "#F3F4F6", color: "#374151", label: status };
  return (
    <span style={{ background: s.bg, color: s.color, fontSize: 12, fontWeight: 500, padding: "3px 10px", borderRadius: 20, whiteSpace: "nowrap" }}>
      {s.label}
    </span>
  );
}

// ─── MODAL ────────────────────────────────────────────────────────────────────
function Modal({ title, onClose, children }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.45)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 9999 }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 32, width: "90%", maxWidth: 520, boxShadow: "0 20px 60px rgba(0,0,0,0.15)", maxHeight: "90vh", overflowY: "auto" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
          <h3 style={{ margin: 0, fontSize: 18, fontWeight: 600, color: "#111827" }}>{title}</h3>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", fontSize: 20, color: "#6B7280" }}>✕</button>
        </div>
        {children}
      </div>
    </div>
  );
}

// ─── FORM FIELD ───────────────────────────────────────────────────────────────
function Field({ label, children }) {
  return (
    <div style={{ marginBottom: 16 }}>
      <label style={{ display: "block", fontSize: 13, fontWeight: 500, color: "#374151", marginBottom: 6 }}>{label}</label>
      {children}
    </div>
  );
}
const inputStyle = { width: "100%", padding: "9px 12px", border: "1px solid #D1D5DB", borderRadius: 8, fontSize: 14, color: "#111827", boxSizing: "border-box", outline: "none" };
const selectStyle = { ...inputStyle, background: "#fff" };

// ─── CONFIRM DIALOG ───────────────────────────────────────────────────────────
function Confirm({ message, onConfirm, onCancel }) {
  return (
    <Modal title="Confirm Action" onClose={onCancel}>
      <p style={{ color: "#374151", marginBottom: 24 }}>{message}</p>
      <div style={{ display: "flex", gap: 12, justifyContent: "flex-end" }}>
        <button onClick={onCancel} style={{ padding: "9px 20px", borderRadius: 8, border: "1px solid #D1D5DB", background: "#fff", cursor: "pointer", fontSize: 14 }}>Cancel</button>
        <button onClick={onConfirm} style={{ padding: "9px 20px", borderRadius: 8, border: "none", background: "#EF4444", color: "#fff", cursor: "pointer", fontSize: 14, fontWeight: 500 }}>Confirm</button>
      </div>
    </Modal>
  );
}

// ─── TOAST ────────────────────────────────────────────────────────────────────
function Toast({ message, type, onClose }) {
  useEffect(() => { const t = setTimeout(onClose, 3000); return () => clearTimeout(t); }, []);
  const colors = { success: { bg: "#D1FAE5", color: "#065F46", border: "#6EE7B7" }, error: { bg: "#FEE2E2", color: "#991B1B", border: "#FCA5A5" }, info: { bg: "#DBEAFE", color: "#1E40AF", border: "#93C5FD" } };
  const c = colors[type] || colors.info;
  return (
    <div style={{ position: "fixed", bottom: 24, right: 24, background: c.bg, color: c.color, border: `1px solid ${c.border}`, borderRadius: 10, padding: "12px 20px", fontSize: 14, fontWeight: 500, zIndex: 99999, boxShadow: "0 4px 12px rgba(0,0,0,0.1)", maxWidth: 320 }}>
      {message}
    </div>
  );
}

// ─── LOGIN PAGE ───────────────────────────────────────────────────────────────
function LoginPage({ onLogin }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError("");
    
    try {
      const res = await api.post("/auth/login", { email, password });
      
      // 1. Check for 'user' and 'token' (matching your PHP return keys)
      if (res.user && res.token) {
        localStorage.setItem("scms_token", res.token);
        
        // 2. Pass the user object to the app state
        onLogin(res.user); 
      } else {
        // If the code reaches here, the keys in PHP and JS are different
        setError("Login successful, but server response is missing user data.");
      }
    } catch (err) {
      // This catches the 404, 401, or 403 errors from your AuthController
      setError(err.message || "Invalid email or password.");
    } finally {
      setLoading(false);
    }
  };
  

  return (
    <div style={{ minHeight: "100vh", background: "#F8FAFC", display: "flex", alignItems: "center", justifyContent: "center" }}>
      <div style={{ background: "#fff", borderRadius: 20, padding: 48, width: "90%", maxWidth: 420, boxShadow: "0 8px 40px rgba(0,0,0,0.08)" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 32 }}>
          <div style={{ width: 42, height: 42, background: "#1D4ED8", borderRadius: 10, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <i className="ti ti-truck" style={{ color: "#fff", fontSize: 22 }} />
          </div>
          <div>
            <div style={{ fontSize: 17, fontWeight: 700, color: "#111827" }}>SCMS</div>
            <div style={{ fontSize: 12, color: "#6B7280" }}>Supply Chain Management</div>
          </div>
        </div>
        <h2 style={{ margin: "0 0 8px", fontSize: 22, fontWeight: 700, color: "#111827" }}>Welcome back</h2>
        <p style={{ margin: "0 0 28px", color: "#6B7280", fontSize: 14 }}>Sign in to your admin account</p>
        {error && <div style={{ background: "#FEE2E2", color: "#991B1B", padding: "10px 14px", borderRadius: 8, marginBottom: 16, fontSize: 13 }}>{error}</div>}
        <form onSubmit={handleSubmit}>
          <Field label="Email address">
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} style={inputStyle} placeholder="Enter your email(admin@scms.local)" required />
          </Field>
          <Field label="Password">
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} style={inputStyle} placeholder="Enter your password" required />
          </Field>
          <button type="submit" disabled={loading} style={{ width: "100%", padding: "11px", background: "#1D4ED8", color: "#fff", border: "none", borderRadius: 10, fontSize: 15, fontWeight: 600, cursor: "pointer", marginTop: 8 }}>
            {loading ? "Signing in…" : "Sign in"}
          </button>
        </form>
      </div>
    </div>
  );
}
const Loading = () => (
  <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", minHeight: "450px", gap: 16 }}>
    <div style={{
      width: 40, height: 40, border: "3px solid #f3f3f3", borderTop: "3px solid #1D4ED8",
      borderRadius: "50%", animation: "spin 1s linear infinite"
    }} />
    <p style={{ color: "#6B7280", fontSize: 14, fontWeight: 500 }}>Syncing Dashboard Metrics...</p>
    <style>{`@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }`}</style>
  </div>
);

function DashboardPage({ toast, onNavigate }) {
  // 1. Unified State Management
  const [stats, setStats] = useState({ 
    total_products: 0, 
    pending_orders: 0, 
    active_deliveries: 0, 
    low_stock_items: 0 
  });
  
  const [recentOrders, setRecentOrders] = useState([]);
  const [activityFeed, setActivityFeed] = useState([]);
  const [loading, setLoading] = useState(true);

  // 2. Data Fetching
  useEffect(() => {
    const fetchDashboardData = async () => {
      setLoading(true);
      try {
        const res = await api.get("/reports/dashboard");
        
        // Syncing with your PHP keys
        setStats(res); 
        setRecentOrders(res.recent_orders || []);
        setActivityFeed(res.activity_feed || []); 

      } catch (error) {
        console.error("Dashboard sync error:", error);
        toast("Could not sync data from server", "error");
      } finally {
        // Subtle delay to prevent flickering on ultra-fast connections
        setTimeout(() => setLoading(false), 400); 
      }
    };
    fetchDashboardData();
  }, [toast]);

  // 3. UI Configuration for KPI Cards
  const statsCards = [
    { label: "Total Products", value: stats.total_products ?? 0, icon: "ti-package", color: "#1D4ED8", bg: "#EFF6FF" },
    { label: "Pending Orders", value: stats.pending_orders ?? 0, icon: "ti-file-invoice", color: "#D97706", bg: "#FFFBEB" },
    { label: "Active Deliveries", value: stats.active_deliveries ?? 0, icon: "ti-truck", color: "#059669", bg: "#ECFDF5" },
    { label: "Low Stock Alerts", value: stats.low_stock_items ?? 0, icon: "ti-alert-triangle", color: "#DC2626", bg: "#FEF2F2" },
  ];

  // 4. Guard Clause for Loading State
  if (loading) return <Loading />;

  return (
    <div>
      <h2 style={{ margin: "0 0 8px", fontSize: 22, fontWeight: 700, color: "#111827" }}>Dashboard</h2>
      <p style={{ margin: "0 0 28px", color: "#6B7280", fontSize: 14 }}>Real-time overview of supply chain operations</p>

      {/* KPI Cards Section */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 16, marginBottom: 32 }}>
        {statsCards.map((s) => (
          <div key={s.label} style={{ background: "#fff", borderRadius: 14, padding: "20px", border: "1px solid #E5E7EB", boxShadow: "0 1px 3px rgba(0,0,0,0.02)" }}>
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 14 }}>
              <span style={{ fontSize: 13, color: "#6B7280", fontWeight: 500 }}>{s.label}</span>
              <div style={{ width: 36, height: 36, borderRadius: 9, background: s.bg, display: "flex", alignItems: "center", justifyContent: "center" }}>
                <i className={`ti ${s.icon}`} style={{ color: s.color, fontSize: 18 }} />
              </div>
            </div>
            <div style={{ fontSize: 30, fontWeight: 700, color: "#111827" }}>{s.value}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 340px", gap: 20 }}>
        
        {/* Recent Orders Table */}
        <div style={{ background: "#fff", borderRadius: 14, border: "1px solid #E5E7EB", overflow: "hidden" }}>
          <div style={{ padding: "18px 20px", borderBottom: "1px solid #F3F4F6", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <h3 style={{ margin: 0, fontSize: 15, fontWeight: 600, color: "#111827" }}>Recent Orders</h3>
            <span onClick={() => onNavigate("orders")} style={{ fontSize: 12, color: "#1D4ED8", cursor: "pointer", fontWeight: 500 }}>View all</span>
          </div>
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
              <thead>
                <tr style={{ background: "#F9FAFB" }}>
                  {["Order ID", "Product", "Supplier", "Total", "Status"].map((h) => (
                    <th key={h} style={{ padding: "10px 16px", textAlign: "left", fontWeight: 500, color: "#6B7280" }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {recentOrders.length > 0 ? recentOrders.map((o) => (
                  <tr key={o.id} style={{ borderTop: "1px solid #F3F4F6" }}>
                    <td style={{ padding: "11px 16px", color: "#1D4ED8", fontWeight: 500 }}>#{o.id}</td>
                    <td style={{ padding: "11px 16px", color: "#374151" }}>{o.product?.name || "N/A"}</td>
                    <td style={{ padding: "11px 16px", color: "#6B7280" }}>{o.supplier?.name || "N/A"}</td>
                    <td style={{ padding: "11px 16px", color: "#111827", fontWeight: 500 }}>₱{(o.total_amount || 0).toLocaleString()}</td>
                    <td style={{ padding: "11px 16px" }}><StatusBadge status={o.status} /></td>
                  </tr>
                )) : (
                  <tr>
                    <td colSpan="5" style={{ padding: 40, textAlign: "center", color: "#9CA3AF" }}>No recent orders found.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Activity Feed */}
        <div style={{ background: "#fff", borderRadius: 14, border: "1px solid #E5E7EB" }}>
          <div style={{ padding: "18px 20px", borderBottom: "1px solid #F3F4F6" }}>
            <h3 style={{ margin: 0, fontSize: 15, fontWeight: 600, color: "#111827" }}>Activity Feed</h3>
          </div>
          <div style={{ padding: "8px 0" }}>
            {activityFeed.length > 0 ? activityFeed.map((a) => {
              const config = ACTIVITY_ICONS[a.type] || ACTIVITY_ICONS.order;
              return (
                <div key={a.id} style={{ display: "flex", gap: 12, padding: "12px 20px", borderBottom: "1px solid #F9FAFB" }}>
                  <div style={{ width: 34, height: 34, borderRadius: 9, background: config.bg, display: "flex", alignItems: "center", justifyContent: "center" }}>
                    <i className={`ti ${config.icon}`} style={{ color: config.color, fontSize: 16 }} />
                  </div>
                  <div>
                    <div style={{ fontSize: 13, color: "#374151", lineHeight: 1.4 }}>{a.message}</div>
                    <div style={{ fontSize: 11, color: "#9CA3AF", marginTop: 4 }}>
                      {new Date(a.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </div>
                  </div>
                </div>
              );
            }) : (
              <div style={{ padding: "40px 20px", textAlign: "center", color: "#9CA3AF", fontSize: 13 }}>
                No recent activity to show.
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
// ─── INVENTORY PAGE ───────────────────────────────────────────────────────────

function InventoryPage({ toast }) {
  const [products, setProducts] = useState([]);
  const [suppliers, setSuppliers] = useState([]);
  const [search, setSearch] = useState("");
  const [showModal, setShowModal] = useState(false);
  const [showDispatchModal, setShowDispatchModal] = useState(false);
  const [editing, setEditing] = useState(null);
  const [confirm, setConfirm] = useState(null);
  const [loading, setLoading] = useState(true);

  const [form, setForm] = useState({
    name: "",
    sku: "",
    category: "",
    stock: "",
    reorder: "",
    unit_price: "",
    supplier_id: "",
  });

  // Form State for Outbound Stock Deductions
  const [dispatchForm, setDispatchForm] = useState({ product_id: "", quantity: "", reason: "Customer Sale" });

  // 1. DATA FETCHING (Handles Laravel Pagination wrappers)
  const fetchInitialData = async () => {
    setLoading(true);
    try {
      const [productsRes, suppliersRes] = await Promise.all([
        api.get("/products"),
        api.get("/suppliers"),
      ]);

      const actualProducts = productsRes.data ? productsRes.data : (Array.isArray(productsRes) ? productsRes : []);
      const actualSuppliers = suppliersRes.data ? suppliersRes.data : (Array.isArray(suppliersRes) ? suppliersRes : []);

      setProducts(actualProducts);
      setSuppliers(actualSuppliers);
    } catch (err) {
      console.error("Failed to load Inventory data", err);
      toast("Error loading inventory items", "error");
    } finally {
      setTimeout(() => setLoading(false), 300);
    }
  };

  useEffect(() => {
    fetchInitialData();
  }, [toast]);

  // 2. SEARCH FILTERING
  const filtered = products.filter(
    (p) =>
      (p.name?.toLowerCase() || "").includes(search.toLowerCase()) ||
      (p.sku?.toLowerCase() || "").includes(search.toLowerCase())
  );

  // 3. HANDLERS
  const openAdd = () => {
    setEditing(null);
    setForm({ name: "", sku: "", category: "", stock: "", reorder: "", unit_price: "", supplier_id: "" });
    setShowModal(true);
  };

  const openEdit = (p) => {
    setEditing(p);
    setForm({
      name: p.name,
      sku: p.sku,
      category: p.category,
      stock: p.stock_qty ?? p.stock ?? 0,
      reorder: p.reorder_point ?? p.reorder ?? 0,
      unit_price: p.unit_price,
      supplier_id: p.supplier_id || "",
    });
    setShowModal(true);
  };

  const handleSave = async () => {
    if (!form.name || !form.sku || !form.supplier_id) {
      toast("Name, SKU, and Supplier are required", "error");
      return;
    }

    const payload = {
      name: form.name,
      sku: form.sku,
      category: form.category || "General",
      supplier_id: Number(form.supplier_id),
      stock_qty: Number(form.stock || 0),
      reorder_point: Number(form.reorder || 10),
      unit_price: Number(form.unit_price || 0),
      unit: "pcs",
    };

    try {
      if (editing) {
        const res = await api.put(`/products/${editing.id}`, payload);
        setProducts((prev) => prev.map((p) => (p.id === editing.id ? { ...p, ...res } : p)));
        toast("Product updated successfully", "success");
      } else {
        const res = await api.post("/products", payload);
        setProducts((prev) => [res, ...prev]);
        toast("Product added successfully", "success");
      }
      setShowModal(false);
      fetchInitialData(); // Refresh values to pull relational data updates safely
    } catch (err) {
      toast("Check validation: SKU must be unique", "error");
    }
  };

  const handleDelete = (id) => {
    setConfirm({
      msg: "Delete this product from inventory?",
      action: async () => {
        try {
          await api.delete(`/products/${id}`);
          setProducts((prev) => prev.filter((p) => p.id !== id));
          toast("Product deleted", "success");
        } catch (err) {
          toast("Delete failed", "error");
        }
        setConfirm(null);
      },
    });
  };

  // 4. OUTBOUND DISPATCH TRANSACTION SUBMIT
  const handleDispatchSubmit = async (e) => {
    e.preventDefault();
    if (!dispatchForm.product_id || !dispatchForm.quantity) {
      toast("Please complete all dispatch fields", "error");
      return;
    }

    try {
      await api.post("/inventory/dispatch", {
        product_id: parseInt(dispatchForm.product_id),
        quantity: parseInt(dispatchForm.quantity),
        reason: dispatchForm.reason
      });

      toast("Stock successfully dispatched", "success");
      setShowDispatchModal(false);
      setDispatchForm({ product_id: "", quantity: "", reason: "Customer Sale" });
      fetchInitialData(); // Force instant quantity sync across layout lines
    } catch (err) {
      toast(err.response?.data?.message || "Dispatch transaction failed", "error");
    }
  };

  if (loading) return <Loading />;

  return (
    <div>
      {/* HEADER SECTION */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 22, fontWeight: 700, color: "#111827" }}>Inventory Management</h2>
          <p style={{ margin: "4px 0 0", color: "#6B7280", fontSize: 14 }}>{products.length} products tracked</p>
        </div>
        <div style={{ display: "flex", gap: 12 }}>
          {/* Dispatch Button */}
          <button 
            onClick={() => setShowDispatchModal(true)} 
            style={{ display: "flex", alignItems: "center", gap: 8, padding: "10px 18px", background: "#DC2626", color: "#fff", border: "none", borderRadius: 10, fontSize: 14, fontWeight: 500, cursor: "pointer" }}
          >
            <i className="ti ti-minus" /> Dispatch Stock (Outbound)
          </button>
          {/* Create Product Button */}
          <button 
            onClick={openAdd} 
            style={{ display: "flex", alignItems: "center", gap: 8, padding: "10px 18px", background: "#1D4ED8", color: "#fff", border: "none", borderRadius: 10, fontSize: 14, fontWeight: 500, cursor: "pointer" }}
          >
            <i className="ti ti-plus" /> Add Product
          </button>
        </div>
      </div>

      {/* SEARCH & TABLE SECTION */}
      <div style={{ background: "#fff", borderRadius: 14, border: "1px solid #E5E7EB", overflow: "hidden" }}>
        <div style={{ padding: "14px 16px", borderBottom: "1px solid #F3F4F6" }}>
          <input placeholder="Search by name or SKU…" value={search} onChange={(e) => setSearch(e.target.value)} style={{ ...inputStyle, maxWidth: 300 }} />
        </div>
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead>
              <tr style={{ background: "#F9FAFB" }}>
                {["SKU", "Product Name", "Category", "Supplier", "Stock", "Reorder Pt.", "Unit Price", "Status", "Actions"].map((h) => (
                  <th key={h} style={{ padding: "10px 14px", textAlign: "left", fontWeight: 500, color: "#6B7280", whiteSpace: "nowrap" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.map((p) => {
                const stock = p.stock_qty ?? p.stock ?? 0;
                const reorder = p.reorder_point ?? p.reorder ?? 0;
                const isLow = stock <= reorder;

                return (
                  <tr key={p.id} style={{ borderTop: "1px solid #F3F4F6" }}>
                    <td style={{ padding: "12px 14px", color: "#6B7280", fontFamily: "monospace" }}>{p.sku}</td>
                    <td style={{ padding: "12px 14px", fontWeight: 500, color: "#111827" }}>{p.name}</td>
                    <td style={{ padding: "12px 14px", color: "#6B7280" }}>{p.category}</td>
                    <td style={{ padding: "12px 14px", color: "#6B7280" }}>{p.supplier?.name || "N/A"}</td>
                    <td style={{ padding: "12px 14px", fontWeight: 600, color: isLow ? "#DC2626" : "#059669" }}>{stock}</td>
                    <td style={{ padding: "12px 14px", color: "#6B7280" }}>{reorder}</td>
                    <td style={{ padding: "12px 14px", color: "#374151" }}>₱{Number(p.unit_price || 0).toLocaleString(undefined, { minimumFractionDigits: 2 })}</td>
                    <td style={{ padding: "12px 14px" }}>
                      {isLow ? <span style={{ background: "#FEE2E2", color: "#991B1B", fontSize: 11, padding: "3px 10px", borderRadius: 20, fontWeight: 500 }}>Low Stock</span>
                        : <span style={{ background: "#D1FAE5", color: "#065F46", fontSize: 11, padding: "3px 10px", borderRadius: 20, fontWeight: 500 }}>In Stock</span>}
                    </td>
                    <td style={{ padding: "12px 14px" }}>
                      <div style={{ display: "flex", gap: 8 }}>
                        <button onClick={() => openEdit(p)} style={{ background: "#EFF6FF", border: "none", borderRadius: 7, padding: "6px 10px", cursor: "pointer", color: "#1D4ED8", fontSize: 13 }}><i className="ti ti-edit" /></button>
                        <button onClick={() => handleDelete(p.id)} style={{ background: "#FEF2F2", border: "none", borderRadius: 7, padding: "6px 10px", cursor: "pointer", color: "#DC2626", fontSize: 13 }}><i className="ti ti-trash" /></button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* CRUD MANAGED FORM MODAL */}
      {showModal && (
        <Modal title={editing ? "Edit Product" : "Add New Product"} onClose={() => setShowModal(false)}>
          <Field label="Product Name">
            <input value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} style={inputStyle} />
          </Field>
          <Field label="SKU">
            <input value={form.sku} onChange={(e) => setForm((f) => ({ ...f, sku: e.target.value }))} style={inputStyle} />
          </Field>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <Field label="Category">
              <input value={form.category} onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))} style={inputStyle} />
            </Field>
            <Field label="Supplier">
              <select value={form.supplier_id} onChange={(e) => setForm((f) => ({ ...f, supplier_id: e.target.value }))} style={selectStyle}>
                <option value="">Select Supplier</option>
                {suppliers.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </Field>
            <Field label="Stock Qty">
              <input type="number" value={form.stock} onChange={(e) => setForm((f) => ({ ...f, stock: e.target.value }))} style={inputStyle} />
            </Field>
            <Field label="Reorder Point">
              <input type="number" value={form.reorder} onChange={(e) => setForm((f) => ({ ...f, reorder: e.target.value }))} style={inputStyle} />
            </Field>
          </div>
          <Field label="Unit Price (₱)">
            <input type="number" step="0.01" value={form.unit_price} onChange={(e) => setForm((f) => ({ ...f, unit_price: e.target.value }))} style={inputStyle} />
          </Field>
          <div style={{ display: "flex", gap: 12, justifyContent: "flex-end", marginTop: 12 }}>
            <button onClick={() => setShowModal(false)} style={cancelStyle}>Cancel</button>
            <button onClick={handleSave} style={saveStyle}>Save</button>
          </div>
        </Modal>
      )}

      {/* OUTBOUND STOCK DISPATCH MODAL */}
      {showDispatchModal && (
        <div style={overlayStyle}>
          <div style={modalBoxStyle}>
            <h3 style={{ margin: "0 0 16px", fontSize: 16, fontWeight: 600, color: "#111827" }}>Deduct Warehouse Inventory</h3>
            <form onSubmit={handleDispatchSubmit}>
              
              <div style={{ marginBottom: 12 }}>
                <label style={labelStyle}>Select Product Item</label>
                <select 
                  value={dispatchForm.product_id} 
                  onChange={(e) => setDispatchForm({ ...dispatchForm, product_id: e.target.value })}
                  style={selectStyle}
                >
                  <option value="">-- Choose item on dock --</option>
                  {products.map(p => (
                    <option key={p.id} value={p.id}>{p.name} (Avail: {p.stock_qty ?? p.stock ?? 0})</option>
                  ))}
                </select>
              </div>

              <div style={{ marginBottom: 12 }}>
                <label style={labelStyle}>Quantity Leaving Facility</label>
                <input 
                  type="number" 
                  min="1"
                  value={dispatchForm.quantity}
                  onChange={(e) => setDispatchForm({ ...dispatchForm, quantity: e.target.value })}
                  style={inputStyle}
                  placeholder="e.g. 15"
                />
              </div>

              <div style={{ marginBottom: 20 }}>
                <label style={labelStyle}>Reason for Outbound Log</label>
                <select 
                  value={dispatchForm.reason} 
                  onChange={(e) => setDispatchForm({ ...dispatchForm, reason: e.target.value })}
                  style={selectStyle}
                >
                  <option value="Customer Sale">Direct Customer Sale</option>
                  <option value="Internal Dispatch">Internal Team Allocation</option>
                  <option value="Damaged Stock">Damaged / Expired Wastage</option>
                </select>
              </div>

              <div style={{ display: "flex", gap: 12, justifyContent: "flex-end" }}>
                <button type="button" onClick={() => setShowDispatchModal(false)} style={cancelStyle}>Cancel</button>
                <button type="submit" style={dispatchSubmitStyle}>Execute Deduction</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* CONFIRMATION DIALOG */}
      {confirm && <Confirm message={confirm.msg} onConfirm={confirm.action} onCancel={() => setConfirm(null)} />}
    </div>
  );
}

// ─── STYLING ATTRIBUTES ──────────────────────────────────────────────────────
const labelStyle = { display: "block", fontSize: 12, fontWeight: 500, color: "#4B5563", marginBottom: 6 };
const overlayStyle = { position: "fixed", top: 0, left: 0, width: "100%", height: "100%", background: "rgba(0,0,0,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 };
const modalBoxStyle = { background: "#fff", padding: 24, borderRadius: 12, width: 400, boxShadow: "0 4px 12px rgba(0,0,0,0.15)" };
const cancelStyle = { padding: "9px 20px", borderRadius: 8, border: "1px solid #D1D5DB", background: "#fff", cursor: "pointer", fontSize: 14 };
const saveStyle = { padding: "9px 20px", borderRadius: 8, border: "none", background: "#1D4ED8", color: "#fff", cursor: "pointer", fontSize: 14, fontWeight: 500 };
const dispatchSubmitStyle = { padding: "9px 16px", borderRadius: 8, border: "none", background: "#DC2626", color: "#fff", fontWeight: 500, cursor: "pointer", fontSize: 14 };

function OrdersPage({ toast }) {
  const [orders, setOrders] = useState([]);
  const [products, setProducts] = useState([]);
  const [suppliers, setSuppliers] = useState([]);
  const [filter, setFilter] = useState("all");
  const [showModal, setShowModal] = useState(false);
  const [confirm, setConfirm] = useState(null);
  const [loading, setLoading] = useState(true);
  
  const [form, setForm] = useState({ product_id: "", supplier_id: "", qty: "", unit_price: "" });

  useEffect(() => {
    const loadData = async () => {
      setLoading(true);
      try {
        const [o, p, s] = await Promise.all([
          api.get("/orders"),
          api.get("/products"),
          api.get("/suppliers")
        ]);

        setOrders(o.data || (Array.isArray(o) ? o : []));
        setProducts(p.data || (Array.isArray(p) ? p : []));
        setSuppliers(s.data || (Array.isArray(s) ? s : []));

      } catch (err) {
        console.error("Database sync failed:", err);
        toast("Failed to load records from server", "error");
      } finally {
        setTimeout(() => setLoading(false), 300);
      }
    };
    loadData();
  }, [toast]);

  // Simplified Filter Logic (Removed 'shipped')
  const filtered = Array.isArray(orders) 
    ? (filter === "all" ? orders : orders.filter((o) => o.status === filter))
    : [];

  const handleAction = (id, newStatus) => {
    const label = newStatus === "approved" ? "Approve" : "Reject";
    setConfirm({ 
      msg: `${label} order #${id}?`, 
      action: async () => {
        try { 
          await api.patch(`/orders/${id}/status`, { status: newStatus }); 
          setOrders((prev) => prev.map((o) => (o.id === id ? { ...o, status: newStatus } : o)));
          toast(`Order #${id} ${newStatus}`, "success");
        } catch (err) { 
          toast("Update failed.", "error"); 
        }
        setConfirm(null);
      }
    });
  };

  const handleDelete = (id, status) => {
    if (status !== "delivered" && status !== "rejected") {
      toast("Only completed or rejected orders can be deleted", "error");
      return;
    }

    setConfirm({
      msg: `Permanently delete Order #${id} from records?`,
      action: async () => {
        try {
          await api.delete(`/orders/${id}`);
          setOrders((prev) => prev.filter((o) => o.id !== id));
          toast(`Order #${id} deleted`, "success");
        } catch (err) {
          toast("Could not delete record.", "error");
        }
        setConfirm(null);
      },
    });
  };

  const handleCreate = async () => {
    if (!form.product_id || !form.supplier_id || !form.qty || !form.unit_price) {
      toast("Please complete all order fields", "error");
      return;
    }

    const qty = Number(form.qty);
    const price = Number(form.unit_price);
    
    const payload = {
      product_id: Number(form.product_id),
      supplier_id: Number(form.supplier_id),
      qty: qty,
      unit_price: price,
      total_amount: qty * price, 
      status: 'pending' 
    };

    try {
      const response = await api.post("/orders", payload);
      setOrders((prev) => [response, ...prev]);
      toast("Order placed successfully", "success");
      setShowModal(false);
      setForm({ product_id: "", supplier_id: "", qty: "", unit_price: "" });
    } catch (error) {
      toast("Validation Error: Product/Supplier ID might be invalid.", "error");
    }
  };

  if (loading) return <Loading />;

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 22, fontWeight: 700, color: "#111827" }}>Order Oversight</h2>
          <p style={{ margin: "4px 0 0", color: "#6B7280", fontSize: 14 }}>Manage and approve supply requests</p>
        </div>
        <button onClick={() => setShowModal(true)} style={{ display: "flex", alignItems: "center", gap: 8, padding: "10px 18px", background: "#1D4ED8", color: "#fff", border: "none", borderRadius: 10, fontSize: 14, fontWeight: 500, cursor: "pointer" }}>
          <i className="ti ti-plus" /> New Order
        </button>
      </div>

      {/* Filter Tabs (Removed 'shipped') */}
      <div style={{ display: "flex", gap: 8, marginBottom: 20 }}>
        {["all", "pending", "approved", "delivered", "rejected"].map((s) => (
          <button key={s} onClick={() => setFilter(s)} style={{ padding: "7px 16px", borderRadius: 20, border: "none", cursor: "pointer", fontSize: 13, fontWeight: 500, background: filter === s ? "#1D4ED8" : "#F3F4F6", color: filter === s ? "#fff" : "#6B7280", textTransform: "capitalize" }}>
            {s}
          </button>
        ))}
      </div>

      <div style={{ background: "#fff", borderRadius: 14, border: "1px solid #E5E7EB", overflow: "hidden" }}>
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead>
              <tr style={{ background: "#F9FAFB" }}>
                {["Order ID", "Product", "Supplier", "Qty", "Total", "Date", "Status", "Actions"].map((h) => (
                  <th key={h} style={{ padding: "10px 14px", textAlign: "left", fontWeight: 500, color: "#6B7280", whiteSpace: "nowrap" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.map((o) => (
                <tr key={o.id} style={{ borderTop: "1px solid #F3F4F6" }}>
                  <td style={{ padding: "12px 14px", color: "#1D4ED8", fontWeight: 500, fontFamily: "monospace" }}>#{o.id}</td>
                  <td style={{ padding: "12px 14px", color: "#111827", fontWeight: 500 }}>{o.product?.name || "Deleted Product"}</td>
                  <td style={{ padding: "12px 14px", color: "#6B7280" }}>{o.supplier?.name || "N/A"}</td>
                  <td style={{ padding: "12px 14px", color: "#374151" }}>{o.qty}</td>
                  <td style={{ padding: "12px 14px", color: "#111827", fontWeight: 500 }}>
                    ₱{(o.total_amount ?? o.total ?? 0).toLocaleString(undefined, {minimumFractionDigits: 2})}
                  </td>
                  <td style={{ padding: "12px 14px", color: "#6B7280" }}>{new Date(o.created_at).toLocaleDateString()}</td>
                  <td style={{ padding: "12px 14px" }}><StatusBadge status={o.status} /></td>
                  <td style={{ padding: "12px 14px" }}>
                    {o.status === "pending" && (
                      <div style={{ display: "flex", gap: 6 }}>
                        <button onClick={() => handleAction(o.id, "approved")} style={{ padding: "5px 12px", background: "#D1FAE5", border: "none", borderRadius: 7, color: "#065F46", fontSize: 12, fontWeight: 500, cursor: "pointer" }}>Approve</button>
                        <button onClick={() => handleAction(o.id, "rejected")} style={{ padding: "5px 12px", background: "#FEE2E2", border: "none", borderRadius: 7, color: "#991B1B", fontSize: 12, fontWeight: 500, cursor: "pointer" }}>Reject</button>
                      </div>
                    )}
                    {(o.status === "delivered" || o.status === "rejected") && (
                      <button onClick={() => handleDelete(o.id, o.status)} style={{ padding: "5px 12px", background: "#F3F4F6", border: "1px solid #D1D5DB", borderRadius: 7, color: "#374151", fontSize: 12, fontWeight: 500, cursor: "pointer" }} title="Delete Record"><i className="ti ti-trash" style={{ marginRight: 4 }} /> Delete</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {showModal && (
        <Modal title="Create New Order" onClose={() => setShowModal(false)}>
          <Field label="Product">
            <select value={form.product_id} onChange={(e) => setForm((f) => ({ ...f, product_id: e.target.value }))} style={selectStyle}>
              <option value="">Select product</option>
              {products.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
            </select>
          </Field>
          <Field label="Supplier">
            <select value={form.supplier_id} onChange={(e) => setForm((f) => ({ ...f, supplier_id: e.target.value }))} style={selectStyle}>
              <option value="">Select supplier</option>
              {suppliers.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </Field>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <Field label="Quantity"><input type="number" value={form.qty} onChange={(e) => setForm((f) => ({ ...f, qty: e.target.value }))} style={inputStyle} /></Field>
            <Field label="Unit Price (₱)"><input type="number" step="0.01" value={form.unit_price} onChange={(e) => setForm((f) => ({ ...f, unit_price: e.target.value }))} style={inputStyle} /></Field>
          </div>
          <div style={{ marginTop: 12, padding: 12, background: "#F9FAFB", borderRadius: 8, fontSize: 14, fontWeight: 600, color: "#111827", textAlign: "right" }}>
            Estimated Total: ₱{(Number(form.qty) * Number(form.unit_price)).toLocaleString()}
          </div>
          <div style={{ display: "flex", gap: 12, justifyContent: "flex-end", marginTop: 16 }}>
            <button onClick={() => setShowModal(false)} style={{ padding: "9px 20px", borderRadius: 8, border: "1px solid #D1D5DB", background: "#fff", cursor: "pointer", fontSize: 14 }}>Cancel</button>
            <button onClick={handleCreate} style={{ padding: "9px 20px", borderRadius: 8, border: "none", background: "#1D4ED8", color: "#fff", cursor: "pointer", fontSize: 14, fontWeight: 500 }}>Create Order</button>
          </div>
        </Modal>
      )}

      {confirm && <Confirm message={confirm.msg} onConfirm={confirm.action} onCancel={() => setConfirm(null)} />}
    </div>
  );
}
// ─── DELIVERIES PAGE ──────────────────────────────────────────────────────────
function DeliveriesPage({ toast }) {
  const [deliveries, setDeliveries] = useState([]);
  const [personnel, setPersonnel] = useState([]); 
  const [showModal, setShowModal] = useState(false);
  const [selected, setSelected] = useState(null);
  const [loading, setLoading] = useState(true);
  
  // Modal Form States
  const [newStatus, setNewStatus] = useState("");
  const [driverId, setDriverId] = useState("");
  const [eta, setEta] = useState("");

  const steps = ["pending", "in_transit", "out_for_delivery", "delivered"];

  // 1. DATA REFRESH FUNCTION
  const fetchDeliveriesData = async () => {
    try {
      const [delRes, userRes] = await Promise.all([
        api.get("/deliveries"),
        api.get("/users") 
      ]);
      
      const delItems = delRes.data?.data || delRes.data || [];
      setDeliveries(delItems);

      // Filter users to only show field personnel
      const drivers = (Array.isArray(userRes) ? userRes : []).filter(
        u => u.role === "field_personnel" || u.role === "personnel"
      );
      setPersonnel(drivers);
    } catch (err) {
      console.error("Fetch error:", err);
      toast("Could not load tracking or personnel data", "error");
    }
  };

  useEffect(() => {
    const initData = async () => {
      setLoading(true);
      await fetchDeliveriesData();
      setTimeout(() => setLoading(false), 300);
    };
    initData();
  }, [toast]);

  // 2. HANDLERS
  const openUpdate = (d) => { 
    setSelected(d); 
    setNewStatus(d.status);
    setDriverId(d.driver_id || ""); 

    if (d.eta) {
      const dateObj = new Date(d.eta);
      const tzOffset = dateObj.getTimezoneOffset() * 60000;
      const localISOTime = new Date(dateObj - tzOffset).toISOString().slice(0, 16);
      setEta(localISOTime);
    } else {
      setEta("");
    }
    setShowModal(true); 
  };

  const handleUpdate = async () => {
    if (!selected?.id) return;

    try { 
      const res = await api.patch(`/deliveries/${selected.id}/status`, { 
        status: newStatus,
        driver_id: driverId || null,
        eta: eta || null
      }); 
      
      const updated = res.data || res;

      setDeliveries((prev) => 
        prev.map((d) => (d.id === selected.id ? { ...d, ...updated } : d))
      );
      
      toast("Tracking and assignment updated", "success");
      setShowModal(false);
    } catch (err) { 
      console.error("Update error:", err);
      toast("Update failed. Check server logs.", "error"); 
    }
  };

  // NEW HANDLER: Submits structural DELETE request directly to Laravel
  const handleDelete = async (id) => {
    if (!window.confirm(`Are you sure you want to permanently delete tracking record TRK-${id}?`)) return;

    try {
      await api.delete(`/deliveries/${id}`);
      toast(`Tracking record TRK-${id} successfully removed`, "success");
      
      // Update local array state to remove the card instantly
      setDeliveries(prev => prev.filter(d => d.id !== id));
    } catch (err) {
      console.error("Delete error:", err);
      toast(err.response?.data?.message || "Failed to remove delivery record.", "error");
    }
  };

  // 3. GUARD CLAUSE
  if (loading) return <Loading />;

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <h2 style={{ margin: 0, fontSize: 22, fontWeight: 700, color: "#111827" }}>Delivery Tracking</h2>
        <p style={{ margin: "4px 0 0", color: "#6B7280", fontSize: 14 }}>Manage assignments and transit status</p>
      </div>

      {/* Delivery Cards Grid */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(320px, 1fr))", gap: 16, marginBottom: 28 }}>
        {deliveries.length > 0 ? deliveries.map((d) => {
          const stepIdx = steps.indexOf(d.status);
          return (
            <div key={d.id} style={{ background: "#fff", borderRadius: 14, border: "1px solid #E5E7EB", padding: 20, display: "flex", flexDirection: "column", justifyContent: "space-between" }}>
              <div>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 14 }}>
                  <div>
                    <div style={{ fontWeight: 600, color: "#1D4ED8", fontSize: 15, fontFamily: "monospace" }}>TRK-{d.id}</div>
                    <div style={{ fontSize: 12, color: "#6B7280", marginTop: 2 }}>Order ID: #{d.order_id}</div>
                  </div>
                  <StatusBadge status={d.status} />
                </div>

                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginBottom: 16, fontSize: 13 }}>
                  <div>
                    <span style={{ color: "#9CA3AF" }}>Driver: </span>
                    <span style={{ color: "#374151", fontWeight: 500 }}>
                      {d.driver?.name || d.driver_name || "Unassigned"}
                    </span>
                  </div>
                  <div>
                    <span style={{ color: "#9CA3AF" }}>ETA: </span>
                    <span style={{ color: "#374151" }}>
                      {d.eta ? new Date(d.eta).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' }) : "TBD"}
                    </span>
                  </div>
                  <div style={{ gridColumn: "1/-1" }}><span style={{ color: "#9CA3AF" }}>To: </span><span style={{ color: "#374151" }}>{d.destination}</span></div>
                </div>

                {/* Transit Progress bar */}
                <div style={{ display: "flex", gap: 4, marginBottom: 14 }}>
                  {steps.map((s, i) => (
                    <div key={s} style={{ 
                      flex: 1, height: 4, borderRadius: 4, 
                      background: i <= stepIdx ? (d.status === "delivered" ? "#059669" : "#1D4ED8") : "#E5E7EB",
                      transition: "all 0.3s ease"
                    }} />
                  ))}
                </div>
              </div>

              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 10 }}>
                <span style={{ fontSize: 11, color: "#9CA3AF" }}>
                  Last Update: {d.updated_at ? new Date(d.updated_at).toLocaleTimeString() : "Just now"}
                </span>
                
                <div style={{ display: "flex", gap: 6 }}>
                  {/* Safely blocks delete option if the status has completed processing */}
                  {d.status !== "delivered" && (
                    <>
                      <button 
                        onClick={() => handleDelete(d.id)} 
                        title="Delete Delivery Record"
                        style={{ padding: "6px 10px", background: "#FEF2F2", border: "none", borderRadius: 7, color: "#DC2626", fontSize: 12, fontWeight: 500, cursor: "pointer", display: "flex", alignItems: "center" }}
                      >
                        Delete
                      </button>
                      <button 
                        onClick={() => openUpdate(d)} 
                        style={{ padding: "6px 12px", background: "#EFF6FF", border: "none", borderRadius: 7, color: "#1D4ED8", fontSize: 12, fontWeight: 500, cursor: "pointer" }}
                      >
                        Update Tracking
                      </button>
                    </>
                  )}
                </div>
              </div>
            </div>
          );
        }) : (
          <div style={{ gridColumn: "1/-1", padding: "60px", textAlign: "center", background: "#fff", borderRadius: 14, border: "1px dashed #D1D5DB", color: "#9CA3AF" }}>
            No active deliveries tracked.
          </div>
        )}
      </div>

      {/* Update Tracking Modal */}
      {showModal && selected && (
        <Modal title={`Update Tracking: TRK-${selected.id}`} onClose={() => setShowModal(false)}>
          <Field label="New Transit Status">
            <select value={newStatus} onChange={(e) => setNewStatus(e.target.value)} style={selectStyle}>
              {steps.map((s) => (
                <option key={s} value={s}>{s.replace(/_/g, ' ').toUpperCase()}</option>
              ))}
            </select>
          </Field>

          <Field label="Assign Driver (Personnel)">
            <select value={driverId} onChange={(e) => setDriverId(e.target.value)} style={selectStyle}>
              <option value="">Select a Driver</option>
              {personnel.map(p => (
                <option key={p.id} value={p.id}>{p.name}</option>
              ))}
            </select>
          </Field>

          <Field label="Estimated Delivery (ETA)">
            <input 
              type="datetime-local" 
              value={eta} 
              onChange={(e) => setEta(e.target.value)} 
              style={inputStyle} 
            />
          </Field>

          <div style={{ display: "flex", gap: 12, justifyContent: "flex-end", marginTop: 20 }}>
            <button type="button" onClick={() => setShowModal(false)} style={{ padding: "9px 20px", borderRadius: 8, border: "1px solid #D1D5DB", background: "#fff", cursor: "pointer", fontSize: 14 }}>Cancel</button>
            <button type="button" onClick={handleUpdate} style={{ padding: "9px 20px", borderRadius: 8, border: "none", background: "#1D4ED8", color: "#fff", cursor: "pointer", fontSize: 14, fontWeight: 500 }}>Save Changes</button>
          </div>
        </Modal>
      )}
    </div>
  );
}

// ─── SUPPLIERS PAGE ───────────────────────────────────────────────────────────
function SuppliersPage({ toast }) {
  const [suppliers, setSuppliers] = useState([]);
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState(null);
  const [confirm, setConfirm] = useState(null);
  const [loading, setLoading] = useState(true);
  
  const [form, setForm] = useState({ name: "", contact: "", phone: "", country: "", rating: "" });

  // 1. DATA FETCHING
  const fetchSuppliers = async () => {
    setLoading(true);
    try {
      const res = await api.get("/suppliers");
      const actualData = res.data?.data || res.data || (Array.isArray(res) ? res : []);
      setSuppliers(actualData);
    } catch (err) {
      toast("Connection error: Could not load suppliers", "error");
    } finally {
      setTimeout(() => setLoading(false), 300);
    }
  };

  useEffect(() => { fetchSuppliers(); }, []);

  // 2. HANDLERS
  const openAdd = () => { 
    setEditing(null); 
    setForm({ name: "", contact: "", phone: "", country: "", rating: "" }); 
    setShowModal(true); 
  };
  
  const openEdit = (s) => { 
    setEditing(s); 
    setForm({ 
      name: s.name, 
      contact: s.email || s.contact || "", 
      phone: s.phone || "", 
      country: s.country || "",
      rating: s.rating || ""
    }); 
    setShowModal(true); 
  };

  const handleSave = async () => {
    if (!form.name) {
      toast("Company Name is required", "error");
      return;
    }

    const payload = {
      name: form.name,
      email: form.contact, 
      phone: form.phone,
      country: form.country,
      rating: form.rating || null
    };

    try {
      if (editing) {
        const updated = await api.put(`/suppliers/${editing.id}`, payload);
        // Merge with existing state to keep orders_count
        setSuppliers((prev) => prev.map((s) => (s.id === editing.id ? { ...s, ...updated } : s)));
        toast("Supplier updated", "success");
      } else {
        const newSup = await api.post("/suppliers", payload);
        setSuppliers((prev) => [...prev, { ...newSup, orders_count: 0 }]);
        toast("Supplier added", "success");
      }
      setShowModal(false);
    } catch (err) {
      toast("Error: Check unique email/name", "error");
    }
  };

  const handleDelete = (id) => {
    setConfirm({ 
      msg: "Permanently remove this supplier?", 
      action: async () => {
        try { 
          await api.delete(`/suppliers/${id}`); 
          setSuppliers((prev) => prev.filter((s) => s.id !== id));
          toast("Supplier deleted", "success");
        } catch (err) {
          toast(err.response?.data?.message || "Delete failed", "error");
        }
        setConfirm(null);
      }
    });
  };

  if (loading) return <Loading />;

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 22, fontWeight: 700, color: "#111827" }}>Supplier Management</h2>
          <p style={{ margin: "4px 0 0", color: "#6B7280", fontSize: 14 }}>{suppliers.length} partners registered</p>
        </div>
        <button onClick={openAdd} style={primaryBtnStyle}>
          <i className="ti ti-plus" /> Add Supplier
        </button>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(320px, 1fr))", gap: 16 }}>
        {suppliers.map((s) => (
          <div key={s.id} style={cardStyle}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 14 }}>
              <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
                <div style={iconBoxStyle}><i className="ti ti-building-factory-2" /></div>
                <div>
                  <div style={{ fontWeight: 600, color: "#111827", fontSize: 14 }}>{s.name}</div>
                  <div style={{ fontSize: 12, color: "#6B7280" }}>{s.country || "Philippines"}</div>
                </div>
              </div>
              <div style={{ display: "flex", gap: 6 }}>
                <button onClick={() => openEdit(s)} style={editBtnStyle}><i className="ti ti-edit" /></button>
                <button onClick={() => handleDelete(s.id)} style={deleteBtnStyle}><i className="ti ti-trash" /></button>
              </div>
            </div>
            
            <div style={{ fontSize: 13, display: "flex", flexDirection: "column", gap: 8 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <i className="ti ti-mail" style={{ color: "#9CA3AF" }} />
                <span style={{ color: "#374151" }}>{s.email || s.contact}</span>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <i className="ti ti-phone" style={{ color: "#9CA3AF" }} />
                <span style={{ color: "#374151" }}>{s.phone || "No contact"}</span>
              </div>
            </div>

            <div style={footerStyle}>
              <div><span style={{ color: "#9CA3AF" }}>Orders: </span><strong>{s.orders_count ?? 0}</strong></div>
              <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
                <i className="ti ti-star-filled" style={{ color: "#F59E0B" }} />
                <strong>{s.orders_count > 0 ? (s.rating || "5.0") : "N/A"}</strong>
              </div>
            </div>
          </div>
        ))}
      </div>

      {showModal && (
        <Modal title={editing ? "Edit Supplier" : "Add New Supplier"} onClose={() => setShowModal(false)}>
          <Field label="Company Name">
            <input value={form.name} onChange={(e) => setForm({...form, name: e.target.value})} style={inputStyle} />
          </Field>
          <Field label="Contact Email">
            <input type="email" value={form.contact} onChange={(e) => setForm({...form, contact: e.target.value})} style={inputStyle} />
          </Field>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <Field label="Phone"><input value={form.phone} onChange={(e) => setForm({...form, phone: e.target.value})} style={inputStyle} /></Field>
            <Field label="Country"><input value={form.country} onChange={(e) => setForm({...form, country: e.target.value})} style={inputStyle} /></Field>
          </div>
          <Field label="Rating (0.0 - 5.0)">
            <input type="number" step="0.1" min="0" max="5" value={form.rating} onChange={(e) => setForm({...form, rating: e.target.value})} style={inputStyle} placeholder="4.5" />
          </Field>
          <div style={{ display: "flex", gap: 12, justifyContent: "flex-end", marginTop: 16 }}>
            <button onClick={() => setShowModal(false)} style={cancelBtnStyle}>Cancel</button>
            <button onClick={handleSave} style={primaryBtnStyle}>Save Changes</button>
          </div>
        </Modal>
      )}

      {confirm && <Confirm message={confirm.msg} onConfirm={confirm.action} onCancel={() => setConfirm(null)} />}
    </div>
  );
}

// ─── STYLES ──────────────────────────────────────────────────────────────────
const cardStyle = { background: "#fff", borderRadius: 14, border: "1px solid #E5E7EB", padding: 20 };
const iconBoxStyle = { width: 40, height: 40, borderRadius: 10, background: "#EFF6FF", display: "flex", alignItems: "center", justifyContent: "center", color: "#1D4ED8", fontSize: 20 };
const footerStyle = { display: "flex", justifyContent: "space-between", marginTop: 14, paddingTop: 14, borderTop: "1px solid #F3F4F6", fontSize: 13 };
const primaryBtnStyle = { display: "flex", alignItems: "center", gap: 8, padding: "10px 18px", background: "#1D4ED8", color: "#fff", border: "none", borderRadius: 10, fontSize: 14, fontWeight: 500, cursor: "pointer" };
const editBtnStyle = { background: "#EFF6FF", border: "none", borderRadius: 7, padding: "6px 10px", cursor: "pointer", color: "#1D4ED8" };
const deleteBtnStyle = { background: "#FEF2F2", border: "none", borderRadius: 7, padding: "6px 10px", cursor: "pointer", color: "#DC2626" };
const cancelBtnStyle = { padding: "9px 20px", borderRadius: 8, border: "1px solid #D1D5DB", background: "#fff", cursor: "pointer", fontSize: 14 };

// ─── REPORTS PAGE ─────────────────────────────────────────────────────────────
function ReportsPage({ toast }) {
  const [period, setPeriod] = useState("monthly");
  const [loading, setLoading] = useState(true);
  
  const [reportData, setReportData] = useState({
    stats: { totalValue: 0, fulfilled: 0, avgDelivery: 0, accuracy: 0 },
    inventory: [],
    breakdown: [],
    suppliers: []
  });

  useEffect(() => {
    const fetchReports = async () => {
      setLoading(true);
      try {
        const [res, supplierRes] = await Promise.all([
          api.get(`/reports/dashboard`),
          api.get(`/reports/supplier-performance`)
        ]);
        
        // Dynamic total calculation for the breakdown chart
        const totalOrders = (res.pending_orders || 0) + (res.approved_orders || 0) + (res.delivered_orders || 0);
        const getPct = (val) => totalOrders > 0 ? Math.round((val / totalOrders) * 100) : 0;

        setReportData({
          stats: {
            totalValue: res.total_order_value || 0,
            fulfilled: res.orders_this_month || 0,
            avgDelivery: res.avg_delivery_days || 0,
            accuracy: 98
          },
          trends: res.trends || {}, // CAPTURE TRENDS HERE
          inventory: res.recent_orders || [], 
          breakdown: [
            { label: "Pending",   count: res.pending_orders || 0,   pct: getPct(res.pending_orders),   color: "#F59E0B" },
            { label: "Approved",  count: res.approved_orders || 0,  pct: getPct(res.approved_orders),  color: "#1D4ED8" },
            { label: "Delivered", count: res.delivered_orders || 0, pct: getPct(res.delivered_orders), color: "#059669" }
          ],
          suppliers: Array.isArray(supplierRes) ? supplierRes : []
        });
      } catch (err) {
        toast("Analytics sync failed", "error");
      } finally {
        setTimeout(() => setLoading(false), 400);
      }
    };
    fetchReports();
  }, [period, toast]);

  const summaryCards = [
    { 
      label: "Total Order Value", 
      value: `₱${(reportData.stats.totalValue || 0).toLocaleString()}`, 
      change: reportData.trends?.value_change || "0%", 
      up: reportData.trends?.value_up ?? true 
    },
    { 
      label: "Orders Fulfilled", 
      value: reportData.stats.fulfilled || 0, 
      change: reportData.trends?.orders_change || "0 vs last period", 
      up: reportData.trends?.orders_up ?? true 
    },
    { 
      label: "Avg. Delivery Time", 
      value: `${reportData.stats.avgDelivery || 0} days`, 
      change: "-0.4 days", // Static until backend logic is added
      up: true 
    },
    { 
      label: "Stock Accuracy", 
      value: `${reportData.stats.accuracy || 0}%`, 
      change: "+0.8%", // Static until backend logic is added
      up: true 
    },
  ];

  if (loading) return <Loading />;

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 22, fontWeight: 700, color: "#111827" }}>Reports & Analytics</h2>
          <p style={{ margin: "4px 0 0", color: "#6B7280", fontSize: 14 }}>Performance metrics and operational insights</p>
        </div>
      </div>

      {/* KPI Cards */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 14, marginBottom: 28 }}>
        {summaryCards.map((c) => (
          <div key={c.label} style={{ background: "#fff", borderRadius: 14, border: "1px solid #E5E7EB", padding: 20 }}>
            <div style={{ fontSize: 12, color: "#9CA3AF", fontWeight: 500, marginBottom: 10 }}>{c.label}</div>
            <div style={{ fontSize: 26, fontWeight: 700, color: "#111827", marginBottom: 6 }}>{c.value}</div>
            <div style={{ fontSize: 12, color: c.up ? "#059669" : "#DC2626", fontWeight: 500 }}>
              <i className={`ti ${c.up ? "ti-arrow-up" : "ti-arrow-down"}`} /> {c.change}
            </div>
          </div>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: 20, marginBottom: 24 }}>
        
        {/* Inventory Analytics */}
        <div style={{ background: "#fff", borderRadius: 14, border: "1px solid #E5E7EB", padding: 24 }}>
          <h3 style={{ margin: "0 0 20px", fontSize: 15, fontWeight: 600, color: "#111827" }}>Inventory Levels vs Reorder Points</h3>
          <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            {reportData.inventory.length > 0 ? reportData.inventory.map((d) => {
              const item = d.product || d; 
              const stock = item.stock_qty ?? 0;
              const reorder = item.reorder_point ?? 10;
              const maxVal = Math.max(stock, reorder, 100); 

              return (
                <div key={d.id} style={{ marginBottom: 16 }}>
                  <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, color: "#6B7280", marginBottom: 5, position: "relative", zIndex: 10 }}>
                    <span style={{ color: "#374151", fontWeight: 500 }}>{item.name || "Unknown Product"}</span>
                    <span style={{ fontWeight: 600, color: stock <= reorder ? "#DC2626" : "#059669" }}>
                      {stock} units
                    </span>
                  </div>
                  <div style={{ position: "relative", height: 10, background: "#F3F4F6", borderRadius: 6, zIndex: 1 }}>
                    <div style={{ 
                      height: "100%", borderRadius: 6, transition: "width 0.5s ease",
                      background: stock <= reorder ? "#EF4444" : "#3B82F6", 
                      width: `${Math.min((stock / maxVal) * 100, 100)}%`,
                      position: "relative", zIndex: 2
                    }} />
                    <div style={{ 
                      position: "absolute", top: -3, width: 2, height: 16, background: "#F59E0B",
                      left: `${(reorder / maxVal) * 100}%`,
                      zIndex: 3, cursor: "help"
                    }} title={`Reorder Point: ${reorder}`} />
                  </div>
                </div>
              );
            }) : <p style={{ textAlign: 'center', color: '#9CA3AF', fontSize: 13 }}>No inventory metrics found.</p>}
          </div>
        </div>

        {/* Status Breakdown (Updated) */}
        <div style={{ background: "#fff", borderRadius: 14, border: "1px solid #E5E7EB", padding: 24 }}>
          <h3 style={{ margin: "0 0 20px", fontSize: 15, fontWeight: 600, color: "#111827" }}>Order Status Breakdown</h3>
          {reportData.breakdown.map((o) => (
            <div key={o.label} style={{ marginBottom: 16 }}>
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13, marginBottom: 6 }}>
                <span style={{ color: "#374151", fontWeight: 500 }}>{o.label}</span>
                <span style={{ color: "#6B7280" }}>{o.count} ({o.pct}%)</span>
              </div>
              <div style={{ height: 8, background: "#F3F4F6", borderRadius: 6 }}>
                <div style={{ height: "100%", borderRadius: 6, background: o.color, width: `${o.pct}%` }} />
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Supplier Performance Analytics */}
      <div style={{ background: "#fff", borderRadius: 14, border: "1px solid #E5E7EB", overflow: "hidden" }}>
        <div style={{ padding: "16px 20px", borderBottom: "1px solid #F3F4F6" }}>
          <h3 style={{ margin: 0, fontSize: 15, fontWeight: 600, color: "#111827" }}>Supplier Performance Analytics</h3>
        </div>
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead>
              <tr style={{ background: "#F9FAFB" }}>
                {["Supplier", "Country", "Active Orders", "Rating", "Health Score"].map((h) => (
                  <th key={h} style={{ padding: "12px 16px", textAlign: "left", fontWeight: 500, color: "#6B7280" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {reportData.suppliers.length > 0 ? reportData.suppliers.map((s) => (
                <tr key={s.id} style={{ borderTop: "1px solid #F3F4F6" }}>
                  <td style={{ padding: "12px 16px", fontWeight: 500, color: "#111827" }}>{s.name}</td>
                  <td style={{ padding: "12px 16px", color: "#6B7280" }}>{s.country}</td>
                  <td style={{ padding: "12px 16px", color: "#374151" }}>{s.active_orders || 0}</td>
                  <td style={{ padding: "12px 16px" }}>⭐ {s.rating || 0}</td>
                  <td style={{ padding: "12px 16px" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                      <div style={{ flex: 1, height: 6, background: "#F3F4F6", borderRadius: 4 }}>
                        <div style={{ 
                          height: "100%", borderRadius: 4, 
                          width: `${(s.rating / 5) * 100}%`,
                          background: s.rating >= 4.5 ? "#059669" : "#1D4ED8"
                        }} />
                      </div>
                      <span style={{ fontSize: 12, color: "#6B7280" }}>{((s.rating / 5) * 100).toFixed(0)}%</span>
                    </div>
                  </td>
                </tr>
              )) : (
                <tr>
                  <td colSpan="5" style={{ padding: 40, textAlign: 'center', color: '#9CA3AF' }}>No data.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

// ─── USER MANAGEMENT PAGE ───────────────────────────────────────────────────
function UserManagementPage({ toast }) {
  const [users, setUsers] = useState([]);
  const [showModal, setShowModal] = useState(false);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({ name: "", email: "", password: "", role: "field_personnel" });

  // 1. DATA FETCHING
  useEffect(() => {
    const fetchUsers = async () => {
      setLoading(true);
      try {
        const res = await api.get("/users");
        // Handles Laravel Pagination or standard array responses
        const actualUsers = res.data ? res.data : (Array.isArray(res) ? res : []);
        setUsers(actualUsers);
      } catch (err) {
        console.error("Failed to load users", err);
        toast("Could not sync user records", "error");
      } finally {
        setTimeout(() => setLoading(false), 300);
      }
    };
    fetchUsers();
  }, [toast]);

  // 2. HANDLERS
  const handleAddUser = async () => {
    if (!form.name || !form.email || !form.password) {
      toast("All fields are required", "error");
      return;
    }

    try {
      const res = await api.post("/auth/register", form);
      
      if (res && res.user) {
        setUsers(prev => [...prev, res.user]);
        toast("User created successfully!", "success");
        setShowModal(false);
        setForm({ name: "", email: "", password: "", role: "field_personnel" });
      }
    } catch (err) {
      const msg = err.response?.data?.message || "Check email uniqueness or password length";
      toast(msg, "error");
      console.error("Registration failed:", err.response?.data);
    }
  };

  // 3. GUARD CLAUSE
  if (loading) return <Loading />;

  return (
    <div>
      {/* Header Section */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 22, fontWeight: 700, color: "#111827" }}>User Management</h2>
          <p style={{ margin: "4px 0 0", color: "#6B7280", fontSize: 14 }}>{users.length} registered staff members</p>
        </div>
        <button onClick={() => setShowModal(true)} style={{ display: "flex", alignItems: "center", gap: 8, padding: "10px 18px", background: "#1D4ED8", color: "#fff", border: "none", borderRadius: 10, fontSize: 14, fontWeight: 500, cursor: "pointer" }}>
          <i className="ti ti-user-plus" /> Add User
        </button>
      </div>

      {/* Users Table */}
      <div style={{ background: "#fff", borderRadius: 14, border: "1px solid #E5E7EB", overflow: "hidden" }}>
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead>
              <tr style={{ background: "#F9FAFB" }}>
                {["Name", "Email Address", "System Role", "Created At"].map(h => (
                  <th key={h} style={{ padding: "12px 16px", textAlign: "left", fontWeight: 500, color: "#6B7280" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {users.length > 0 ? users.map(u => (
                <tr key={u.id} style={{ borderTop: "1px solid #F3F4F6" }}>
                  <td style={{ padding: "14px 16px", fontWeight: 500, color: "#111827" }}>{u.name}</td>
                  <td style={{ padding: "14px 16px", color: "#374151" }}>{u.email}</td>
                  <td style={{ padding: "14px 16px" }}><StatusBadge status={u.role} /></td>
                  <td style={{ padding: "14px 16px", color: "#9CA3AF" }}>{new Date(u.created_at).toLocaleDateString()}</td>
                </tr>
              )) : (
                <tr>
                  <td colSpan="4" style={{ padding: 40, textAlign: "center", color: "#9CA3AF" }}>No system users found.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Registration Modal */}
      {showModal && (
        <Modal title="Create New System User" onClose={() => setShowModal(false)}>
          <Field label="Full Name">
            <input value={form.name} onChange={e => setForm({...form, name: e.target.value})} style={inputStyle} placeholder="John Doe" />
          </Field>
          
          <Field label="Email Address">
            <input type="email" value={form.email} onChange={e => setForm({...form, email: e.target.value})} style={inputStyle} placeholder="john@scms.local" />
          </Field>
          
          <Field label="Initial Password">
            <input 
              type="password" 
              value={form.password} 
              onChange={e => setForm({...form, password: e.target.value})} 
              style={inputStyle} 
              placeholder="••••••••"
            />
            <p style={{ margin: "6px 0 0", fontSize: 12, color: "#6B7280", display: "flex", alignItems: "center", gap: 4 }}>
              <i className="ti ti-info-circle" style={{ fontSize: 14 }} />
              Must be at least 8 characters long.
            </p>
          </Field>
          
          <Field label="System Role">
            <select value={form.role} onChange={e => setForm({...form, role: e.target.value})} style={selectStyle}>
              <option value="field_personnel">Field Personnel (Driver)</option>
              <option value="manager">Manager</option>
              <option value="admin">Administrator</option>
              <option value="supplier">Supplier Portal</option>
            </select>
          </Field>
          
          <div style={{ display: "flex", gap: 12, justifyContent: "flex-end", marginTop: 16 }}>
            <button onClick={() => setShowModal(false)} style={{ padding: "9px 20px", borderRadius: 8, border: "1px solid #D1D5DB", background: "#fff", cursor: "pointer" }}>Cancel</button>
            <button onClick={handleAddUser} style={{ padding: "9px 20px", borderRadius: 8, border: "none", background: "#1D4ED8", color: "#fff", cursor: "pointer", fontWeight: 500 }}>Create User Account</button>
          </div>
        </Modal>
      )}
    </div>
  );
}

// ─── MAIN APP ─────────────────────────────────────────────────────────────────
export default function App() {
  const [user, setUser] = useState(null);
  const [page, setPage] = useState("dashboard");
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [toastMsg, setToastMsg] = useState(null);
  const [searchTerm, setSearchTerm] = useState("");
  
  // Notification States
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [showNotifications, setShowNotifications] = useState(false);

  const toast = useCallback((msg, type = "success") => {
    setToastMsg({ msg, type });
  }, []);

  // ─── NOTIFICATION LOGIC ───────────────────────────────────────────────────
  
  const fetchNotifications = async () => {
    try {
      const res = await api.get("/notifications");
      // Laravel pagination returns the array in 'data'
      const allNotifs = res.data?.data || res.data || [];
      setNotifications(allNotifs);
      
      // Calculate unread count based on your DB 'read_at' column
      const unread = allNotifs.filter(n => n.read_at === null).length;
      setUnreadCount(unread);
    } catch (err) {
      console.error("Notification sync failed:", err);
    }
  };

  useEffect(() => {
    if (user) {
      fetchNotifications();
      // Set up polling to check for new alerts in Valencia City every 5 seconds
      const interval = setInterval(fetchNotifications, 5000);
      return () => clearInterval(interval);
    }
  }, [user]);

  const handleMarkAllRead = async () => {
    try {
      await api.post("/notifications/read-all");
      setUnreadCount(0);
      // Update local state so UI reflects change instantly
      setNotifications(prev => prev.map(n => ({ ...n, read_at: new Date().toISOString() })));
      setShowNotifications(false);
      toast("All notifications marked as read");
    } catch (err) {
      toast("Failed to mark notifications as read", "error");
    }
  };

  // NEW HANDLER: Wipes out the DB records completely and clears local state array
  const handleClearAll = async () => {
    try {
      await api.delete("/notifications/clear-all");
      setNotifications([]);
      setUnreadCount(0);
      setShowNotifications(false);
      toast("Notification history cleared permanently", "success");
    } catch (err) {
      toast("Failed to clear notification center", "error");
    }
  };

  // ─── AUTH HANDLERS ────────────────────────────────────────────────────────

  const handleLogout = async () => {
    try {
      await api.post("/auth/logout");
      localStorage.removeItem("scms_token");
    } catch (err) {
      console.error("Logout error", err);
    }
    setUser(null);
    setPage("dashboard");
  };

  if (!user) return <LoginPage onLogin={setUser} />;

  const pages = { 
    dashboard: DashboardPage, 
    inventory: InventoryPage, 
    orders: OrdersPage,
    users: user?.role === "admin" ? UserManagementPage : DashboardPage, 
    deliveries: DeliveriesPage, 
    suppliers: SuppliersPage, 
    reports: ReportsPage,
    logs: user?.role === "admin" ? ActivityLogsPage : DashboardPage
  };

  const PageComponent = pages[page] || DashboardPage;

  return (
    <div style={{ display: "flex", height: "100vh", background: "#F8FAFC", fontFamily: "'Inter', system-ui, sans-serif" }}>
      
      {/* Sidebar */}
      <div style={{ width: sidebarOpen ? 240 : 64, background: "#111827", transition: "width 0.2s", display: "flex", flexDirection: "column", flexShrink: 0 }}>
        <div style={{ padding: sidebarOpen ? "20px 20px 16px" : "20px 12px 16px", display: "flex", alignItems: "center", gap: 10, borderBottom: "1px solid #1F2937" }}>
          <div style={{ width: 36, height: 36, background: "#1D4ED8", borderRadius: 9, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
            <i className="ti ti-truck" style={{ color: "#fff", fontSize: 18 }} />
          </div>
          {sidebarOpen && (
            <div>
              <div style={{ fontSize: 14, fontWeight: 700, color: "#fff" }}>SCMS</div>
              <div style={{ fontSize: 10, color: "#6B7280" }}>Supply Chain Mgmt</div>
            </div>
          )}
        </div>

        <nav style={{ flex: 1, padding: "12px 8px" }}>
          {NAV_ITEMS.map((item) => {
            const active = page === item.key;
            return (
              <button 
                key={item.key} 
                onClick={() => setPage(item.key)} 
                title={!sidebarOpen ? item.label : ""}
                style={{ 
                  width: "100%", display: "flex", alignItems: "center", gap: 10, padding: sidebarOpen ? "10px 12px" : "10px", 
                  borderRadius: 9, border: "none", cursor: "pointer", marginBottom: 2, 
                  background: active ? "#1D4ED8" : "transparent", color: active ? "#fff" : "#9CA3AF", 
                  textAlign: "left", transition: "all 0.15s" 
                }}
              >
                <i className={`ti ${item.icon}`} style={{ fontSize: 18, flexShrink: 0 }} />
                {sidebarOpen && <span style={{ fontSize: 13, fontWeight: 500 }}>{item.label}</span>}
              </button>
            );
          })}
          {user?.role === "admin" && (
            <button 
              onClick={() => setPage("users")}
              style={{ 
                width: "100%", display: "flex", alignItems: "center", gap: 10, padding: sidebarOpen ? "10px 12px" : "10px", 
                borderRadius: 9, border: "none", cursor: "pointer", 
                background: page === "users" ? "#1D4ED8" : "transparent", color: page === "users" ? "#fff" : "#9CA3AF"
              }}
            >
              <i className="ti ti-users" style={{ fontSize: 18 }} />
              {sidebarOpen && <span style={{ fontSize: 13, fontWeight: 500 }}>User Management</span>}
            </button>
          )}
          {user?.role === "admin" && (
            <button 
              onClick={() => setPage("logs")}
              style={{ 
                width: "100%", display: "flex", alignItems: "center", gap: 10, padding: sidebarOpen ? "10px 12px" : "10px", 
                borderRadius: 9, border: "none", cursor: "pointer", 
                background: page === "logs" ? "#1D4ED8" : "transparent", color: page === "logs" ? "#fff" : "#9CA3AF"
              }}
            >
              <i className="ti ti-clipboard-list" style={{ fontSize: 18 }} />
              {sidebarOpen && <span style={{ fontSize: 13, fontWeight: 500 }}>Activity Logs</span>}
            </button>
          )}
        </nav>

        <div style={{ padding: "12px 8px", borderTop: "1px solid #1F2937" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10, padding: sidebarOpen ? "10px 12px" : "10px", borderRadius: 9 }}>
            <div style={{ width: 32, height: 32, borderRadius: "50%", background: "#374151", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
              <span style={{ color: "#D1D5DB", fontSize: 12, fontWeight: 600 }}>{user.name?.slice(0, 2).toUpperCase()}</span>
            </div>
            {sidebarOpen && (
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 13, fontWeight: 500, color: "#F9FAFB", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{user.name}</div>
                <div style={{ fontSize: 10, color: "#6B7280", textTransform: 'capitalize' }}>{user.role?.replace('_', ' ')}</div>
              </div>
            )}
            {sidebarOpen && (
              <button onClick={handleLogout} style={{ background: "none", border: "none", cursor: "pointer", color: "#6B7280", padding: 4 }}>
                <i className="ti ti-logout" style={{ fontSize: 15 }} />
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Main Content Area */}
      <div style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "hidden" }}>
        <div style={{ height: 60, background: "#fff", borderBottom: "1px solid #E5E7EB", display: "flex", alignItems: "center", padding: "0 24px", gap: 16, flexShrink: 0 }}>
          <button onClick={() => setSidebarOpen(!sidebarOpen)} style={{ background: "none", border: "none", cursor: "pointer", color: "#6B7280", fontSize: 20, padding: 4 }}>
            <i className="ti ti-menu-2" />
          </button>
          <div style={{ flex: 1 }} />
          
          {/* Notification Bell with Logic */}
          <div style={{ position: 'relative' }}>
            <button onClick={() => setShowNotifications(!showNotifications)} style={{ background: "none", border: "none", cursor: "pointer", color: "#6B7280", fontSize: 20, position: "relative", padding: 4 }}>
              <i className="ti ti-bell" />
              {unreadCount > 0 && (
                <span style={{ 
                  position: "absolute", top: 2, right: 4, background: "#EF4444", color: "#fff", 
                  fontSize: 10, padding: "2px 5px", borderRadius: "10px", border: "2px solid #fff", fontWeight: "bold" 
                }}>
                  {unreadCount}
                </span>
              )}
            </button>

            {showNotifications && (
              <div style={{ position: 'absolute', top: 50, right: 0, width: 340, background: '#fff', borderRadius: 12, boxShadow: '0 10px 25px rgba(0,0,0,0.1)', border: '1px solid #E5E7EB', zIndex: 1000, overflow: 'hidden' }}>
                <div style={{ padding: '12px 16px', borderBottom: '1px solid #F3F4F6', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontWeight: 600, fontSize: 14 }}>Notifications</span>
                  <div style={{ display: "flex", gap: 10 }}>
                    {unreadCount > 0 && (
                      <button onClick={handleMarkAllRead} style={{ background: 'none', border: 'none', color: '#1D4ED8', fontSize: 12, cursor: 'pointer', fontWeight: 500, padding: 0 }}>
                        Read All
                      </button>
                    )}
                    {notifications.length > 0 && (
                      <button onClick={handleClearAll} style={{ background: 'none', border: 'none', color: '#DC2626', fontSize: 12, cursor: 'pointer', fontWeight: 500, padding: 0 }}>
                        Clear All
                      </button>
                    )}
                  </div>
                </div>
                <div style={{ maxHeight: 350, overflowY: 'auto' }}>
                  {notifications.length > 0 ? notifications.map((n) => (
                    <div key={n.id} style={{ 
                      padding: '12px 16px', borderBottom: '1px solid #F9FAFB', fontSize: 13,
                      background: n.read_at ? "#fff" : "#F0F7FF" 
                    }}>
                      <div style={{ color: '#374151', fontWeight: n.read_at ? 400 : 600 }}>{n.message}</div>
                      <div style={{ color: '#9CA3AF', fontSize: 11, marginTop: 4 }}>{new Date(n.created_at).toLocaleTimeString()}</div>
                    </div>
                  )) : (
                    <div style={{ padding: '30px 20px', textAlign: 'center', color: '#9CA3AF', fontSize: 13 }}>
                      No new notifications
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>

        <div style={{ flex: 1, overflowY: "auto", padding: 28 }}>
          <PageComponent toast={toast} searchTerm={searchTerm} onNavigate={setPage} />
        </div>
      </div>

      {toastMsg && <Toast message={toastMsg.msg} type={toastMsg.type} onClose={() => setToastMsg(null)} />}
    </div>
  );
}