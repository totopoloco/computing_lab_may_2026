package at.mavila.computing_lab_may_2026.infrastructure.web.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;

/**
 * GraphQL integration tests for {@link ArithmeticController}.
 */
@SpringBootTest
@AutoConfigureGraphQlTester
class ArithmeticControllerTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @Nested
    class AddQueryTests {

        @Test
        void addsTwoNumbers() {
            graphQlTester.document("{ add(a: \"3\", b: \"4\") }")
                    .execute()
                    .path("add")
                    .entity(BigDecimal.class)
                    .satisfies(result -> assertThat(result).isEqualByComparingTo(new BigDecimal("7")));
        }

        @Test
        void addsDecimalNumbers() {
            graphQlTester.document("{ add(a: \"1.5\", b: \"2.5\") }")
                    .execute()
                    .path("add")
                    .entity(BigDecimal.class)
                    .satisfies(result -> assertThat(result).isEqualByComparingTo(new BigDecimal("4.0")));
        }

    }

    @Nested
    class SubtractQueryTests {

        @Test
        void subtractsTwoNumbers() {
            graphQlTester.document("{ subtract(a: \"10\", b: \"3\") }")
                    .execute()
                    .path("subtract")
                    .entity(BigDecimal.class)
                    .satisfies(result -> assertThat(result).isEqualByComparingTo(new BigDecimal("7")));
        }

    }

    @Nested
    class MultiplyQueryTests {

        @Test
        void multipliesTwoNumbers() {
            graphQlTester.document("{ multiply(a: \"6\", b: \"7\") }")
                    .execute()
                    .path("multiply")
                    .entity(BigDecimal.class)
                    .satisfies(result -> assertThat(result).isEqualByComparingTo(new BigDecimal("42")));
        }

    }

    @Nested
    class DivideQueryTests {

        @Test
        void dividesTwoNumbers() {
            graphQlTester.document("{ divide(a: \"10\", b: \"4\") }")
                    .execute()
                    .path("divide")
                    .entity(BigDecimal.class)
                    .satisfies(result -> assertThat(result).isEqualByComparingTo(new BigDecimal("2.5")));
        }

        @Test
        void returnsDivisionByZeroError() {
            graphQlTester.document("{ divide(a: \"10\", b: \"0\") }")
                    .execute()
                    .errors()
                    .satisfy(errors -> {
                        assertThat(errors).hasSize(1);
                        assertThat(errors.getFirst().getMessage()).contains("Division by zero");
                    });
        }

    }

}
