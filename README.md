# RouteFlow

RouteFlow is a real-time logistics control platform designed to automate delivery scheduling. A dispatcher can create delivery orders, automatically optimize multi-stop routes across a fleet, dispatch them, and watch drivers move on a live map. Drivers use a responsive mobile web view to work through their assigned stops, update statuses, and capture proof of delivery.

## 🚀 Project Overview

RouteFlow aims to replace manual routing and dispatching with an automated, transparent, real-time system.
- **Route Optimization**: Uses a nearest-depot clustering algorithm with priority and time-window-aware scoring to optimize delivery routes.
- **Real-Time Tracking**: Provides highly-responsive real-time driver tracking natively via WebSockets (less than ~2s latency).
- **Delivery Validation**: Supports Proof of Delivery (POD) via embedded photo uploads, digital signatures, and geo-timestamps.
- **Analytics Dashboard**: Tracks KPIs such as on-time percentages, deliveries per day, and real-time planned vs. actual distance metrics.

## 🏗 Architecture

The platform operates using a decoupled backend-frontend structure and features robust event-driven real-time updates.

- **Backend (API Layer)**: Java 17 and Spring Boot 3 (Data MongoDB, Security, Validation, Actuator) handling business logic, optimization, and REST endpoints.
- **Database**: MongoDB (local or Atlas) acts as the central data store, using embedded documents to efficiently load and mutate stops on individual routes.
- **Real-Time Layer**: STOMP over raw WebSockets with Spring's in-memory `SimpleBroker` handling pub-sub mechanics, securely authenticated via JWT STOMP headers on connect.
- **Frontend (Dispatcher & Driver UI)**: React 19 + Vite powered by vanilla CSS. Features Leaflet for interactive maps, OpenStreetMap tiles, and Recharts for analytics data visualization.
- **Routing & Geocoding Engine**: Connects to the OSRM public demo server for real-world distances and road-mapped polylines. Nominatim resolves address-to-coordinates lookups. Both have robust local fallback mechanisms (haversine distances and manual pin drops, respectively).

## 🛠 Setup & Run Instructions

Docker is optional but recommended. You can run natively or with a single `docker compose up` command.

### Prerequisite Checks
If running natively, ensure you have **Java 17+**, **Maven**, **Node 18+**, and **MongoDB** installed and running on your system.

### Option 1: Native Setup

You will need three terminal windows:

```powershell
# 1. MongoDB - skip if MONGODB_URI in backend/.env points at a cloud MongoDB Atlas instance
mongod --dbpath C:\data\db

# 2. Backend API -> http://localhost:8080 (Swagger: /swagger-ui.html)
cd backend
mvn spring-boot:run

# 3. Frontend -> http://localhost:5173
cd frontend
npm install
npm run dev
```

### Option 2: Docker Setup
Run the whole stack via Docker Compose from the repository root:
```powershell
docker compose up --build -d  # Build + start all containers in detached mode
docker compose ps             # Wait for backend + mongodb to show "healthy"
curl http://localhost:8080/actuator/health
```

### Configuration
Update the `.env` settings based on the available examples:
- **Backend (`backend/.env`)** - Handles configurations for `MONGODB_URI`, `APP_TIME_ZONE`, `CORS_ALLOWED_ORIGINS`, `JWT_SECRET`, and more.
- **Frontend (`frontend/.env`)** - Define `VITE_API_BASE_URL` (defaults to `http://localhost:8080`).

---

### Demo Accounts
On a **fresh, empty database**, the backend seeds the scenario by itself. For dev builds, you can click-to-fill these accounts on the login screen. Note that driver accounts automatically route to the mobile driver UI.

| Role | Email | Password | Access |
|---|---|---|---|
| Dispatcher | `dispatcher@routeflow.dev` | `Dispatcher@123` | Full dispatcher console |
| Manager | `manager@routeflow.dev` | `Manager@123` | Full dispatcher console |
| Driver (Van-1) | `driver1@routeflow.dev` | `Driver@123` | Mobile driver view |
| Driver (Van-2) | `driver2@routeflow.dev` | `Driver@123` | Mobile driver view |
| Driver (Bike-1) | `driver3@routeflow.dev` | `Driver@123` | Mobile driver view |

## 🧪 Testing

The platform has extensive tests covering the heuristic algorithm, lifecycle states, token security, analytics mapping, and UI consistency. Run the tests using:
```powershell
cd backend
mvn test        # 26 Backend unit tests
cd ../frontend 
npm test        # 7 Frontend unit tests
```

---
*(Note: If deploying in an AWS production environment, you may containerize the application to ECR and deploy into an ECS Fargate cluster fronted by an Application Load Balancer.)*
