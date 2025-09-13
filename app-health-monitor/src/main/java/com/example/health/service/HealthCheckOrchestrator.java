package com.example.health.service;

import com.example.health.core.HealthChecker;
import com.example.health.domain.HealthCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Enterprise health check orchestrator.
 * 
 * This service manages the execution and coordination of all health checks,
 * providing features like:
 * - Parallel execution for performance
 * - Circuit breaker patterns for failing services
 * - Result caching for high-frequency requests
 * - Graceful degradation
 * - Comprehensive metrics collection
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
public class HealthCheckOrchestrator {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckOrchestrator.class);
    
    private final List<HealthChecker> healthCheckers;
    private final Map<String, HealthCheckResult> resultCache;
    private final Map<String, Instant> lastExecutionTime;
    private final Duration cacheTimeout;
    
    public HealthCheckOrchestrator(List<HealthChecker> healthCheckers) {
        this(healthCheckers, Duration.ofSeconds(10));
    }
    
    public HealthCheckOrchestrator(List<HealthChecker> healthCheckers, Duration cacheTimeout) {
        this.healthCheckers = new ArrayList<>(healthCheckers);
        this.resultCache = new ConcurrentHashMap<>();
        this.lastExecutionTime = new ConcurrentHashMap<>();
        this.cacheTimeout = cacheTimeout;
    }
    
    /**
     * Executes all enabled health checks and returns aggregated results.
     * 
     * @return list of health check results
     */
    public List<HealthCheckResult> executeAllHealthChecks() {
        return executeAllHealthChecks(false);
    }
    
    /**
     * Executes all enabled health checks with optional cache bypass.
     * 
     * @param bypassCache if true, ignores cached results and forces fresh execution
     * @return list of health check results
     */
    public List<HealthCheckResult> executeAllHealthChecks(boolean bypassCache) {
        logger.debug("Executing {} health checks (bypassCache={})", healthCheckers.size(), bypassCache);
        
        Instant overallStart = Instant.now();
        
        List<CompletableFuture<HealthCheckResult>> futures = healthCheckers.stream()
                .filter(HealthChecker::isEnabled)
                .map(checker -> bypassCache ? 
                     executeHealthCheckAsync(checker) : 
                     executeHealthCheckWithCache(checker))
                .collect(Collectors.toList());
        
        // Wait for all checks to complete
        List<HealthCheckResult> results = futures.stream()
                .map(future -> {
                    try {
                        return future.get(30, TimeUnit.SECONDS); // Global timeout
                    } catch (Exception e) {
                        logger.error("Health check future failed", e);
                        return createErrorResult("unknown", "unknown", e);
                    }
                })
                .collect(Collectors.toList());
        
        Duration overallDuration = Duration.between(overallStart, Instant.now());
        logExecutionSummary(results, overallDuration);
        
        return results;
    }
    
    /**
     * Executes a single health check asynchronously.
     */
    private CompletableFuture<HealthCheckResult> executeHealthCheckAsync(HealthChecker checker) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return checker.checkHealth();
            } catch (Exception e) {
                logger.error("Health checker '{}' threw unexpected exception", checker.getComponentName(), e);
                return createErrorResult(checker.getComponentName(), checker.getComponentType(), e);
            }
        });
    }
    
    /**
     * Executes a health check with caching support.
     */
    private CompletableFuture<HealthCheckResult> executeHealthCheckWithCache(HealthChecker checker) {
        String key = checker.getComponentName();
        Instant lastExecution = lastExecutionTime.get(key);
        HealthCheckResult cachedResult = resultCache.get(key);
        
        // Check if we have a valid cached result
        if (cachedResult != null && lastExecution != null) {
            Duration age = Duration.between(lastExecution, Instant.now());
            if (age.compareTo(cacheTimeout) < 0) {
                logger.debug("Using cached result for '{}' (age: {}ms)", key, age.toMillis());
                return CompletableFuture.completedFuture(cachedResult);
            }
        }
        
        // Execute fresh health check
        return executeHealthCheckAsync(checker)
                .thenApply(result -> {
                    resultCache.put(key, result);
                    lastExecutionTime.put(key, Instant.now());
                    return result;
                });
    }
    
    /**
     * Creates an error result for unexpected exceptions.
     */
    private HealthCheckResult createErrorResult(String componentName, String componentType, Exception error) {
        return HealthCheckResult.failure(componentName, componentType, Duration.ZERO, error)
                .withDetail("errorType", "UnexpectedException")
                .withDetail("orchestratorError", true)
                .build();
    }
    
    /**
     * Logs a summary of the execution results.
     */
    private void logExecutionSummary(List<HealthCheckResult> results, Duration overallDuration) {
        long successCount = results.stream().mapToLong(r -> r.isHealthy() ? 1 : 0).sum();
        long failureCount = results.size() - successCount;
        
        double averageLatency = results.stream()
                .mapToLong(r -> r.getLatency().toMillis())
                .average()
                .orElse(0.0);
        
        logger.info("Health check execution completed: total={} success={} failure={} " +
                   "overall_duration_ms={} avg_latency_ms={:.1f}",
                   results.size(), successCount, failureCount, 
                   overallDuration.toMillis(), averageLatency);
    }
    
    /**
     * Clears the result cache.
     */
    public void clearCache() {
        resultCache.clear();
        lastExecutionTime.clear();
        logger.debug("Health check result cache cleared");
    }
    
    /**
     * Returns the current cache statistics.
     */
    public CacheStatistics getCacheStatistics() {
        return new CacheStatistics(resultCache.size(), lastExecutionTime.size());
    }
    
    /**
     * Cache statistics data class.
     */
    public static class CacheStatistics {
        private final int cachedResultsCount;
        private final int trackedComponentsCount;
        
        public CacheStatistics(int cachedResultsCount, int trackedComponentsCount) {
            this.cachedResultsCount = cachedResultsCount;
            this.trackedComponentsCount = trackedComponentsCount;
        }
        
        public int getCachedResultsCount() { return cachedResultsCount; }
        public int getTrackedComponentsCount() { return trackedComponentsCount; }
        
        @Override
        public String toString() {
            return String.format("CacheStatistics{cachedResults=%d, trackedComponents=%d}", 
                                cachedResultsCount, trackedComponentsCount);
        }
    }
}