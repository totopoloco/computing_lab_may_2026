package at.mavila.computing_lab_may_2026.domain.arithmetic;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.validation.constraints.NotNull;

import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import at.mavila.computing_lab_may_2026.domain.arithmetic.exception.DivisionByZeroException;

/**
 * Domain service that performs basic arithmetic operations using arbitrary-precision arithmetic.
 *
 * <p>
 * All operations accept {@link BigDecimal} operands, ensuring no floating-point rounding
 * errors. Division uses {@link RoundingMode#HALF_UP} with a fixed scale of
 * {@value #DIVISION_SCALE} decimal places.
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
 * <p>Time: O(n) where n is the number of digits; Space: O(n).</p>
 *
 * @author mavila
 * @since May 2026
 * @see DivisionByZeroException
 */
@Component
@Validated
public class Calculator {

    private static final int DIVISION_SCALE = 10;

    /**
     * Returns the sum of {@code a} and {@code b}.
     *
     * @param a the first operand; must not be null
     * @param b the second operand; must not be null
     * @return {@code a + b}
     */
    public BigDecimal add(@NotNull final BigDecimal a, @NotNull final BigDecimal b) {
        return a.add(b);
    }

    /**
     * Returns the difference of {@code a} minus {@code b}.
     *
     * @param a the minuend; must not be null
     * @param b the subtrahend; must not be null
     * @return {@code a - b}
     */
    public BigDecimal subtract(@NotNull final BigDecimal a, @NotNull final BigDecimal b) {
        return a.subtract(b);
    }

    /**
     * Returns the product of {@code a} and {@code b}.
     *
     * @param a the first factor; must not be null
     * @param b the second factor; must not be null
     * @return {@code a * b}
     */
    public BigDecimal multiply(@NotNull final BigDecimal a, @NotNull final BigDecimal b) {
        return a.multiply(b);
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
        return a.divide(b, DIVISION_SCALE, RoundingMode.HALF_UP);
    }

}
