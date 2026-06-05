# Observability for Developers

A practical guide to measuring non-functional behaviour in this project —
what to instrument, when to do it, and how to read the results.

---

## 1. Why measure non-functional requirements _while_ you build

A story or bug fix changes the behaviour of running code. That change has two
dimensions: **what** the code does (correctness) and **how well** it does it
(performance, reliability, resource consumption). Both dimensions can regress,
and both regressions hurt users.

The practical problem with deferring non-functional measurement to a "hardening
sprint" or a separate performance track is that context is gone by then. The
developer who introduced the regression has moved on; the change is buried inside
three other merges; and reproducing the load conditions from weeks ago is
guesswork. Fixing it costs far more than catching it at the pull-request stage.

There are concrete, daily reasons to measure early:

- **Latency regressions are invisible without a baseline.** A new GraphQL
  resolver that takes 120 ms instead of 12 ms looks like a green build and
  passes every unit test. The p95 panel in Grafana makes it visible immediately.

- **Memory leaks surface under sustained load, not under a single test run.**
  Running the load generator for two minutes while watching the Heap Memory
  chart is a routine check that costs nothing and catches leaks before they
  reach production.

- **Thread and connection-pool exhaustion happens at the integration boundary.**
  No unit test exercises the Tomcat connector, the HikariCP pool, or the JVM
  thread scheduler simultaneously. The JVM Threads panel and the request rate
  panel together tell you whether a concurrency change is safe.

- **Documenting the baseline is part of the definition of done.** A ticket that
  says "response time unchanged" is more defensible than one that says "tests
  pass". If the Jira ticket carries a before/after screenshot from Grafana,
  reviewers and QA have evidence, not assertions.

The overhead is low. The load generator runs in under two minutes. Grafana
updates every 30 seconds. The cost of not doing it is measured in incidents.

---

## 2. Why this is a developer responsibility, not DevOps

Observability has historically been handed off to operations teams because the
tooling was heavyweight and the setup was manual. That model has two failure
modes: the metrics that get instrumented are generic (JVM uptime, heap) rather
than specific to the business domain, and the feedback loop is measured in
hours, not minutes.

In this project the full observability stack — Prometheus, Loki, Grafana — runs
locally inside the devcontainer, wired up and ready from the first `docker
compose up`. There is no ticket to raise, no environment to request, no
configuration to ask someone else to apply. The developer is the first person
with both the context (what the code just changed) and the tool (Grafana open
in a browser tab) at the same moment.

Concretely, this means:

- **You own the metrics for the code you write.** If a new domain service has a
  path that could be expensive, add a timer or a counter via Micrometer. Don't
  wait for someone else to discover that it is slow.
- **You own the interpretation.** A DevOps engineer reading a Grafana panel after
  the fact cannot tell whether a 40 ms p99 is a regression or expected. You can,
  because you know what the code does.
- **You own the escalation.** If something looks wrong in the dashboard after
  your change, it is your signal to investigate — not a separate team's alert to
  file.

The DevOps team still manages production infrastructure, alerting thresholds,
and long-term retention. But the first measurement of whether a change is safe
belongs in the pull request, not in a post-deploy incident review.

---

## 3. The Grafana dashboard

Open `http://localhost:3000` — no login is required in the devcontainer. Navigate
to **Dashboards → Computing Lab — Spring Boot Overview**. The dashboard polls
Prometheus every 30 seconds and defaults to the last hour.

The Prometheus scrape target is `http://app:8080/actuator/prometheus`, polled
every 15 seconds. All panels below are scoped to `job="computing_lab_may_2026"`.

### Row 1 — Application Overview (stat panels)

These are single-value indicators at the top of the dashboard. They give a
health snapshot without requiring any time-series context.

| Panel | Metric | What it measures | What to watch |
|---|---|---|---|
| **Uptime** | `process_uptime_seconds` | How long the JVM process has been running, formatted as days/hours/min | A reset to 0 means the app crashed or was restarted; investigate if unexpected |
| **Heap Used** | `sum(jvm_memory_used_bytes{area="heap"})` | Current heap consumption in bytes | Orange background >256 MB, red >512 MB; a value near the red threshold warrants a heap dump or review of object retention |
| **CPU Usage** | `process_cpu_usage * 100` | CPU time consumed by the JVM process as a percentage | Orange >70 %, red >90 %; sustained high CPU under normal load indicates a hot loop or excessive GC |
| **GraphQL Request Rate** | `rate(http_server_requests_seconds_count{uri="/graphql"}[1m])` | Requests per second to the `/graphql` endpoint over the last minute | Confirms that the load generator or manual traffic is actually reaching the app; 0 means nothing is hitting it |

