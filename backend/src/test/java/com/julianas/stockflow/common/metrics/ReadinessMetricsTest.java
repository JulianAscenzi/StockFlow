package com.julianas.stockflow.common.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.actuate.endpoint.SystemHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReadinessMetricsTest {
    @Test
    void scrapeReadsCachedUnknownStateWithoutCallingDatabaseHealth() {
        var registry = new SimpleMeterRegistry();
        try {
            var health = mock(HealthEndpoint.class);
            new ReadinessMetrics(health, registry);
            assertThat(registry.get("stockflow.readiness").gauge().value()).isEqualTo(-1);
            assertThat(registry.get("stockflow.readiness.checked.timestamp.seconds").gauge().value()).isZero();
            verifyNoInteractions(health);
        } finally {
            registry.close();
        }
    }

    @Test
    void nativeReadinessUpdatesCachedGaugeAndFreshnessAcrossRecovery() {
        var registry = new SimpleMeterRegistry();
        try {
            var health = mock(HealthEndpoint.class);
            var descriptor = mock(SystemHealthDescriptor.class);
            when(health.healthForPath("readiness")).thenReturn(descriptor);
            when(descriptor.getStatus()).thenReturn(Status.UP, Status.DOWN, Status.UP);
            var metrics = new ReadinessMetrics(health, registry);
            for (double expected : new double[]{1, 0, 1}) {
                metrics.refresh();
                assertThat(registry.get("stockflow.readiness").gauge().value()).isEqualTo(expected);
                assertThat(registry.get("stockflow.readiness.checked.timestamp.seconds").gauge().value()).isPositive();
            }
            verify(health, times(3)).healthForPath("readiness");
        } finally {
            registry.close();
        }
    }
}
