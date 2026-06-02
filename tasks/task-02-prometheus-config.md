# Task 02 — Create Prometheus configuration

**Spec reference:** Changes required § 2  
**File:** `.devcontainer/prometheus/prometheus.yml` (new)

---

## What to do

Create the directory and file. Prometheus uses this to know what to scrape.

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: computing_lab_may_2026
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - app:8080
```

The hostname `app` resolves inside `dev-network` to the Spring Boot container.
`/actuator/prometheus` is already exposed — no Spring Boot changes needed.

## Done when

- File exists at `.devcontainer/prometheus/prometheus.yml`.
- After `docker compose up`, `http://localhost:9090/targets` shows job
  `computing_lab_may_2026` with state `UP`.