### Row 2 — JVM Memory (time series + gauge)

| Panel | Metric(s) | What it measures | What to watch |
|---|---|---|---|
| **Heap Memory** | `jvm_memory_used_bytes{area="heap"}` vs `jvm_memory_max_bytes{area="heap"}` | Heap used and max over time | The used line climbing toward the max line without a sawtooth pattern (GC reclaim) is the signature of a memory leak |
| **Non-Heap Memory** | `jvm_memory_used_bytes{area="nonheap"}` | Metaspace + JIT code cache | Slow, unbounded growth here points to classloader leaks or excessive dynamic code generation |
| **Heap Utilization** | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100` | Heap used as a percentage of the configured max | Gauge thresholds at 70 % (orange) and 90 % (red); anything above 80 % under sustained load is a risk of `OutOfMemoryError` |

### Row 3 — GraphQL & HTTP (time series + donut)

| Panel | Metric(s) | What it measures | What to watch |
|---|---|---|---|
| **GraphQL Request Rate** | `rate(http_server_requests_seconds_count{uri="/graphql"}[1m])` | Throughput over time | Use this alongside the load generator's reported req/s to confirm the server is receiving what the client is sending; a gap means dropped connections |
| **Response Time Percentiles** | `histogram_quantile(0.50/0.95/0.99, ...)` on `http_server_requests_seconds_bucket` | p50, p95, p99 latency in seconds | p50 is the typical user experience. p95 and p99 catch tail latency that averages hide. A p99 that is 10× the p50 under load suggests thread contention or GC pauses |
| **Requests by HTTP Status** | `increase(http_server_requests_seconds_count{...}[$__range])` grouped by `status` | Count of 2xx / 4xx / 5xx over the selected time window | Healthy traffic is almost entirely 2xx. A slice of 4xx indicates client errors (bad input, schema violations). Any 5xx slice is a server-side failure to investigate |

> **Note on SLOs.** The application publishes latency service-level objectives defined in
> `application.properties` at `1 ms, 5 ms, 10 ms, 25 ms, 50 ms, 100 ms`. These bucket
> boundaries feed the histogram used by the percentile panels. Treat 100 ms as the informal
> upper bound for a single GraphQL arithmetic query under normal load.

### Row 4 — JVM Runtime (time series)

| Panel | Metric(s) | What it measures | What to watch |
|---|---|---|---|
| **CPU Usage** | `process_cpu_usage` and `system_cpu_usage` | JVM process CPU and total system CPU, both as % over time | If process CPU is high but system CPU is low, the JVM is busy. If both are high the host is saturated. After a change, watch for sustained CPU increase relative to your pre-change baseline |
| **GC Pause Duration** | `rate(jvm_gc_pause_seconds_sum[1m])` grouped by `action` and `cause` | Time spent in GC pauses per second, broken out by collection type and trigger | Under load, minor GC pauses should be sub-millisecond. Frequent major (full) GC pauses that coincide with latency spikes confirm that memory pressure is the root cause |
| **JVM Threads** | `jvm_threads_live_threads`, `jvm_threads_daemon_threads`, `jvm_threads_peak_threads` | Active, background, and historical maximum thread counts | This app runs on virtual threads (`spring.threads.virtual.enabled=true`), so live thread count can be high. Watch the **peak** line: if it climbs monotonically it suggests threads are being created but not returned |

### Row 5 — Errors (time series + pie)

| Panel | Metric(s) | What it measures | What to watch |
|---|---|---|---|
| **HTTP Error Rate** | `rate(http_server_requests_seconds_count{status=~"4.."})` and `rate(...)status=~"5..")` | 4xx and 5xx request rate per second, orange and red series | A spike that coincides exactly with a deployment or a change in load is a regression signal. Steady-state 4xx from the load generator is expected (input validation). Any 5xx is not |
| **Error Distribution** | `increase(http_server_requests_seconds_count{status=~"4..\|5.."}[$__range])` | Proportional breakdown of error statuses over the selected window | The donut shows whether errors are concentrated in one status code (e.g. all 400) or spread (400 and 500 both spiking), which narrows the investigation |

---

## 4. Performance scripts — running and reading them

### The load generator

`scripts/graphql-load.py` is a multi-threaded load generator that sends random
arithmetic operations (add, subtract, multiply, divide) to the GraphQL endpoint.
Its concurrency model mirrors JMeter's: `-c` threads, each sending `-r` requests,
for a total of `r × c` requests.

**Prerequisites**

```bash
# Python 3.11+ (pre-installed in the devcontainer)
python3 --version
```

No third-party libraries are required; only the standard library is used.

**Basic run — warm-up and baseline**

```bash
# 50 threads × 200 requests = 10 000 total. ~15-30 s depending on host speed.
python3 scripts/graphql-load.py -r 200 -c 50
```

Use this run before starting work on a story to capture your pre-change baseline.
Take a screenshot of the Grafana Response Time Percentiles and Heap Memory panels
covering this window.

**Sustained load run — detect leaks and saturation**

```bash
# 60 threads × 1000 requests = 60 000 total. ~1-3 min.
python3 scripts/graphql-load.py -r 1000 -c 60
```

Watch the Heap Memory chart continuously during this run. A healthy JVM shows a
sawtooth pattern (GC reclaiming between allocations). A straight upward climb
means something is holding references.

**Targeted endpoint**

```bash
# Point at a non-default host, e.g. a staging container
python3 scripts/graphql-load.py -r 500 -c 40 -e http://staging-host:8080/graphql
```

**All options**

```
  -e, --endpoint      URL to the /graphql endpoint  (default: http://localhost:8080/graphql)
  -r, --requests      Requests per thread            (default: 1000)
  -c, --concurrency   Number of threads              (default: 50)
  -t, --timeout       Per-request timeout in seconds (default: 10)
  -z, --timezone      IANA timezone for timestamps   (default: system local)
```

### Reading the script output

```
==================================================
  GraphQL Arithmetic Load Generator  [Python]
==================================================
  Endpoint:          http://localhost:8080/graphql
  Threads:           60
  Requests/thread:   1000
  Total requests:    60000
  Timezone:          system local
  Operations:        add, subtract, multiply, divide

  [#########################]  60000/60000  100%  done!

==================================================
  Started:     2026-06-05 14:22:01 CEST
  Finished:    2026-06-05 14:23:44 CEST
  OK 200:      60000
  Non-200:     0
  Duration:    103.1s  (~582 req/s)
  Operations:
    add          15123  (25%)
    subtract     15041  (25%)
    multiply     14919  (24%)
    divide       14917  (24%)
==================================================
```

| Field | Meaning |
|---|---|
| `OK 200` | HTTP 200 responses. GraphQL wraps application errors inside a 200 body, so this measures transport-level success, not GraphQL-level correctness |
| `Non-200` | Any non-200 response (timeout, 5xx, connection refused). Any non-zero value here under a normal run is a server-side problem |
| `~N req/s` | Achieved throughput. Compare this to the **GraphQL Request Rate** panel in Grafana — they should be within 10 % of each other. A large gap means requests are being dropped or the Prometheus scrape is lagging |
| Operations breakdown | Confirms the four arithmetic operations are being exercised roughly evenly. Divide uses `b ≥ 1` to avoid division-by-zero, so `DivisionByZeroException` paths are not exercised by this generator |

### Correlating script output with Grafana

1. Note the **Started** and **Finished** timestamps from the script output.
2. In Grafana, set the time range to cover those two timestamps (use the
   time picker in the top-right corner, or type the absolute times directly).
3. The **Response Time Percentiles** panel shows p50/p95/p99 for exactly
   that window. These are your post-change numbers.
4. Compare them to your pre-change baseline screenshot.

**Reading the percentile panel**

- **p50 (median)**: Half of all requests completed faster than this. For simple
  arithmetic over GraphQL it should be in the single-digit millisecond range on
  a warm JVM.
- **p95**: 95 % of requests were faster than this. A p95 more than ~3× the p50
  indicates tail latency worth investigating.
- **p99**: 99 % of requests were faster than this. p99 spikes that align with
  GC Pause Duration spikes confirm GC as the cause. p99 spikes without
  corresponding GC spikes suggest thread contention or connection pool saturation.

**A regression has occurred if:**
- p99 increases by more than 2× relative to the baseline under the same load.
- The Heap Memory chart shows a monotonically increasing line (no sawtooth).
- `Non-200` count is non-zero.
- CPU Usage stays near 100 % after the load ends (runaway work, not just
  absorbing the load).

---

## 5. Reporting results in Jira

Every story or bug fix that touches the domain or infrastructure layer should
carry a measurement comment before merge. This is not a separate task — it is
part of the acceptance criteria for any change that could affect latency,
throughput, or resource consumption.

### Workflow

1. **Record your environment** — do this once per machine. Numbers are only comparable
   when both runs happen on the same machine with the same Docker limits.
2. **Run the load generator before your change** — note the `Started` / `Finished`
   timestamps printed by the script, then set Grafana's time picker to that exact window.
3. **Apply your change.**
4. **Run the load generator again** with identical `-r` and `-c` flags.
5. **Fill in the After column** and compute Delta.
6. **Paste the filled tables** into the Jira ticket as a comment and attach screenshots.

**Delta = After − Before.**
A negative delta on latency (p50/p95/p99) means the system got faster — that is an improvement.
A negative delta on throughput (req/s) means fewer requests per second — that is a regression.
For resources, watch the direction: a heap peak that grows under the same load is the primary
warning sign of a memory leak.

### Minimum required comment

Copy the tables below into the Jira ticket, fill in every cell, and attach Grafana screenshots.
Each metric row tells you exactly where to read the number — no need to look anywhere else.

---

#### Observability check — \[Story/Bug ID\]

**Change:** _one sentence describing what changed_  
**Load run:** `python3 scripts/graphql-load.py -r <N> -c <M>` → `<total>` requests  
**Profiles active:** `dev,loki`

#### Environment

| Setting | How to find it | Value |
|---|---|---|
| Host machine | Model and CPU of your laptop/desktop (the host, not the container) — e.g. _MacBook Pro M3 Max_, _ThinkPad X1 Carbon i7-1185G7_ | |
| Host RAM | Total physical RAM. macOS: Apple menu → About This Mac. Windows: Task Manager → Performance. Linux: `dmidecode -t memory \| grep Size` | |
| Docker CPUs allocated | Docker Desktop → Settings → Resources → CPUs. Confirm inside the devcontainer with `nproc` | |
| Docker memory allocated | Docker Desktop → Settings → Resources → Memory. Confirm inside the devcontainer with `free -h` | |
| OS / runtime | Host OS and version. Windows users: include WSL2 kernel version (`uname -r` inside WSL), as the virtualisation layer affects scheduler and I/O behaviour | |

> Both runs (Before and After) were performed on this machine with the same Docker limits.

#### Measurements

| Metric | Where to read it | Before | After | Delta (After − Before) |
|---|---|---|---|---|
| **Throughput — req/s** | Script output → last block → `Duration: Xs (~N req/s)`. Use the `N` value | | | ↑ positive = more capacity |
| **p50 latency (ms)** | Grafana → _GraphQL & HTTP_ row → **Response Time Percentiles** panel → p50 curve. Hover at end of run. Panel unit is seconds; multiply × 1 000 for ms | | | ↓ negative = faster |
| **p95 latency (ms)** | Same panel, p95 curve | | | ↓ negative = faster |
| **p99 latency (ms)** | Same panel, p99 curve | | | ↓ negative = faster |
| **Heap peak (MB)** | Grafana → _JVM Memory_ row → **Heap Memory** time series → highest point of the _Heap Used_ line during the run window. Hover tooltip shows the exact value | | | Growing peak under same load = leak risk |
| **Heap utilization %** | Grafana → _JVM Memory_ row → **Heap Utilization** gauge → value at end of run | | | |
| **CPU peak %** | Grafana → _JVM Runtime_ row → **CPU Usage** time series → highest point of the _Process CPU_ line during the run window | | | |

#### Errors

| Metric | Where to read it | Before | After |
|---|---|---|---|
| **Non-200 responses** | Script output → last block → `Non-200: <count>`. Expected: `0` | | |
| **5xx rate (req/s)** | Grafana → _Errors_ row → **HTTP Error Rate** panel → peak of the red **5xx** series during the run window. If flat, write `0` | | |

#### Assessment

- [ ] No regression — p99 delta ≤ 2× baseline, heap peak stable, zero 5xx
- [ ] Regression found — see notes below

**Notes / Grafana screenshots:**  
_Attach before/after screenshots of the Response Time Percentiles and Heap Memory panels.
Use Grafana's absolute time range (set to the script's `Started` / `Finished` timestamps)
so the panels show only the load window, without idle-period noise._

---

### What reviewers look for

Reviewers checking an observability comment are looking for three things:

1. **Completeness** — both a before and an after measurement taken under the same
   load parameters.
2. **Honesty** — a regression flagged in the "Assessment" section with a note is
   better than a blank or missing comment. It starts the conversation.
3. **Evidence** — a Grafana screenshot is concrete. A statement like "felt fast"
   is not.

If a story is purely documentation or test-only with no executable path changed,
write `N/A — no runtime code changed` in the comment. Do not skip the comment
entirely; that makes it impossible to distinguish a skipped check from a
deliberate decision.

---

## 6. Adding new metrics as you build

Micrometer is on the classpath and auto-configured by Spring Boot. Any new domain
or application service can emit custom metrics without modifying the Grafana
dashboard JSON — add a panel after the fact once you have confirmed the metric
is useful.

### Counter (how many times X happened)

```java
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Component
@RequiredArgsConstructor
public class MyDomainService {

    private final MeterRegistry registry;

    public void doSomething() {
        // ... business logic ...
        registry.counter("my_domain.something.total", "operation", "doSomething").increment();
    }
}
```

Query in Grafana/Prometheus: `rate(my_domain_something_total[1m])`

### Timer (how long X took)

```java
import io.micrometer.core.instrument.Timer;

Timer timer = Timer.builder("my_domain.operation.duration")
    .tag("operation", "doSomething")
    .register(registry);

timer.record(() -> doExpensiveOperation());
```

Query: `histogram_quantile(0.95, rate(my_domain_operation_duration_seconds_bucket[5m]))`

### Gauge (current value of X)

```java
import io.micrometer.core.instrument.Gauge;
import java.util.concurrent.atomic.AtomicInteger;

AtomicInteger queueDepth = new AtomicInteger(0);

Gauge.builder("my_domain.queue.depth", queueDepth, AtomicInteger::get)
    .register(registry);
```

Query: `my_domain_queue_depth`

After adding a metric, run `curl http://localhost:8080/actuator/prometheus | grep my_domain`
to confirm it is being exported before adding a Grafana panel.

---

## 7. Quick-reference — services and endpoints

| Service | Local URL | Purpose |
|---|---|---|
| Application (GraphQL) | `http://localhost:8080/graphql` | The API under test |
| GraphiQL IDE | `http://localhost:8080/graphiql` | Interactive query explorer (dev profile only) |
| Prometheus metrics | `http://localhost:8080/actuator/prometheus` | Raw metric export |
| Prometheus UI | `http://localhost:9090` | Ad-hoc PromQL queries and target status |
| Grafana | `http://localhost:3000` | Dashboards (no login required) |
| Loki API | `http://localhost:3100` | Structured log store |

All services run inside the devcontainer and are accessible from the host on the
ports above. They share `dev-network` and communicate using internal hostnames
(`app`, `prometheus`, `loki`, `grafana`).

### Checking Prometheus scrape health

```bash
# Should show computing_lab_may_2026 job with state="UP"
curl -s http://localhost:9090/api/v1/targets | python3 -m json.tool | grep -A3 '"state"'
```

### Querying Loki directly

```bash
END=$(date +%s)000000000
START=$(( $(date +%s) - 300 ))000000000
curl -s -G "http://localhost:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={service="computing_lab_may_2026"} |= "GRAPHQL_REQUEST"' \
  --data-urlencode "start=$START" \
  --data-urlencode "end=$END" \
  --data-urlencode "limit=10"
```

See [LOGGING.md](../LOGGING.md) for the full Loki/Logback wiring and how to add
new Loki-only loggers.
