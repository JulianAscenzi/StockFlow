package com.julianas.stockflow.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.assertj.core.api.Assertions.assertThat;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.availability.LivenessState;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties = {"app.auth.enabled=true", "app.cors.allowed-origins=https://shop.stockflow.test"})
@AutoConfigureMockMvc
@Testcontainers
class AuthApiIntegrationTest {

    private static final PostgreSQLContainer POSTGRESQL = startPostgresql();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

    @Autowired
    private ConfigurableApplicationContext context;

    @Autowired
    private HealthContributorRegistry healthContributors;

    @Test
    void databaseDownAffectsReadinessButNeverLivenessOrLeaksDetails() throws Exception {
        var database = healthContributors.unregisterContributor("db");
        assertThat(database).isNotNull();
        healthContributors.registerContributor("db", (HealthIndicator) () ->
                Health.down().withDetail("connection", "sensitive-internal-detail").build());
        try {
            mockMvc.perform(get("/actuator/health/liveness"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
            mockMvc.perform(get("/actuator/health/readiness"))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value("DOWN"))
                    .andExpect(jsonPath("$.components").doesNotExist())
                    .andExpect(jsonPath("$.details").doesNotExist());
        } finally {
            healthContributors.unregisterContributor("db");
            healthContributors.registerContributor("db", database);
        }
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void readinessStateStopsTrafficWithoutChangingLiveness() throws Exception {
        try {
            AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
            mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isServiceUnavailable());
            mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        } finally {
            AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
        }
    }

    @Test
    void livenessStateReportsBrokenProcessWithoutDatabaseDetails() throws Exception {
        try {
            AvailabilityChangeEvent.publish(context, LivenessState.BROKEN);
            mockMvc.perform(get("/actuator/health/liveness"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value("DOWN"))
                    .andExpect(jsonPath("$.components").doesNotExist());
        } finally {
            AvailabilityChangeEvent.publish(context, LivenessState.CORRECT);
        }
    }

    @Test
    void allowedCorsOriginCanSendAndReadRequestId() throws Exception {
        mockMvc.perform(options("/api/dashboard").header("Origin", "https://shop.stockflow.test")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "X-Request-ID"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("X-Request-ID")));
        mockMvc.perform(post("/api/auth/login").header("Origin", "https://shop.stockflow.test")
                        .header("X-Request-ID", "cors-login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"admin@stockflow.test\",\"password\":\"test-only-password\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "cors-login"))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("X-Request-ID")));
    }

    @Test
    void operationalPropertiesAreLoadedByIntegrationTests() {
        assertThat(environment.getProperty("management.endpoint.health.group.liveness.include")).isEqualTo("livenessState");
        assertThat(environment.getProperty("management.endpoint.health.group.readiness.include")).isEqualTo("readinessState,db");
        assertThat(environment.getProperty("spring.datasource.hikari.connection-timeout")).isEqualTo("3000");
    }

    @DynamicPropertySource
    static void configurePostgresql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Test
    void infrastructureHealthIsAnonymousAndNeverRevealsComponents() throws Exception {
        for (String path : new String[]{"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"}) {
            mockMvc.perform(get(path).header("X-Request-ID", "probe-123"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Request-ID", "probe-123"))
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components").doesNotExist())
                    .andExpect(jsonPath("$.details").doesNotExist());
        }
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void securityDoesNotOpenOtherActuatorEndpointsOrHealthSubpaths() throws Exception {
        String token = login("admin@stockflow.test", "test-only-password");
        for (String path : new String[]{"/actuator/env", "/actuator/metrics", "/actuator/prometheus", "/actuator/health/db"}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        }
        mockMvc.perform(post("/actuator/health/liveness")).andExpect(status().isUnauthorized());
    }

    @Test
    void securityRejectionsReturnRequestIdWithoutLeakingMdc() throws Exception {
        mockMvc.perform(get("/api/dashboard").header("X-Request-ID", "rejected-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-ID", "rejected-123"));
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void loginIssuesTokenThatProtectsApiRoutes() throws Exception {
        String token = login("admin@stockflow.test", "test-only-password");

        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsInvalidCredentialsAndRequestsWithoutToken() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"admin@stockflow.test\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private String login(String email, String password) throws Exception {
        String content = mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("accessToken").asString();
    }

    private static PostgreSQLContainer startPostgresql() {
        PostgreSQLContainer container = new PostgreSQLContainer("postgres:17-alpine");
        container.start();
        return container;
    }
}
