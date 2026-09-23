#!/usr/bin/env node
/**
 * RouteFlow API output dumper.
 *
 * Exercises the running backend end-to-end (login -> seed vehicles/orders -> optimize ->
 * dispatch -> analytics) and writes every response into a single JSON file. Handy for quick
 * smoke-testing after `docker compose up` / `mvn spring-boot:run`, and as a fixture for the
 * (not-yet-built) frontend.
 *
 * Requires Node.js 18+ (built-in `fetch`), no npm install needed.
 *
 * Usage:
 *   node scripts/fetch-api-output.js
 *
 * Config (env vars, all optional - see .env.example):
 *   API_BASE_URL         default http://localhost:8080
 *   API_LOGIN_EMAIL      default dispatcher@routeflow.dev  (seeded by DataSeeder)
 *   API_LOGIN_PASSWORD   default Dispatcher@123
 *   OUTPUT_FILE          default ./output/api-output.json
 *
 * NOTE: this does nothing useful until the backend is actually running with a real database
 * and (optionally) real OSRM/Nominatim endpoints configured - see backend/.env.example.
 */

const fs = require("fs");
const path = require("path");

const BASE_URL = process.env.API_BASE_URL || "http://localhost:8080";
const LOGIN_EMAIL = process.env.API_LOGIN_EMAIL || "dispatcher@routeflow.dev";
const LOGIN_PASSWORD = process.env.API_LOGIN_PASSWORD || "Dispatcher@123";
const OUTPUT_FILE = process.env.OUTPUT_FILE || path.join(__dirname, "..", "output", "api-output.json");

const today = new Date().toISOString().slice(0, 10);

async function callApi(method, urlPath, token, body) {
    const headers = { "Content-Type": "application/json" };
    if (token) headers["Authorization"] = `Bearer ${token}`;

    const res = await fetch(`${BASE_URL}${urlPath}`, {
        method,
        headers,
        body: body !== undefined ? JSON.stringify(body) : undefined,
    });

    let data = null;
    const text = await res.text();
    try {
        data = text ? JSON.parse(text) : null;
    } catch {
        data = text;
    }

    return { status: res.status, ok: res.ok, data };
}

async function main() {
    const output = {
        generatedAt: new Date().toISOString(),
        baseUrl: BASE_URL,
        steps: {},
    };

    try {
        console.log(`Logging in as ${LOGIN_EMAIL} ...`);
        const login = await callApi("POST", "/api/auth/login", null, {
            email: LOGIN_EMAIL,
            password: LOGIN_PASSWORD,
        });
        output.steps.login = login;

        if (!login.ok || !login.data?.accessToken) {
            throw new Error("Login failed - is the backend running and seeded? See backend/.env.example");
        }
        const token = login.data.accessToken;

        console.log("Fetching vehicles ...");
        output.steps.vehicles = await callApi("GET", "/api/vehicles", token);

        console.log("Fetching orders ...");
        output.steps.orders = await callApi("GET", `/api/orders?date=${today}`, token);

        const vehicleIds = (output.steps.vehicles.data || []).map((v) => v.id);

        console.log("Requesting route optimization ...");
        output.steps.optimize = await callApi("POST", "/api/routes/optimize", token, {
            date: today,
            vehicleIds,
        });

        const routes = output.steps.optimize.data?.routes || [];
        if (routes.length > 0) {
            const firstRouteId = routes[0].id;
            console.log(`Dispatching route ${firstRouteId} ...`);
            output.steps.dispatch = await callApi("POST", `/api/routes/${firstRouteId}/dispatch`, token);
        } else {
            output.steps.dispatch = { skipped: "No routes were produced by /routes/optimize (no orders/vehicles yet?)" };
        }

        console.log("Fetching routes for today ...");
        output.steps.routesForDate = await callApi("GET", `/api/routes?date=${today}`, token);

        console.log("Fetching analytics summary ...");
        output.steps.analyticsSummary = await callApi(
            "GET",
            `/api/analytics/summary?from=${today}&to=${today}`,
            token
        );

        output.success = true;
    } catch (err) {
        output.success = false;
        output.error = err.message;
        console.error("fetch-api-output failed:", err.message);
    }

    fs.mkdirSync(path.dirname(OUTPUT_FILE), { recursive: true });
    fs.writeFileSync(OUTPUT_FILE, JSON.stringify(output, null, 2), "utf-8");
    console.log(`Wrote output to ${OUTPUT_FILE}`);

    process.exit(output.success ? 0 : 1);
}

main();
