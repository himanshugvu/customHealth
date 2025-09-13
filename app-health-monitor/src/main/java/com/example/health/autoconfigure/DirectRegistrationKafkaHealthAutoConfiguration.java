package com.example.health.autoconfigure;

import com.example.health.checkers.KafkaHealthChecker;
import com.example.health.config.ValidatedHealthMonitoringProperties;
import com.example.health.core.HealthChecker;
import com.example.health.registry.HealthCheckRegistry;
import org.apache.kafka.clients.admin.AdminClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

/**
 * Direct registration Kafka health checker auto-configuration.
 * 
 * This configuration directly registers Kafka health checkers into the HealthCheckRegistry
 * instead of creating beans that are discovered by ObjectProvider.
 * 
 * Features:
 * - Direct registration into HealthCheckRegistry
 * - No intermediate bean creation
 * - Centralized health checker management
 * - Proper ordering with registry lifecycle
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
    
    public DirectRegistrationKafkaHealthAutoConfiguration() {
        logger.info("DirectRegistrationKafkaHealthAutoConfiguration instantiated - will register directly into HealthCheckRegistry");
    }
    
    /**
     * Creates a registrar that adds Kafka health checker directly to the registry.
     */
    @Bean
    @ConditionalOnBean(HealthCheckRegistry.class)
    @ConditionalOnProperty(prefix = "app.health.kafka", name = "enabled", havingValue = "true")
    public KafkaHealthCheckerRegistrar kafkaHealthCheckerRegistrar(
            HealthCheckRegistry registry,
            BeanFactory beanFactory,
            ValidatedHealthMonitoringProperties properties) {
        
        logger.info("Creating Kafka health checker registrar for direct registry registration");
        return new KafkaHealthCheckerRegistrar(registry, beanFactory, properties);
    }
    
    /**
     * Kafka health checker registrar that directly registers into the registry.
     * This approach eliminates the need for ObjectProvider discovery patterns.
     */
    public static class KafkaHealthCheckerRegistrar implements InitializingBean {
        
        private static final Logger logger = LoggerFactory.getLogger(KafkaHealthCheckerRegistrar.class);
        
        private final HealthCheckRegistry registry;
        private final BeanFactory beanFactory;
        private final ValidatedHealthMonitoringProperties properties;
        
        public KafkaHealthCheckerRegistrar(HealthCheckRegistry registry,
                                         BeanFactory beanFactory,
                                         ValidatedHealthMonitoringProperties properties) {
            this.registry = registry;
            this.beanFactory = beanFactory;
            this.properties = properties;
        }
        
        @Override
        public void afterPropertiesSet() {
            logger.info("Registering Kafka health checker directly into HealthCheckRegistry");
            
            try {
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
                
                ValidatedHealthMonitoringProperties.KafkaConfig kafkaConfig = properties.getKafka();
                if (kafkaConfig == null) {
                    logger.warn("Kafka configuration is null, using defaults");
                    kafkaConfig = new ValidatedHealthMonitoringProperties.KafkaConfig();
                }
                
                try {
                    // Resolve AdminClient bean
                    AdminClient adminClient = resolveAdminClientBean(kafkaConfig);
                    
                    // Determine component name
                    String componentName = determineComponentName(kafkaConfig);
                    
                    logger.info("Creating Kafka health checker for component '{}' using AdminClient bean '{}'", 
                               componentName, kafkaConfig.getAdminClientBeanName());
                    
                    // Create the health checker
                    HealthChecker kafkaHealthChecker = new KafkaHealthChecker(componentName, adminClient, kafkaConfig);
                    
                    // Register directly into the registry
                    registry.register(kafkaHealthChecker);
                    
                    logger.info("✅ Successfully registered Kafka health checker '{}' directly into HealthCheckRegistry", componentName);
                    
                } catch (NoSuchBeanDefinitionException e) {
                    logger.warn("❌ Kafka AdminClient bean '{}' not found - Kafka health checking will be skipped", 
                               kafkaConfig.getAdminClientBeanName());
                    // Don't throw - just skip this health checker
                    
                } catch (Exception e) {
                    logger.error("❌ Failed to register Kafka health checker into registry", e);
                    throw new IllegalStateException("Kafka health checker registration failed", e);
                }
                
            } catch (Exception e) {
                logger.error("❌ Failed to register Kafka health checker into registry", e);
                throw new IllegalStateException("Kafka health checker registration failed", e);
            }
        }
        
        /**
         * Resolves the AdminClient bean using the configured bean name.
         */
        private AdminClient resolveAdminClientBean(ValidatedHealthMonitoringProperties.KafkaConfig config) {
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
}