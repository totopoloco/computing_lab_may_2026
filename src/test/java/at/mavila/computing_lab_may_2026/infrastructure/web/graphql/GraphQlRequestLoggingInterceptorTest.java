package at.mavila.computing_lab_may_2026.infrastructure.web.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Integration tests for {@link GraphQlRequestLoggingInterceptor}.
 *
 * <p>
 * {@code RANDOM_PORT} is required because {@link GraphQlRequestLoggingInterceptor} implements
 * {@code WebGraphQlInterceptor}, which only fires on the real HTTP transport path (Tomcat →
 * DispatcherServlet → GraphQlHttpHandler → WebGraphQlService). A plain {@code GraphQlTester}
 * backed by {@code ExecutionGraphQlService} bypasses the web layer entirely.
 * </p>
 *
 * <p>
 * {@link OutputCaptureExtension} is used for log assertions because it captures
 * {@code System.out} at the JVM level and therefore picks up lines written by Tomcat
 * worker threads as well as the test thread. The captured handle is injected into
 * {@link #setUp} and stored as a field so individual test methods need no parameters.
 * </p>
 *
 * @author mavila
 * @since June 2026
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class GraphQlRequestLoggingInterceptorTest {

  @LocalServerPort
  private int port;

  @Value("${spring.graphql.http.path}")
  private String graphQlPath;

  private RestClient client;
  private CapturedOutput output;

  /**
   * Builds a base-URI-aware {@link RestClient} and captures the current test's log output.
   *
   * @param capturedOutput the log-capture handle provided by {@link OutputCaptureExtension}
   */
  @BeforeEach
  void setUp(final CapturedOutput capturedOutput) {
    this.output = capturedOutput;
    this.client = RestClient.create("http://localhost:" + port);
  }

  /**
   * Posts a raw GraphQL query document to the configured endpoint.
   *
   * @param query the GraphQL document string
   */
  private void sendQuery(final String query) {
    client.post()
        .uri(graphQlPath)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("query", query))
        .retrieve()
        .toBodilessEntity();
  }

  @Nested
  class LogStructure {

    @Test
    void logsGraphQlRequestMarker() {
      sendQuery("{ add(a: 1, b: 2) }");

      assertThat(output.toString()).contains("GRAPHQL_REQUEST");
    }

    @Test
    void logsRemoteIpField() {
      sendQuery("{ add(a: 1, b: 2) }");

      assertThat(output.toString()).contains("remoteIp=");
    }

    @Test
    void logsUriField() {
      sendQuery("{ add(a: 1, b: 2) }");

      assertThat(output.toString()).contains("uri=");
    }

    @Test
    void logsDocumentField() {
      sendQuery("{ add(a: 3, b: 4) }");

      assertThat(output.toString()).contains("document=");
    }

  }

  @Nested
  class IntrospectionFiltering {

    @Test
    void doesNotLogIntrospectionQuery() {
      sendQuery("{ __schema { queryType { name } } }");

      assertThat(output.toString()).doesNotContain("GRAPHQL_REQUEST");
    }

  }

  @Nested
  class Truncation {

    @Test
    void appendsTruncationMarkerWhenDocumentExceedsLimit() {
      final String longPadding = "x".repeat(GraphQlRequestLoggingInterceptor.MAX_PAYLOAD_LENGTH + 50);
      sendQuery("{ add(a: 1, b: 2) } # " + longPadding);

      assertThat(output.toString()).contains("[truncated]");
    }

  }

}
