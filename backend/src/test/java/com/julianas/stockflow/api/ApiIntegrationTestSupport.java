package com.julianas.stockflow.api;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class ApiIntegrationTestSupport {

    static final PostgreSQLContainer POSTGRESQL = startPostgresql();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configurePostgresql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    private static PostgreSQLContainer startPostgresql() {
        PostgreSQLContainer container = new PostgreSQLContainer("postgres:17-alpine");
        container.start();
        return container;
    }

    @AfterEach
    void clearDatabase() {
        // The product API has no delete endpoint, so this is the only API-independent cleanup.
        jdbcTemplate.execute("TRUNCATE TABLE sale_items, sales, stock_movements, products, categories RESTART IDENTITY");
    }
}
