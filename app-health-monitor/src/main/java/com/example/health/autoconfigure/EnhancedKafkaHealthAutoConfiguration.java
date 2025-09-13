package com.example.health.autoconfigure;

import com.example.health.checkers.KafkaHealthChecker;
import com.example.health.config.HealthMonitoringProperties;
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
 * Enhanced auto-configuration for Kafka health checking.
 * 
 * This configuration class creates Kafka health checkers following
 * enterprise patterns with proper error handling, bean resolution,
 * and configuration validation.
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(AdminClient.class)
@ConditionalOnProperty(prefix = "app.health.monitoring", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EnhancedKafkaHealthAutoConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedKafkaHealthAutoConfiguration.class);
    
    /**
     * Creates a Kafka health checker when AdminClient is available.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.health.monitoring.kafka", name = "enabled", havingValue = "true")
    public HealthChecker kafkaHealthChecker(BeanFactory beanFactory, HealthMonitoringProperties properties) {
        
        HealthMonitoringProperties.KafkaConfig kafkaConfig = properties.getKafka();
        
        try {
            // Resolve AdminClient bean
            AdminClient adminClient = resolveAdminClientBean(beanFactory, kafkaConfig);
            
            // Determine component name
            String componentName = determineComponentName(kafkaConfig);
            
            logger.info("Creating Kafka health checker for component '{}' using AdminClient bean '{}'", 
                       componentName, kafkaConfig.getAdminClientBeanName());
            
            return new KafkaHealthChecker(componentName, adminClient, kafkaConfig);
            
        } catch (NoSuchBeanDefinitionException e) {
            logger.warn("Kafka AdminClient bean '{}' not found - Kafka health checking will be disabled", 
                       kafkaConfig.getAdminClientBeanName());
            throw e;
        } catch (Exception e) {
            logger.error("Failed to create Kafka health checker", e);
            throw new IllegalStateException("Kafka health checker configuration failed", e);
        }
    }
    
    /**
     * Resolves the AdminClient bean using the configured bean name.
     */
    private AdminClient resolveAdminClientBean(BeanFactory beanFactory, HealthMonitoringProperties.KafkaConfig config) {
        String beanName = config.getAdminClientBeanName();
        
        if (beanName == null || beanName.trim().isEmpty()) {
            throw new IllegalArgumentException("Kafka AdminClient bean name must be specified");
        }
        
        logger.debug("Resolving Kafka AdminClient by bean name: {}", beanName);
        return beanFactory.getBean(beanName, AdminClient.class);
    }
    
    /**
     * Determines an appropriate component name for the health checker.
     */
    private String determineComponentName(HealthMonitoringProperties.KafkaConfig config) {
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