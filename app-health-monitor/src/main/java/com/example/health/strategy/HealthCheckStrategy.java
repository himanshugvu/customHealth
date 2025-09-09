package com.example.health.strategy;

import com.example.health.domain.HealthCheckResult;
import com.example.health.core.HealthCheckContext;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface HealthCheckStrategy {
    
    /**
     * Executes a health check asynchronously with full context support.
     * 
     * @param context Execution context containing configuration, metrics, circuit breaker, etc.
     * @return CompletableFuture containing the health check result
     */
    CompletableFuture<HealthCheckResult> execute(HealthCheckContext context);
    
    /**
     * Returns the logical name for this health check strategy.
     * Used for identification, metrics, and configuration mapping.
     */
    default String getStrategyName() {
        return this.getClass().getSimpleName().replace("HealthStrategy", "").toLowerCase();
    }
    
    /**
     * Indicates whether this strategy supports graceful degradation.
     * Strategies that support degradation can return DEGRADED status instead of DOWN
     * when experiencing minor issues.
     */
    default boolean supportsDegradation() {
        return false;
    }
    
    /**
     * Returns the recommended timeout for this health check type.
     * Used as fallback when not explicitly configured.
     */
    default java.time.Duration getRecommendedTimeout() {
        return java.time.Duration.ofSeconds(5);
    }
    
    /**
     * Returns metadata about this strategy for documentation and introspection.
     */
    default StrategyMetadata getMetadata() {
        return StrategyMetadata.builder()
                .name(getStrategyName())
                .description("Health check strategy for " + getStrategyName())
                .supportsDegradation(supportsDegradation())
                .recommendedTimeout(getRecommendedTimeout())
                .build();
    }
}