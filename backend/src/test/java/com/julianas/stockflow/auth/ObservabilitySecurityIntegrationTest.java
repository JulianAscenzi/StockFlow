package com.julianas.stockflow.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"spring.profiles.include=observability", "app.auth.enabled=true"})
@Testcontainers
class ObservabilitySecurityIntegrationTest {

    private static final PostgreSQLContainer POSTGRESQL = startPostgresql();

    @DynamicPropertySource
    static void configurePostgresql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @LocalServerPort
    private int applicationPort;
    @Value("${local.management.port}")
    private int managementPort;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void metricsAreOnlyAccessibleOnManagementListener() throws Exception {
        var response = get(managementPort, "/actuator/prometheus");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("jvm_memory_used_bytes", "process_uptime_seconds", "hikaricp_connections_max");
        assertThat(get(applicationPort, "/actuator/prometheus").statusCode()).isEqualTo(401);
        assertThat(get(applicationPort, "/api/dashboard").statusCode()).isEqualTo(401);
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + applicationPort + "/actuator/prometheus"))
                .header("X-Forwarded-Port", "9091").GET().build();
        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    }

    @Test
    void managementDoesNotExposeSensitiveEndpointsOrDomainApi() throws Exception {
        for (String path : new String[]{"/actuator/env", "/actuator/configprops", "/actuator/heapdump", "/actuator/metrics",
                "/actuator/health/db", "/actuator", "/api/dashboard"}) {
            assertThat(get(managementPort, path).statusCode()).isBetween(400, 499);
        }
    }

    @Test
    void healthEndpointsRemainPublicAndSanitized() throws Exception {
        for (String path : new String[]{"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"}) {
            var response = get(managementPort, path);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("UP").doesNotContain("components", "details");
        }
    }

    @Test
    void httpHistogramUsesRouteTemplatesWithoutIdentifiersOrRequestIds() throws Exception {
        var login = HttpRequest.newBuilder(URI.create("http://localhost:" + applicationPort + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"admin@stockflow.test\",\"password\":\"test-only-password\"}"))
                .build();
        var response = client.send(login, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        String token = new ObjectMapper().readTree(response.body()).get("accessToken").asString();
        for (long id : new long[]{82728391, 82728392}) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + applicationPort + "/api/products/" + id))
                    .header("Authorization", "Bearer " + token).header("X-Request-ID", "cardinality-check-secret").GET().build();
            assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
        }
        String metrics = get(managementPort, "/actuator/prometheus").body();
        assertThat(metrics).contains("http_server_requests_seconds_bucket", "uri=\"/api/products/{id}\"")
                .doesNotContain("82728391", "82728392", "cardinality-check-secret", "email=", "sku=", "requestId=");
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static PostgreSQLContainer startPostgresql() {
        PostgreSQLContainer container = new PostgreSQLContainer("postgres:17-alpine");
        container.start();
        return container;
    }
}
