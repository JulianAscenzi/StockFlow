package com.julianas.stockflow.auth;

import com.julianas.stockflow.auth.api.AuthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.auth.enabled=false", "app.auth.jwt.secret="})
@AutoConfigureMockMvc
@Testcontainers
class NoAuthenticationApiIntegrationTest {

    private static final PostgreSQLContainer POSTGRESQL = startPostgresql();

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void configurePostgresql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Test
    void startsWithoutJwtSecretAndDoesNotExposeAuthenticationComponents() throws Exception {
        assertThat(applicationContext.getBeansOfType(JwtService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(AuthService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(AuthController.class)).isEmpty();

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login"))
                .andExpect(status().isNotFound());
    }

    private static PostgreSQLContainer startPostgresql() {
        PostgreSQLContainer container = new PostgreSQLContainer("postgres:17-alpine");
        container.start();
        return container;
    }
}
