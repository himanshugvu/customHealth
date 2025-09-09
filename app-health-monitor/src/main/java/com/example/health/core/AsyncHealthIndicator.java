package com.example.health.core;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Base class for asynchronous health indicators that prevent blocking HTTP request threads.
 * Provides timeout handling and fallback responses for unresponsive dependencies.
 */
public abstract class AsyncHealthIndicator implements HealthIndicator {

    private final Duration timeout;
    private final String componentName;

    protected AsyncHealthIndicator(Duration timeout, String componentName) {
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(5);
        this.componentName = componentName;
    }

    @Override
    public Health health() {
        long started = System.nanoTime();
        
        try {
            // Execute the health check asynchronously with timeout
            CompletableFuture<Health> healthFuture = CompletableFuture.supplyAsync(this::doHealthCheck);
            
            Health result = healthFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return result;
            
        } catch (TimeoutException e) {
            long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
            return Health.down()
                    .withDetail("error", "Health check timed out after " + timeout.toMillis() + "ms")
                    .withDetail("latencyMs", ms)
                    .withDetail("component", componentName)
                    .withDetail("timeout", true)
                    .build();
                    
        } catch (Exception e) {
            long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
            return Health.down()
                    .withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .withDetail("latencyMs", ms)
                    .withDetail("component", componentName)
                    .build();
        }
    }

    /**
     * Perform the actual health check. This method should be implemented by subclasses
     * to check the specific dependency (database, Kafka, external service, etc.)
     * 
     * @return Health status
     */
    protected abstract Health doHealthCheck();
}