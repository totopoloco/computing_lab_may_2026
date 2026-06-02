# Task 05 — Verify end-to-end

**Spec reference:** Verification steps  
**Depends on:** tasks 01 – 04 all complete

---

## Steps

1. Start the stack:
   ```bash
   docker compose -f .devcontainer/docker-compose.yml up
   ```

2. Confirm Prometheus is scraping the app:
   ```bash
   curl http://localhost:9090/targets
   ```
   Expected: job `computing_lab_may_2026` with `state: "UP"`.

3. Open `http://localhost:3000` — browser should land on the Grafana home page
   with no login prompt.

4. Navigate to Dashboards → "Computing Lab — Spring Boot Overview" — all 20
   panels must load without any "datasource not found" errors.

5. Send a test request to generate metric data:
   ```bash
   curl -s -X POST http://localhost:8080/graphql \
     -H 'Content-Type: application/json' \
     -d '{"query":"{ add(a: 3, b: 4) }"}'
   ```
   Within 30 s (one Prometheus scrape + one Grafana refresh) the GraphQL request
   rate and response time panels should update.

6. Confirm persistence survives a restart:
   ```bash
   docker compose -f .devcontainer/docker-compose.yml down
   docker compose -f .devcontainer/docker-compose.yml up
   ```
   Grafana must open directly to the home page (no login), the dashboard and
   datasource must be present, and Prometheus history must be intact.

## Done when

All six steps pass without errors.
