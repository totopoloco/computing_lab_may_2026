package at.mavila.computing_lab_may_2026.domain.arithmetic;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Deliberately weak test suite for {@link Calculator} that proves mutation-testing survivors
 * emerge when assertions are too imprecise to distinguish correct from mutated behaviour.
 *
 * <h2>Surviving mutants explained</h2>
 * <ul>
 *   <li><strong>NULL_RETURNS</strong> (Pitest DEFAULTS) — replaces any {@link Object} return
 *       with {@code null}. Survives every test below that discards the return value or only
 *       checks that no exception is thrown, because returning {@code null} is a silent change
 *       that does not raise an exception.</li>
 *   <li><strong>NEGATE_CONDITIONALS</strong> (Pitest DEFAULTS) — negates
 *       {@code b.compareTo(BigDecimal.ZERO) == 0} to {@code != 0} inside
 *       {@link Calculator#divide}. With the negated guard a zero divisor no longer triggers
 *       the custom {@link at.mavila.computing_lab_may_2026.domain.arithmetic.exception.DivisionByZeroException};
 *       instead the JDK throws an {@link ArithmeticException}. Because both are
 *       {@link RuntimeException}, the broad {@code isInstanceOf(RuntimeException.class)}
 *       assertion below still passes and the mutant goes undetected.</li>
 * </ul>
 *
 * <h2>How to observe the survivors</h2>
 * <p>Point {@code targetTests} in {@code build.gradle} exclusively at this class and run
 * {@code ./gradlew pitest}. The HTML report at
 * {@code build/reports/pitest/index.html} will show each surviving mutant highlighted in red.
 *
 * @author mavila
 * @since May 2026
 * @see CalculatorTest
 */
@SpringBootTest
class CalculatorWeakTest {

  @Autowired
  private Calculator calculator;

  /**
   * Weak tests for {@link Calculator#add(BigDecimal, BigDecimal)}.
   */
  @Nested
  class AddWeakTests {

    /**
     * Calls {@code add} but makes no assertion on the returned value.
     *
     * <p><strong>Surviving mutant — NULL_RETURNS:</strong> Pitest replaces
     * {@code return a.add(b)} with {@code return null}. The test discards the result
     * entirely, so the mutation is never detected and the mutant survives.
     *
     * @see Calculator#add(BigDecimal, BigDecimal)
     */
    @Test
    void add_noAssertionOnReturnValue() {
      calculator.add(new BigDecimal("3"), new BigDecimal("4"));
    }

  }

  /**
   * Weak tests for {@link Calculator#subtract(BigDecimal, BigDecimal)}.
   */
  @Nested
  class SubtractWeakTests {

    /**
     * Only verifies that {@code subtract} throws no exception; never inspects the value.
     *
     * <p><strong>Surviving mutant — NULL_RETURNS:</strong> Pitest replaces
     * {@code return a.subtract(b)} with {@code return null}. Returning {@code null}
     * does not throw any exception, so {@code doesNotThrowAnyException()} passes and
     * the mutant survives.
     *
     * @see Calculator#subtract(BigDecimal, BigDecimal)
     */
    @Test
    void subtract_assertsOnlyThatNoExceptionIsThrown() {
      assertThatCode(() -> calculator.subtract(new BigDecimal("10"), new BigDecimal("3")))
          .doesNotThrowAnyException();
    }

  }

  /**
   * Weak tests for {@link Calculator#multiply(BigDecimal, BigDecimal)}.
   */
  @Nested
  class MultiplyWeakTests {

    /**
     * Only verifies that {@code multiply} throws no exception; never inspects the value.
     *
     * <p><strong>Surviving mutant — NULL_RETURNS:</strong> Pitest replaces
     * {@code return a.multiply(b)} with {@code return null}. Returning {@code null}
     * does not throw any exception, so {@code doesNotThrowAnyException()} passes and
     * the mutant survives.
     *
     * @see Calculator#multiply(BigDecimal, BigDecimal)
     */
    @Test
    void multiply_assertsOnlyThatNoExceptionIsThrown() {
      assertThatCode(() -> calculator.multiply(new BigDecimal("3"), new BigDecimal("4")))
          .doesNotThrowAnyException();
    }

  }

  /**
   * Weak tests for {@link Calculator#divide(BigDecimal, BigDecimal)}.
   */
  @Nested
  class DivideWeakTests {

    /**
     * Covers the happy-path return of {@code divide} but only checks that no exception
     * is thrown, never that the result is correct.
     *
     * <p><strong>Surviving mutant — NULL_RETURNS:</strong> Pitest replaces the
     * {@code return a.divide(…)} statement with {@code return null}. Returning
     * {@code null} never causes an exception, so {@code doesNotThrowAnyException()}
     * passes and the mutant survives.
     *
     * @see Calculator#divide(BigDecimal, BigDecimal)
     */
    @Test
    void divide_happyPath_assertsOnlyThatNoExceptionIsThrown() {
      assertThatCode(() -> calculator.divide(new BigDecimal("10"), new BigDecimal("2")))
          .doesNotThrowAnyException();
    }

    /**
     * Only checks that dividing by zero raises some {@link RuntimeException};
     * does not require the specific
     * {@link at.mavila.computing_lab_may_2026.domain.arithmetic.exception.DivisionByZeroException}.
     *
     * <p><strong>Surviving mutant — NEGATE_CONDITIONALS:</strong> Pitest negates
     * {@code b.compareTo(BigDecimal.ZERO) == 0} to {@code != 0} inside
     * {@link Calculator#divide}. With that change the custom guard is never reached
     * for a zero divisor, so the JDK throws a plain {@link ArithmeticException} from
     * the underlying {@link BigDecimal#divide} call. Because
     * {@link ArithmeticException} is a {@link RuntimeException}, the broad
     * {@code isInstanceOf(RuntimeException.class)} assertion still passes and the
     * mutant goes undetected.
     *
     * @see Calculator#divide(BigDecimal, BigDecimal)
     */
    @Test
    void divide_byZero_onlyVerifiesThatARuntimeExceptionIsThrown() {
      assertThatThrownBy(() -> calculator.divide(new BigDecimal("10"), BigDecimal.ZERO))
          .isInstanceOf(RuntimeException.class);
    }

  }

}
