# RouteFlow backend

Spring Boot 3 / Java 17 REST + WebSocket API implementing the PRD's Phase 0-6 backend surface:
auth, order/vehicle CRUD + CSV import, route optimization (VRP heuristic), dispatch, live
tracking over STOMP, a server-side driver simulator, proof-of-delivery capture, and an analytics
summary endpoint, plus the driver-facing API. The React frontend lives in `../frontend` (see the top-level README).

All external dependencies are free/open-source:

| Concern | Choice |
|---|---|
| Routing (distance/duration/polyline) | [OSRM](https://project-osrm.org) public demo server, self-hostable via `docker-compose` |
| Geocoding | [Nominatim](https://nominatim.org) (OpenStreetMap) |
| Database | MongoDB (local via `docker-compose`, or MongoDB Atlas free tier) |
| Auth | Spring Security + JWT (`jjwt`) |
| Real-time | STOMP over WebSocket (Spring's simple in-memory broker) |
| File storage (POD photos) | local disk under `uploads/`, served at `/uploads/**` |

## Running (once you add API keys / start MongoDB)

Nothing in this backend strictly requires a paid API key - OSRM/Nominatim public servers need
none. You do need a MongoDB instance - either the bundled `docker-compose` service, or a free
MongoDB Atlas cluster (set `MONGODB_URI` to its connection string, with a database name in the
path, e.g. `.../cluster0.mongodb.net/routeflow?retryWrites=true&w=majority`).

```bash
cp .env.example .env       # review/edit values
docker compose up -d       # mongodb + the api itself
# or, for local dev without Docker:
mvn spring-boot:run
```

The app seeds demo accounts on first boot (`routeflow.seed.enabled=true` by default):

| Role | Email | Password |
|---|---|---|
| DISPATCHER | dispatcher@routeflow.dev | Dispatcher@123 |
| MANAGER | manager@routeflow.dev | Manager@123 |
| DRIVER | driver1@routeflow.dev | Driver@123 |
| DRIVER | driver2@routeflow.dev | Driver@123 |

Swagger UI: `http://localhost:8080/swagger-ui.html`
Health check: `http://localhost:8080/actuator/health`

## API surface

```
POST   /api/auth/login
POST   /api/auth/register

GET    /api/orders?date=YYYY-MM-DD
GET    /api/orders/{id}
POST   /api/orders
PUT    /api/orders/{id}
DELETE /api/orders/{id}
POST   /api/orders/import                 (multipart CSV)

GET    /api/vehicles
GET    /api/vehicles/{id}
POST   /api/vehicles
PUT    /api/vehicles/{id}
DELETE /api/vehicles/{id}                 (soft delete -> active=false)

POST   /api/routes/optimize               { date, vehicleIds? } -> planned routes
GET    /api/routes?date=YYYY-MM-DD
GET    /api/routes/{id}
POST   /api/routes/{id}/dispatch
PATCH  /api/routes/{id}/reorder           { orderedStopIds }

PATCH  /api/stops/{id}/status             { status, exceptionReason? }
DELETE /api/routes/{id}                   discard a PLANNED route (orders return to PENDING)
PATCH  /api/stops/{id}/reassign           { targetRouteId, sequence? }   (staff only, PLANNED routes)

GET    /api/driver/routes?date=           driver: my vehicle + dispatched routes
POST   /api/driver/routes/{id}/start      driver: DISPATCHED -> IN_PROGRESS

POST   /api/auth/refresh                  { refreshToken } -> new access + refresh token
POST   /api/admin/demo/reset              rebuild the demo scenario (staff, seed.enabled only)

POST   /api/orders/{orderId}/pod          (multipart: photo?, signatureData?, notes?, lat?, lng?)
GET    /api/orders/{orderId}/pod

GET    /api/analytics/summary?from=&to=

POST   /api/tracking/location             REST fallback for a location ping
GET    /api/tracking/vehicles/{id}/last
WS     /ws (STOMP)  ->  send to /app/location, subscribe to /topic/vehicles/{id} or /topic/vehicles
WS     /ws-sockjs   ->  same, wrapped in SockJS for clients that need an HTTP fallback
```

## Route optimization approach

`RouteOptimizationService` is a **cluster-first, route-second heuristic**: (1) each order is given to the
nearest depot that still has capacity (urgent and heavy orders first); (2) each vehicle sequences its
cluster by repeatedly taking the best feasible next stop, scored as road-km minus a priority bonus,
plus a penalty for idling at a window that has not opened and a bonus as a window's deadline nears;
(3) orders a vehicle could not fit (window/shift) are offered to every vehicle again. Feasibility
honours capacity, time windows and the driver's shift end. This intentionally avoids a native solver dependency (OR-Tools) or extra library (jsprit)
so the service has zero setup beyond `mvn package`. The PRD's `OptimizeResponse` contract is
solver-agnostic, so swapping in a real OR-Tools/jsprit solver later only touches this one class.

Distances/durations/polylines come from OSRM; if OSRM is unreachable the service transparently
falls back to a haversine straight-line distance at a configurable average speed
(`routeflow.routing.fallback-avg-speed-kmh`), so the app keeps working without network access.

## Driver simulator

`DriverSimulatorService` runs on a fixed schedule (`routeflow.simulator.tick-ms`, default 3s) and
moves every `DISPATCHED`/`IN_PROGRESS` route's vehicle toward its next stop, feeding the same
`TrackingService.ingest()` path a real driver's browser would use. This satisfies PRD 6.3's
"driver simulator" requirement so the live-tracking map is demoable without real phones. Disable
with `DRIVER_SIMULATOR_ENABLED=false`.

## Output dump script

`scripts/fetch-api-output.js` logs in, seeds a route via `/routes/optimize`, dispatches it, and
saves every response into `output/api-output.json`. Requires Node.js 18+ (uses the built-in
`fetch`), no npm dependencies.

```bash
node scripts/fetch-api-output.js
```

Configure via env vars (see `.env.example`): `API_BASE_URL`, `API_LOGIN_EMAIL`,
`API_LOGIN_PASSWORD`, `OUTPUT_FILE`. **This will fail until the backend is actually running**
against a real database - it has not been run as part of this change.

## Data model on MongoDB

Originally built on PostgreSQL/JPA, then migrated to MongoDB. Key differences from a relational
mapping worth knowing before extending this code:

- **`RouteStop` is embedded inside its parent `Route` document**, not a top-level collection -
  Mongo has no joins, so a route's stops live in its `stops` array. Stop-level operations
  (`StopService`, `DispatchService`) load the owning `Route` (via `RouteRepository.findByStopsId`),
  mutate the stop within the list, and save the whole `Route` back.
- **Cross-document references are plain `String` id fields**, not object references - e.g.
  `Route.vehicleId`, `RouteStop.orderId`, `Vehicle.driverId`. Rarely-changing display fields that
  used to come from a JPA lazy-load (`vehicleLabel` on `Route`, `orderRef`/`addressText`/`lat`/`lng`
  on `RouteStop`, `driverName` on `Vehicle`) are denormalized as snapshots at write time instead,
  since Mongo has no live joins to pull them from on every read.
- **No implicit dirty-checking.** JPA auto-persisted any change to a managed entity at transaction
  commit; MongoRepository does not - every mutation is followed by an explicit `.save()` (or
  `saveAll()`), including the Order status flips that ride along with a stop/route update
  (`StopService`/`DispatchService` call `OrderService.updateStatus(...)` explicitly for this).
- **No `@Transactional`.** A standalone/local MongoDB (the `docker-compose` default) doesn't
  support multi-document transactions the way a replica set or Atlas does, so this app doesn't
  rely on them - each service performs its writes as a short sequence of independent document
  saves. Fine at this scale; revisit with `MongoTransactionManager` if true multi-document
  atomicity becomes a requirement.

## Security model

- **Roles** (`DISPATCHER`, `MANAGER`, `DRIVER`) are enforced per route in `SecurityConfig`. Drivers may only
  reach `/api/driver/**`, `/api/stops/*/status`, `/api/orders/*/pod` and `/api/tracking/**`.
- **Ownership**: on those shared endpoints a driver is additionally restricted to *their own* vehicle,
  stops and orders (`DriverAccessService`) - one driver cannot update another's stop or report another's
  position. Dispatchers/managers are unrestricted.
- **Accounts**: `/api/auth/register` requires a dispatcher/manager token (it used to be open).
- **Tokens**: short-lived access token + refresh token (`POST /api/auth/refresh`). Tokens carry a `type`
  claim; a refresh token is rejected as an access token and vice versa. Disabled users cannot log in or refresh.
- **WebSocket**: the STOMP `CONNECT` frame must carry `Authorization: Bearer <access token>`
  (`StompAuthInterceptor`); location messages are checked against the driver's vehicle.
- `/uploads/**` (proof-of-delivery photos) is served without auth; filenames are random UUIDs.
- `POST /api/admin/demo/reset` only works while `routeflow.seed.enabled=true`.

## Demo data

`DemoDataService` builds a deterministic Mumbai scenario (fleet, 21 orders for today - including three
deliberately un-routable ones - and 7 days of completed history with proofs of delivery and GPS pings).
It runs automatically on an empty database, and on demand via `POST /api/admin/demo/reset` (also the
Dashboard's "Reset demo data" button). Users are never deleted.

## Known simplifications

- No PostGIS/geospatial indexing - lat/lng are plain fields. Geofencing (stretch goal) would want
  MongoDB's native 2dsphere indexes.
- The VRP solver is a heuristic, not OR-Tools/jsprit (see above).
- WebSocket broker is Spring's in-memory `SimpleBroker`; the PRD's "Redis pub/sub for scaling
  WebSocket fan-out" stretch goal would replace it with a STOMP relay for multi-instance scaling.
- Rolling ETAs use straight-line distance x 1.3 (cheap enough to run on every ping), not OSRM.
- Server-side simulator moves vehicles in straight lines between stops rather than along the road polyline.
