package at.mavila.computing_lab_may_2026.application.arithmetic;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import at.mavila.computing_lab_may_2026.domain.arithmetic.Calculator;
import lombok.RequiredArgsConstructor;

/**
 * Application service that delegates arithmetic operations to the domain layer.
 *
 * <p>
 * This service acts as a facade for the infrastructure layer (GraphQL) to
 * access all
 * calculator operations. It coordinates the {@link Calculator} domain service
 * and
 * enforces application-level concerns such as input defaulting before calling
 * into
 * the domain.
 * </p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 * <li>Delegate arithmetic requests to {@link Calculator}</li>
 * <li>Apply null-defaults for optional parameters (currently none)</li>
 * <li>Enforce application-level business rules</li>
 * </ul>
 *
 * @author mavila
 * @since May 2026
 * @see Calculator
 */
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class CalculatorService {

  private final Calculator calculator;

  /**
   * Returns the sum of {@code a} and {@code b}.
   *
   * @param a the first operand
   * @param b the second operand
   * @return {@code a + b}
   */
  public BigDecimal add(final BigDecimal a, final BigDecimal b) {
    return this.calculator.add(a, b);
  }

  /**
   * Returns the difference of {@code a} minus {@code b}.
   *
   * @param a the minuend
   * @param b the subtrahend
   * @return {@code a - b}
   */
  public BigDecimal subtract(final BigDecimal a, final BigDecimal b) {
    return this.calculator.subtract(a, b);
  }

  /**
   * Returns the product of {@code a} and {@code b}.
   *
   * @param a the first factor
   * @param b the second factor
   * @return {@code a * b}
   */
  public BigDecimal multiply(final BigDecimal a, final BigDecimal b) {
    return this.calculator.multiply(a, b);
  }

  /**
   * Returns the quotient of {@code a} divided by {@code b}.
   *
   * @param a the dividend
   * @param b the divisor; must not be zero
   * @return {@code a / b} rounded to 10 decimal places
   * @throws at.mavila.computing_lab_may_2026.domain.arithmetic.exception.DivisionByZeroException
   *                                                                                              if
   *                                                                                              {@code b}
   *                                                                                              is
   *                                                                                              zero
   */
  public BigDecimal divide(final BigDecimal a, final BigDecimal b) {
    return this.calculator.divide(a, b);
  }

}
