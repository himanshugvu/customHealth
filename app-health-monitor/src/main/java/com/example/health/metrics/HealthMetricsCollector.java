package com.example.health.metrics;

import com.example.health.domain.HealthCheckResult;
import com.example.health.domain.HealthStatus;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Production-grade metrics collection for health monitoring system.
 * Integrates with Micrometer for enterprise monitoring platforms.
 */
public final class HealthMetricsCollector {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthMetricsCollector.class);
    
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, HealthCheckMetrics> checkMetrics = new ConcurrentHashMap<>();
    
    // Global metrics
    private final Counter totalHealthChecks;
    private final Counter healthyChecks;
    private final Counter unhealthyChecks;
    private final Counter degradedChecks;
    private final Counter errorChecks;
    private final Timer overallLatency;
    private final Gauge activeChecks;
    private final Counter cacheHits;
    private final Counter cacheMisses;

    public HealthMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize global metrics
        this.totalHealthChecks = Counter.builder("health.checks.total")
                .description("Total number of health checks executed")
                .register(meterRegistry);
                
        this.healthyChecks = Counter.builder("health.checks.healthy")
                .description("Number of healthy health checks")
                .register(meterRegistry);
                
        this.unhealthyChecks = Counter.builder("health.checks.unhealthy")
                .description("Number of unhealthy health checks")
                .register(meterRegistry);
                
        this.degradedChecks = Counter.builder("health.checks.degraded")
                .description("Number of degraded health checks")
                .register(meterRegistry);
                
        this.errorChecks = Counter.builder("health.checks.error")
                .description("Number of health checks that failed with errors")
                .register(meterRegistry);
                
        this.overallLatency = Timer.builder("health.checks.latency")
                .description("Overall health check execution latency")
                .register(meterRegistry);
                
        this.activeChecks = Gauge.builder("health.checks.active")
                .description("Number of currently active health check types")
                .register(meterRegistry, checkMetrics, ConcurrentHashMap::size);
                
        this.cacheHits = Counter.builder("health.cache.hits")
                .description("Number of health check cache hits")
                .register(meterRegistry);
                
        this.cacheMisses = Counter.builder("health.cache.misses")
                .description("Number of health check cache misses")
                .register(meterRegistry);
    }

    /**
     * Records execution of a health check result.
     */
    public void recordHealthCheck(HealthCheckResult result) {
        String checkName = result.getCheckName();
        HealthStatus status = result.getStatus();
        Duration latency = result.getLatency();
        
        // Update global metrics
        totalHealthChecks.increment();
        overallLatency.record(latency);
        
        switch (status) {
            case UP -> healthyChecks.increment();
            case DEGRADED -> degradedChecks.increment();
            case DOWN -> unhealthyChecks.increment();
            case UNKNOWN -> errorChecks.increment();
        }
        
        // Update per-check metrics
        HealthCheckMetrics checkMetric = getOrCreateCheckMetrics(checkName);
        checkMetric.record(result);
        
        logger.trace("Recorded health check metrics for {}: status={}, latency={}ms", 
                    checkName, status, latency.toMillis());
    }

    /**
     * Records cache hit/miss statistics.
     */
    public void recordCacheHit(String checkName) {
        cacheHits.increment(Tags.of("check", checkName));
    }

    public void recordCacheMiss(String checkName) {
        cacheMisses.increment(Tags.of("check", checkName));
    }

    /**
     * Records circuit breaker state changes.
     */
    public void recordCircuitBreakerStateChange(String checkName, String fromState, String toState) {
        Counter.builder("health.circuit_breaker.state_changes")
                .description("Circuit breaker state changes")
                .tags("check", checkName, "from", fromState, "to", toState)
                .register(meterRegistry)
                .increment();
        
        logger.info("Circuit breaker for {} transitioned from {} to {}", checkName, fromState, toState);
    }

    /**
     * Records health check execution errors.
     */
    public void recordHealthCheckError(String checkName, String errorType, Duration latency) {
        Counter.builder("health.checks.errors")
                .description("Health check execution errors")
                .tags("check", checkName, "error_type", errorType)
                .register(meterRegistry)
                .increment();
                
        Timer.builder("health.checks.error.latency")
                .description("Latency of failed health checks")
                .tags("check", checkName, "error_type", errorType)
                .register(meterRegistry)
                .record(latency);
    }

    /**
     * Gets metrics for a specific health check.
     */
    public HealthCheckMetrics getCheckMetrics(String checkName) {
        return checkMetrics.get(checkName);
    }

    /**
     * Gets summary of all health check metrics.
     */
    public HealthMetricsSummary getSummary() {
        return HealthMetricsSummary.builder()
                .totalChecks((long) totalHealthChecks.count())
                .healthyChecks((long) healthyChecks.count())
                .unhealthyChecks((long) unhealthyChecks.count())
                .degradedChecks((long) degradedChecks.count())
                .errorChecks((long) errorChecks.count())
                .averageLatency(Duration.ofNanos((long) overallLatency.mean(java.util.concurrent.TimeUnit.NANOSECONDS)))
                .cacheHitRate(calculateCacheHitRate())
                .activeCheckTypes(checkMetrics.size())
                .build();
    }

    private HealthCheckMetrics getOrCreateCheckMetrics(String checkName) {
        return checkMetrics.computeIfAbsent(checkName, name -> 
                new HealthCheckMetrics(name, meterRegistry));
    }

    private double calculateCacheHitRate() {
        double hits = cacheHits.count();
        double misses = cacheMisses.count();
        double total = hits + misses;
        return total == 0 ? 0.0 : hits / total;
    }

    /**
     * Metrics for individual health check types.
     */
    public static final class HealthCheckMetrics {
        private final String checkName;
        private final Counter executions;
        private final Counter successes;
        private final Counter failures;
        private final Counter degraded;
        private final Timer executionTime;
        private final Gauge currentStatus;
        private final AtomicLong lastStatusValue = new AtomicLong(1); // 1=UP, 0.5=DEGRADED, 0=DOWN
        
        public HealthCheckMetrics(String checkName, MeterRegistry meterRegistry) {
            this.checkName = checkName;
            
            Tags tags = Tags.of("check", checkName);
            
            this.executions = Counter.builder("health.check.executions")
                    .description("Number of executions for this health check")
                    .tags(tags)
                    .register(meterRegistry);
                    
            this.successes = Counter.builder("health.check.successes")
                    .description("Number of successful executions for this health check")
                    .tags(tags)
                    .register(meterRegistry);
                    
            this.failures = Counter.builder("health.check.failures")
                    .description("Number of failed executions for this health check")
                    .tags(tags)
                    .register(meterRegistry);
                    
            this.degraded = Counter.builder("health.check.degraded")
                    .description("Number of degraded executions for this health check")
                    .tags(tags)
                    .register(meterRegistry);
                    
            this.executionTime = Timer.builder("health.check.execution_time")
                    .description("Execution time for this health check")
                    .tags(tags)
                    .register(meterRegistry);
                    
            this.currentStatus = Gauge.builder("health.check.status")
                    .description("Current status of health check (1=UP, 0.5=DEGRADED, 0=DOWN)")
                    .tags(tags)
                    .register(meterRegistry, lastStatusValue, AtomicLong::get);
        }

        void record(HealthCheckResult result) {
            executions.increment();
            executionTime.record(result.getLatency());
            
            switch (result.getStatus()) {
                case UP -> {
                    successes.increment();
                    lastStatusValue.set(100); // 1.0 as long
                }
                case DEGRADED -> {
                    degraded.increment();
                    lastStatusValue.set(50); // 0.5 as long
                }
                case DOWN, UNKNOWN -> {
                    failures.increment();
                    lastStatusValue.set(0); // 0.0 as long
                }
            }
        }

        public String getCheckName() { return checkName; }
        public double getExecutionCount() { return executions.count(); }
        public double getSuccessCount() { return successes.count(); }
        public double getFailureCount() { return failures.count(); }
        public double getDegradedCount() { return degraded.count(); }
        public double getSuccessRate() {
            double total = getExecutionCount();
            return total == 0 ? 0.0 : getSuccessCount() / total;
        }
        public Duration getAverageExecutionTime() {
            return Duration.ofNanos((long) executionTime.mean(java.util.concurrent.TimeUnit.NANOSECONDS));
        }
    }

    /**
     * Summary of all health metrics.
     */
    public static final class HealthMetricsSummary {
        private final long totalChecks;
        private final long healthyChecks;
        private final long unhealthyChecks;
        private final long degradedChecks;
        private final long errorChecks;
        private final Duration averageLatency;
        private final double cacheHitRate;
        private final int activeCheckTypes;

        private HealthMetricsSummary(Builder builder) {
            this.totalChecks = builder.totalChecks;
            this.healthyChecks = builder.healthyChecks;
            this.unhealthyChecks = builder.unhealthyChecks;
            this.degradedChecks = builder.degradedChecks;
            this.errorChecks = builder.errorChecks;
            this.averageLatency = builder.averageLatency;
            this.cacheHitRate = builder.cacheHitRate;
            this.activeCheckTypes = builder.activeCheckTypes;
        }

        public static Builder builder() { return new Builder(); }

        // Getters
        public long getTotalChecks() { return totalChecks; }
        public long getHealthyChecks() { return healthyChecks; }
        public long getUnhealthyChecks() { return unhealthyChecks; }
        public long getDegradedChecks() { return degradedChecks; }
        public long getErrorChecks() { return errorChecks; }
        public Duration getAverageLatency() { return averageLatency; }
        public double getCacheHitRate() { return cacheHitRate; }
        public int getActiveCheckTypes() { return activeCheckTypes; }
        
        public double getHealthRate() {
            return totalChecks == 0 ? 0.0 : (double) healthyChecks / totalChecks;
        }

        public static final class Builder {
            private long totalChecks;
            private long healthyChecks;
            private long unhealthyChecks;
            private long degradedChecks;
            private long errorChecks;
            private Duration averageLatency = Duration.ZERO;
            private double cacheHitRate;
            private int activeCheckTypes;

            public Builder totalChecks(long totalChecks) { this.totalChecks = totalChecks; return this; }
            public Builder healthyChecks(long healthyChecks) { this.healthyChecks = healthyChecks; return this; }
            public Builder unhealthyChecks(long unhealthyChecks) { this.unhealthyChecks = unhealthyChecks; return this; }
            public Builder degradedChecks(long degradedChecks) { this.degradedChecks = degradedChecks; return this; }
            public Builder errorChecks(long errorChecks) { this.errorChecks = errorChecks; return this; }
            public Builder averageLatency(Duration averageLatency) { this.averageLatency = averageLatency; return this; }
            public Builder cacheHitRate(double cacheHitRate) { this.cacheHitRate = cacheHitRate; return this; }
            public Builder activeCheckTypes(int activeCheckTypes) { this.activeCheckTypes = activeCheckTypes; return this; }

            public HealthMetricsSummary build() { return new HealthMetricsSummary(this); }
        }
    }
}