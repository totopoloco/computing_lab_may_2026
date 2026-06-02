package at.mavila.computing_lab_may_2026.infrastructure.web.graphql;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/**
 * GraphQL interceptor that logs each incoming request for observability and attack-pattern detection.
 *
 * <p>
 * Fires before every GraphQL execution and emits a single structured {@code INFO} log line
 * containing the caller's effective remote IP, the request URI, the GraphQL document, and any
 * variables. The log format uses {@code key="value"} pairs so Loki/LogQL can filter on individual
 * fields without a JSON parser.
 * </p>
 *
 * <h2>Remote-IP resolution order</h2>
 * <ol>
 * <li>{@code X-Forwarded-For} — first token (leftmost client, de-facto standard behind proxies)</li>
 * <li>{@code X-Real-IP} — set by nginx when XFF is absent</li>
 * <li>{@code "unknown"} — no proxy header present (direct connection or stripped by proxy)</li>
 * </ol>
 *
 * <h2>Payload truncation</h2>
 * Documents and variable maps longer than {@value #MAX_PAYLOAD_LENGTH} characters are truncated
 * to prevent a crafted large payload from flooding the log stream.
 *
 * @author mavila
 * @since June 2026
 */
@Component
public class GraphQlRequestLoggingInterceptor implements WebGraphQlInterceptor {

  private static final Logger LOG = LoggerFactory.getLogger(GraphQlRequestLoggingInterceptor.class);

  /** Maximum number of characters logged for document or variables before truncation. */
  static final int MAX_PAYLOAD_LENGTH = 500;

  /**
   * Logs the request then delegates to the next interceptor in the chain.
   *
   * @param request the incoming GraphQL request, including HTTP headers and the parsed document
   * @param chain   the remaining interceptor chain
   * @return the response produced by the downstream chain
   */
  @Override
  public Mono<WebGraphQlResponse> intercept(final WebGraphQlRequest request, final Chain chain) {
    if (!request.getDocument().contains("__schema")) {
      final String remoteIp = resolveRemoteIp(request.getHeaders());
      LOG.info(
          "GRAPHQL_REQUEST remoteIp=\"{}\" uri=\"{}\" document=\"{}\" variables=\"{}\"",
          remoteIp,
          request.getUri(),
          truncate(request.getDocument()),
          truncate(request.getVariables().toString()));
    }
    return chain.next(request);
  }

  /**
   * Resolves the effective remote IP from proxy headers, falling back to {@code "unknown"}.
   *
   * @param headers the HTTP headers of the incoming request
   * @return the resolved IP string; never {@code null}
   */
  private String resolveRemoteIp(final HttpHeaders headers) {
    final List<String> forwarded = headers.get("X-Forwarded-For");
    if (Objects.nonNull(forwarded) && !forwarded.isEmpty()) {
      return forwarded.get(0).split(",")[0].trim();
    }
    final List<String> realIp = headers.get("X-Real-IP");
    if (Objects.nonNull(realIp) && !realIp.isEmpty()) {
      return realIp.get(0);
    }
    return "unknown";
  }

  /**
   * Truncates a string to {@value #MAX_PAYLOAD_LENGTH} characters, appending a marker when cut.
   *
   * @param value the string to truncate; may be {@code null}
   * @return the original value if short enough, a truncated version otherwise, or {@code null}
   */
  private String truncate(final String value) {
    if (Objects.isNull(value) || value.length() <= MAX_PAYLOAD_LENGTH) {
      return value;
    }
    return "%s...[truncated]".formatted(value.substring(0, MAX_PAYLOAD_LENGTH));
  }

}
