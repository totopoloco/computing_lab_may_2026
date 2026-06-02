# Spec: Grafana Integration

## Goal

Add a Grafana dashboard to the dev environment, fed by Prometheus scraping the Spring Boot
`/actuator/prometheus` endpoint. Grafana is open with no login required. Data must survive
container rebuilds via named volumes.

---

## What is already in place (no changes needed)

| Concern | Location | Status |
|---|---|---|
| Micrometer Prometheus registry | `build.gradle` — `runtimeOnly 'io.micrometer:micrometer-registry-prometheus'` | Done |
| Actuator endpoint exposure | `application.properties` — `management.endpoints.web.exposure.include=health,info,metrics,prometheus` | Done |
| Docker network | `.devcontainer/docker-compose.yml` — `dev-network` bridge | Done |

The `/actuator/prometheus` endpoint is live on `http://app:8080/actuator/prometheus` the moment
the Spring Boot app starts under the `dev` profile. No application code or properties changes
are required.

---

## Architecture

```
Spring Boot (app:8080)
  └─ /actuator/prometheus  ←── scrapes every 15 s ──── Prometheus (prometheus:9090)
                                                              │
                                                        data source
                                                              │
                                                        Grafana (grafana:3000)  ← open, no login
```

All three services share `dev-network`. Prometheus and Grafana each get a named volume so
metric history and dashboard state are not lost on `docker compose down` or image rebuilds.

---

## Changes required

### 1. `.devcontainer/docker-compose.yml`

Add two services and two volumes. The existing `dev-network` and `gradle-cache` volume are
unchanged.

**Prometheus service**

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
```

**Grafana service** — anonymous access, no login form

```yaml
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

**Additional named volumes** (add alongside the existing `gradle-cache` entry)

```yaml
volumes:
  gradle-cache:
  prometheus-data:
  grafana-data:
```

---

### 2. `.devcontainer/prometheus/prometheus.yml` — new file

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

The hostname `app` resolves via `dev-network` to the Spring Boot container.

---

### 3. `.devcontainer/grafana/provisioning/datasources/prometheus.yml` — new file

The `uid: prometheus` field is required so the provisioned dashboard JSON can reference the
data source by a stable identifier without relying on Grafana's auto-generated UID.

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

---

### 4. `.devcontainer/grafana/provisioning/dashboards/provider.yml` — new file

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

---

### 5. `.devcontainer/grafana/dashboards/computing-lab-overview.json` — new file

Ready-to-use dashboard. Five rows: Application Overview, JVM Memory, GraphQL & HTTP,
JVM Runtime, Errors. Auto-refreshes every 30 seconds. Default window: last 1 hour.

