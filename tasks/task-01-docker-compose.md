# Task 01 — Update docker-compose.yml

**Spec reference:** Changes required § 1  
**File:** `.devcontainer/docker-compose.yml`

---

## Prerequisites

Complete tasks 02, 03, and 04 before rebuilding the container. Docker will
bind-mount the files created in those tasks at startup; if any source path is
missing, the service will either fail or start with an empty directory.

---

## What to do

Add two new services (`prometheus`, `grafana`) and two new named volumes
(`prometheus-data`, `grafana-data`) to the existing compose file. Do not touch
the `app` service, `dev-network`, or `gradle-cache` volume.

### Add after the `app` service block

```yaml
  prometheus:
    image: prom/prometheus:v3.4.0
    restart: unless-stopped
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus
    ports:
      - "9090:9090"
    networks:
      - dev-network
    depends_on:
      - app

  grafana:
    image: grafana/grafana:12.0.1
    restart: unless-stopped
    environment:
      - GF_AUTH_ANONYMOUS_ENABLED=true
      - GF_AUTH_ANONYMOUS_ORG_ROLE=Admin
      - GF_AUTH_DISABLE_LOGIN_FORM=true
      - GF_PATHS_PROVISIONING=/etc/grafana/provisioning
    volumes:
      - grafana-data:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning:ro
      - ./grafana/dashboards:/etc/grafana/dashboards:ro
    ports:
      - "3000:3000"
    networks:
      - dev-network
    depends_on:
      - prometheus
```

### Extend the `volumes` block

Add the two new entries alongside the existing ones — keep the `gradle-cache`
entry and the `# postgres-data:` comment untouched:

```yaml
volumes:
  gradle-cache:
  prometheus-data:
  grafana-data:
  # postgres-data:  # Uncomment if using PostgreSQL
```

## Done when

- `docker compose -f .devcontainer/docker-compose.yml config` reports no errors.
- Three services are listed: `app`, `prometheus`, `grafana`.
- Two new volumes appear under the `volumes` key.
