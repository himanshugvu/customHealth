package com.example.health.autoconfigure;

import com.example.health.checkers.KafkaHealthChecker;
import com.example.health.config.ValidatedHealthMonitoringProperties;
import com.example.health.core.HealthChecker;
import com.example.health.registry.HealthCheckRegistry;
import org.apache.kafka.clients.admin.AdminClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;

/**
 * Direct registration Kafka health checker auto-configuration.
 * 
 * This configuration directly registers Kafka health checkers into the HealthCheckRegistry
 * in its constructor, eliminating the need for any beans or InitializingBean patterns.
 * 
 * Features:
 * - Constructor-based direct registration
 * - No bean creation whatsoever
 * - Immediate registration on instantiation
 * - Bean name resolution for AdminClient
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(AdminClient.class)
@ConditionalOnProperty(prefix = "app.health", name = "enabled", havingValue = "true", matchIfMissing = true)
@Order(2050) // Register after registry and database, before external services
public class DirectRegistrationKafkaHealthAutoConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(DirectRegistrationKafkaHealthAutoConfiguration.class);
    
    /**
     * Constructor that immediately registers Kafka health checker into the registry.
     * No beans, no InitializingBean - just direct registration.
     */
    public DirectRegistrationKafkaHealthAutoConfiguration(
            HealthCheckRegistry registry,
            BeanFactory beanFactory,
            ValidatedHealthMonitoringProperties properties) {
        
        logger.info("DirectRegistrationKafkaHealthAutoConfiguration instantiated - registering Kafka health checker directly");
        
        try {
            // Check if Kafka health check is enabled
            ValidatedHealthMonitoringProperties.KafkaConfig kafkaConfig = properties.getKafka();
            if (kafkaConfig == null) {
                logger.warn("Kafka configuration is null, using defaults");
                kafkaConfig = new ValidatedHealthMonitoringProperties.KafkaConfig();
            }
            
            if (!kafkaConfig.isEnabled()) {
                logger.info("Kafka health checking is disabled, skipping registration");
                return;
            }
            
            // Defensive programming: Validate inputs
            if (registry == null) {
                throw new IllegalStateException("HealthCheckRegistry is required but was null");
            }
            if (beanFactory == null) {
                throw new IllegalStateException("BeanFactory is required but was null");
            }
            if (properties == null) {
                throw new IllegalStateException("Health monitoring properties are required but were null");
            }
            
            try {
                // Resolve AdminClient bean
                AdminClient adminClient = resolveAdminClientBean(beanFactory, kafkaConfig);
                
                // Determine component name
                String componentName = determineComponentName(kafkaConfig);
                
                logger.info("Creating Kafka health checker for component '{}' using AdminClient bean '{}'", 
                           componentName, kafkaConfig.getAdminClientBeanName());
                
                // Create and register the health checker immediately
                HealthChecker kafkaHealthChecker = new KafkaHealthChecker(componentName, adminClient, kafkaConfig);
                registry.register(kafkaHealthChecker);
                
                logger.info("✅ Successfully registered Kafka health checker '{}' directly in constructor", componentName);
                
            } catch (NoSuchBeanDefinitionException e) {
                logger.warn("❌ Kafka AdminClient bean '{}' not found - Kafka health checking will be skipped", 
                           kafkaConfig.getAdminClientBeanName());
                // Don't throw - just skip this health checker
                
            } catch (Exception e) {
                logger.error("❌ Failed to register Kafka health checker in constructor", e);
                throw new IllegalStateException("Kafka health checker registration failed", e);
            }
            
        } catch (Exception e) {
            logger.error("❌ Failed to register Kafka health checker in constructor", e);
            throw new IllegalStateException("Kafka health checker registration failed", e);
        }
    }
    
    /**
     * Resolves the AdminClient bean using the configured bean name.
     */
    private AdminClient resolveAdminClientBean(BeanFactory beanFactory, ValidatedHealthMonitoringProperties.KafkaConfig config) {
        String beanName = config.getAdminClientBeanName();
        
        if (beanName == null || beanName.trim().isEmpty()) {
            throw new IllegalArgumentException("Kafka AdminClient bean name must be specified in configuration");
        }
        
        logger.debug("Resolving Kafka AdminClient by bean name: {}", beanName);
        return beanFactory.getBean(beanName, AdminClient.class);
    }
    
    /**
     * Determines an appropriate component name for the health checker.
     */
    private String determineComponentName(ValidatedHealthMonitoringProperties.KafkaConfig config) {
        String beanName = config.getAdminClientBeanName();
        
        if (beanName != null && !beanName.trim().isEmpty()) {
            // Convert bean name to a more readable component name
            if (beanName.toLowerCase().contains("admin")) {
                return "kafka-cluster";
            } else {
                return "kafka-" + beanName;
            }
        }
        
        return "kafka-primary";
    }
}