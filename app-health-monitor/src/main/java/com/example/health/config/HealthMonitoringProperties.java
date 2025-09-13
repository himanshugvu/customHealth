package com.example.health.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the health monitoring library.
 * 
 * This class provides comprehensive configuration options with proper validation,
 * documentation, and sensible defaults. It follows the Spring Boot configuration
 * properties pattern for seamless integration.
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "app.health.monitoring")
@Validated
public class HealthMonitoringProperties {
    
    /**
     * Whether health monitoring is enabled.
     */
    private boolean enabled = true;
    
    /**
     * Whether to perform startup health checks and log results.
     */
    private boolean startupLogging = true;
    
    /**
     * Global timeout for health checks.
     */
    @NotNull
    private Duration defaultTimeout = Duration.ofSeconds(5);
    
    /**
     * Database health check configuration.
     */
    @Valid
    private DatabaseConfig database = new DatabaseConfig();
    
    /**
     * MongoDB health check configuration.
     */
    @Valid
    private MongoConfig mongo = new MongoConfig();
    
    /**
     * Kafka health check configuration.
     */
    @Valid
    private KafkaConfig kafka = new KafkaConfig();
    
    /**
     * External service health check configurations.
     */
    @Valid
    private List<ExternalServiceConfig> externalServices = new ArrayList<>();
    
    /**
     * Logging configuration for health checks.
     */
    @Valid
    private LoggingConfig logging = new LoggingConfig();
    
    // Getters and Setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public boolean isStartupLogging() { return startupLogging; }
    public void setStartupLogging(boolean startupLogging) { this.startupLogging = startupLogging; }
    
    public Duration getDefaultTimeout() { return defaultTimeout; }
    public void setDefaultTimeout(Duration defaultTimeout) { this.defaultTimeout = defaultTimeout; }
    
    public DatabaseConfig getDatabase() { return database; }
    public void setDatabase(DatabaseConfig database) { this.database = database; }
    
    public MongoConfig getMongo() { return mongo; }
    public void setMongo(MongoConfig mongo) { this.mongo = mongo; }
    
    public KafkaConfig getKafka() { return kafka; }
    public void setKafka(KafkaConfig kafka) { this.kafka = kafka; }
    
    public List<ExternalServiceConfig> getExternalServices() { return externalServices; }
    public void setExternalServices(List<ExternalServiceConfig> externalServices) { this.externalServices = externalServices; }
    
    public LoggingConfig getLogging() { return logging; }
    public void setLogging(LoggingConfig logging) { this.logging = logging; }
    
    /**
     * Database health check configuration.
     */
    public static class DatabaseConfig {
        private boolean enabled = true;
        private String validationQuery = "SELECT 1";
        private String dataSourceBeanName;
        private Duration timeout;
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        @NotBlank(message = "Validation query cannot be blank")
        public String getValidationQuery() { return validationQuery; }
        public void setValidationQuery(String validationQuery) { this.validationQuery = validationQuery; }
        
        public String getDataSourceBeanName() { return dataSourceBeanName; }
        public void setDataSourceBeanName(String dataSourceBeanName) { this.dataSourceBeanName = dataSourceBeanName; }
        
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
    }
    
    /**
     * MongoDB health check configuration.
     */
    public static class MongoConfig {
        private boolean enabled = true;
        private String mongoTemplateBeanName;
        private Duration timeout;
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getMongoTemplateBeanName() { return mongoTemplateBeanName; }
        public void setMongoTemplateBeanName(String mongoTemplateBeanName) { this.mongoTemplateBeanName = mongoTemplateBeanName; }
        
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
    }
    
    /**
     * Kafka health check configuration.
     */
    public static class KafkaConfig {
        private boolean enabled = false;
        private String adminClientBeanName = "kafkaAdminClient";
        private Duration timeout;
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        @NotBlank(message = "Admin client bean name cannot be blank")
        public String getAdminClientBeanName() { return adminClientBeanName; }
        public void setAdminClientBeanName(String adminClientBeanName) { this.adminClientBeanName = adminClientBeanName; }
        
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
    }
    
    /**
     * External service health check configuration.
     */
    public static class ExternalServiceConfig {
        @NotBlank(message = "Service name cannot be blank")
        private String name;
        
        private boolean enabled = true;
        
        @NotBlank(message = "REST client bean name cannot be blank")
        private String restClientBeanName;
        
        @NotBlank(message = "URL bean name cannot be blank")
        private String urlBeanName;
        
        private Duration timeout;
        
        @PositiveOrZero
        private int retries = 0;
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getRestClientBeanName() { return restClientBeanName; }
        public void setRestClientBeanName(String restClientBeanName) { this.restClientBeanName = restClientBeanName; }
        
        public String getUrlBeanName() { return urlBeanName; }
        public void setUrlBeanName(String urlBeanName) { this.urlBeanName = urlBeanName; }
        
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        
        public int getRetries() { return retries; }
        public void setRetries(int retries) { this.retries = retries; }
    }
    
    /**
     * Logging configuration for health checks.
     */
    public static class LoggingConfig {
        private boolean structured = true;
        private boolean includeStackTrace = false;
        
        @Positive
        private int maxErrorMessageLength = 200;
        
        public boolean isStructured() { return structured; }
        public void setStructured(boolean structured) { this.structured = structured; }
        
        public boolean isIncludeStackTrace() { return includeStackTrace; }
        public void setIncludeStackTrace(boolean includeStackTrace) { this.includeStackTrace = includeStackTrace; }
        
        public int getMaxErrorMessageLength() { return maxErrorMessageLength; }
        public void setMaxErrorMessageLength(int maxErrorMessageLength) { this.maxErrorMessageLength = maxErrorMessageLength; }
    }
}