package com.example.health.core;

import com.example.health.domain.HealthCheckResult;

import java.util.concurrent.CompletableFuture;

/**
 * Core abstraction for health checking operations.
 * 
 * This interface defines the contract for all health checking implementations.
 * It supports both synchronous and asynchronous execution patterns, providing
 * flexibility for different integration scenarios.
 * 
 * Implementations should be:
 * - Thread-safe
 * - Non-blocking when possible
 * - Resilient to failures
 * - Properly instrumented for observability
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
public interface HealthChecker {
    
    /**
     * Performs a health check operation synchronously.
     * 
     * This method should never throw exceptions - all errors should be
     * captured in the returned HealthCheckResult.
     * 
     * @return the health check result, never null
     */
    HealthCheckResult checkHealth();
    
    /**
     * Performs a health check operation asynchronously.
     * 
     * The default implementation delegates to the synchronous method
     * using CompletableFuture.supplyAsync(). Implementations can override
     * this for true asynchronous behavior.
     * 
     * @return a CompletableFuture containing the health check result
     */
    default CompletableFuture<HealthCheckResult> checkHealthAsync() {
        return CompletableFuture.supplyAsync(this::checkHealth);
    }
    
    /**
     * Returns the name of the component being checked.
     * 
     * @return component name, never null or empty
     */
    String getComponentName();
    
    /**
     * Returns the type of the component being checked.
     * 
     * @return component type (e.g., "database", "cache", "messaging"), never null or empty
     */
    String getComponentType();
    
    /**
     * Indicates whether this health checker is currently enabled.
     * 
     * @return true if enabled, false otherwise
     */
    default boolean isEnabled() {
        return true;
    }
}