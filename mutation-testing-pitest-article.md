# Your Tests Are Lying to You — and PITest Can Prove It

You run your test suite. Green. You check coverage. 100% lines executed. You feel good.

And yet, your code is fragile. You just don't know it yet.

This is the problem mutation testing was built to expose — and it is the reason I have been experimenting with [PITest](https://pitest.org/) in this project.

---

## The gap between coverage and confidence

Line coverage tells you which lines were *executed* during a test run. It says nothing about whether any test would *fail* if that code were wrong.

Consider a method that adds two numbers and returns the result. If your test calls it but only checks that no exception was thrown, you have 100% line coverage and zero behavioral verification. The method could return `null`, return the wrong value, or silently do nothing — and your test suite would still be green.

This is not a theoretical concern. It happens in real codebases, often in the tests added under time pressure or written to satisfy a coverage gate rather than to verify behavior.

---

## What mutation testing actually does

PITest takes a different approach. Instead of asking "was this line executed?", it asks: "if I break this code in a small, realistic way, does at least one test fail?"

It does this by automatically generating *mutants* — slightly modified copies of your bytecode. A mutant might:

- Replace `a + b` with `a - b`
- Replace a `return result` with `return null`
- Negate a conditional (`if (x == 0)` becomes `if (x != 0)`)

Each mutant is run against your full test suite. If a test fails, the mutant is *killed* — your tests detected the change. If all tests still pass, the mutant *survives* — meaning no test currently distinguishes correct code from that particular form of broken code.

A mutation score of 79% means 79% of mutants were killed. The remaining 21% survived, each one pointing at a specific behavioral gap in your test suite.

---

## A concrete example: the same coverage, very different protection

This project includes two test classes for the same `Calculator` domain service:

- **`CalculatorTest`** — precise assertions using `isEqualByComparingTo`, checking exact return values and specific exception types
- **`CalculatorWeakTest`** — an intentional weak companion that calls every method but makes no meaningful assertions on what comes back

Running `./compare-pitest.sh` produces this table:

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

Both suites produce identical JaCoCo scores — 69% — because both call every `Calculator` method. The mutation score tells the real story: 100% versus 16%.

The two mutant types that survive `CalculatorWeakTest` but not `CalculatorTest` are:

| Mutant | Why it survives weak tests |
|--------|---------------------------|
| `NULL_RETURNS` on `add`, `subtract`, `multiply`, `divide` | Tests discard the return value or only assert `doesNotThrowAnyException()` — returning `null` is invisible |
| `NEGATE_CONDITIONALS` on the division zero-guard | Test checks `isInstanceOf(RuntimeException.class)`; negating the guard causes a plain `ArithmeticException` instead of `DivisionByZeroException`, and both satisfy the broad check |

The lesson is not subtle: a test that calls the code without asserting precisely on what it returns is little more than a smoke test wearing a coverage badge.

---

## Running it yourself

The demo project is a Spring Boot calculator exposed over a GraphQL API. It uses `BigDecimal` throughout for exact arithmetic — no floating-point surprises — and follows a three-layer DDD structure.

```bash
# Clone and run mutation testing
git clone https://github.com/totopoloco/computing_lab_may_2026
cd computing_lab_may_2026
./gradlew pitest
# Report: build/reports/pitest/index.html

# Side-by-side comparison of strong vs weak test suites
./compare-pitest.sh
```

> **Note on Java 25:** PITest was not compatible with Java 25 earlier this year, but it is now. The project runs cleanly on Java 25 + Spring Boot 4.x with PITest 1.25.1.

The build enforces a **≥ 79% threshold** for both mutation and line coverage. If your new code drops either below that, the build fails — mutation testing is a first-class gate, not an afterthought.

---

## Why this matters beyond the demo

Coverage metrics are useful. They are not sufficient. A codebase that uses mutation testing as a quality gate makes a different kind of claim: not just "we executed this code" but "we would notice if it were wrong."

That is a much stronger guarantee — and it is the one that actually matters when something breaks in production at an inconvenient hour.

If you would like to explore this together and kill some mutants, the project is open and the invitation stands.

---

*The source code, test suites, and comparison script referenced in this article are available at [github.com/totopoloco/computing_lab_may_2026](https://github.com/totopoloco/computing_lab_may_2026).*
