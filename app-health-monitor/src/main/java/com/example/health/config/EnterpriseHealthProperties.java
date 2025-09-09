package com.example.health.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Enterprise-grade configuration properties with validation, security, and advanced features.
 */
@ConfigurationProperties(prefix = "app.health")
public record EnterpriseHealthProperties(
        
        // Core configuration
        @DefaultValue("false") boolean enabled,
        @DefaultValue("true") boolean startupLog,
        @DefaultValue("false") boolean productionMode,
        
        // Security settings
        @Valid SecurityConfig security,
        
        // Performance settings  
        @Valid PerformanceConfig performance,
        
        // Cache configuration
        @Valid CacheConfig cache,
        
        // Circuit breaker configuration
        @Valid CircuitBreakerConfig circuitBreaker,
        
        // Alerting configuration
        @Valid AlertingConfig alerting,
        
        // Database health configuration
        @Valid DatabaseConfig database,
        
        // MongoDB health configuration  
        @Valid MongoConfig mongodb,
        
        // Kafka health configuration
        @Valid KafkaConfig kafka,
        
        // External services configuration
        @Valid ExternalConfig external

) {

    public record SecurityConfig(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("BASIC") AuthMode authMode,
            @DefaultValue("") String username,
            @DefaultValue("") String password,
            @DefaultValue("") String jwtSecret,
            @DefaultValue("PT1H") Duration tokenExpiry,
            List<String> allowedRoles,
            @DefaultValue("true") boolean auditLogging
    ) {
        public enum AuthMode { NONE, BASIC, JWT, OAUTH2 }
    }

    public record PerformanceConfig(
            @Min(1) @Max(50) @DefaultValue("10") int maxConcurrentChecks,
            @DefaultValue("PT30S") Duration defaultTimeout,
            @DefaultValue("PT5S") Duration degradationThreshold,
            @DefaultValue("true") boolean asyncExecution,
            @Min(1) @Max(1000) @DefaultValue("100") int threadPoolSize,
            @DefaultValue("health-check-") String threadNamePrefix
    ) {}

    public record CacheConfig(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("MEMORY") CacheType type,
            @DefaultValue("PT5M") Duration healthyTtl,
            @DefaultValue("PT2M") Duration degradedTtl,
            @DefaultValue("PT30S") Duration unhealthyTtl,
            @DefaultValue("PT10S") Duration errorTtl,
            @DefaultValue("PT10M") Duration cleanupInterval,
            @DefaultValue("true") boolean returnStaleOnError,
            RedisConfig redis
    ) {
        public enum CacheType { MEMORY, REDIS, HAZELCAST }
        
        public record RedisConfig(
                @DefaultValue("localhost") String host,
                @Min(1) @Max(65535) @DefaultValue("6379") int port,
                @DefaultValue("") String password,
                @Min(0) @Max(15) @DefaultValue("0") int database,
                @DefaultValue("PT30S") Duration timeout,
                @DefaultValue("health:") String keyPrefix
        ) {}
    }

    public record CircuitBreakerConfig(
            @DefaultValue("true") boolean enabled,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.5") double failureRateThreshold,
            @Min(1) @DefaultValue("5") int minimumNumberOfCalls,
            @DefaultValue("PT1M") Duration slidingWindowSize,
            @DefaultValue("PT30S") Duration waitDurationInOpenState,
            @Min(1) @DefaultValue("3") int permittedNumberOfCallsInHalfOpenState
    ) {}

    public record AlertingConfig(
            @DefaultValue("false") boolean enabled,
            List<AlertChannel> channels,
            @DefaultValue("PT5M") Duration cooldownPeriod,
            @Valid ThresholdConfig thresholds
    ) {
        public record AlertChannel(
                @NotBlank String name,
                @NotNull ChannelType type,
                @NotEmpty Map<String, String> config
        ) {
            public enum ChannelType { SLACK, EMAIL, WEBHOOK, PAGERDUTY, TEAMS }
        }
        
        public record ThresholdConfig(
                @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.8") double healthRateThreshold,
                @DefaultValue("PT1M") Duration responseTimeThreshold,
                @Min(1) @DefaultValue("3") int consecutiveFailuresThreshold
        ) {}
    }

    public record DatabaseConfig(
            @DefaultValue("true") boolean enabled,
            @NotBlank @DefaultValue("SELECT 1") String validationQuery,
            @DefaultValue("PT10S") Duration timeout,
            @DefaultValue("false") boolean validateSchema,
            List<String> requiredTables
    ) {}

    public record MongoConfig(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("PT10S") Duration timeout,
            @DefaultValue("true") boolean validateCollections,
            List<String> requiredCollections
    ) {}

    public record KafkaConfig(
            @DefaultValue("false") boolean enabled,
            @NotBlank @DefaultValue("kafkaAdminClient") String adminClientBean,
            @DefaultValue("PT15S") Duration timeout,
            @Min(0) @DefaultValue("1") int minimumNodes,
            List<String> requiredTopics
    ) {}

    public record ExternalConfig(
            List<@Valid ServiceConfig> services
    ) {
        public record ServiceConfig(
                @NotBlank String name,
                @DefaultValue("true") boolean enabled,
                @NotBlank String restClientBean,
                @NotBlank String urlBean,
                @DefaultValue("PT10S") Duration timeout,
                @DefaultValue("HEAD") String method,
                @Valid AuthConfig auth,
                @Valid HealthConfig health
        ) {
            public record AuthConfig(
                    @DefaultValue("NONE") AuthType type,
                    Map<String, String> credentials
            ) {
                public enum AuthType { NONE, BASIC, BEARER, API_KEY, OAUTH2 }
            }
            
            public record HealthConfig(
                    @DefaultValue("/health") String endpoint,
                    List<Integer> expectedStatusCodes,
                    @DefaultValue("true") boolean followRedirects,
                    @DefaultValue("false") boolean validateResponse,
                    String expectedResponsePattern
            ) {}
        }
    }

    // Validation methods
    public boolean isValid() {
        if (!enabled) return true;
        
        // Validate at least one health check is enabled
        boolean hasEnabledCheck = database.enabled || mongodb.enabled || 
                                kafka.enabled || 
                                (external.services != null && external.services.stream().anyMatch(ServiceConfig::enabled));
        
        if (!hasEnabledCheck) {
            throw new IllegalStateException("At least one health check must be enabled when app.health.enabled=true");
        }
        
        // Validate security configuration in production mode
        if (productionMode && security.enabled && 
            security.authMode != SecurityConfig.AuthMode.NONE && 
            (security.username.isBlank() && security.jwtSecret.isBlank())) {
            throw new IllegalStateException("Security credentials must be configured in production mode");
        }
        
        return true;
    }

    // Helper methods
    public Duration getEffectiveTimeout(String checkType) {
        return switch (checkType.toLowerCase()) {
            case "database" -> database.timeout;
            case "mongodb" -> mongodb.timeout;
            case "kafka" -> kafka.timeout;
            default -> performance.defaultTimeout;
        };
    }

    public boolean isSecurityEnabled() {
        return security.enabled && security.authMode != SecurityConfig.AuthMode.NONE;
    }

    public boolean isCacheEnabled() {
        return cache.enabled;
    }

    public boolean isCircuitBreakerEnabled() {
        return circuitBreaker.enabled;
    }

    public boolean isAlertingEnabled() {
        return alerting.enabled && alerting.channels != null && !alerting.channels.isEmpty();
    }
}