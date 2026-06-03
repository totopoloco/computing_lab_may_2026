# LOGGING.md

How logging is wired in this project, and how to add new **Loki-only** loggers
(such as a future `ExceptionLoggingInterceptor`) without leaking those lines to
the server console.

> TL;DR — Console and Loki are two independent sinks. The console is the default
> for everything; Loki receives **only** the lines from loggers we explicitly
> route to it. Routing a logger to Loki *only* (and keeping it off the console)
> is done with one rule: a **dedicated logger with `additivity="false"` whose
> sole appender is `LOKI`**, declared inside the `loki` Spring profile. The
> `LOKI` appender is **never** attached to `<root>`.

---

## 1. The two sinks

| Sink | Appender | Defined by | Who writes to it |
|------|----------|-----------|------------------|
| Server console (stdout) | `CONSOLE` | Spring Boot `base.xml` (included in [logback-spring.xml](src/main/resources/logback-spring.xml)), attached to `<root>` | Every logger that propagates to root (i.e. `additivity="true"`, the default) |
| Grafana via Loki | `LOKI` (`com.github.loki4j.logback.Loki4jAppender`) | [logback-spring.xml](src/main/resources/logback-spring.xml), only in the `loki` profile | **Only** loggers that explicitly reference `LOKI` |

The console format in dev is intentionally bare (`logging.pattern.console=%msg%n`
in [application-dev.properties](src/main/resources/application-dev.properties)).
The Loki message carries a full pattern (timestamp, level, thread, logger, msg)
set on the appender's `<message>` layout.

### Why a line could appear in *both*

The console (`CONSOLE`) lives on the **root** logger. In Logback, a child logger
with default *additivity* (`true`) sends its events to its own appenders **and**
to every ancestor's appenders — including root's `CONSOLE`. So if you attach
`LOKI` to a logger that still propagates to root, the line is written **twice**:
once to Loki, once to the console.

Two ways this bites you, both of which we avoid:

1. **`LOKI` on `<root>`** → every log line in the app is shipped to Loki, and
   the interceptor line is duplicated to the console. (This was the original
   bug.)
2. **A Loki-targeted logger left at `additivity="true"`** → its line reaches
   Loki *and* bubbles up to root's console.

The fix for both is the same rule, stated next.

---

## 2. The routing rule (the important part)

To make a logger's output go to **Loki only** and **never** the console:

```xml
<springProfile name="loki">
  <!-- the LOKI appender is declared once, here -->
  <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender"> ... </appender>

  <!-- one dedicated logger per Loki-only concern -->
  <logger name="fully.qualified.LoggerName" additivity="false">
    <appender-ref ref="LOKI"/>
  </logger>
</springProfile>
```

- **`additivity="false"`** — stops propagation to `<root>`, so the console
  (`CONSOLE`) never sees the line.
- **`<appender-ref ref="LOKI"/>` only** — the line's sole destination is Loki.
- **`LOKI` is never on `<root>`** — guarantees Loki receives *only* the lines
  from these dedicated loggers, which is what we want in Grafana.

The live example is the GraphQL request logger in
[logback-spring.xml](src/main/resources/logback-spring.xml):

```xml
<logger
    name="at.mavila.computing_lab_may_2026.infrastructure.web.graphql.GraphQlRequestLoggingInterceptor"
    additivity="false">
  <appender-ref ref="LOKI"/>
</logger>
```

It emits a single structured line per request
([GraphQlRequestLoggingInterceptor.java](src/main/java/at/mavila/computing_lab_may_2026/infrastructure/web/graphql/GraphQlRequestLoggingInterceptor.java)):

```
GRAPHQL_REQUEST remoteIp="..." uri="..." document="..." variables="..."
```

---

## 3. Profiles and levels

Profiles are activated via `SPRING_PROFILES_ACTIVE=dev,loki`
([.devcontainer/docker-compose.yml](.devcontainer/docker-compose.yml)); the
default when nothing overrides is `dev`
([application.properties](src/main/resources/application.properties)). Spring
prints `The following 2 profiles are active: "dev", "loki"` on startup.

The **appender wiring** lives in logback (section 2). The **level** — i.e.
whether the logger emits at all — is controlled per profile via
`logging.level.*` properties. Spring Boot applies these *after* the logback file
is parsed; setting a level does **not** touch the logger's appenders or
additivity, so the section-2 wiring is preserved.

