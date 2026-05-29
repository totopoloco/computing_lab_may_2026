package at.mavila.computing_lab_may_2026.infrastructure.web.graphql;

import java.math.BigDecimal;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import at.mavila.computing_lab_may_2026.application.arithmetic.CalculatorService;
import lombok.RequiredArgsConstructor;

/**
 * GraphQL controller that exposes arithmetic operations as queries.
 *
 * <p>
 * Translates GraphQL arguments into application service calls. Contains no business logic —
 * it is a pure adapter between the GraphQL transport layer and {@link CalculatorService}.
 * </p>
 *
 * <h2>Exposed Queries</h2>
 * <ul>
 * <li>{@code add(a, b)} — addition</li>
 * <li>{@code subtract(a, b)} — subtraction</li>
 * <li>{@code multiply(a, b)} — multiplication</li>
 * <li>{@code divide(a, b)} — division</li>
 * </ul>
 *
 * @author mavila
 * @since May 2026
 * @see CalculatorService
 */
@Controller
@RequiredArgsConstructor
public class ArithmeticController {

    private final CalculatorService calculatorService;

    /**
     * Resolves the {@code add} GraphQL query.
     *
     * @param a the first operand
     * @param b the second operand
     * @return {@code a + b}
     */
    @QueryMapping
    public BigDecimal add(@Argument("a") final BigDecimal a, @Argument("b") final BigDecimal b) {
        return calculatorService.add(a, b);
    }

    /**
     * Resolves the {@code subtract} GraphQL query.
     *
     * @param a the minuend
     * @param b the subtrahend
     * @return {@code a - b}
     */
    @QueryMapping
    public BigDecimal subtract(@Argument("a") final BigDecimal a, @Argument("b") final BigDecimal b) {
        return calculatorService.subtract(a, b);
    }

    /**
     * Resolves the {@code multiply} GraphQL query.
     *
     * @param a the first factor
     * @param b the second factor
     * @return {@code a * b}
     */
    @QueryMapping
    public BigDecimal multiply(@Argument("a") final BigDecimal a, @Argument("b") final BigDecimal b) {
        return calculatorService.multiply(a, b);
    }

    /**
     * Resolves the {@code divide} GraphQL query.
     *
     * @param a the dividend
     * @param b the divisor; must not be zero
     * @return {@code a / b} rounded to 10 decimal places
     * @throws at.mavila.computing_lab_may_2026.domain.arithmetic.exception.DivisionByZeroException
     *             if {@code b} is zero
     */
    @QueryMapping
    public BigDecimal divide(@Argument("a") final BigDecimal a, @Argument("b") final BigDecimal b) {
        return calculatorService.divide(a, b);
    }

}
