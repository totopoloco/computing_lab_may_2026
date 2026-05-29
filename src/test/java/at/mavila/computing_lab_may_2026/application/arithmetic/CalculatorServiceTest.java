package at.mavila.computing_lab_may_2026.application.arithmetic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import at.mavila.computing_lab_may_2026.domain.arithmetic.exception.DivisionByZeroException;

/**
 * Tests for {@link CalculatorService}.
 */
@SpringBootTest
class CalculatorServiceTest {

    @Autowired
    private CalculatorService calculatorService;

    @Nested
    class AddTests {

        @Test
        void delegatesAddToCalculator() {
            final BigDecimal result = calculatorService.add(new BigDecimal("2"), new BigDecimal("3"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("5"));
        }

    }

    @Nested
    class SubtractTests {

        @Test
        void delegatesSubtractToCalculator() {
            final BigDecimal result = calculatorService.subtract(new BigDecimal("9"), new BigDecimal("4"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("5"));
        }

    }

    @Nested
    class MultiplyTests {

        @Test
        void delegatesMultiplyToCalculator() {
            final BigDecimal result = calculatorService.multiply(new BigDecimal("3"), new BigDecimal("4"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("12"));
        }

    }

    @Nested
    class DivideTests {

        @Test
        void delegatesDivideToCalculator() {
            final BigDecimal result = calculatorService.divide(new BigDecimal("10"), new BigDecimal("4"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("2.5"));
        }

        @Test
        void propagatesDivisionByZeroException() {
            assertThatThrownBy(() -> calculatorService.divide(new BigDecimal("5"), BigDecimal.ZERO))
                    .isInstanceOf(DivisionByZeroException.class);
        }

    }

}
