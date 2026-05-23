# SCMS Web Dashboard — Setup Guide

A production-ready React + Vite web dashboard for the Supply Chain Management System.

---

## Project Structure

```
scms-dashboard/
├── index.html              ← HTML entry point
├── vite.config.js          ← Vite config with API proxy
├── package.json
├── public/
│   └── favicon.svg
└── src/
    ├── main.jsx            ← React entry point
    └── App.jsx             ← Full app (all pages + components)
```

---

## Requirements

- Node.js 18+
- npm or yarn
- Laravel backend running at `http://localhost:8000`

---

## Setup & Run

```bash
# 1. Navigate to project folder
cd scms-dashboard

# 2. Install dependencies
npm install

# 3. Start development server
npm run dev
# Dashboard runs at: http://localhost:3000
```

---

## Connect to Laravel Backend

The `vite.config.js` already proxies `/api` calls to `http://localhost:8000` in development, so no CORS issues during dev.

For **production**, update `API_BASE` in `src/App.jsx`:
```js
const API_BASE = "https://your-production-api.com/api";
```

---

## Pages & Modules

| Page | Route (tab) | Features |
|------|-------------|----------|
| Login | — | Token auth, demo mode fallback |
| Dashboard | dashboard | Stats cards, recent orders, activity feed |
| Inventory | inventory | Product CRUD, low-stock badges, search |
| Orders | orders | Create, approve/reject, filter by status |
| Deliveries | deliveries | Card + table view, progress bar, status update |
| Suppliers | suppliers | Supplier CRUD, card grid view |
| Reports | reports | KPI cards, inventory chart, order breakdown, supplier performance |

---

## Default Login (matches Laravel seeder)

| Field | Value |
|-------|-------|
| Email | admin@scms.local |
| Password | password |

> In demo mode (backend offline), any email + password will log you in.

---

## Build for Production

```bash
npm run build
# Output in /dist — deploy to any static host (Nginx, Apache, Vercel, Netlify)
```

---

## Notes

- The app uses **mock data** when the Laravel API is offline, so you can develop/preview the UI independently.
- All CRUD actions call real API endpoints and fall back gracefully on errors.
- Token is stored in `localStorage` under key `scms_token`.
- Sidebar is collapsible. Logout clears the token and returns to login.
