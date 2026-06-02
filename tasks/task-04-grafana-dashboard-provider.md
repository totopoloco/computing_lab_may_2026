# Task 04 — Create Grafana dashboard provider

**Spec reference:** Changes required § 4  
**File:** `.devcontainer/grafana/provisioning/dashboards/provider.yml` (new)

---

## What to do

Create the file. This tells Grafana to watch `/etc/grafana/dashboards` inside the
container (bind-mounted from `.devcontainer/grafana/dashboards/`) and load any
`.json` files it finds there.

```yaml
apiVersion: 1

providers:
  - name: local
    type: file
    disableDeletion: false
    updateIntervalSeconds: 30
    options:
      path: /etc/grafana/dashboards
```

The dashboard JSON (`computing-lab-overview.json`) is already present in
`.devcontainer/grafana/dashboards/` and will be picked up automatically.

## Done when

- File exists at `.devcontainer/grafana/provisioning/dashboards/provider.yml`.
- After `docker compose up`, navigating to Dashboards in Grafana shows
  "Computing Lab — Spring Boot Overview".
