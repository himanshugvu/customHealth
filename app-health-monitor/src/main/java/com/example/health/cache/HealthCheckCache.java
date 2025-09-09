package com.example.health.cache;

import com.example.health.domain.HealthCheckResult;
import com.example.health.domain.HealthStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * High-performance, multi-tier caching system for health check results.
 * Implements smart caching strategies with different TTLs based on health status.
 */
public final class HealthCheckCache {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckCache.class);
    
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final HealthCheckCacheConfig config;
    private final ScheduledExecutorService cleanupExecutor;
    private final CacheMetrics metrics;

    public HealthCheckCache() {
        this(HealthCheckCacheConfig.defaultConfig());
    }

    public HealthCheckCache(HealthCheckCacheConfig config) {
        this.config = config;
        this.metrics = new CacheMetrics();
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "health-cache-cleanup");
            t.setDaemon(true);
            return t;
        });
        
        // Schedule periodic cleanup
        cleanupExecutor.scheduleAtFixedRate(
            this::cleanup, 
            config.getCleanupInterval().toMinutes(), 
            config.getCleanupInterval().toMinutes(), 
            TimeUnit.MINUTES
        );
    }

    /**
     * Gets cached result or executes health check with intelligent caching strategy.
     */
    public HealthCheckResult getOrCompute(String checkName, Supplier<HealthCheckResult> healthCheck) {
        CacheEntry entry = cache.get(checkName);
        
        // Check if we have a valid cache hit
        if (entry != null && !entry.isExpired()) {
            metrics.recordHit(checkName);
            logger.debug("Cache hit for health check: {}", checkName);
            return entry.getResult();
        }
        
        // Cache miss - execute health check
        metrics.recordMiss(checkName);
        logger.debug("Cache miss for health check: {}, executing...", checkName);
        
        try {
            HealthCheckResult result = healthCheck.get();
            
            // Cache with appropriate TTL based on result status
            Duration ttl = calculateTTL(result);
            cache.put(checkName, new CacheEntry(result, ttl));
            
            metrics.recordComputation(checkName, result.getLatency());
            return result;
            
        } catch (Exception e) {
            logger.error("Health check execution failed for: {}", checkName, e);
            
            // Return stale result if available and configured to do so
            if (entry != null && config.isReturnStaleOnError()) {
                logger.warn("Returning stale result for {} due to execution failure", checkName);
                metrics.recordStaleHit(checkName);
                return entry.getResult();
            }
            
            // Return error result
            HealthCheckResult errorResult = HealthCheckResult.builder(checkName)
                    .status(HealthStatus.DOWN)
                    .latency(Duration.ZERO)
                    .errorMessage("Cache execution failed: " + e.getMessage())
                    .build();
            
            cache.put(checkName, new CacheEntry(errorResult, config.getErrorTTL()));
            return errorResult;
        }
    }

    /**
     * Invalidates cache entry for specific health check.
     */
    public void invalidate(String checkName) {
        CacheEntry removed = cache.remove(checkName);
        if (removed != null) {
            logger.debug("Invalidated cache entry for: {}", checkName);
            metrics.recordInvalidation(checkName);
        }
    }

    /**
     * Invalidates all cache entries.
     */
    public void invalidateAll() {
        int size = cache.size();
        cache.clear();
        logger.info("Invalidated all {} cache entries", size);
        metrics.recordBulkInvalidation(size);
    }

    /**
     * Pre-warms cache with health check results.
     */
    public void preWarm(Map<String, HealthCheckResult> results) {
        results.forEach((checkName, result) -> {
            Duration ttl = calculateTTL(result);
            cache.put(checkName, new CacheEntry(result, ttl));
            logger.debug("Pre-warmed cache for: {}", checkName);
        });
        metrics.recordPreWarm(results.size());
    }

    /**
     * Gets current cache statistics.
     */
    public CacheMetrics getMetrics() {
        return metrics.snapshot();
    }

    /**
     * Gets current cache size.
     */
    public int size() {
        return cache.size();
    }

    /**
     * Checks if cache contains entry for given check name.
     */
    public boolean contains(String checkName) {
        CacheEntry entry = cache.get(checkName);
        return entry != null && !entry.isExpired();
    }

    private Duration calculateTTL(HealthCheckResult result) {
        switch (result.getStatus()) {
            case UP:
                return config.getHealthyTTL();
            case DEGRADED:
                return config.getDegradedTTL();
            case DOWN:
                return config.getUnhealthyTTL();
            case UNKNOWN:
            default:
                return config.getErrorTTL();
        }
    }

    private void cleanup() {
        try {
            Instant now = Instant.now();
            int beforeSize = cache.size();
            
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
            
            int afterSize = cache.size();
            int removed = beforeSize - afterSize;
            
            if (removed > 0) {
                logger.debug("Cache cleanup removed {} expired entries, {} remaining", removed, afterSize);
                metrics.recordCleanup(removed);
            }
        } catch (Exception e) {
            logger.error("Error during cache cleanup", e);
        }
    }

    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static final class CacheEntry {
        private final HealthCheckResult result;
        private final Instant expirationTime;

        CacheEntry(HealthCheckResult result, Duration ttl) {
            this.result = result;
            this.expirationTime = Instant.now().plus(ttl);
        }

        HealthCheckResult getResult() {
            return result;
        }

        boolean isExpired() {
            return isExpired(Instant.now());
        }

        boolean isExpired(Instant now) {
            return now.isAfter(expirationTime);
        }

        Instant getExpirationTime() {
            return expirationTime;
        }
    }

    public static final class CacheMetrics {
        private volatile long hits = 0;
        private volatile long misses = 0;
        private volatile long staleHits = 0;
        private volatile long computations = 0;
        private volatile long invalidations = 0;
        private volatile long cleanups = 0;
        private volatile Duration totalComputationTime = Duration.ZERO;

        void recordHit(String checkName) {
            hits++;
        }

        void recordMiss(String checkName) {
            misses++;
        }

        void recordStaleHit(String checkName) {
            staleHits++;
        }

        void recordComputation(String checkName, Duration computationTime) {
            computations++;
            totalComputationTime = totalComputationTime.plus(computationTime);
        }

        void recordInvalidation(String checkName) {
            invalidations++;
        }

        void recordBulkInvalidation(int count) {
            invalidations += count;
        }

        void recordPreWarm(int count) {
            // Pre-warm counts as hits since we're populating cache
        }

        void recordCleanup(int removedCount) {
            cleanups += removedCount;
        }

        public long getHits() { return hits; }
        public long getMisses() { return misses; }
        public long getStaleHits() { return staleHits; }
        public long getComputations() { return computations; }
        public long getInvalidations() { return invalidations; }
        public long getCleanups() { return cleanups; }

        public double getHitRate() {
            long total = hits + misses;
            return total == 0 ? 0.0 : (double) hits / total;
        }

        public Duration getAverageComputationTime() {
            return computations == 0 ? Duration.ZERO : 
                   totalComputationTime.dividedBy(computations);
        }

        public CacheMetrics snapshot() {
            CacheMetrics snapshot = new CacheMetrics();
            snapshot.hits = this.hits;
            snapshot.misses = this.misses;
            snapshot.staleHits = this.staleHits;
            snapshot.computations = this.computations;
            snapshot.invalidations = this.invalidations;
            snapshot.cleanups = this.cleanups;
            snapshot.totalComputationTime = this.totalComputationTime;
            return snapshot;
        }
    }

    public static final class HealthCheckCacheConfig {
        private final Duration healthyTTL;
        private final Duration degradedTTL;
        private final Duration unhealthyTTL;
        private final Duration errorTTL;
        private final Duration cleanupInterval;
        private final boolean returnStaleOnError;

        private HealthCheckCacheConfig(Builder builder) {
            this.healthyTTL = builder.healthyTTL;
            this.degradedTTL = builder.degradedTTL;
            this.unhealthyTTL = builder.unhealthyTTL;
            this.errorTTL = builder.errorTTL;
            this.cleanupInterval = builder.cleanupInterval;
            this.returnStaleOnError = builder.returnStaleOnError;
        }

        public static HealthCheckCacheConfig defaultConfig() {
            return builder().build();
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public Duration getHealthyTTL() { return healthyTTL; }
        public Duration getDegradedTTL() { return degradedTTL; }
        public Duration getUnhealthyTTL() { return unhealthyTTL; }
        public Duration getErrorTTL() { return errorTTL; }
        public Duration getCleanupInterval() { return cleanupInterval; }
        public boolean isReturnStaleOnError() { return returnStaleOnError; }

        public static final class Builder {
            private Duration healthyTTL = Duration.ofMinutes(5);     // Cache healthy results longer
            private Duration degradedTTL = Duration.ofMinutes(2);   // Cache degraded results medium time
            private Duration unhealthyTTL = Duration.ofSeconds(30); // Cache unhealthy results briefly
            private Duration errorTTL = Duration.ofSeconds(10);     // Cache errors very briefly
            private Duration cleanupInterval = Duration.ofMinutes(10);
            private boolean returnStaleOnError = true;

            public Builder healthyTTL(Duration healthyTTL) {
                this.healthyTTL = healthyTTL;
                return this;
            }

            public Builder degradedTTL(Duration degradedTTL) {
                this.degradedTTL = degradedTTL;
                return this;
            }

            public Builder unhealthyTTL(Duration unhealthyTTL) {
                this.unhealthyTTL = unhealthyTTL;
                return this;
            }

            public Builder errorTTL(Duration errorTTL) {
                this.errorTTL = errorTTL;
                return this;
            }

            public Builder cleanupInterval(Duration cleanupInterval) {
                this.cleanupInterval = cleanupInterval;
                return this;
            }

            public Builder returnStaleOnError(boolean returnStaleOnError) {
                this.returnStaleOnError = returnStaleOnError;
                return this;
            }

            public HealthCheckCacheConfig build() {
                return new HealthCheckCacheConfig(this);
            }
        }
    }
}