| Logger | `application-dev.properties` | `application-loki.properties` | Effective when `dev,loki` active |
|--------|------------------------------|-------------------------------|----------------------------------|
| `...GraphQlRequestLoggingInterceptor` | `OFF` | `INFO` | `INFO` — `loki` is listed last, so its value wins |

Resulting behavior:

| Run mode | Console | Loki | Notes |
|----------|---------|------|-------|
| `dev` only | silent (`OFF`) | n/a — `LOKI` appender not defined outside the `loki` profile | dev-only runs produce no request logs at all |
| `dev,loki` | silent (`additivity="false"`) | receives the line at `INFO` | the normal devcontainer setup |
| `prod` | governed by [application-prod.properties](src/main/resources/application-prod.properties); `loki` profile inactive ⇒ no `LOKI` appender | n/a | enable Loki in prod by also activating the `loki` profile |

> Profile precedence: with `dev,loki`, both `application-dev.properties` and
> `application-loki.properties` load, and the **last-listed** profile (`loki`)
> wins conflicts. That is why `loki`'s `INFO` overrides `dev`'s `OFF`.

---

## 4. How to add a new Loki-only logger (e.g. `ExceptionLoggingInterceptor`)

Suppose you add an interceptor that logs handled exceptions and you want those
lines in Grafana **only**, exactly like the GraphQL ones. No code in the
existing classes changes; you add one component and three small config entries.

### Step 1 — Write the component with its own class logger and a marker prefix

Use an SLF4J logger named after the class (so it gets its own logger name), and
prefix the message with a stable, greppable marker so it is easy to filter in
Grafana:

```java
package at.mavila.computing_lab_may_2026.infrastructure.web.graphql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// ...

@Component
public class ExceptionLoggingInterceptor /* implements WebGraphQlInterceptor, etc. */ {

  private static final Logger LOG = LoggerFactory.getLogger(ExceptionLoggingInterceptor.class);

  // ... emit one structured line, mirroring the GRAPHQL_REQUEST style:
  // LOG.error("EXCEPTION_LOG type=\"{}\" message=\"{}\" path=\"{}\"", ...);
}
```

Guidance:
- **Marker prefix** (`EXCEPTION_LOG`) — lets you filter with LogQL `|= "EXCEPTION_LOG"`.
- **`key="value"` pairs** — same convention as `GRAPHQL_REQUEST`, so LogQL can
  pattern-match individual fields without a JSON parser.
- The **class name** also lands in the Loki line via the appender's
  `%logger{36}` pattern, giving you a second way to filter
  (`|= "ExceptionLoggingInterceptor"`).

### Step 2 — Route the logger to Loki only in [logback-spring.xml](src/main/resources/logback-spring.xml)

Inside the existing `<springProfile name="loki">` block, add a second dedicated
logger next to the GraphQL one:

```xml
<logger
    name="at.mavila.computing_lab_may_2026.infrastructure.web.graphql.ExceptionLoggingInterceptor"
    additivity="false">
  <appender-ref ref="LOKI"/>
</logger>
```

Reuse the **same** `LOKI` appender — do not declare a second one, and do not add
`LOKI` to `<root>`.

### Step 3 — Add the level toggle in the profile property files

- [application-dev.properties](src/main/resources/application-dev.properties) — keep it silent when Loki is not active:

  ```properties
  logging.level.at.mavila.computing_lab_may_2026.infrastructure.web.graphql.ExceptionLoggingInterceptor=OFF
  ```

- [application-loki.properties](src/main/resources/application-loki.properties) — enable it when shipping to Loki (use the level your `LOG.x(...)` call uses, e.g. `INFO` or `ERROR`):

  ```properties
  logging.level.at.mavila.computing_lab_may_2026.infrastructure.web.graphql.ExceptionLoggingInterceptor=INFO
  ```

### Step 4 — Restart and verify (section 6)

That's it. The new interceptor's lines now appear in Grafana and never on the
console.

### The general rule, restated

> **One dedicated logger per Loki-only concern. `additivity="false"`. Only the
> `LOKI` appender. Never on `<root>`. Level gated by the `dev`/`loki` property
> pair.**

