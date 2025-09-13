package com.example.health.core;

import com.example.health.domain.HealthCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * Abstract base class for health checker implementations.
 * 
 * This class provides common functionality including:
 * - Execution timing
 * - Error handling and logging
 * - Template method pattern for health checking
 * - Consistent result building
 * 
 * Subclasses need only implement the doHealthCheck() method to provide
 * their specific health checking logic.
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
public abstract class AbstractHealthChecker implements HealthChecker {
    
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    private final String componentName;
    private final String componentType;
    private final Duration timeout;
    
    protected AbstractHealthChecker(String componentName, String componentType) {
        this(componentName, componentType, Duration.ofSeconds(5));
    }
    
    protected AbstractHealthChecker(String componentName, String componentType, Duration timeout) {
        this.componentName = Objects.requireNonNull(componentName, "Component name cannot be null");
        this.componentType = Objects.requireNonNull(componentType, "Component type cannot be null");
        this.timeout = Objects.requireNonNull(timeout, "Timeout cannot be null");
        
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
    }
    
    @Override
    public final HealthCheckResult checkHealth() {
        logger.debug("Starting health check for component '{}' of type '{}'", componentName, componentType);
        
        Instant start = Instant.now();
        
        try {
            HealthCheckResult result = executeWithTimeout();
            logResult(result);
            return result;
            
        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            logger.warn("Health check failed for component '{}': {}", componentName, e.getMessage(), e);
            
            return HealthCheckResult.failure(componentName, componentType, elapsed, e)
                    .withDetail("errorType", e.getClass().getSimpleName())
                    .withDetail("timeout", e instanceof TimeoutException)
                    .build();
        }
    }
    
    /**
     * Executes the health check with timeout protection.
     */
    private HealthCheckResult executeWithTimeout() throws Exception {
        Instant start = Instant.now();
        
        try {
            // Execute the actual health check
            HealthCheckResult result = doHealthCheck();
            
            // Verify execution time didn't exceed timeout
            Duration elapsed = Duration.between(start, Instant.now());
            if (elapsed.compareTo(timeout) > 0) {
                throw new TimeoutException("Health check exceeded timeout of " + timeout.toMillis() + "ms");
            }
            
            return result;
            
        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            
            // If it's already a timeout or we exceeded our timeout, preserve/create timeout exception
            if (e instanceof TimeoutException || elapsed.compareTo(timeout) > 0) {
                throw new TimeoutException("Health check timed out after " + elapsed.toMillis() + "ms (limit: " + timeout.toMillis() + "ms)");
            }
            
            throw e;
        }
    }
    
    /**
     * Performs the actual health check logic.
     * 
     * Implementations should:
     * - Be quick and efficient
     * - Not throw exceptions for expected failures (capture in result)
     * - Use the HealthCheckResult builder for consistent results
     * 
     * @return the health check result
     * @throws Exception for unexpected technical errors
     */
    protected abstract HealthCheckResult doHealthCheck() throws Exception;
    
    /**
     * Logs the health check result in structured format.
     */
    private void logResult(HealthCheckResult result) {
        if (result.isHealthy()) {
            logger.info("component={} type={} status=UP latency_ms={}", 
                       componentName, componentType, result.getLatency().toMillis());
        } else {
            String errorMsg = result.getError() != null ? result.getError().getMessage() : "Unknown error";
            logger.warn("component={} type={} status=DOWN latency_ms={} error=\"{}\"", 
                       componentName, componentType, result.getLatency().toMillis(), errorMsg);
        }
    }
    
    @Override
    public String getComponentName() {
        return componentName;
    }
    
    @Override
    public String getComponentType() {
        return componentType;
    }
    
    protected Duration getTimeout() {
        return timeout;
    }
}