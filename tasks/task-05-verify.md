# Task 05 — Verify end-to-end

**Spec reference:** Verification steps  
**Depends on:** tasks 01 – 04 all complete

---

## Context

The `app` service uses `command: sleep infinity` — the Spring Boot application
does NOT start automatically on container rebuild. It must be started manually.
Prometheus will show the scrape target as `DOWN` until the app is listening on
port 8080.

---

## Steps

### 1. Rebuild the container

From VS Code: **Dev Containers: Rebuild Container** (Command Palette or the
green bottom-left button → Rebuild Container).

This starts the `app`, `prometheus`, and `grafana` services. Do not run
`docker compose up` manually — you are inside the container.

### 2. Start the Spring Boot application

Inside the devcontainer terminal:

```bash
./gradlew bootRun
```

Wait for the log line that includes `Started ComputingLabMay2026Application`.
Leave this terminal running.

### 3. Confirm Prometheus is scraping the app

In a second terminal (or from the host):

```bash
curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | {job: .labels.job, health: .health}'
```

Expected:

```json
{
  "job": "computing_lab_may_2026",
  "health": "up"
}
```

### 4. Confirm Grafana opens without a login prompt

Open `http://localhost:3000` in a browser. It should land directly on the
Grafana home page — no login form, no credentials.

### 5. Confirm the dashboard loads

Navigate to **Dashboards → "Computing Lab — Spring Boot Overview"**. All 20
panels must load without any "datasource not found" errors.

### 6. Generate metric data and watch the dashboard update

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ add(a: 3, b: 4) }"}'
```

Within 30 s (one Prometheus scrape cycle + one Grafana refresh) the GraphQL
request rate and response time panels should show a data point.

### 7. Confirm persistence survives a rebuild

Rebuild the container again via **Dev Containers: Rebuild Container**.

After the rebuild, start the app again (`./gradlew bootRun`), then open
`http://localhost:3000`. The dashboard and Prometheus datasource must be present
immediately — no re-provisioning needed. Prometheus metric history must be
intact (the named volume `prometheus-data` is preserved across rebuilds).

---

## Done when

All seven steps pass without errors.
