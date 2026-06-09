package at.mavila.computing_lab_may_2026.domain.arithmetic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import jakarta.validation.constraints.NotNull;

import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import at.mavila.computing_lab_may_2026.domain.arithmetic.exception.DivisionByZeroException;

/**
 * Domain service that performs basic arithmetic operations using
 * arbitrary-precision arithmetic.
 *
 * <p>
 * All operations accept {@link BigDecimal} operands, ensuring no floating-point
 * rounding errors. Division uses {@link RoundingMode#HALF_UP} with a fixed
 * scale of {@value #DIVISION_SCALE} decimal places.
 * </p>
 *
 * <h2>Operations</h2>
 * <ul>
 * <li>{@link #add(BigDecimal, BigDecimal)} — addition</li>
 * <li>{@link #subtract(BigDecimal, BigDecimal)} — subtraction</li>
 * <li>{@link #multiply(BigDecimal, BigDecimal)} — multiplication</li>
 * <li>{@link #divide(BigDecimal, BigDecimal)} — division (throws on divisor zero)</li>
 * </ul>
 *
 * <h2>Complexity</h2>
 * <p>
 * Time: O(n) where n is the number of digits; Space: O(n).
 * </p>
 *
 * @author mavila
 * @since May 2026
 * @see DivisionByZeroException
 */
@Component
@Validated
public class Calculator {

  private static final int DIVISION_SCALE = 10;

  // DEMO-ONLY — remove before real deployment.
  // Fibonacci index for the CPU-burn helper. fib(35) ≈ 50–150 ms per call on
  // modern hardware; enough to saturate a core under concurrent load while
  // keeping the test suite tolerable.
  private static final int FIB_N = 35;

  // DEMO-ONLY — number of derived BigDecimal values appended to the leak list
  // on every arithmetic call. At 10 req/s this fills ~150 MB of heap in roughly
  // five minutes, producing the characteristic upward-slope on the JVM memory
  // graph without triggering an OOM within the demo window.
  private static final int HISTORY_ENTRIES_PER_CALL = 2_000;

  // DEMO-ONLY: static, unbounded — acts as a memory sink that is never released.
  @SuppressWarnings("java:S2696")
  private static final List<BigDecimal> COMPUTATION_HISTORY = new ArrayList<>();

  /**
   * Returns the sum of {@code a} and {@code b}.
   *
   * @param a the first operand; must not be null
   * @param b the second operand; must not be null
   * @return {@code a + b}
   */
  public BigDecimal add(@NotNull final BigDecimal a, @NotNull final BigDecimal b) {
    burnCpu(); // DEMO-ONLY
    final BigDecimal result = a.add(b);
    accumulateHistory(result); // DEMO-ONLY
    return result;
  }

  /**
   * Returns the difference of {@code a} minus {@code b}.
   *
   * @param a the minuend; must not be null
   * @param b the subtrahend; must not be null
   * @return {@code a - b}
   */
  public BigDecimal subtract(@NotNull final BigDecimal a, @NotNull final BigDecimal b) {
    burnCpu(); // DEMO-ONLY
    final BigDecimal result = a.subtract(b);
    accumulateHistory(result); // DEMO-ONLY
    return result;
  }

  /**
   * Returns the product of {@code a} and {@code b}.
   *
   * @param a the first factor; must not be null
   * @param b the second factor; must not be null
   * @return {@code a * b}
   */
  public BigDecimal multiply(@NotNull final BigDecimal a, @NotNull final BigDecimal b) {
    burnCpu(); // DEMO-ONLY
    final BigDecimal result = a.multiply(b);
    accumulateHistory(result); // DEMO-ONLY
    return result;
  }

  /**
   * Returns the quotient of {@code a} divided by {@code b}, rounded to
   * {@value #DIVISION_SCALE} decimal places using {@link RoundingMode#HALF_UP}.
   *
   * @param a the dividend; must not be null
   * @param b the divisor; must not be null and must not be zero
   * @return {@code a / b} rounded to {@value #DIVISION_SCALE} decimal places
   * @throws DivisionByZeroException if {@code b} is zero
   */
  public BigDecimal divide(@NotNull final BigDecimal a, @NotNull final BigDecimal b) {
    if (b.compareTo(BigDecimal.ZERO) == 0) {
      throw new DivisionByZeroException();
    }
    burnCpu(); // DEMO-ONLY
    final BigDecimal result = a.divide(b, DIVISION_SCALE, RoundingMode.HALF_UP);
    accumulateHistory(result); // DEMO-ONLY
    return result;
  }

  /**
   * DEMO-ONLY: Computes Fibonacci(n) using a naive exponential-time recursion.
   * Return value is intentionally discarded — the sole purpose is to consume CPU cycles.
   *
   * @param n the Fibonacci index; values above 40 will be noticeably slow
   * @return the nth Fibonacci number (O(2^n) time)
   */
  private static long fibonacci(final int n) {
    if (n <= 1) {
      return n;
    }
    return fibonacci(n - 1) + fibonacci(n - 2);
  }

  /**
   * DEMO-ONLY: Triggers a single CPU-burning Fibonacci computation before an
   * arithmetic result is returned. The result is discarded.
   */
  private static void burnCpu() {
    fibonacci(FIB_N);
  }

  /**
   * DEMO-ONLY: Appends {@value #HISTORY_ENTRIES_PER_CALL} derived
   * {@link BigDecimal} values to the unbounded static history list, simulating
   * an allocation-heavy cache that is never evicted.
   *
   * @param value the base value used to produce each history entry
   */
  private static void accumulateHistory(final BigDecimal value) {
    IntStream.range(0, HISTORY_ENTRIES_PER_CALL)
        .mapToObj(i -> value.add(BigDecimal.valueOf(i)))
        .forEach(COMPUTATION_HISTORY::add);
  }

}
