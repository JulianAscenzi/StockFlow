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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"spring.profiles.include=observability", "app.auth.enabled=false"})
@Testcontainers
class NoAuthenticationObservabilityIntegrationTest {

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
    void disablingDomainAuthenticationNeverExposesMetricsOnApplicationPortOrApiOnManagementPort() throws Exception {
        assertThat(get(applicationPort, "/api/dashboard").statusCode()).isEqualTo(200);
        assertThat(get(applicationPort, "/actuator/prometheus").statusCode()).isEqualTo(404);
        assertThat(get(managementPort, "/actuator/prometheus").statusCode()).isEqualTo(200);
        for (String path : new String[]{"/api/dashboard", "/actuator/env", "/actuator/configprops", "/actuator/heapdump"}) {
            assertThat(get(managementPort, path).statusCode()).isBetween(400, 499);
        }
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
