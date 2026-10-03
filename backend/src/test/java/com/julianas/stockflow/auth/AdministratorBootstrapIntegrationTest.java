package com.julianas.stockflow.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.auth.enabled=true")
@Testcontainers
class AdministratorBootstrapIntegrationTest {
    private static final PostgreSQLContainer DATABASE = startDatabase();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired private AuthService bootstrap;
    @Autowired private ApplicationUserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @AfterEach
    void restoreAdministrator() {
        bootstrap.createInitialAdministrator();
    }

    @Test
    void concurrentNodesSerializeEvenWithDifferentAdministratorEmails() throws Exception {
        users.deleteAllInBatch();
        CountDownLatch encoding = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        PasswordEncoder gatedEncoder = new PasswordEncoder() {
            @Override public String encode(CharSequence password) {
                encoding.countDown();
                try {
                    if (!commit.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Commit gate timeout");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return encoder.encode(password);
            }
            @Override public boolean matches(CharSequence password, String encoded) {
                return encoder.matches(password, encoded);
            }
        };
        AuthService firstNode = new AuthService(users, gatedEncoder,
                new AuthProperties(new AuthProperties.Admin("first@stockflow.test", "first-test-password"), null),
                Clock.systemUTC());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> transaction.executeWithoutResult(status -> firstNode.createInitialAdministrator()));
            assertThat(encoding.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> bootstrap.createInitialAdministrator());
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            boolean waiting = false;
            while (System.nanoTime() < deadline) {
                waiting = Boolean.TRUE.equals(jdbc.queryForObject(
                        "select exists(select 1 from pg_locks where locktype = 'advisory' and not granted)", Boolean.class));
                if (waiting) break;
                Thread.onSpinWait();
            }
            assertThat(waiting).as("Second node waits on the real PostgreSQL bootstrap lock").isTrue();
            assertThat(second.isDone()).isFalse();
            commit.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertThat(users.count()).isEqualTo(1);
            var administrator = users.findByEmailIgnoreCase("first@stockflow.test").orElseThrow();
            assertThat(encoder.matches("first-test-password", administrator.getPasswordHash())).isTrue();
            bootstrap.createInitialAdministrator();
            assertThat(users.count()).isEqualTo(1);
            assertThat(users.findByEmailIgnoreCase("admin@stockflow.test")).isEmpty();
        } finally {
            commit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void unexpectedFailureRollsBackAndReleasesLockInsteadOfBeingSwallowed() {
        users.deleteAllInBatch();
        AuthService invalid = new AuthService(users, encoder,
                new AuthProperties(new AuthProperties.Admin("invalid@stockflow.test", ""), null), Clock.systemUTC());
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> invalid.createInitialAdministrator()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("APP_ADMIN_PASSWORD");
        assertThat(users.count()).isZero();
        bootstrap.createInitialAdministrator();
        assertThat(users.count()).isEqualTo(1);
    }

    private static PostgreSQLContainer startDatabase() {
        PostgreSQLContainer container = new PostgreSQLContainer("postgres:17-alpine");
        container.start();
        return container;
    }
}
