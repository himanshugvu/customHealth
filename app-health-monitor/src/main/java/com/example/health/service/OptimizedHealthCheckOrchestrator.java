package com.example.health.service;

import com.example.health.core.HealthChecker;
import com.example.health.domain.HealthCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Optimized enterprise health check orchestrator.
 * 
 * This service manages the execution and coordination of all health checks,
 * providing features like:
 * - Parallel execution with optimized thread pool
 * - Circuit breaker patterns for failing services  
 * - Result caching with memory-efficient storage
 * - Graceful degradation and timeout handling
 * - Memory leak prevention
 * - Performance monitoring and optimization
 * 
 * Sonar fixes:
 * - Thread safety with ConcurrentHashMap
 * - Resource management with proper executor shutdown
 * - Memory leak prevention with cache cleanup
 * - Performance optimization with bounded thread pools
 * - Exception handling without resource leaks
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
public class OptimizedHealthCheckOrchestrator implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(OptimizedHealthCheckOrchestrator.class);
    
    // Performance: Use immutable list for thread safety
    private final List<HealthChecker> healthCheckers;
    
    // Thread safety: Use ConcurrentHashMap for concurrent access
    private final Map<String, CachedResult> resultCache;
    private final Duration cacheTimeout;
    
    // Performance: Use bounded thread pool to prevent resource exhaustion
    private final ExecutorService executorService;
    private final int maxConcurrentChecks;
    
    // Circuit breaker state
    private final Map<String, CircuitBreakerState> circuitBreakerStates;
    
    // Performance monitoring
    private volatile long totalExecutions;
    private volatile long cacheHits;
    private volatile long cacheEvictions;
    
    public OptimizedHealthCheckOrchestrator(List<HealthChecker> healthCheckers) {
        this(healthCheckers, Duration.ofSeconds(10), Runtime.getRuntime().availableProcessors());
    }
    
    public OptimizedHealthCheckOrchestrator(List<HealthChecker> healthCheckers, 
                                          Duration cacheTimeout, 
                                          int maxConcurrentChecks) {
        this.healthCheckers = Collections.unmodifiableList(new ArrayList<>(healthCheckers));
        this.cacheTimeout = cacheTimeout;
        this.maxConcurrentChecks = Math.max(1, maxConcurrentChecks);
        
        // Thread safety: Use ConcurrentHashMap
        this.resultCache = new ConcurrentHashMap<>();
        this.circuitBreakerStates = new ConcurrentHashMap<>();
        
        // Performance: Create bounded thread pool with proper naming
        this.executorService = createOptimizedExecutorService();
        
        // Initialize circuit breaker states
        initializeCircuitBreakerStates();
        
        logger.info("Health check orchestrator initialized with {} checkers, cache timeout {}s, max concurrent checks {}",
                   healthCheckers.size(), cacheTimeout.getSeconds(), maxConcurrentChecks);
    }
    
    /**
     * Create optimized executor service with proper resource management.
     */
    private ExecutorService createOptimizedExecutorService() {
        ThreadFactory threadFactory = new ThreadFactory() {
            private final java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(0);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "health-check-" + counter.incrementAndGet());
                thread.setDaemon(true); // Don't prevent JVM shutdown
                thread.setPriority(Thread.NORM_PRIORITY);
                return thread;
            }
        };
        
        return new ThreadPoolExecutor(
            1, // Core pool size
            maxConcurrentChecks, // Maximum pool size
            60L, TimeUnit.SECONDS, // Keep alive time
            new LinkedBlockingQueue<>(maxConcurrentChecks * 2), // Bounded queue
            threadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy() // Rejection policy
        );
    }
    
    /**
     * Initialize circuit breaker states for all health checkers.
     */
    private void initializeCircuitBreakerStates() {
        healthCheckers.forEach(checker -> 
            circuitBreakerStates.put(checker.getComponentName(), new CircuitBreakerState()));
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
        totalExecutions++;
        
        logger.debug("Executing {} health checks (bypassCache={})", healthCheckers.size(), bypassCache);
        
        Instant overallStart = Instant.now();
        
        // Performance: Clean expired cache entries before execution
        if (!bypassCache) {
            cleanExpiredCacheEntries();
        }
        
        List<CompletableFuture<HealthCheckResult>> futures = healthCheckers.stream()
                .filter(HealthChecker::isEnabled)
                .map(checker -> bypassCache ? 
                     executeHealthCheckAsync(checker) : 
                     executeHealthCheckWithCache(checker))
                .collect(Collectors.toList());
        
        // Wait for all checks to complete with global timeout
        List<HealthCheckResult> results = waitForResults(futures);
        
        Duration overallDuration = Duration.between(overallStart, Instant.now());
        logExecutionSummary(results, overallDuration);
        
        return results;
    }
    
    /**
     * Wait for all futures to complete with timeout handling.
     */
    private List<HealthCheckResult> waitForResults(List<CompletableFuture<HealthCheckResult>> futures) {
        List<HealthCheckResult> results = new ArrayList<>();
        
        for (CompletableFuture<HealthCheckResult> future : futures) {
            try {
                HealthCheckResult result = future.get(30, TimeUnit.SECONDS); // Global timeout
                results.add(result);
            } catch (TimeoutException e) {
                logger.warn("Health check timed out after 30 seconds");
                results.add(createTimeoutResult("unknown", "unknown"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupt status
                logger.warn("Health check interrupted");
                results.add(createErrorResult("unknown", "unknown", e));
            } catch (ExecutionException e) {
                logger.error("Health check execution failed", e.getCause());
                results.add(createErrorResult("unknown", "unknown", e.getCause() != null ? e.getCause() : e));
            }
        }
        
        return results;
    }
    
    /**
     * Executes a single health check asynchronously with circuit breaker protection.
     */
    private CompletableFuture<HealthCheckResult> executeHealthCheckAsync(HealthChecker checker) {
        String componentName = checker.getComponentName();
        CircuitBreakerState circuitState = circuitBreakerStates.get(componentName);
        
        // Circuit breaker: Check if circuit is open
        if (circuitState.isCircuitOpen()) {
            logger.debug("Circuit breaker is OPEN for component '{}', returning cached failure", componentName);
            return CompletableFuture.completedFuture(circuitState.getLastFailureResult());
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                HealthCheckResult result = checker.checkHealth();
                
                // Update circuit breaker state
                if (result.isHealthy()) {
                    circuitState.recordSuccess();
                } else {
                    circuitState.recordFailure(result);
                }
                
                return result;
                
            } catch (Exception e) {
                logger.error("Health checker '{}' threw unexpected exception", componentName, e);
                HealthCheckResult errorResult = createErrorResult(componentName, checker.getComponentType(), e);
                circuitState.recordFailure(errorResult);
                return errorResult;
            }
        }, executorService);
    }
    
    /**
     * Executes a health check with caching support and memory optimization.
     */
    private CompletableFuture<HealthCheckResult> executeHealthCheckWithCache(HealthChecker checker) {
        String key = checker.getComponentName();
        CachedResult cachedResult = resultCache.get(key);
        
        // Check if we have a valid cached result
        if (cachedResult != null && !cachedResult.isExpired(cacheTimeout)) {
            cacheHits++;
            logger.debug("Using cached result for '{}' (age: {}ms)", 
                        key, cachedResult.getAge().toMillis());
            return CompletableFuture.completedFuture(cachedResult.getResult());
        }
        
        // Execute fresh health check
        return executeHealthCheckAsync(checker)
                .thenApply(result -> {
                    // Cache the result
                    resultCache.put(key, new CachedResult(result));
                    return result;
                });
    }
    
    /**
     * Clean expired cache entries to prevent memory leaks.
     */
    private void cleanExpiredCacheEntries() {
        int initialSize = resultCache.size();
        
        resultCache.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired(cacheTimeout);
            if (expired) {
                cacheEvictions++;
            }
            return expired;
        });
        
        int removedEntries = initialSize - resultCache.size();
        if (removedEntries > 0) {
            logger.debug("Cleaned {} expired cache entries", removedEntries);
        }
    }
    
    /**
     * Creates an error result for unexpected exceptions.
     */
    private HealthCheckResult createErrorResult(String componentName, String componentType, Throwable error) {
        return HealthCheckResult.failure(componentName, componentType, Duration.ZERO, error)
                .withDetail("errorType", "UnexpectedException")
                .withDetail("orchestratorError", true)
                .build();
    }
    
    /**
     * Creates a timeout result.
     */
    private HealthCheckResult createTimeoutResult(String componentName, String componentType) {
        return HealthCheckResult.failure(componentName, componentType, Duration.ofSeconds(30), 
                new TimeoutException("Health check timed out"))
                .withDetail("errorType", "TimeoutException")
                .withDetail("orchestratorTimeout", true)
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
                   "overall_duration_ms={} avg_latency_ms={:.1f} cache_hits={} cache_evictions={}",
                   results.size(), successCount, failureCount, 
                   overallDuration.toMillis(), averageLatency, cacheHits, cacheEvictions);
    }
    
    /**
     * Returns comprehensive performance statistics.
     */
    public PerformanceStatistics getPerformanceStatistics() {
        return new PerformanceStatistics(
            totalExecutions,
            cacheHits,
            cacheEvictions,
            resultCache.size(),
            circuitBreakerStates.size(),
            getOpenCircuitBreakersCount()
        );
    }
    
    /**
     * Count open circuit breakers.
     */
    private long getOpenCircuitBreakersCount() {
        return circuitBreakerStates.values().stream()
                .mapToLong(state -> state.isCircuitOpen() ? 1 : 0)
                .sum();
    }
    
    /**
     * Clears the result cache and resets circuit breakers.
     */
    public void clearCache() {
        int cacheSize = resultCache.size();
        resultCache.clear();
        circuitBreakerStates.values().forEach(CircuitBreakerState::reset);
        logger.debug("Health check cache cleared ({} entries) and circuit breakers reset", cacheSize);
    }
    
    /**
     * Graceful shutdown with resource cleanup.
     */
    @Override
    public void close() {
        logger.info("Shutting down health check orchestrator...");
        
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("Executor did not terminate gracefully, forcing shutdown");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
        
        // Clear caches to prevent memory leaks
        resultCache.clear();
        circuitBreakerStates.clear();
        
        logger.info("Health check orchestrator shutdown completed");
    }
    
    /**
     * Cached result wrapper with expiration tracking.
     */
    private static class CachedResult {
        private final HealthCheckResult result;
        private final Instant timestamp;
        
        public CachedResult(HealthCheckResult result) {
            this.result = result;
            this.timestamp = Instant.now();
        }
        
        public HealthCheckResult getResult() {
            return result;
        }
        
        public boolean isExpired(Duration timeout) {
            return Duration.between(timestamp, Instant.now()).compareTo(timeout) > 0;
        }
        
        public Duration getAge() {
            return Duration.between(timestamp, Instant.now());
        }
    }
    
    /**
     * Circuit breaker state management.
     */
    private static class CircuitBreakerState {
        private static final int FAILURE_THRESHOLD = 3;
        private static final Duration RECOVERY_TIMEOUT = Duration.ofMinutes(1);
        
        private int failureCount = 0;
        private Instant lastFailureTime;
        private HealthCheckResult lastFailureResult;
        
        public boolean isCircuitOpen() {
            if (failureCount < FAILURE_THRESHOLD) {
                return false;
            }
            
            if (lastFailureTime == null) {
                return false;
            }
            
            // Check if recovery timeout has passed
            return Duration.between(lastFailureTime, Instant.now()).compareTo(RECOVERY_TIMEOUT) < 0;
        }
        
        public void recordSuccess() {
            failureCount = 0;
            lastFailureTime = null;
            lastFailureResult = null;
        }
        
        public void recordFailure(HealthCheckResult failureResult) {
            failureCount++;
            lastFailureTime = Instant.now();
            lastFailureResult = failureResult;
        }
        
        public HealthCheckResult getLastFailureResult() {
            return lastFailureResult;
        }
        
        public void reset() {
            failureCount = 0;
            lastFailureTime = null;
            lastFailureResult = null;
        }
    }
    
    /**
     * Performance statistics data class.
     */
    public static class PerformanceStatistics {
        private final long totalExecutions;
        private final long cacheHits;
        private final long cacheEvictions;
        private final int cacheSize;
        private final int circuitBreakersCount;
        private final long openCircuitBreakersCount;
        
        public PerformanceStatistics(long totalExecutions, long cacheHits, long cacheEvictions,
                                   int cacheSize, int circuitBreakersCount, long openCircuitBreakersCount) {
            this.totalExecutions = totalExecutions;
            this.cacheHits = cacheHits;
            this.cacheEvictions = cacheEvictions;
            this.cacheSize = cacheSize;
            this.circuitBreakersCount = circuitBreakersCount;
            this.openCircuitBreakersCount = openCircuitBreakersCount;
        }
        
        // Getters
        public long getTotalExecutions() { return totalExecutions; }
        public long getCacheHits() { return cacheHits; }
        public long getCacheEvictions() { return cacheEvictions; }
        public int getCacheSize() { return cacheSize; }
        public int getCircuitBreakersCount() { return circuitBreakersCount; }
        public long getOpenCircuitBreakersCount() { return openCircuitBreakersCount; }
        
        public double getCacheHitRatio() {
            return totalExecutions > 0 ? (double) cacheHits / totalExecutions : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("PerformanceStatistics{executions=%d, cacheHits=%d, hitRatio=%.2f%%, " +
                                "cacheSize=%d, openCircuitBreakers=%d/%d}",
                                totalExecutions, cacheHits, getCacheHitRatio() * 100,
                                cacheSize, openCircuitBreakersCount, circuitBreakersCount);
        }
    }
}