package com.example.health.domain;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable value object representing the result of a health check operation.
 * 
 * This class encapsulates all the information about a health check execution,
 * including timing, status, and contextual details. It follows the Value Object
 * pattern to ensure immutability and proper encapsulation.
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
public final class HealthCheckResult {
    
    private final String componentName;
    private final String componentType;
    private final Status status;
    private final Duration latency;
    private final Instant timestamp;
    private final Map<String, Object> details;
    private final Throwable error;
    
    private HealthCheckResult(Builder builder) {
        this.componentName = Objects.requireNonNull(builder.componentName, "Component name cannot be null");
        this.componentType = Objects.requireNonNull(builder.componentType, "Component type cannot be null");
        this.status = Objects.requireNonNull(builder.status, "Status cannot be null");
        this.latency = Objects.requireNonNull(builder.latency, "Latency cannot be null");
        this.timestamp = Objects.requireNonNull(builder.timestamp, "Timestamp cannot be null");
        this.details = builder.details != null ? Map.copyOf(builder.details) : Collections.emptyMap();
        this.error = builder.error;
    }
    
    /**
     * Creates a successful health check result.
     */
    public static Builder success(String componentName, String componentType, Duration latency) {
        return new Builder(componentName, componentType, Status.UP, latency);
    }
    
    /**
     * Creates a failed health check result.
     */
    public static Builder failure(String componentName, String componentType, Duration latency, Throwable error) {
        return new Builder(componentName, componentType, Status.DOWN, latency)
                .withError(error);
    }
    
    /**
     * Converts this result to a Spring Boot Health object.
     */
    public Health toSpringHealth() {
        var healthBuilder = status == Status.UP ? Health.up() : Health.down();
        
        healthBuilder
                .withDetail("component", componentName)
                .withDetail("type", componentType)
                .withDetail("latencyMs", latency.toMillis())
                .withDetail("timestamp", timestamp.toString());
        
        if (error != null) {
            healthBuilder.withDetail("error", error.getClass().getSimpleName() + ": " + error.getMessage());
        }
        
        details.forEach(healthBuilder::withDetail);
        
        return healthBuilder.build();
    }
    
    /**
     * Checks if this health check was successful.
     */
    public boolean isHealthy() {
        return status == Status.UP;
    }
    
    // Getters
    public String getComponentName() { return componentName; }
    public String getComponentType() { return componentType; }
    public Status getStatus() { return status; }
    public Duration getLatency() { return latency; }
    public Instant getTimestamp() { return timestamp; }
    public Map<String, Object> getDetails() { return details; }
    public Throwable getError() { return error; }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof HealthCheckResult that)) return false;
        return Objects.equals(componentName, that.componentName) &&
               Objects.equals(componentType, that.componentType) &&
               Objects.equals(status, that.status) &&
               Objects.equals(latency, that.latency) &&
               Objects.equals(timestamp, that.timestamp);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(componentName, componentType, status, latency, timestamp);
    }
    
    @Override
    public String toString() {
        return String.format("HealthCheckResult{component='%s', type='%s', status=%s, latency=%dms, healthy=%s}",
                componentName, componentType, status, latency.toMillis(), isHealthy());
    }
    
    public static class Builder {
        private final String componentName;
        private final String componentType;
        private final Status status;
        private final Duration latency;
        private final Instant timestamp;
        private Map<String, Object> details;
        private Throwable error;
        
        private Builder(String componentName, String componentType, Status status, Duration latency) {
            this.componentName = componentName;
            this.componentType = componentType;
            this.status = status;
            this.latency = latency;
            this.timestamp = Instant.now();
        }
        
        public Builder withDetails(Map<String, Object> details) {
            this.details = details;
            return this;
        }
        
        public Builder withDetail(String key, Object value) {
            if (this.details == null) {
                this.details = new java.util.HashMap<>();
            }
            this.details.put(key, value);
            return this;
        }
        
        public Builder withError(Throwable error) {
            this.error = error;
            return this;
        }
        
        public HealthCheckResult build() {
            return new HealthCheckResult(this);
        }
    }
}