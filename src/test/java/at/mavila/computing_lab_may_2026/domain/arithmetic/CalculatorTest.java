package at.mavila.computing_lab_may_2026.domain.arithmetic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import at.mavila.computing_lab_may_2026.domain.arithmetic.exception.DivisionByZeroException;

/**
 * Tests for {@link Calculator}.
 */
@SpringBootTest
class CalculatorTest {

    @Autowired
    private Calculator calculator;

    @Nested
    class AddTests {

        @Test
        void addsTwoPositiveNumbers() {
            final BigDecimal result = calculator.add(new BigDecimal("3"), new BigDecimal("4"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("7"));
        }

        @Test
        void addsNegativeAndPositive() {
            final BigDecimal result = calculator.add(new BigDecimal("-5"), new BigDecimal("3"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("-2"));
        }

        @Test
        void addsZero() {
            final BigDecimal result = calculator.add(new BigDecimal("10"), BigDecimal.ZERO);
            assertThat(result).isEqualByComparingTo(new BigDecimal("10"));
        }

        @Test
        void addsDecimalValues() {
            final BigDecimal result = calculator.add(new BigDecimal("1.5"), new BigDecimal("2.3"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("3.8"));
        }

    }

    @Nested
    class SubtractTests {

        @Test
        void subtractsTwoPositiveNumbers() {
            final BigDecimal result = calculator.subtract(new BigDecimal("10"), new BigDecimal("3"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("7"));
        }

        @Test
        void subtractingLargerFromSmaller() {
            final BigDecimal result = calculator.subtract(new BigDecimal("3"), new BigDecimal("10"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("-7"));
        }

        @Test
        void subtractsZero() {
            final BigDecimal result = calculator.subtract(new BigDecimal("5"), BigDecimal.ZERO);
            assertThat(result).isEqualByComparingTo(new BigDecimal("5"));
        }

        @Test
        void subtractsDecimalValues() {
            final BigDecimal result = calculator.subtract(new BigDecimal("5.5"), new BigDecimal("2.2"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("3.3"));
        }

    }

    @Nested
    class MultiplyTests {

        @Test
        void multipliesTwoPositiveNumbers() {
            final BigDecimal result = calculator.multiply(new BigDecimal("3"), new BigDecimal("4"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("12"));
        }

        @Test
        void multipliesByZero() {
            final BigDecimal result = calculator.multiply(new BigDecimal("100"), BigDecimal.ZERO);
            assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void multipliesNegativeNumbers() {
            final BigDecimal result = calculator.multiply(new BigDecimal("-3"), new BigDecimal("-4"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("12"));
        }

        @Test
        void multipliesDecimalValues() {
            final BigDecimal result = calculator.multiply(new BigDecimal("2.5"), new BigDecimal("4"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("10.0"));
        }

    }

    @Nested
    class DivideTests {

        @Test
        void dividesTwoPositiveNumbers() {
            final BigDecimal result = calculator.divide(new BigDecimal("10"), new BigDecimal("2"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("5"));
        }

        @Test
        void divisionResultIsRoundedToTenDecimalPlaces() {
            final BigDecimal result = calculator.divide(new BigDecimal("1"), new BigDecimal("3"));
            assertThat(result.scale()).isEqualTo(10);
            assertThat(result).isEqualByComparingTo(new BigDecimal("0.3333333333"));
        }

        @Test
        void dividesByNegativeDivisor() {
            final BigDecimal result = calculator.divide(new BigDecimal("10"), new BigDecimal("-2"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("-5"));
        }

        @Test
        void throwsDivisionByZeroExceptionWhenDivisorIsZero() {
            assertThatThrownBy(() -> calculator.divide(new BigDecimal("10"), BigDecimal.ZERO))
                    .isInstanceOf(DivisionByZeroException.class)
                    .hasMessage("Division by zero is not allowed");
        }

    }

}
