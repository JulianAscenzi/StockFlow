package com.julianas.stockflow.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class DatabaseUrlEnvironmentPostProcessorTest {

    private final DatabaseUrlEnvironmentPostProcessor postProcessor = new DatabaseUrlEnvironmentPostProcessor();

    @Test
    void convertsRenderPostgresqlUrlIntoDatasourceProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://stockflow:secret@database.internal:5432/stockflow?sslmode=require");

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://database.internal:5432/stockflow?sslmode=require");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("stockflow");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("secret");
    }

    @Test
    void usesDefaultPostgresqlPortWhenRenderUrlOmitsIt() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://stockflow:secret@database.internal/stockflow");

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://database.internal:5432/stockflow");
    }

    @Test
    void leavesLocalConfigurationUntouchedWithoutRenderUrl() {
        MockEnvironment environment = new MockEnvironment();

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.datasource.url")).isNull();
    }
}
