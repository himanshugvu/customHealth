package com.example.health.core;

import com.example.health.config.EnterpriseHealthProperties;
import com.example.health.metrics.HealthMetricsCollector;
import com.example.health.cache.HealthCheckCache;
import com.example.health.resilience.CircuitBreakerManager;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Execution context for health checks containing all necessary dependencies and configuration.
 */
public final class HealthCheckContext {
    
    private final String checkName;
    private final EnterpriseHealthProperties properties;
    private final Executor executor;
    private final HealthMetricsCollector metricsCollector;
    private final HealthCheckCache cache;
    private final CircuitBreakerManager circuitBreakerManager;
    private final Duration timeout;
    private final Duration degradationThreshold;
    private final Map<String, Object> metadata;
    private final Instant executionStartTime;

    private HealthCheckContext(Builder builder) {
        this.checkName = Objects.requireNonNull(builder.checkName);
        this.properties = Objects.requireNonNull(builder.properties);
        this.executor = Objects.requireNonNull(builder.executor);
        this.metricsCollector = builder.metricsCollector;
        this.cache = builder.cache;
        this.circuitBreakerManager = builder.circuitBreakerManager;
        this.timeout = Objects.requireNonNull(builder.timeout);
        this.degradationThreshold = Objects.requireNonNull(builder.degradationThreshold);
        this.metadata = Map.copyOf(Objects.requireNonNull(builder.metadata));
        this.executionStartTime = Objects.requireNonNull(builder.executionStartTime);
    }

    public static Builder builder(String checkName) {
        return new Builder(checkName);
    }

    // Getters
    public String getCheckName() { return checkName; }
    public EnterpriseHealthProperties getProperties() { return properties; }
    public Executor getExecutor() { return executor; }
    public HealthMetricsCollector getMetricsCollector() { return metricsCollector; }
    public HealthCheckCache getCache() { return cache; }
    public CircuitBreakerManager getCircuitBreakerManager() { return circuitBreakerManager; }
    public Duration getTimeout() { return timeout; }
    public Duration getDegradationThreshold() { return degradationThreshold; }
    public Map<String, Object> getMetadata() { return metadata; }
    public Instant getExecutionStartTime() { return executionStartTime; }

    // Utility methods
    public boolean isCacheEnabled() {
        return cache != null && properties.isCacheEnabled();
    }

    public boolean isCircuitBreakerEnabled() {
        return circuitBreakerManager != null && properties.isCircuitBreakerEnabled();
    }

    public boolean isMetricsEnabled() {
        return metricsCollector != null;
    }

    public Duration getElapsedTime() {
        return Duration.between(executionStartTime, Instant.now());
    }

    public static final class Builder {
        private final String checkName;
        private EnterpriseHealthProperties properties;
        private Executor executor;
        private HealthMetricsCollector metricsCollector;
        private HealthCheckCache cache;
        private CircuitBreakerManager circuitBreakerManager;
        private Duration timeout = Duration.ofSeconds(30);
        private Duration degradationThreshold = Duration.ofSeconds(5);
        private Map<String, Object> metadata = Map.of();
        private Instant executionStartTime = Instant.now();

        private Builder(String checkName) {
            this.checkName = Objects.requireNonNull(checkName);
        }

        public Builder properties(EnterpriseHealthProperties properties) {
            this.properties = properties;
            return this;
        }

        public Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        public Builder metricsCollector(HealthMetricsCollector metricsCollector) {
            this.metricsCollector = metricsCollector;
            return this;
        }

        public Builder cache(HealthCheckCache cache) {
            this.cache = cache;
            return this;
        }

        public Builder circuitBreakerManager(CircuitBreakerManager circuitBreakerManager) {
            this.circuitBreakerManager = circuitBreakerManager;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder degradationThreshold(Duration degradationThreshold) {
            this.degradationThreshold = degradationThreshold;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder executionStartTime(Instant executionStartTime) {
            this.executionStartTime = executionStartTime;
            return this;
        }

        public HealthCheckContext build() {
            return new HealthCheckContext(this);
        }
    }
}