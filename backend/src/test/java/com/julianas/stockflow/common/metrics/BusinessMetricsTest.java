package com.julianas.stockflow.common.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessMetricsTest {
    @Test
    void preRegistersOnlyFiveBoundedCounterSeriesWithZeroEvents() {
        var registry = new SimpleMeterRegistry();
        try {
            new BusinessMetrics(registry);
            assertThat(registry.getMeters()).hasSize(5);
            assertThat(registry.find("stockflow.sales.confirmed").counter().getId().getTags()).isEmpty();
            assertThat(registry.find("stockflow.sales.rejected").counters())
                    .extracting(counter -> counter.getId().getTag("reason"))
                    .containsExactlyInAnyOrder("INSUFFICIENT_STOCK", "INACTIVE_PRODUCT");
            assertThat(registry.find("stockflow.stock.movements").counters())
                    .extracting(counter -> counter.getId().getTag("type")).containsExactlyInAnyOrder("IN", "OUT");
            registry.getMeters().forEach(meter -> {
                assertThat(meter.getId().getTags()).allMatch(tag -> tag.getKey().equals("reason") || tag.getKey().equals("type"));
                assertThat(((io.micrometer.core.instrument.Counter) meter).count()).isZero();
            });
        } finally {
            registry.close();
        }
    }

    @Test
    void unmanagedCallsNeverReportSuccess() {
        var registry = new SimpleMeterRegistry();
        try {
            var metrics = new BusinessMetrics(registry);
            assertThatThrownBy(metrics::saleConfirmed).isInstanceOf(IllegalStateException.class);
            assertThat(registry.get("stockflow.sales.confirmed").counter().count()).isZero();
        } finally {
            registry.close();
        }
    }
}
