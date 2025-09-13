package com.example.health.autoconfigure;

import com.example.health.checkers.KafkaHealthChecker;
import com.example.health.config.ValidatedHealthMonitoringProperties;
import com.example.health.core.HealthChecker;
import org.apache.kafka.clients.admin.AdminClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Sonar-compliant auto-configuration for Kafka health checking.
 * 
 * This configuration class creates secure Kafka health checkers following
 * enterprise patterns with comprehensive quality improvements:
 * - Security hardening and input validation
 * - Proper error handling and resource management
 * - Defensive programming practices
 * - Performance optimizations
 * - Comprehensive logging and monitoring
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(AdminClient.class)
@ConditionalOnProperty(prefix = "app.health", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SonarCompliantKafkaHealthAutoConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(SonarCompliantKafkaHealthAutoConfiguration.class);
    
    /**
     * Creates a secure Kafka health checker when AdminClient is available.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.health.kafka", name = "enabled", havingValue = "true")
    public HealthChecker secureKafkaHealthChecker(BeanFactory beanFactory, 
                                                ValidatedHealthMonitoringProperties properties) {
        
        // Defensive programming: Validate inputs
        if (beanFactory == null) {
            throw new IllegalStateException("BeanFactory is required but was null");
        }
        if (properties == null) {
            throw new IllegalStateException("Health monitoring properties are required but were null");
        }
        
        ValidatedHealthMonitoringProperties.KafkaConfig kafkaConfig = properties.getKafka();
        if (kafkaConfig == null) {
            logger.warn("Kafka configuration is null, using defaults");
            kafkaConfig = new ValidatedHealthMonitoringProperties.KafkaConfig();
        }
        
        try {
            // Resolve AdminClient bean with proper error handling
            AdminClient adminClient = resolveAdminClientBean(beanFactory, kafkaConfig);
            
            // Determine component name with fallback
            String componentName = determineComponentName(kafkaConfig);
            
            logger.info("Creating secure Kafka health checker for component '{}' using AdminClient bean '{}'", 
                       componentName, kafkaConfig.getAdminClientBeanName());
            
            return new KafkaHealthChecker(componentName, adminClient, kafkaConfig);
            
        } catch (NoSuchBeanDefinitionException e) {
            logger.warn("Kafka AdminClient bean '{}' not found - Kafka health checking will be disabled: {}", 
                       kafkaConfig.getAdminClientBeanName(), e.getMessage());
            throw new IllegalStateException("Required Kafka AdminClient bean is not available", e);
        } catch (Exception e) {
            logger.error("Failed to create secure Kafka health checker: {}", e.getMessage(), e);
            throw new IllegalStateException("Kafka health checker configuration failed", e);
        }
    }
    
    /**
     * Resolves the AdminClient bean with comprehensive error handling.
     */
    private AdminClient resolveAdminClientBean(BeanFactory beanFactory, 
                                             ValidatedHealthMonitoringProperties.KafkaConfig config) {
        String beanName = validateAndGetBeanName(config);
        
        try {
            logger.debug("Resolving Kafka AdminClient by bean name: {}", beanName);
            return beanFactory.getBean(beanName, AdminClient.class);
            
        } catch (NoSuchBeanDefinitionException e) {
            logger.error("Kafka AdminClient bean '{}' not found in application context", beanName);
            throw e;
        } catch (Exception e) {
            logger.error("Failed to resolve Kafka AdminClient bean '{}': {}", beanName, e.getMessage());
            throw new IllegalStateException("AdminClient resolution failed for bean: " + beanName, e);
        }
    }
    
    /**
     * Validates and gets the AdminClient bean name.
     */
    private String validateAndGetBeanName(ValidatedHealthMonitoringProperties.KafkaConfig config) {
        String beanName = config.getAdminClientBeanName();
        
        if (beanName == null || beanName.trim().isEmpty()) {
            throw new IllegalArgumentException("Kafka AdminClient bean name must be specified and cannot be empty");
        }
        
        String trimmedBeanName = beanName.trim();
        
        // Security: Validate bean name format
        if (!trimmedBeanName.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid AdminClient bean name format: " + trimmedBeanName + 
                                             ". Bean name must start with a letter and contain only letters, numbers, and underscores.");
        }
        
        // Security: Prevent excessively long bean names
        if (trimmedBeanName.length() > 100) {
            throw new IllegalArgumentException("AdminClient bean name too long (max 100 characters): " + trimmedBeanName);
        }
        
        return trimmedBeanName;
    }
    
    /**
     * Determines an appropriate component name with security considerations.
     */
    private String determineComponentName(ValidatedHealthMonitoringProperties.KafkaConfig config) {
        try {
            String beanName = config.getAdminClientBeanName();
            
            if (beanName != null && !beanName.trim().isEmpty()) {
                String sanitized = sanitizeComponentName(beanName.trim());
                
                // Convert bean name to a more readable component name
                if (sanitized.toLowerCase().contains("admin")) {
                    return "kafka-cluster";
                } else {
                    return "kafka-" + sanitized;
                }
            }
            
            return "kafka-primary";
            
        } catch (Exception e) {
            logger.debug("Could not determine component name, using default: {}", e.getMessage());
            return "kafka-primary";
        }
    }
    
    /**
     * Security: Sanitize component name to prevent injection attacks.
     */
    private String sanitizeComponentName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "kafka";
        }
        
        // Remove non-alphanumeric characters except hyphens and underscores
        String sanitized = name.replaceAll("[^a-zA-Z0-9_-]", "-");
        
        // Ensure it starts with a letter
        if (!sanitized.matches("^[a-zA-Z].*")) {
            sanitized = "kafka-" + sanitized;
        }
        
        // Limit length
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 47) + "...";
        }
        
        return sanitized;
    }
}