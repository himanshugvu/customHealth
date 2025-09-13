package com.example.health.actuator;

import com.example.health.domain.HealthCheckResult;
import com.example.health.service.HealthCheckOrchestrator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Enterprise Spring Boot Health Indicator integration.
 * 
 * This class bridges our enterprise health checking framework with
 * Spring Boot Actuator, providing:
 * - Aggregated health status
 * - Detailed component breakdown
 * - Performance metrics
 * - Failure analysis
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
public class EnterpriseHealthIndicator implements HealthIndicator {
    
    private final HealthCheckOrchestrator orchestrator;
    private final String indicatorName;
    
    public EnterpriseHealthIndicator(String indicatorName, HealthCheckOrchestrator orchestrator) {
        this.indicatorName = Objects.requireNonNull(indicatorName, "Indicator name cannot be null");
        this.orchestrator = Objects.requireNonNull(orchestrator, "Health check orchestrator cannot be null");
    }
    
    @Override
    public Health health() {
        Instant start = Instant.now();
        
        try {
            List<HealthCheckResult> results = orchestrator.executeAllHealthChecks();
            Duration overallLatency = Duration.between(start, Instant.now());
            
            return buildAggregatedHealth(results, overallLatency);
            
        } catch (Exception e) {
            Duration overallLatency = Duration.between(start, Instant.now());
            
            return Health.down()
                    .withDetail("error", "Health check orchestration failed: " + e.getMessage())
                    .withDetail("errorType", e.getClass().getSimpleName())
                    .withDetail("overallLatencyMs", overallLatency.toMillis())
                    .withDetail("indicator", indicatorName)
                    .build();
        }
    }
    
    /**
     * Builds an aggregated health response from individual check results.
     */
    private Health buildAggregatedHealth(List<HealthCheckResult> results, Duration overallLatency) {
        if (results.isEmpty()) {
            return Health.unknown()
                    .withDetail("message", "No health checks configured")
                    .withDetail("indicator", indicatorName)
                    .build();
        }
        
        // Calculate overall status
        boolean overallHealthy = results.stream().allMatch(HealthCheckResult::isHealthy);
        
        // Build health response
        Health.Builder healthBuilder = overallHealthy ? Health.up() : Health.down();
        
        // Add summary information
        healthBuilder
                .withDetail("indicator", indicatorName)
                .withDetail("overallLatencyMs", overallLatency.toMillis())
                .withDetail("timestamp", Instant.now().toString())
                .withDetail("summary", buildSummary(results))
                .withDetail("components", buildComponentDetails(results))
                .withDetail("cache", orchestrator.getCacheStatistics());
        
        return healthBuilder.build();
    }
    
    /**
     * Builds a summary of health check results.
     */
    private Map<String, Object> buildSummary(List<HealthCheckResult> results) {
        Map<String, Object> summary = new HashMap<>();
        
        long totalChecks = results.size();
        long healthyChecks = results.stream().mapToLong(r -> r.isHealthy() ? 1 : 0).sum();
        long unhealthyChecks = totalChecks - healthyChecks;
        
        double healthPercentage = totalChecks > 0 ? (double) healthyChecks / totalChecks * 100 : 0;
        
        double averageLatency = results.stream()
                .mapToLong(r -> r.getLatency().toMillis())
                .average()
                .orElse(0.0);
        
        summary.put("totalChecks", totalChecks);
        summary.put("healthyChecks", healthyChecks);
        summary.put("unhealthyChecks", unhealthyChecks);
        summary.put("healthPercentage", Math.round(healthPercentage * 100.0) / 100.0);
        summary.put("averageLatencyMs", Math.round(averageLatency * 100.0) / 100.0);
        
        return summary;
    }
    
    /**
     * Builds detailed component information.
     */
    private Map<String, Object> buildComponentDetails(List<HealthCheckResult> results) {
        Map<String, Object> components = new HashMap<>();
        
        for (HealthCheckResult result : results) {
            Map<String, Object> componentInfo = new HashMap<>();
            componentInfo.put("status", result.getStatus().getCode());
            componentInfo.put("type", result.getComponentType());
            componentInfo.put("latencyMs", result.getLatency().toMillis());
            componentInfo.put("timestamp", result.getTimestamp().toString());
            
            // Add details
            if (!result.getDetails().isEmpty()) {
                componentInfo.put("details", result.getDetails());
            }
            
            // Add error information if present
            if (result.getError() != null) {
                componentInfo.put("error", result.getError().getMessage());
                componentInfo.put("errorType", result.getError().getClass().getSimpleName());
            }
            
            components.put(result.getComponentName(), componentInfo);
        }
        
        return components;
    }
}