package com.example.health.service;

import com.example.health.config.ValidatedHealthMonitoringProperties;
import com.example.health.domain.HealthCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Enterprise startup health reporter.
 * 
 * This service handles health check reporting during application startup,
 * providing comprehensive visibility into the health status of all
 * configured dependencies when the application starts.
 * 
 * Features:
 * - Asynchronous execution to avoid blocking startup
 * - Structured logging with configurable detail levels
 * - Startup performance metrics
 * - Graceful error handling
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
public class StartupHealthReporter {
    
    private static final Logger logger = LoggerFactory.getLogger(StartupHealthReporter.class);
    
    private final OptimizedHealthCheckOrchestrator orchestrator;
    private final ValidatedHealthMonitoringProperties properties;
    
    public StartupHealthReporter(OptimizedHealthCheckOrchestrator orchestrator, ValidatedHealthMonitoringProperties properties) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "Health check orchestrator cannot be null");
        this.properties = Objects.requireNonNull(properties, "Health monitoring properties cannot be null");
    }
    
    /**
     * Reports startup health status asynchronously.
     */
    public void reportStartupHealth() {
        if (!properties.isStartupLogging()) {
            logger.debug("Startup health logging is disabled");
            return;
        }
        
        logger.info("Starting application health verification...");
        
        CompletableFuture.runAsync(this::executeStartupHealthChecks)
                .exceptionally(this::handleStartupHealthCheckError);
    }
    
    /**
     * Executes startup health checks and logs results.
     */
    private void executeStartupHealthChecks() {
        Instant startTime = Instant.now();
        
        try {
            // Execute all health checks with fresh results (bypass cache)
            List<HealthCheckResult> results = orchestrator.executeAllHealthChecks(true);
            
            Duration totalDuration = Duration.between(startTime, Instant.now());
            
            // Log summary first
            logStartupSummary(results, totalDuration);
            
            // Log individual component results
            logIndividualResults(results);
            
            // Log final status
            logFinalStartupStatus(results, totalDuration);
            
        } catch (Exception e) {
            Duration totalDuration = Duration.between(startTime, Instant.now());
            logger.error("startup_health=ERROR message=\"Failed to execute startup health checks\" " +
                        "duration_ms={} error_type=\"{}\" error_message=\"{}\"",
                        totalDuration.toMillis(), e.getClass().getSimpleName(), 
                        truncateErrorMessage(e.getMessage()));
        }
    }
    
    /**
     * Logs a summary of startup health check results.
     */
    private void logStartupSummary(List<HealthCheckResult> results, Duration totalDuration) {
        long totalChecks = results.size();
        long healthyChecks = results.stream().mapToLong(r -> r.isHealthy() ? 1 : 0).sum();
        long unhealthyChecks = totalChecks - healthyChecks;
        
        double healthPercentage = totalChecks > 0 ? (double) healthyChecks / totalChecks * 100 : 0;
        double avgLatency = results.stream()
                .mapToLong(r -> r.getLatency().toMillis())
                .average()
                .orElse(0.0);
        
        logger.info("startup_health=SUMMARY total_checks={} healthy_checks={} unhealthy_checks={} " +
                   "health_percentage={:.1f}% avg_latency_ms={:.1f} total_duration_ms={}",
                   totalChecks, healthyChecks, unhealthyChecks, healthPercentage, 
                   avgLatency, totalDuration.toMillis());
    }
    
    /**
     * Logs individual health check results.
     */
    private void logIndividualResults(List<HealthCheckResult> results) {
        if (!properties.getLogging().isStructured()) {
            logIndividualResultsNatural(results);
            return;
        }
        
        for (HealthCheckResult result : results) {
            if (result.isHealthy()) {
                logger.info("startup_health=CHECK component=\"{}\" type={} status=UP latency_ms={}{}",
                           result.getComponentName(), result.getComponentType(), 
                           result.getLatency().toMillis(), 
                           formatAdditionalDetails(result));
            } else {
                String errorMsg = result.getError() != null ? 
                    truncateErrorMessage(result.getError().getMessage()) : "Unknown error";
                
                logger.warn("startup_health=CHECK component=\"{}\" type={} status=DOWN latency_ms={} " +
                           "error_type=\"{}\" error_message=\"{}\"{}",
                           result.getComponentName(), result.getComponentType(), 
                           result.getLatency().toMillis(),
                           result.getError() != null ? result.getError().getClass().getSimpleName() : "Unknown",
                           errorMsg, formatAdditionalDetails(result));
            }
        }
    }
    
    /**
     * Logs individual results in natural language format.
     */
    private void logIndividualResultsNatural(List<HealthCheckResult> results) {
        for (HealthCheckResult result : results) {
            if (result.isHealthy()) {
                logger.info("[StartupHealth] {} ({}) is UP - {}ms", 
                           result.getComponentName(), result.getComponentType(), 
                           result.getLatency().toMillis());
            } else {
                String errorMsg = result.getError() != null ? 
                    result.getError().getMessage() : "Unknown error";
                
                logger.warn("[StartupHealth] {} ({}) is DOWN - {}ms - {}", 
                           result.getComponentName(), result.getComponentType(), 
                           result.getLatency().toMillis(), truncateErrorMessage(errorMsg));
            }
        }
    }
    
    /**
     * Logs the final startup status.
     */
    private void logFinalStartupStatus(List<HealthCheckResult> results, Duration totalDuration) {
        boolean allHealthy = results.stream().allMatch(HealthCheckResult::isHealthy);
        
        if (allHealthy) {
            logger.info("startup_health=COMPLETE status=ALL_HEALTHY message=\"All {} health checks passed\" " +
                       "duration_ms={}", results.size(), totalDuration.toMillis());
        } else {
            long failedCount = results.stream().mapToLong(r -> r.isHealthy() ? 0 : 1).sum();
            logger.warn("startup_health=COMPLETE status=DEGRADED message=\"{} of {} health checks failed\" " +
                       "duration_ms={}", failedCount, results.size(), totalDuration.toMillis());
        }
    }
    
    /**
     * Formats additional details from health check results.
     */
    private String formatAdditionalDetails(HealthCheckResult result) {
        if (result.getDetails().isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        result.getDetails().forEach((key, value) -> {
            if (isRelevantDetailForLogging(key)) {
                sb.append(" ").append(key).append("=\"").append(value).append("\"");
            }
        });
        
        return sb.toString();
    }
    
    /**
     * Determines if a detail key is relevant for startup logging.
     */
    private boolean isRelevantDetailForLogging(String key) {
        return switch (key.toLowerCase()) {
            case "endpoint", "host", "port", "databaseproduct", "databaseversion", 
                 "mongoversion", "nodeid", "clusterid" -> true;
            default -> false;
        };
    }
    
    /**
     * Truncates error messages to prevent log pollution.
     */
    private String truncateErrorMessage(String message) {
        if (message == null) {
            return "null";
        }
        
        int maxLength = properties.getLogging().getMaxErrorMessageLength();
        if (message.length() <= maxLength) {
            return message;
        }
        
        return message.substring(0, maxLength - 3) + "...";
    }
    
    /**
     * Handles errors during startup health check execution.
     */
    private Void handleStartupHealthCheckError(Throwable throwable) {
        logger.error("startup_health=ERROR message=\"Critical error during startup health verification\" " +
                    "error_type=\"{}\" error_message=\"{}\"",
                    throwable.getClass().getSimpleName(), 
                    truncateErrorMessage(throwable.getMessage()), throwable);
        return null;
    }
}