```json
{
  "annotations": { "list": [] },
  "description": "JVM, HTTP, and GraphQL metrics — computing_lab_may_2026",
  "editable": true,
  "fiscalYearStartMonth": 0,
  "graphTooltip": 1,
  "links": [],
  "panels": [
    {
      "collapsed": false,
      "gridPos": { "h": 1, "w": 24, "x": 0, "y": 0 },
      "id": 1,
      "title": "Application Overview",
      "type": "row"
    },
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "thresholds" },
          "thresholds": { "mode": "absolute", "steps": [{ "color": "green", "value": null }] },
          "unit": "dtdhms"
        },
        "overrides": []
      },
      "gridPos": { "h": 3, "w": 6, "x": 0, "y": 1 },
      "id": 2,
      "options": {
        "colorMode": "background",
        "graphMode": "none",
        "justifyMode": "center",
        "orientation": "auto",
        "reduceOptions": { "calcs": ["lastNotNull"], "fields": "", "values": false },
        "textMode": "auto"
      },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "process_uptime_seconds{job=\"computing_lab_may_2026\"}",
          "instant": true,
          "legendFormat": "",
          "refId": "A"
        }
      ],
      "title": "Uptime",
      "type": "stat"
    },
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "thresholds" },
          "thresholds": {
            "mode": "absolute",
            "steps": [
              { "color": "green", "value": null },
              { "color": "orange", "value": 268435456 },
              { "color": "red", "value": 536870912 }
            ]
          },
          "unit": "bytes"
        },
        "overrides": []
      },
      "gridPos": { "h": 3, "w": 6, "x": 6, "y": 1 },
      "id": 3,
      "options": {
        "colorMode": "background",
        "graphMode": "area",
        "justifyMode": "center",
        "orientation": "auto",
        "reduceOptions": { "calcs": ["lastNotNull"], "fields": "", "values": false },
        "textMode": "auto"
      },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "sum(jvm_memory_used_bytes{job=\"computing_lab_may_2026\", area=\"heap\"})",
          "instant": true,
          "legendFormat": "",
          "refId": "A"
        }
      ],
      "title": "Heap Used",
      "type": "stat"
    },
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "thresholds" },
          "max": 100,
          "min": 0,
          "thresholds": {
            "mode": "absolute",
            "steps": [
              { "color": "green", "value": null },
              { "color": "orange", "value": 70 },
              { "color": "red", "value": 90 }
            ]
          },
          "unit": "percent"
        },
        "overrides": []
      },
      "gridPos": { "h": 3, "w": 6, "x": 12, "y": 1 },
      "id": 4,
      "options": {
        "colorMode": "background",
        "graphMode": "area",
        "justifyMode": "center",
        "orientation": "auto",
        "reduceOptions": { "calcs": ["lastNotNull"], "fields": "", "values": false },
        "textMode": "auto"
      },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "process_cpu_usage{job=\"computing_lab_may_2026\"} * 100",
          "instant": true,
          "legendFormat": "",
          "refId": "A"
        }
      ],
      "title": "CPU Usage",
      "type": "stat"
    },
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "thresholds" },
          "thresholds": { "mode": "absolute", "steps": [{ "color": "blue", "value": null }] },
          "unit": "reqps"
        },
        "overrides": []
      },
      "gridPos": { "h": 3, "w": 6, "x": 18, "y": 1 },
      "id": 5,
      "options": {
        "colorMode": "background",
        "graphMode": "area",
        "justifyMode": "center",
        "orientation": "auto",
        "reduceOptions": { "calcs": ["lastNotNull"], "fields": "", "values": false },
        "textMode": "auto"
      },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "sum(rate(http_server_requests_seconds_count{job=\"computing_lab_may_2026\", uri=\"/graphql\"}[1m]))",
          "instant": true,
          "legendFormat": "",
          "refId": "A"
        }
      ],
      "title": "GraphQL Request Rate",
      "type": "stat"
    },
    {
      "collapsed": false,
      "gridPos": { "h": 1, "w": 24, "x": 0, "y": 4 },
      "id": 6,
      "title": "JVM Memory",
      "type": "row"
    },
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "palette-classic" },
          "custom": { "fillOpacity": 10, "lineWidth": 2, "showPoints": "never" },
          "unit": "bytes"
        },
        "overrides": []
      },
      "gridPos": { "h": 8, "w": 12, "x": 0, "y": 5 },
      "id": 7,
      "options": {
        "legend": { "calcs": ["lastNotNull", "max"], "displayMode": "table", "placement": "bottom" },
        "tooltip": { "mode": "multi" }
      },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "sum(jvm_memory_used_bytes{job=\"computing_lab_may_2026\", area=\"heap\"})",
          "legendFormat": "Heap Used",
          "refId": "A"
        },
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "sum(jvm_memory_max_bytes{job=\"computing_lab_may_2026\", area=\"heap\"})",
          "legendFormat": "Heap Max",
          "refId": "B"
        }
      ],
      "title": "Heap Memory",
      "type": "timeseries"
    },
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "palette-classic" },
          "custom": { "fillOpacity": 10, "lineWidth": 2, "showPoints": "never" },
          "unit": "bytes"
        },
        "overrides": []
      },
      "gridPos": { "h": 8, "w": 6, "x": 12, "y": 5 },
      "id": 8,
      "options": {
        "legend": { "calcs": ["lastNotNull"], "displayMode": "table", "placement": "bottom" },
        "tooltip": { "mode": "single" }
      },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "sum(jvm_memory_used_bytes{job=\"computing_lab_may_2026\", area=\"nonheap\"})",
          "legendFormat": "Non-Heap Used",
          "refId": "A"
        }
      ],
      "title": "Non-Heap Memory",
      "type": "timeseries"
    },
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "thresholds" },
          "max": 100,
          "min": 0,
          "thresholds": {
            "mode": "absolute",
            "steps": [
              { "color": "green", "value": null },
              { "color": "orange", "value": 70 },
              { "color": "red", "value": 90 }
            ]
          },
          "unit": "percent"
        },
        "overrides": []
      },
      "gridPos": { "h": 8, "w": 6, "x": 18, "y": 5 },
      "id": 9,
      "options": {
        "minVizHeight": 75,
        "minVizWidth": 75,
        "orientation": "auto",
        "reduceOptions": { "calcs": ["lastNotNull"], "fields": "", "values": false },
        "showThresholdLabels": false,
        "showThresholdMarkers": true
      },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "sum(jvm_memory_used_bytes{job=\"computing_lab_may_2026\", area=\"heap\"}) / sum(jvm_memory_max_bytes{job=\"computing_lab_may_2026\", area=\"heap\"}) * 100",
          "instant": true,
          "legendFormat": "Heap Utilization",
          "refId": "A"
        }
      ],
      "title": "Heap Utilization",
      "type": "gauge"
    },
    {
      "collapsed": false,
      "gridPos": { "h": 1, "w": 24, "x": 0, "y": 13 },
      "id": 10,
      "title": "GraphQL & HTTP",
      "type": "row"
    },
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "palette-classic" },
          "custom": { "fillOpacity": 5, "lineWidth": 2, "showPoints": "never" },
          "unit": "reqps"
        },
        "overrides": []
      },
      "gridPos": { "h": 8, "w": 8, "x": 0, "y": 14 },
      "id": 11,
      "options": {
        "legend": { "calcs": ["mean", "max"], "displayMode": "table", "placement": "bottom" },
        "tooltip": { "mode": "multi" }
      },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "sum(rate(http_server_requests_seconds_count{job=\"computing_lab_may_2026\", uri=\"/graphql\"}[1m]))",
          "legendFormat": "Requests/s",
          "refId": "A"
        }
      ],
      "title": "GraphQL Request Rate",
      "type": "timeseries"
    },
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "palette-classic" },
          "custom": { "lineWidth": 2, "showPoints": "never" },
          "unit": "s"
        },
        "overrides": []
      },
      "gridPos": { "h": 8, "w": 8, "x": 8, "y": 14 },
      "id": 12,
      "options": {
        "legend": { "calcs": ["lastNotNull", "max"], "displayMode": "table", "placement": "bottom" },
        "tooltip": { "mode": "multi" }
      },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "histogram_quantile(0.50, sum by(le) (rate(http_server_requests_seconds_bucket{job=\"computing_lab_may_2026\", uri=\"/graphql\"}[5m])))",
          "legendFormat": "p50",
          "refId": "A"
        },
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "histogram_quantile(0.95, sum by(le) (rate(http_server_requests_seconds_bucket{job=\"computing_lab_may_2026\", uri=\"/graphql\"}[5m])))",
          "legendFormat": "p95",
          "refId": "B"
        },
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "histogram_quantile(0.99, sum by(le) (rate(http_server_requests_seconds_bucket{job=\"computing_lab_may_2026\", uri=\"/graphql\"}[5m])))",
          "legendFormat": "p99",
          "refId": "C"
        }
      ],
      "title": "Response Time Percentiles",
      "type": "timeseries"
    },
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "palette-classic" },
          "unit": "short"
        },
        "overrides": []
      },
      "gridPos": { "h": 8, "w": 8, "x": 16, "y": 14 },
      "id": 13,
      "options": {
        "displayLabels": ["percent"],
        "legend": { "displayMode": "table", "placement": "right", "values": ["value", "percent"] },
        "pieType": "donut",
        "tooltip": { "mode": "single" }
      },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "sum by(status) (increase(http_server_requests_seconds_count{job=\"computing_lab_may_2026\", uri=\"/graphql\"}[$__range]))",
          "legendFormat": "HTTP {{status}}",
          "refId": "A"
        }
      ],
      "title": "Requests by HTTP Status",
      "type": "piechart"
    },
    {
      "collapsed": false,
      "gridPos": { "h": 1, "w": 24, "x": 0, "y": 22 },
      "id": 14,
      "title": "JVM Runtime",
      "type": "row"
    },
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "palette-classic" },
          "custom": { "lineWidth": 2, "showPoints": "never" },
          "max": 100,
          "min": 0,
          "unit": "percent"
        },
        "overrides": []
      },
      "gridPos": { "h": 8, "w": 8, "x": 0, "y": 23 },
      "id": 15,
      "options": {
        "legend": { "calcs": ["mean", "max"], "displayMode": "table", "placement": "bottom" },
        "tooltip": { "mode": "multi" }
      },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "process_cpu_usage{job=\"computing_lab_may_2026\"} * 100",
          "legendFormat": "Process CPU",
          "refId": "A"
        },
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "system_cpu_usage{job=\"computing_lab_may_2026\"} * 100",
          "legendFormat": "System CPU",
          "refId": "B"
        }
      ],
      "title": "CPU Usage",
      "type": "timeseries"
    },
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "palette-classic" },
          "custom": { "fillOpacity": 10, "lineWidth": 2, "showPoints": "never" },
          "unit": "s"
        },
        "overrides": []
      },
      "gridPos": { "h": 8, "w": 8, "x": 8, "y": 23 },
      "id": 16,
      "options": {
        "legend": { "calcs": ["mean", "max"], "displayMode": "table", "placement": "bottom" },
        "tooltip": { "mode": "multi" }
      },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "rate(jvm_gc_pause_seconds_sum{job=\"computing_lab_may_2026\"}[1m])",
          "legendFormat": "{{action}} {{cause}}",
          "refId": "A"
        }
      ],
      "title": "GC Pause Duration",
      "type": "timeseries"
    },
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "palette-classic" },
          "custom": { "lineWidth": 2, "showPoints": "never" },
          "unit": "short"
        },
        "overrides": []
      },
      "gridPos": { "h": 8, "w": 8, "x": 16, "y": 23 },
      "id": 17,
      "options": {
        "legend": { "calcs": ["lastNotNull", "max"], "displayMode": "table", "placement": "bottom" },
        "tooltip": { "mode": "multi" }
      },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "jvm_threads_live_threads{job=\"computing_lab_may_2026\"}",
          "legendFormat": "Live",
          "refId": "A"
        },
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "jvm_threads_daemon_threads{job=\"computing_lab_may_2026\"}",
          "legendFormat": "Daemon",
          "refId": "B"
        },
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "jvm_threads_peak_threads{job=\"computing_lab_may_2026\"}",
          "legendFormat": "Peak",
          "refId": "C"
        }
      ],
      "title": "JVM Threads",
      "type": "timeseries"
    },
    {
      "collapsed": false,
      "gridPos": { "h": 1, "w": 24, "x": 0, "y": 31 },
      "id": 18,
      "title": "Errors",
      "type": "row"
    },
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "palette-classic" },
          "custom": { "fillOpacity": 10, "lineWidth": 2, "showPoints": "never" },
          "unit": "reqps"
        },
        "overrides": [
          {
            "matcher": { "id": "byName", "options": "4xx" },
            "properties": [{ "id": "color", "value": { "fixedColor": "orange", "mode": "fixed" } }]
          },
          {
            "matcher": { "id": "byName", "options": "5xx" },
            "properties": [{ "id": "color", "value": { "fixedColor": "red", "mode": "fixed" } }]
          }
        ]
      },
      "gridPos": { "h": 8, "w": 12, "x": 0, "y": 32 },
      "id": 19,
      "options": {
        "legend": { "calcs": ["mean", "max"], "displayMode": "table", "placement": "bottom" },
        "tooltip": { "mode": "multi" }
      },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "sum(rate(http_server_requests_seconds_count{job=\"computing_lab_may_2026\", status=~\"4..\"}[1m]))",
          "legendFormat": "4xx",
          "refId": "A"
        },
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "sum(rate(http_server_requests_seconds_count{job=\"computing_lab_may_2026\", status=~\"5..\"}[1m]))",
          "legendFormat": "5xx",
          "refId": "B"
        }
      ],
      "title": "HTTP Error Rate",
      "type": "timeseries"
    },
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "palette-classic" },
          "unit": "short"
        },
        "overrides": []
      },
      "gridPos": { "h": 8, "w": 12, "x": 12, "y": 32 },
      "id": 20,
      "options": {
        "displayLabels": ["name", "percent"],
        "legend": { "displayMode": "table", "placement": "right", "values": ["value", "percent"] },
        "pieType": "pie",
        "tooltip": { "mode": "single" }
      },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "sum by(status) (increase(http_server_requests_seconds_count{job=\"computing_lab_may_2026\", status=~\"4..|5..\"}[$__range]))",
          "legendFormat": "HTTP {{status}}",
          "refId": "A"
        }
      ],
      "title": "Error Distribution",
      "type": "piechart"
    }
  ],
  "refresh": "30s",
  "schemaVersion": 39,
  "tags": ["spring-boot", "jvm", "graphql"],
  "templating": { "list": [] },
  "time": { "from": "now-1h", "to": "now" },
  "timepicker": {},
  "timezone": "browser",
  "title": "Computing Lab — Spring Boot Overview",
  "uid": "computing-lab-overview",
  "version": 1
}
```

