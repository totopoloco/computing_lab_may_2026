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
    """
    Adds two numbers and returns the result.
    Supports arbitrary-precision arithmetic via BigDecimal.
    """
    add(a: BigDecimal!, b: BigDecimal!): BigDecimal!

    """
    Subtracts b from a and returns the result.
    Supports arbitrary-precision arithmetic via BigDecimal.
    """
    subtract(a: BigDecimal!, b: BigDecimal!): BigDecimal!

    """
    Multiplies two numbers and returns the result.
    Supports arbitrary-precision arithmetic via BigDecimal.
    """
    multiply(a: BigDecimal!, b: BigDecimal!): BigDecimal!

    """
    Divides a by b and returns the result rounded to 10 decimal places (HALF_UP).
    Throws an error if b is zero.
    Supports arbitrary-precision arithmetic via BigDecimal.
    """
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
{
    "data": {
        "add": 4.0
    }
}
```

#### Subtraction

```graphql
query {
    subtract(a: 10, b: 3.7)
}
```

**Response:**

```json
{
    "data": {
        "subtract": 6.3
    }
}
```

#### Multiplication

```graphql
query {
    multiply(a: 3.14, b: 2)
}
```

**Response:**

```json
{
    "data": {
        "multiply": 6.28
    }
}
```

#### Division

```graphql
query {
    divide(a: 10, b: 3)
}
```

**Response:**

```json
{
    "data": {
        "divide": 3.3333333333
    }
}
```

### Error Handling

The GraphQL API returns structured errors for domain exceptions.

#### Division by Zero

```graphql
query {
    divide(a: 5, b: 0)
}
```

**Response:**

```json
{
    "errors": [
        {
            "message": "Division by zero is not allowed",
            "extensions": {
                "errorCode": "DIVISION_BY_ZERO",
                "classification": "BAD_REQUEST"
            }
        }
    ]
}
```

#### Invalid Argument

```graphql
query {
    divide(a: null, b: 3)
}
```

**Response:**

```json
{
    "errors": [
        {
            "message": "Invalid input: argument must not be null",
            "extensions": {
                "errorCode": "INVALID_ARGUMENT",
                "reason": "argument must not be null",
                "classification": "BAD_REQUEST"
            }
        }
    ]
}
```

### Using cURL

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "{ add(a: 1.5, b: 2.5) }"}'
```

---

## Running the Project

### Prerequisites

- Java 25
- Gradle

### Commands

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

### Key Configuration

- Active profile: `dev` (H2 in-memory DB, GraphiQL enabled)
- Java 25 toolchain, Spring Boot 4.x
- Pitest mutation threshold: ≥ 79% (both mutation and line coverage)
- Pitest targets only `domain.*` and `application.*` packages

---

## License

This project is for educational purposes.
