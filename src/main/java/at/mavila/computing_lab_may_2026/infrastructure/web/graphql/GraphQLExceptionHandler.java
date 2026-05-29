package at.mavila.computing_lab_may_2026.infrastructure.web.graphql;

import java.util.Map;

import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

import at.mavila.computing_lab_may_2026.domain.arithmetic.exception.DivisionByZeroException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;

/**
 * GraphQL exception handler that converts domain exceptions into structured GraphQL errors.
 *
 * <p>
 * Intercepts exceptions thrown during GraphQL query execution and transforms them into
 * {@link GraphQLError} responses that include a human-readable message, an error type
 * classification, and extension fields with detailed error information.
 * </p>
 *
 * <h2>Handled Exceptions</h2>
 * <ul>
 * <li>{@link DivisionByZeroException} — Returns BAD_REQUEST with errorCode "DIVISION_BY_ZERO"</li>
 * <li>{@link IllegalArgumentException} — Returns BAD_REQUEST with errorCode "INVALID_ARGUMENT"</li>
 * </ul>
 *
 * @author mavila
 * @since May 2026
 * @see DivisionByZeroException
 * @see DataFetcherExceptionResolverAdapter
 */
@Component
public class GraphQLExceptionHandler extends DataFetcherExceptionResolverAdapter {

    /**
     * Resolves an exception to a single GraphQL error.
     *
     * @param ex  the exception that was thrown
     * @param env the data fetching environment containing query context
     * @return a {@link GraphQLError} for handled exceptions, or {@code null} to delegate
     *         to the default resolver
     */
    @Override
    protected GraphQLError resolveToSingleError(final Throwable ex, final DataFetchingEnvironment env) {
        if (ex instanceof DivisionByZeroException divisionByZeroEx) {
            return buildBadRequestError(env, divisionByZeroEx.getMessage(),
                    Map.of("errorCode", "DIVISION_BY_ZERO"));
        }

        if (ex instanceof IllegalArgumentException illegalArgEx) {
            return buildBadRequestError(env, "Invalid input: %s".formatted(illegalArgEx.getMessage()),
                    Map.of(
                            "errorCode", "INVALID_ARGUMENT",
                            "reason", illegalArgEx.getMessage()));
        }

        return null;
    }

    /**
     * Builds a GraphQL error with BAD_REQUEST type and the given extensions.
     *
     * @param env        the data fetching environment
     * @param message    the error message
     * @param extensions the extension fields to include in the error
     * @return the constructed GraphQL error
     */
    private GraphQLError buildBadRequestError(final DataFetchingEnvironment env, final String message,
            final Map<String, Object> extensions) {
        return GraphqlErrorBuilder.newError(env)
                .message(message)
                .errorType(ErrorType.BAD_REQUEST)
                .extensions(extensions)
                .build();
    }

}
