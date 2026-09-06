package com.julianas.stockflow.common.config;

import java.net.URI;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Render exposes PostgreSQL URLs with the {@code postgresql://} scheme, while
 * the JDBC driver requires {@code jdbc:postgresql://}.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String RENDER_DATABASE_URL_PREFIX = "postgresql://";
    private static final int DEFAULT_POSTGRESQL_PORT = 5432;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || !databaseUrl.startsWith(RENDER_DATABASE_URL_PREFIX)) {
            return;
        }

        URI uri = URI.create(databaseUrl);
        String[] credentials = uri.getUserInfo().split(":", 2);
        if (credentials.length != 2) {
            throw new IllegalStateException("DATABASE_URL must include a PostgreSQL user and password");
        }

        int port = uri.getPort() == -1 ? DEFAULT_POSTGRESQL_PORT : uri.getPort();
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getRawPath();
        if (uri.getRawQuery() != null) {
            jdbcUrl += "?" + uri.getRawQuery();
        }

        environment.getPropertySources().addFirst(new MapPropertySource("renderDatabaseUrl", Map.of(
                "spring.datasource.url", jdbcUrl,
                "spring.datasource.username", credentials[0],
                "spring.datasource.password", credentials[1])));
    }
}
