# RouteFlow

Real-time delivery tracking & route optimization. A dispatcher creates orders, optimizes routes
across a fleet, dispatches them and watches drivers move on a live map; drivers use a mobile web
view to work through their stops and capture proof of delivery. Spec:
[`PRD_1_RouteFlow_Delivery_Tracking_and_Route_Optimization.md`](PRD_1_RouteFlow_Delivery_Tracking_and_Route_Optimization.md).
Project write-up + interview prep: [`PROJECT_EXPLAINER.txt`](PROJECT_EXPLAINER.txt).

| Part | Stack |
|---|---|
| `backend/` | Java 17, Spring Boot 3 (REST, Security + JWT, WebSocket/STOMP, Actuator), **MongoDB** |
| `frontend/` | React 19 + Vite, Leaflet + OpenStreetMap, Recharts, STOMP over WebSocket |
| Routing / geocoding | OSRM + Nominatim public servers (free, no keys; haversine fallback when offline) |

Docker is optional, not required - run natively (below) or with one `docker compose up` (see
[Docker](#docker)). Native needs **Java 17+**, **Maven**, **Node 18+** and a **MongoDB** (local
`mongod`, or a free MongoDB Atlas cluster); Docker only needs Docker.

---

## Run it

Three terminals (or two if you use Atlas):

```powershell
# 1. MongoDB - skip if MONGODB_URI in backend/.env points at Atlas
mongod --dbpath C:\data\db

# 2. Backend  ->  http://localhost:8080   (Swagger: /swagger-ui.html)
cd backend
mvn spring-boot:run

# 3. Frontend ->  http://localhost:5173
cd frontend
npm install
npm run dev
```

`backend/.env` is loaded automatically (copy `backend/.env.example`). If port 5173 is taken Vite
picks 5174 - both origins are already allowed by CORS. If a port is busy, find the owner with
`netstat -ano | findstr :8080` instead of starting a second copy.

On a **fresh, empty database** the backend seeds the whole demo scenario by itself. If your
database already has data, open the **Dashboard -> "Reset demo data"** button once (it wipes
orders/routes/proofs/GPS history/vehicles and re-creates the scenario; user accounts are kept).

### Demo accounts

| Role | Email | Password | Sees |
|---|---|---|---|
| Dispatcher | `dispatcher@routeflow.dev` | `Dispatcher@123` | full console |
| Manager | `manager@routeflow.dev` | `Manager@123` | full console |
| Driver (Van-1) | `driver1@routeflow.dev` | `Driver@123` | mobile driver view |
| Driver (Van-2) | `driver2@routeflow.dev` | `Driver@123` | mobile driver view |
| Driver (Bike-1) | `driver3@routeflow.dev` | `Driver@123` | mobile driver view |

The login screen has click-to-fill chips for these in dev builds. One session at a time; a driver
is routed to the driver view automatically.

---

## Guided walkthrough - see every PRD feature in ~10 minutes

The seeded scenario is built so each feature has something to show (all in Mumbai, all "today").

**Fleet** - `Van-1` (cap 24, Andheri depot), `Van-2` (cap 20, Lower Parel), `Bike-1` (cap 8, Bandra),
and an **inactive** `Truck-1` (ignored by the optimizer). **Orders** - 21 pending for today, plus 7
days of completed history (73 orders, 70 proofs of delivery with signatures/photos, ~1,100 GPS pings).

| # | Do this | You will see | PRD |
|---|---|---|---|
| 1 | **Orders** page | 21 `T-1xx/2xx` orders on the map + table; depots as dark squares; status filter chips | 6.1 |
| 2 | Order **T-203** ("No location" badge) -> Edit -> click the map beside the form -> Save | Manual pin-drop fallback; the badge disappears | 6.1 |
| 3 | **Import CSV** (download the template link under the table) | Bulk import with per-row error reporting | 6.1 |
| 4 | **Dashboard -> Optimize today's routes** | 3 routes split by nearest depot & capacity. **T-201** (load 90 > any vehicle) and **T-202** (window closes 09:05) are reported *unassigned with the reason*; T-203 is skipped (no coordinates) | 6.2 |
| 5 | **Live map** -> a planned route | Polyline + numbered stops per vehicle; **T-104** waits for its 11:00 window; urgent **T-103/T-108** (priority 2) are pulled to the front | 6.2 |
| 6 | Move a stop with ▲▼ or "Move to..." another vehicle; "Discard" a plan | ETAs and geometry recompute; discarded orders return to Pending | 6.2 (manual override) |
| 7 | **Dispatch** a route | Vehicle marker (purple) starts moving; green **ETA** per stop updates on every ping; "Live" indicator = authenticated WebSocket | 6.3, 6.4 |
| 8 | Click a stop's **Deliver** -> draw a signature, add notes -> Mark delivered | Stop turns green. **Orders -> View POD** shows signature, notes, geo-stamp, timestamp | 6.5 |
| 9 | Click **Failed** on another stop, pick a reason | Reason shown on the order; counted under "Why deliveries failed" in Analytics | 6.4 |
| 10 | **Analytics** | KPIs, deliveries/day, **on-time trend**, planned-vs-actual km, leaderboard, failure reasons (mostly from the 7-day history) | 6.6 |
| 11 | Log out, sign in as **Driver (Van-2)** (use a phone-width window) | Driver view: next stop, call/navigate, Arrived / Delivered (with POD) / Couldn't deliver. Tick **Share my live location** to stream real browser GPS - the simulator stands down for that vehicle | 6.3, 6.7 |
| 12 | **Drivers** page | Deactivate/reactivate a driver (blocks login), edit vehicles | 6.1, 6.7 |

The server-side **driver simulator** moves dispatched vehicles by itself (8x fast-forward, 10-tick
dwell at each stop so you have time to click Deliver). Tune it with `DRIVER_SIMULATOR_*` in `.env`.

---

## PRD coverage

| Area | Status |
|---|---|
| 6.1 Orders/fleet CRUD, CSV import, geocoding + pin-drop | Done |
| 6.2 VRP: capacity, time windows, shift, priority; polyline; manual reorder/reassign/discard | Done - built-in heuristic (cluster-first, route-second), not OR-Tools/jsprit |
| 6.3 Dispatch, live tracking, driver web view, simulator | Done (authenticated STOMP; browser Geolocation) |
| 6.4 Rolling ETAs, lifecycle state machine, failure reasons | Done (ETAs recomputed on every location ping) |
| 6.5 Proof of delivery (photo/signature/notes/geo/time) + audit view | Done |
| 6.6 Analytics: KPIs + 4 charts + leaderboard | Done |
| 6.7 JWT auth, roles, guards | Done - refresh tokens, driver-owns-vehicle checks, staff-only registration |
| 7 NFRs | Async-safe ingestion, stateless JWT API, Actuator health/metrics, responsive to 360px |
| 12 Phase 7 tests / docs / CI | 26 backend + 7 frontend unit tests, this README, GitHub Actions workflow, Docker + [AWS deploy guide](DEPLOY_AWS.md) |
| Not done | OR-Tools/jsprit, microservice split, stretch goals (geofencing, Redis STOMP relay, ...) |

Deliberate deviations: **MongoDB** instead of PostgreSQL/PostGIS; a pure-Java routing heuristic;
STOMP over plain WebSocket (no SockJS needed by modern browsers).

---

## Docker

One command runs the whole stack (Mongo + backend + nginx-served frontend) - no local Java/Node/
Mongo install needed. `docker-compose.yml` at the repo root builds `backend/Dockerfile` and
`frontend/Dockerfile` (multi-stage: build, then a slim runtime image) and wires them together.

**Check ports before running** - `docker-compose.yml` wants host ports **8080** (backend), **8081**
(frontend) and **27018** (Mongo, intentionally *not* 27017, since that's commonly already taken by
a local MongoDB install/service - this container's Mongo is internal-only unless you need to
inspect it from the host):

```powershell
# Windows PowerShell
foreach ($p in 8080, 8081, 27018) {
  $c = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue
  if ($c) { "port $p IN USE by pid $($c.OwningProcess)" } else { "port $p free" }
}
```
```bash
# macOS/Linux
for p in 8080 8081 27018; do lsof -i :$p -sTCP:LISTEN || echo "port $p free"; done
```

If any are busy, either stop whatever owns them, or override the port in a local `.env` file (copy
`.env.docker.example` - `MONGO_PORT`, `BACKEND_PORT`, `FRONTEND_PORT`).

```powershell
docker compose up --build -d     # build + start all three, detached
docker compose ps                # wait for backend + mongodb to show "healthy"
curl http://localhost:8080/actuator/health
```

Open `http://localhost:8081` and log in with the seeded demo accounts (see above). Note: the
demo-account chips on the login screen only render in a `vite dev` build (`import.meta.env.DEV`) -
the Docker image is a production build, so type the credentials in manually.

```powershell
docker compose down               # stop + remove containers (keeps the mongo/uploads volumes)
docker compose down -v            # also delete the volumes (fresh start next time)
docker compose logs -f backend    # tail one service's logs
```

`VITE_API_BASE_URL` is baked into the frontend's JS bundle at **build** time (the browser can't
resolve Docker service names like `backend`) - it must be wherever the backend's port is actually
published (`http://localhost:8080` by default). Changing it needs `docker compose up --build`, not
just `up`, to take effect.

