# Computing Lab — May 2026

A growing collection of computing exercises implemented in Java with Spring Boot, following **Domain-Driven Design (DDD)** architecture. All features are exposed exclusively through a **GraphQL API**.

## Architecture

This project follows Domain-Driven Design principles with a clear separation of concerns:

```
src/main/java/at/mavila/computing_lab_may_2026/
├── domain/                          # Domain Layer — Core Business Logic
│   └── arithmetic/                  # Calculator domain
│       ├── Calculator               # BigDecimal arithmetic operations
│       └── exception/
│           └── DivisionByZeroException
├── application/                     # Application Layer — Use Cases
│   └── arithmetic/                  # Mirrors domain structure
│       └── CalculatorService        # Delegates to domain, no business logic
└── infrastructure/                  # Infrastructure Layer — External Concerns
    ├── config/
    │   └── GraphQLConfig            # GraphQL scalar configuration
    └── web/graphql/
        ├── ArithmeticController     # @QueryMapping resolvers
        └── GraphQLExceptionHandler  # Structured error responses
```

### Layer Responsibilities

| Layer              | Responsibility                                                                                   |
| ------------------ | ------------------------------------------------------------------------------------------------ |
| **Domain**         | Core business logic, algorithms, domain models, and exceptions. No dependencies on outer layers. |
| **Application**    | Use case orchestration, coordinates domain services, handles null defaulting.                    |
| **Infrastructure** | External concerns: web adapters (GraphQL), configuration, persistence, messaging.                |

## Table of Contents