---

## Dashboard panels reference

| Row | Panel | Type | What it shows |
|---|---|---|---|
| Application Overview | Uptime | Stat | `process_uptime_seconds` formatted as days/hours/min |
| Application Overview | Heap Used | Stat | Current heap consumption; orange >256 MB, red >512 MB |
| Application Overview | CPU Usage | Stat | Process CPU %; orange >70, red >90 |
| Application Overview | GraphQL Request Rate | Stat | Requests/s to `/graphql` over the last 1 m |
| JVM Memory | Heap Memory | Time series | Heap used vs heap max over time |
| JVM Memory | Non-Heap Memory | Time series | Metaspace + code cache over time |
| JVM Memory | Heap Utilization | Gauge | Heap used/max %; thresholds at 70 and 90 |
| GraphQL & HTTP | GraphQL Request Rate | Time series | req/s to `/graphql` |
| GraphQL & HTTP | Response Time Percentiles | Time series | p50 / p95 / p99 latency in seconds |
| GraphQL & HTTP | Requests by HTTP Status | Donut | Count of 2xx/4xx/5xx over the selected window |
| JVM Runtime | CPU Usage | Time series | Process CPU % and system CPU % overlaid |
| JVM Runtime | GC Pause Duration | Time series | GC pause seconds/s by action and cause |
| JVM Runtime | JVM Threads | Time series | Live, daemon, and peak thread counts |
| Errors | HTTP Error Rate | Time series | 4xx and 5xx req/s — orange and red series |
| Errors | Error Distribution | Pie | Proportion of each error status over the selected window |