---

## 5. Variations

- **Want a line in BOTH console and Loki?** Drop `additivity="false"` (or set it
  `true`) and add **both** appenders to the logger, or keep additivity on so it
  inherits root's `CONSOLE` and add `<appender-ref ref="LOKI"/>` for Loki.
- **Want a whole package routed, not one class?** Use a package-level logger
  name (e.g. `...infrastructure.web.graphql.audit`) — every logger under it
  inherits the wiring. Be deliberate: this widens what reaches Loki.
- **Do NOT** attach `LOKI` to `<root>` to "catch everything". That reintroduces
  the original bug — all app logs flood Loki and Loki-targeted lines double up
  on the console.

---

## 6. Verifying a change

The running app loads `logback-spring.xml` **once at startup**, so config edits
require an app restart (`./gradlew bootRun`, or restart the devcontainer app) to
take effect — a file change alone does not hot-reload.

After restarting with `dev,loki` active, exercise the path, then check both
sinks.

**Console must NOT contain the marker** (replace with your marker / instance log):

```bash
# Expect zero matches in the server's stdout
grep -c "GRAPHQL_REQUEST" <server-console-output>
```

**Loki MUST contain it** — query the Loki HTTP API directly:

```bash
END=$(date +%s)000000000
START=$(( $(date +%s) - 300 ))000000000
curl -s -G "http://loki:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={service="computing_lab_may_2026"} |= "GRAPHQL_REQUEST"' \
  --data-urlencode "start=$START" --data-urlencode "end=$END" --data-urlencode "limit=5"
```

> The `LOKI` appender batches asynchronously (non-blocking by design), so allow
> a couple of seconds after the request before the line is queryable.

In **Grafana** (`http://localhost:3000`, anonymous admin, Loki datasource uid
`loki`), the equivalent Explore query is:

```logql
{service="computing_lab_may_2026"} |= "GRAPHQL_REQUEST"
```

To distinguish multiple interceptors once you have more than one:

```logql
{service="computing_lab_may_2026"} |= "EXCEPTION_LOG"
{service="computing_lab_may_2026"} |= "ExceptionLoggingInterceptor"   # by logger name
{service="computing_lab_may_2026", level="ERROR"}                     # by the level label
```

---

## 7. Reference

### Files

| File | Role |
|------|------|
| [src/main/resources/logback-spring.xml](src/main/resources/logback-spring.xml) | `LOKI` appender + the dedicated, `additivity="false"` Loki-only loggers |
| [src/main/resources/application.properties](src/main/resources/application.properties) | shared config; default `spring.profiles.active=dev` |
| [src/main/resources/application-dev.properties](src/main/resources/application-dev.properties) | console pattern; per-logger `OFF` toggles for dev-only runs |
| [src/main/resources/application-loki.properties](src/main/resources/application-loki.properties) | per-logger `INFO` toggles that enable shipping when `loki` is active |
| [.devcontainer/docker-compose.yml](.devcontainer/docker-compose.yml) | `SPRING_PROFILES_ACTIVE=dev,loki`; `loki` and `grafana` services |
| [.devcontainer/loki/config.yaml](.devcontainer/loki/config.yaml) | Loki server config |
| [.devcontainer/grafana/provisioning/datasources/loki.yml](.devcontainer/grafana/provisioning/datasources/loki.yml) | Grafana → Loki datasource (uid `loki`) |

### Loki appender facts (Loki4j 2.x)

- Push URL: `http://loki:3100/loki/api/v1/push`.
- `<labels>` are **newline-separated** `key=value` pairs — comma-separated pairs
  throw `IllegalArgumentException`. Current labels: `service`, `level`.
- `<message>` is a Logback `PatternLayout`; the pattern includes `%logger{36}`,
  which is why the logger/class name is searchable in Loki.
- Keep label cardinality low (Loki indexes labels). Filter on message **content**
  with `|=` / `|~` rather than minting a new label per concern.

### Endpoints

| Service | URL |
|---------|-----|
| Application (GraphQL) | `http://localhost:8080/graphql` |
| GraphiQL (dev) | `http://localhost:8080/graphiql` |
| Grafana | `http://localhost:3000` |
| Loki API | `http://localhost:3100` |
| Prometheus | `http://localhost:9090` |