1. [Calculator](#1-calculator)
2. [GraphQL API](#2-graphql-api)
3. [Testing Quality](#3-testing-quality)
4. [Observability](#4-observability)

---

## 1. Calculator

**Location:** `domain/arithmetic/Calculator.java`

### Problem

Perform the four basic arithmetic operations — addition, subtraction, multiplication, and division — with arbitrary-precision decimal numbers.

### Operations

| Operation      | Method       | Description                                               |
| -------------- | ------------ | --------------------------------------------------------- |
| Addition       | `add`        | Returns `a + b`                                           |
| Subtraction    | `subtract`   | Returns `a - b`                                           |
| Multiplication | `multiply`   | Returns `a * b`                                           |
| Division       | `divide`     | Returns `a / b` rounded to 10 decimal places (HALF_UP)   |

### Algorithm

All operations delegate directly to `BigDecimal` methods. Division includes a zero-guard:

```
────────────────────────────────────────────────────
  INPUT: a (BigDecimal), b (BigDecimal)
────────────────────────────────────────────────────
                        │
                        ▼
────────────────────────────────────────────────────
  Is b == 0?
  YES → throw DivisionByZeroException
  NO  → return a.divide(b, 10, HALF_UP)
────────────────────────────────────────────────────
                        │
                        ▼
────────────────────────────────────────────────────
  OUTPUT: BigDecimal result
────────────────────────────────────────────────────
```

Null inputs are rejected at the boundary by Jakarta `@NotNull` + `@Validated` before the method body runs, so no manual null guard is needed inside `divide`.

### Why BigDecimal?

`double` and `float` cannot represent many decimal fractions exactly (e.g., `0.1 + 0.2 ≠ 0.3` in IEEE 754). `BigDecimal` guarantees exact decimal representation and controlled rounding, making it suitable for financial and scientific computations.

### Complexity

- **Time:** O(n) where n is the number of significant digits
- **Space:** O(n) for the result representation

### Examples

| Operation         | Input         | Output                   |
| ----------------- | ------------- | ------------------------ |
| Addition          | 1.5 + 2.5     | 4.0                      |
| Subtraction       | 10 − 3.7      | 6.3                      |
| Multiplication    | 3.14 × 2      | 6.28                     |
| Division          | 10 ÷ 3        | 3.3333333333             |
| Division by zero  | 5 ÷ 0         | DivisionByZeroException  |

### Edge Cases

| Case              | Input     | Result                  | Notes                                    |
| ----------------- | --------- | ----------------------- | ---------------------------------------- |
| Division by zero  | a=5, b=0  | Error                   | DivisionByZeroException thrown           |
| Null operand      | a=null    | Error                   | Jakarta `@NotNull` constraint violation  |
| Exact result      | 1 ÷ 4     | 0.2500000000            | 10 decimal places, trailing zeros kept  |
| Large numbers     | 9999999999999999 × 2 | 19999999999999998 | No overflow — arbitrary precision      |
| Negative numbers  | −5 + 3    | −2                      | Sign handled natively by BigDecimal      |

---

## 2. GraphQL API

The only API is GraphQL. There are no REST controllers.

### Endpoints

| Endpoint    | Description              |
| ----------- | ------------------------ |
| `/graphql`  | GraphQL API endpoint     |
| `/graphiql` | Interactive GraphQL IDE  |

### Schema

```graphql
type Query {
    add(a: BigDecimal!, b: BigDecimal!): BigDecimal!
    subtract(a: BigDecimal!, b: BigDecimal!): BigDecimal!
    multiply(a: BigDecimal!, b: BigDecimal!): BigDecimal!
    divide(a: BigDecimal!, b: BigDecimal!): BigDecimal!
}

scalar BigDecimal
```

### Example Queries

#### Addition

```graphql
query {
    add(a: 1.5, b: 2.5)
}
```

**Response:**

```json
{ "data": { "add": 4.0 } }
```

#### Division

```graphql
query {
    divide(a: 10, b: 3)
}
```

**Response:**

```json
{ "data": { "divide": 3.3333333333 } }
```

### Error Handling

#### Division by Zero

```graphql
query {
    divide(a: 5, b: 0)
}
```

**Response:**

```json
{
    "errors": [{
        "message": "Division by zero is not allowed",
        "extensions": { "errorCode": "DIVISION_BY_ZERO", "classification": "BAD_REQUEST" }
    }]
}
```

#### Null Argument

```graphql
query {
    divide(a: null, b: 3)
}
```

**Response:**

```json
{
    "errors": [{
        "message": "Invalid input: argument must not be null",
        "extensions": { "errorCode": "INVALID_ARGUMENT", "reason": "argument must not be null", "classification": "BAD_REQUEST" }
    }]
}
```

### Using cURL

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "{ add(a: 1.5, b: 2.5) }"}'
```

---

## 3. Testing Quality

This project uses two complementary tools to measure test quality. They answer different questions and must be read together.

| Tool       | Question answered                              | Report location                             |
| ---------- | ---------------------------------------------- | ------------------------------------------- |
| **JaCoCo** | Were these lines/branches *executed* by tests? | `build/reports/jacoco/test/html/index.html` |
| **Pitest** | Did the tests *detect a behavioral change*?    | `build/reports/pitest/index.html`           |

A test suite can achieve 100% JaCoCo line coverage while still letting many Pitest mutants survive — if the tests call the code but never assert meaningfully on its output.

### JaCoCo (line and branch coverage)

JaCoCo runs automatically after every `./gradlew test`. No extra command is needed.

```bash
./gradlew test    # runs tests → JaCoCo report generated automatically
```

The report is scoped to `domain.*` and `application.*` packages.

### Pitest (mutation testing)

```bash
./gradlew pitest  # report: build/reports/pitest/index.html
```

Pitest injects small code changes (mutants) one at a time and checks whether at least one test fails. A surviving mutant means no test currently detects that change. The build threshold is ≥ 79% for both mutation and line coverage.

The target test class and thresholds are configurable from the command line without editing `build.gradle`:

```bash
# Run against the strong test suite (default)
./gradlew pitest \
  -PpitestTargetTests=at.mavila.computing_lab_may_2026.domain.arithmetic.CalculatorTest

# Run against the weak test suite (thresholds lowered so the build completes)
./gradlew pitest \
  -PpitestTargetTests=at.mavila.computing_lab_may_2026.domain.arithmetic.CalculatorWeakTest \
  -PpitestMutationThreshold=0 -PpitestCoverageThreshold=0
```

### CalculatorWeakTest — demonstrating surviving mutants

`CalculatorWeakTest` is a deliberately weak companion to `CalculatorTest`. Its tests call every `Calculator` method but make no meaningful assertions — no return-value checks, only loose exception-type checks. This causes two classes of Pitest mutants to survive undetected:

| Mutant | Where | Why it survives |
| ------ | ----- | --------------- |
| `NULL_RETURNS` | `add`, `subtract`, `multiply`, `divide` | Tests discard the return value or only call `doesNotThrowAnyException()` — returning `null` is invisible |
| `NEGATE_CONDITIONALS` | `divide` zero-guard | Test checks `isInstanceOf(RuntimeException.class)`; the negated guard causes a plain `ArithmeticException` instead of `DivisionByZeroException`, and both satisfy the broad check |

### compare-pitest.sh — side-by-side comparison

```bash
./compare-pitest.sh
```

Runs pitest and JaCoCo twice — once scoped to `CalculatorTest`, once to `CalculatorWeakTest` — then prints a combined table:

```
╔════════════════════════════════════════════════════════════════════╗
║         JaCoCo Line Coverage  vs  Pitest Mutation Score           ║
╠════════════════════════════════════════════════════════════════════╣
║  Test suite                     JaCoCo lines    Pitest mutants   ║
╠════════════════════════════════════════════════════════════════════╣
║  CalculatorTest      (strong)           69%              100%    ║
║  CalculatorWeakTest  (weak)             69%               16%    ║
╚════════════════════════════════════════════════════════════════════╝
```

Both suites produce the same JaCoCo score because both call every Calculator method. The mutation score collapses from 100% to 16% because weak assertions cannot distinguish correct code from mutated code.

Reports for each run are preserved under `build/reports/pitest-strong/` and `build/reports/pitest-weak/`, each containing both a pitest report (`index.html`) and a JaCoCo report (`jacoco/index.html`).

---

## 4. Observability

The dev stack ships a pre-wired observability pipeline: **Prometheus** for metrics, **Loki** for logs, and **Grafana** for dashboards. Everything starts automatically with Docker Compose and requires no manual configuration.

### Stack

| Service    | Port | Purpose                                           |
| ---------- | ---- | ------------------------------------------------- |
| App        | 8080 | Spring Boot application                           |
| Grafana    | 3000 | Dashboard UI (anonymous admin, no login required) |
| Prometheus | 9090 | Metrics store — scrapes `/actuator/prometheus`    |
| Loki       | 3100 | Log aggregation — receives push from Loki4j       |

### Accessing the Tools

Open these in a browser (forward the ports from the devcontainer if needed):

| Tool            | URL                             |
| --------------- | ------------------------------- |
| Grafana         | `http://localhost:3000`         |
| GraphiQL        | `http://localhost:8080/graphiql`|
| Prometheus      | `http://localhost:9090`         |
| Actuator health | `http://localhost:8080/actuator/health` |

### Grafana Dashboard

The dashboard **Computing Lab — Spring Boot Overview** is provisioned automatically at startup. It is split into six sections:

| Section                  | Panels                                                                  |
| ------------------------ | ----------------------------------------------------------------------- |
| **Application Overview** | Uptime · Heap Used · CPU Usage · GraphQL Request Rate                   |
| **JVM Memory**           | Heap over time · Non-Heap over time · Heap Utilization gauge            |
| **GraphQL & HTTP**       | Request rate · Response-time percentiles (p50/p95/p99) · Status breakdown |
| **Incoming Request Logs**| Live log stream of every `GRAPHQL_REQUEST` entry from Loki              |
| **JVM Runtime**          | CPU usage · GC pause duration · Live thread count                       |
| **Errors**               | HTTP 4xx/5xx error rate · Error distribution pie chart                  |

### Prometheus Metrics

Prometheus scrapes `/actuator/prometheus` every 15 seconds (job `computing_lab_may_2026`). Key metrics used by the dashboard:

| Metric                                    | What it measures                              |
| ----------------------------------------- | --------------------------------------------- |
| `process_uptime_seconds`                  | Application uptime                            |
| `jvm_memory_used_bytes{area="heap"}`      | Heap memory in use                            |
| `jvm_memory_max_bytes{area="heap"}`       | Max heap — used for utilisation %             |
| `http_server_requests_seconds_count`      | Request throughput, filtered to `uri="/graphql"` |
| `http_server_requests_seconds_bucket`     | Latency histogram for p50/p95/p99 percentiles |
| `process_cpu_usage`                       | CPU utilisation (0–1 normalised to %)         |
| `jvm_gc_pause_seconds_sum`                | GC pause time per interval                    |
| `jvm_threads_live_threads`                | Live JVM thread count                         |

SLO histogram buckets are configured in `application.properties`:

```properties
management.metrics.distribution.percentiles-histogram.http.server.requests=true
management.metrics.distribution.slo.http.server.requests=1ms,5ms,10ms,25ms,50ms,100ms
```

### Log Streaming with Loki

Every incoming GraphQL request is logged by `GraphQlRequestLoggingInterceptor` (in `infrastructure/web/graphql/`) as a structured `INFO` line:

```
GRAPHQL_REQUEST remoteIp="<ip>" uri="<uri>" document="<query>" variables="<vars>"
```

Loki4j (logback appender) batches these lines and pushes them to Loki with stream labels:

| Label     | Value                       | Source                         |
| --------- | --------------------------- | ------------------------------ |
| `service` | `computing_lab_may_2026`    | `spring.application.name`      |
| `level`   | e.g. `INFO`                 | log level of the event         |

The Grafana Logs panel queries these with:

```logql
{service="computing_lab_may_2026"} |= "GRAPHQL_REQUEST"
```

#### The `loki` Spring Profile

The Loki4j appender is guarded by the `loki` Spring profile so that local runs and test suites never attempt a DNS lookup for the `loki` container:

```properties
# docker-compose.yml injects this into the app container
SPRING_PROFILES_ACTIVE=dev,loki
```

When the `loki` profile is **not** active (plain `./gradlew bootRun` outside Docker Compose), the appender block in `logback-spring.xml` is skipped entirely. No connection errors, no log noise.

#### Loki4j 2.x Configuration Note

Loki4j 2.x changed the XML configuration format from 1.x. Key differences relevant to `logback-spring.xml`:

- No `<format>` wrapper — `<labels>` and `<message>` are top-level appender elements.
- `<labels>` pairs are separated by **newlines** (`KV_PAIR_SEPARATOR = regex:\n|\r`), not commas. Comma-separated pairs throw `IllegalArgumentException` at startup.
- `<message>` takes a full Logback `Layout`; use `class="ch.qos.logback.classic.PatternLayout"` explicitly.

---

## Running the Project

### Prerequisites

- Java 25
- Gradle

### Commands

```bash
./gradlew build            # compile + test + JaCoCo report
./gradlew build -x test    # compile only
./gradlew test             # run all tests + generate JaCoCo report automatically
./gradlew bootRun          # start application (port 8080)
./gradlew pitest           # mutation testing (report: build/reports/pitest/index.html)
./compare-pitest.sh        # side-by-side JaCoCo vs pitest for strong and weak test suites
```

Run a single test class:

```bash
./gradlew test --tests "at.mavila.computing_lab_may_2026.domain.arithmetic.CalculatorTest"
```

### Key Configuration

- Active profile: `dev` (H2 in-memory DB, GraphiQL enabled at `/graphiql`)
- Observability profile: `loki` — activated by Docker Compose (`SPRING_PROFILES_ACTIVE=dev,loki`); enables the Loki4j log appender
- Java 25 toolchain, Spring Boot 4.x
- Pitest mutation threshold: ≥ 79%; overridable via `-PpitestMutationThreshold` and `-PpitestCoverageThreshold`
- Pitest target tests: defaults to `domain.*`; overridable via `-PpitestTargetTests`
- JaCoCo report scoped to `domain.*` and `application.*`; generated automatically after `./gradlew test`

---

## License

This project is for educational purposes.