---

## Port assignments

| Service | Host port | Container port |
|---|---|---|
| Spring Boot app | 8080 | 8080 |
| Prometheus | 9090 | 9090 |
| Grafana | 3000 | 3000 |

---

## Persistence strategy

| Data | Volume | What is preserved |
|---|---|---|
| Prometheus metrics | `prometheus-data` | Raw time-series data, WAL |
| Grafana state | `grafana-data` | UI-created dashboards, annotation history, sessions |
| Grafana datasource | bind-mount (`:ro`) | Provisioned from source control on every startup |
| Grafana dashboard JSON | bind-mount (`:ro`) | Provisioned from source control on every startup |

Volumes survive `docker compose down` but are removed by `docker compose down -v`. A fresh
environment has an empty Prometheus history but a fully wired, immediately usable Grafana
thanks to the provisioning bind-mounts.

---

## File tree after implementation

```
.devcontainer/
  docker-compose.yml                          ← modified: two new services, two new volumes
  prometheus/
    prometheus.yml                            ← new
  grafana/
    provisioning/
      datasources/
        prometheus.yml                        ← new (uid: prometheus required by dashboard JSON)
      dashboards/
        provider.yml                          ← new
    dashboards/
      computing-lab-overview.json             ← new (ready-to-use dashboard, 20 panels)
```

---

## Verification steps (for the implementation task)

1. `docker compose up` — all three services reach running state.
2. `curl http://localhost:9090/targets` — `computing_lab_may_2026` job shows state `UP`.
3. Open `http://localhost:3000` — lands directly on the home page, no login prompt.
4. Navigate to Dashboards → "Computing Lab — Spring Boot Overview" — all 20 panels load.
5. Send a request: `curl -s -X POST http://localhost:8080/graphql -H 'Content-Type: application/json' -d '{"query":"{ add(a: 3, b: 4) }"}'` — within 30 s the request rate and response time panels update.
6. `docker compose down && docker compose up` — dashboard and datasource are present immediately; Prometheus history is intact.
