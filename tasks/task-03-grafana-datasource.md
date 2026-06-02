# Task 03 — Create Grafana datasource provisioning

**Spec reference:** Changes required § 3  
**File:** `.devcontainer/grafana/provisioning/datasources/prometheus.yml` (new)

---

## What to do

Create the file. Grafana reads this on startup and auto-wires Prometheus as the
default data source — no manual UI setup needed on a fresh container.

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    uid: prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: false
```

**Important:** the `uid: prometheus` field is required. The dashboard JSON in
`.devcontainer/grafana/dashboards/computing-lab-overview.json` references the
datasource by this exact UID. Without it Grafana assigns a random UID on first
start and every panel shows "datasource not found".

## Done when

- File exists at `.devcontainer/grafana/provisioning/datasources/prometheus.yml`.
- After `docker compose up`, opening `http://localhost:3000` and navigating to
  Connections → Data sources shows Prometheus with a green health check.
