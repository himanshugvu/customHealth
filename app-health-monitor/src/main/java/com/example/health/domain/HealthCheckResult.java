package com.example.health.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class HealthCheckResult {
    
    private final String checkName;
    private final HealthStatus status;
    private final Instant timestamp;
    private final Duration latency;
    private final Optional<String> errorMessage;
    private final Optional<Throwable> exception;
    private final Map<String, Object> metadata;
    private final String version;

    @JsonCreator
    private HealthCheckResult(
            @JsonProperty("checkName") String checkName,
            @JsonProperty("status") HealthStatus status,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("latency") Duration latency,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("version") String version) {
        this.checkName = Objects.requireNonNull(checkName, "checkName cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
        this.latency = Objects.requireNonNull(latency, "latency cannot be null");
        this.errorMessage = Optional.ofNullable(errorMessage);
        this.exception = Optional.empty(); // Never serialize exceptions
        this.metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata cannot be null"));
        this.version = version;
    }
    
    public static Builder builder(String checkName) {
        return new Builder(checkName);
    }
    
    public static HealthCheckResult healthy(String checkName, Duration latency) {
        return builder(checkName)
                .status(HealthStatus.UP)
                .latency(latency)
                .build();
    }
    
    public static HealthCheckResult unhealthy(String checkName, Duration latency, String error) {
        return builder(checkName)
                .status(HealthStatus.DOWN)
                .latency(latency)
                .errorMessage(error)
                .build();
    }
    
    public static HealthCheckResult degraded(String checkName, Duration latency, String reason) {
        return builder(checkName)
                .status(HealthStatus.DEGRADED)
                .latency(latency)
                .errorMessage(reason)
                .build();
    }

    // Getters
    public String getCheckName() { return checkName; }
    public HealthStatus getStatus() { return status; }
    public Instant getTimestamp() { return timestamp; }
    public Duration getLatency() { return latency; }
    public Optional<String> getErrorMessage() { return errorMessage; }
    public Optional<Throwable> getException() { return exception; }
    public Map<String, Object> getMetadata() { return metadata; }
    public String getVersion() { return version; }
    
    // Utility methods
    public boolean isHealthy() { 
        return status == HealthStatus.UP; 
    }
    
    public boolean isDegraded() { 
        return status == HealthStatus.DEGRADED; 
    }
    
    public boolean isUnhealthy() { 
        return status == HealthStatus.DOWN; 
    }
    
    public long getLatencyMs() { 
        return latency.toMillis(); 
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HealthCheckResult that = (HealthCheckResult) o;
        return Objects.equals(checkName, that.checkName) &&
               status == that.status &&
               Objects.equals(timestamp, that.timestamp) &&
               Objects.equals(latency, that.latency) &&
               Objects.equals(errorMessage, that.errorMessage) &&
               Objects.equals(metadata, that.metadata) &&
               Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(checkName, status, timestamp, latency, errorMessage, metadata, version);
    }

    @Override
    public String toString() {
        return String.format("HealthCheckResult{name='%s', status=%s, latency=%dms, timestamp=%s}", 
                           checkName, status, latency.toMillis(), timestamp);
    }

    public static final class Builder {
        private final String checkName;
        private HealthStatus status = HealthStatus.UP;
        private Instant timestamp = Instant.now();
        private Duration latency = Duration.ZERO;
        private String errorMessage;
        private Map<String, Object> metadata = Map.of();
        private String version = "1.0";

        private Builder(String checkName) {
            this.checkName = Objects.requireNonNull(checkName);
        }

        public Builder status(HealthStatus status) {
            this.status = Objects.requireNonNull(status);
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = Objects.requireNonNull(timestamp);
            return this;
        }

        public Builder latency(Duration latency) {
            this.latency = Objects.requireNonNull(latency);
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = Objects.requireNonNull(metadata);
            return this;
        }
        
        public Builder addMetadata(String key, Object value) {
            var mutableMetadata = new java.util.HashMap<>(this.metadata);
            mutableMetadata.put(key, value);
            this.metadata = mutableMetadata;
            return this;
        }

        public Builder version(String version) {
            this.version = Objects.requireNonNull(version);
            return this;
        }

        public HealthCheckResult build() {
            return new HealthCheckResult(checkName, status, timestamp, latency, 
                                       errorMessage, metadata, version);
        }
    }
}