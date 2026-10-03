package com.julianas.stockflow.common.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;

@Configuration(proxyBeanMethods = false)
@Profile("observability")
@EnableScheduling
public class ReadinessMetrics {
    private final HealthEndpoint health;
    private volatile double ready = -1;
    private volatile double checkedAt;

    public ReadinessMetrics(HealthEndpoint health, MeterRegistry registry) {
        this.health = health;
        Gauge.builder("stockflow.readiness", this, state -> state.ready).register(registry);
        Gauge.builder("stockflow.readiness.checked.timestamp.seconds", this, state -> state.checkedAt).register(registry);
    }

    // Do not execute DB health checks on the Prometheus scrape thread.
    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    public void refresh() {
        var result = health.healthForPath("readiness");
        ready = result != null && "UP".equals(result.getStatus().getCode()) ? 1 : 0;
        checkedAt = Instant.now().getEpochSecond();
    }
}
