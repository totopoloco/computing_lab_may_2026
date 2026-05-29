package at.mavila.computing_lab_may_2026.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import graphql.scalars.ExtendedScalars;

/**
 * Infrastructure configuration for GraphQL.
 *
 * <p>
 * Registers custom scalars required by the calculator schema.
 * </p>
 *
 * <h2>Custom Scalars</h2>
 * <ul>
 * <li>BigDecimal — Support for arbitrary-precision decimal numbers</li>
 * </ul>
 *
 * @author mavila
 * @since May 2026
 */
@Configuration
public class GraphQLConfig {

    /**
     * Configures custom scalars for GraphQL.
     *
     * @return the runtime wiring configurer with custom scalars
     */
    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder
                .scalar(ExtendedScalars.GraphQLBigDecimal);
    }

}