To run the backend + Mongo only (e.g. while iterating on the frontend with `npm run dev` instead),
`backend/docker-compose.yml` still works standalone: `cd backend && docker compose up --build`.

**Deploying these images to AWS**: see [`DEPLOY_AWS.md`](DEPLOY_AWS.md) - an EC2 + Docker Compose
walkthrough (security group/port setup included) plus notes on the ECR/ECS/Atlas path for a more
production-shaped deployment.

---

## Configuration

`backend/.env` (see `.env.example` for all keys). The ones you'll touch:

| Key | Default | Meaning |
|---|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017/routeflow` | local or Atlas (`mongodb+srv://...`) |
| `APP_TIME_ZONE` | `Asia/Kolkata` | decides what "today" is and how shifts (09:00) map to instants |
| `CORS_ALLOWED_ORIGINS` | `:5173,:5174,:3000` | frontend origins |
| `SEED_DEMO_DATA` | `true` | seed on empty DB; enables the Reset-demo-data endpoint. **Set `false` in production** |
| `DRIVER_SIMULATOR_ENABLED` | `true` | server-side fake drivers |
| `JWT_SECRET` | dev value | **change for anything real** |

`frontend/.env`: `VITE_API_BASE_URL` (default `http://localhost:8080`).

**Restyling**: every colour, spacing, radius, font and map-marker colour lives in
`frontend/src/theme.css`. Nothing else hardcodes a value, so editing that one file re-skins the app.

---

## Tests

```powershell
cd backend;  mvn test        # 26 tests: optimizer, stop state machine, JWT, ETAs, analytics rules
cd frontend; npm test        # 7 tests: polyline parsing, HTML escaping, status helpers
cd frontend; npm run lint; npm run build
```

Unit tests need no database. Beyond them, the whole stack was verified end to end against an isolated
MongoDB and a real headless browser (40 API checks + 33 browser checks covering login, optimize,
pin-drop, CSV import, live WebSocket tracking, POD capture and audit, driver GPS, analytics).

## Troubleshooting

- **"Failed to fetch" on every page** - the backend isn't running, or your frontend origin isn't in
  `CORS_ALLOWED_ORIGINS` (restart the backend after editing `.env`).
- **`Port 8080 was already in use`** - a previous backend is still running; stop it, don't start another.
- **Data ends up in the wrong database** - `.env` is loaded by the backend on startup; check
  `MONGODB_URI` and watch the `MongoClient` line in the startup log.
- **Map tiles are blank** - OpenStreetMap tiles need internet access.
- **Routes look like straight lines** - OSRM's public server was unreachable and the haversine
  fallback kicked in (it retries after a minute).
