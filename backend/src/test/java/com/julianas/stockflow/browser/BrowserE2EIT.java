package com.julianas.stockflow.browser;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.auth.enabled=true", "spring.jpa.open-in-view=false", "spring.jpa.hibernate.ddl-auto=validate",
        "app.auth.admin.email=browser@stockflow.test", "app.auth.admin.password=browser-test-password",
        "app.auth.jwt.secret=browser-test-only-secret-long-enough-for-hmac-sha256"
})
@Testcontainers
@DirtiesContext
class BrowserE2EIT {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @LocalServerPort
    int port;

    @Test
    void browserExercisesRealApplication() throws Exception {
        Path frontend = Path.of("../frontend").toAbsolutePath().normalize();
        Path report = frontend.resolve("test-results/report.json");
        Files.deleteIfExists(report);
        int frontendPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            frontendPort = socket.getLocalPort();
        }
        ProcessBuilder builder = new ProcessBuilder("npm", "run", "test:e2e:browser")
                .directory(frontend.toFile()).redirectErrorStream(true)
                .redirectOutput(Path.of("target/browser-e2e.log").toFile());
        builder.environment().put("E2E_BACKEND_URL", "http://127.0.0.1:" + port);
        builder.environment().put("E2E_FRONTEND_PORT", String.valueOf(frontendPort));
        Process process = builder.start();
        try {
            assertThat(process.waitFor(240, TimeUnit.SECONDS)).as("Playwright timeout").isTrue();
            assertThat(process.exitValue()).as("Playwright exit code").isZero();
            assertThat(report).isRegularFile();
            var stats = new ObjectMapper().readTree(Files.readString(report)).path("stats");
            assertThat(stats.path("expected").asInt()).as("Executed browser tests").isPositive();
            assertThat(stats.path("unexpected").asInt()).isZero();
            assertThat(stats.path("skipped").asInt()).isZero();
            assertThat(stats.path("flaky").asInt()).isZero();
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
    }
}
