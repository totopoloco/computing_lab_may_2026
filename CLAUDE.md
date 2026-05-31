# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
./gradlew build            # compile + test
./gradlew build -x test    # compile only
./gradlew test             # run all tests
./gradlew bootRun          # start application (port 8080)
./gradlew pitest           # mutation testing (report: build/reports/pitest/index.html)
```

Run a single test class:

```bash
./gradlew test --tests "at.mavila.computing_lab_may_2026.domain.arithmetic.CalculatorTest"
```

## Architecture

Three-layer DDD. Base package: `at.mavila.computing_lab_may_2026`.

```
domain/          # Core logic — @Component beans, no outer-layer deps
  arithmetic/    # Calculator domain — BigDecimal arithmetic operations
    exception/   # DivisionByZeroException
application/
  arithmetic/    # CalculatorService — delegates to domain, no business logic
infrastructure/
  config/        # GraphQLConfig — registers custom scalars (BigDecimal via graphql-java-extended-scalars)
  web/graphql/   # ArithmeticController (@QueryMapping), GraphQLExceptionHandler
```

**The only API is GraphQL** (`/graphql`). There are no REST controllers. The schema lives at `src/main/resources/graphql/schema.graphqls`. The GraphiQL IDE is enabled in dev at `/graphiql`. `BigDecimal` is a custom scalar registered in `GraphQLConfig`; add new scalars there.

When adding a new feature the full touch list is:

1. `domain/arithmetic/<Name>.java` — `@Component @Validated` domain service
2. `application/arithmetic/CalculatorService.java` — delegate method
3. `resources/graphql/schema.graphqls` — new query
4. `infrastructure/web/graphql/ArithmeticController.java` — `@QueryMapping`
5. `infrastructure/web/graphql/GraphQLExceptionHandler.java` — if new domain exception
6. Tests mirroring the main package structure (`@SpringBootTest`, AssertJ for domain; `GraphQlTester` for GraphQL integration tests)
7. Verify `./gradlew pitest` still passes (≥ 79% mutation and line coverage)

## Coding conventions

**Null handling** — always `Objects.isNull()` / `Objects.nonNull()`, never `== null` or `!= null`.

**Input validation** — domain services must use **Jakarta Bean Validation** on parameter `records`. No manual null-guard `if` statements in domain service bodies. Use custom `@Constraint` annotations when standard ones don't suffice. The application layer (`CalculatorService`) is the only place where null defaulting via `Objects.isNull()` is permitted. Domain service classes must be annotated `@Validated` for parameter-level constraints to be enforced by Spring AOP.

**Iteration** — prefer Stream API. Use enhanced for-each when index-dependent or carrying mutable state. Never use C-style indexed `for` loops when a stream or for-each suffices.

**Immutability** — all method parameters `final`, local variables `final` when not reassigned, `List.of()`/`Map.of()` for literals, `private static final` for constants.

**Method design**:

- Public methods ≤ 3 parameters; group extras into a `record` parameter object.
- Cyclomatic complexity ≤ 10 per method; extract private helpers.
- Guard-clause pattern: check the **exceptional/error** condition at the top and return/throw early; let the happy path flow naturally below.
- Domain services must delegate sub-concerns to injected `@Component` collaborators. No monolithic methods.

**String formatting** — `String.format(...)` or `"...".formatted(...)` for messages with dynamic values. No string concatenation (`+`) for error messages.

**Javadoc** — required on every public class and public method across all layers, including `@param`, `@return`, `@throws`, algorithm description, and time/space complexity where applicable. `package-info.java` required for every package.

**Lombok** — `@RequiredArgsConstructor` for constructor injection; `@Getter`/`@Setter`/`@Builder` on models.

## Testing conventions

- Domain tests: `@SpringBootTest` (full context, no mocks), AssertJ assertions, exhaustive coverage of happy paths, edge cases, and constraint violations.
- Application tests: `@Nested` inner classes using AssertJ assertions (same as domain tests).
- GraphQL integration tests: `GraphQlTester` with raw GraphQL query documents, covering happy-path and error responses.
- Mutation testing: every new domain class must maintain the project-wide ≥ 79% mutation and line coverage threshold (`./gradlew pitest`). Tests must kill mutations — avoid trivial assertions that survive mutants.
- `CalculatorWeakTest` is an intentional educational artifact that demonstrates how imprecise assertions allow mutation survivors (e.g., `NULL_RETURNS`, `NEGATE_CONDITIONALS`). Do not strengthen it.

## Key configuration

- Active profile: `dev` (H2 in-memory DB, GraphiQL enabled, `logging.pattern.console=%msg%n` for clean output)
- Java 25 toolchain, Spring Boot 4.x
- Pitest mutation threshold: 79%; targets `domain.*` classes only
- JaCoCo line/branch coverage runs automatically after `./gradlew test`; report: `build/reports/jacoco/test/html/index.html`; scoped to `domain.*` + `application.*`

Pitest CLI overrides (used by `compare-pitest.sh` and useful for iterating on a single domain class):

```bash
./gradlew pitest \
  -PpitestTargetTests=at.mavila.computing_lab_may_2026.domain.arithmetic.CalculatorTest \
  -PpitestMutationThreshold=79 \
  -PpitestCoverageThreshold=79
```

`./compare-pitest.sh` runs pitest + JaCoCo twice — once with `CalculatorTest`, once with `CalculatorWeakTest` — and prints a side-by-side table showing mutation score collapse with weak assertions. Reinforces why `CalculatorWeakTest` must not be strengthened.

## Commit style

Commit messages must carry a Freddie Mercury tone — confident, precise, and with a quiet sense of flair — without tipping into theatrics. Think "a small lie, but a lie nonetheless" rather than "WE ARE THE CHAMPIONS OF NULL SAFETY". The message should still be informative first; the personality is seasoning, not the dish.
