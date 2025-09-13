package com.example.health.autoconfigure;

import com.example.health.checkers.SecureMongoHealthChecker;
import com.example.health.config.ValidatedHealthMonitoringProperties;
import com.example.health.core.HealthChecker;
import com.example.health.registry.HealthCheckRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Direct registration MongoDB health checker auto-configuration.
 * 
 * This configuration directly registers MongoDB health checkers into the HealthCheckRegistry
 * instead of creating beans that are discovered by ObjectProvider.
 * 
 * Features:
 * - Direct registration into HealthCheckRegistry
 * - No intermediate bean creation
 * - Centralized health checker management
 * - Proper ordering with registry lifecycle
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(MongoTemplate.class)
@ConditionalOnProperty(prefix = "app.health", name = "enabled", havingValue = "true", matchIfMissing = true)
@Order(2000) // Register after registry initialization (Order 1500)
public class DirectRegistrationMongoHealthAutoConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(DirectRegistrationMongoHealthAutoConfiguration.class);
    
    public DirectRegistrationMongoHealthAutoConfiguration() {
        logger.info("DirectRegistrationMongoHealthAutoConfiguration instantiated - will register directly into HealthCheckRegistry");
    }
    
    /**
     * Creates a registrar that adds MongoDB health checker directly to the registry.
     */
    @Bean
    @ConditionalOnBean({MongoTemplate.class, HealthCheckRegistry.class})
    @ConditionalOnProperty(prefix = "app.health.mongodb", name = "enabled", 
                          havingValue = "true", matchIfMissing = true)
    public MongoHealthCheckerRegistrar mongoHealthCheckerRegistrar(
            HealthCheckRegistry registry,
            MongoTemplate mongoTemplate,
            ValidatedHealthMonitoringProperties properties) {
        
        logger.info("Creating MongoDB health checker registrar for direct registry registration");
        return new MongoHealthCheckerRegistrar(registry, mongoTemplate, properties);
    }
    
    /**
     * MongoDB health checker registrar that directly registers into the registry.
     * This approach eliminates the need for ObjectProvider discovery patterns.
     */
    public static class MongoHealthCheckerRegistrar implements InitializingBean {
        
        private static final Logger logger = LoggerFactory.getLogger(MongoHealthCheckerRegistrar.class);
        
        private final HealthCheckRegistry registry;
        private final MongoTemplate mongoTemplate;
        private final ValidatedHealthMonitoringProperties properties;
        
        public MongoHealthCheckerRegistrar(HealthCheckRegistry registry,
                                         MongoTemplate mongoTemplate,
                                         ValidatedHealthMonitoringProperties properties) {
            this.registry = registry;
            this.mongoTemplate = mongoTemplate;
            this.properties = properties;
        }
        
        @Override
        public void afterPropertiesSet() {
            logger.info("Registering MongoDB health checker directly into HealthCheckRegistry");
            
            try {
                // Defensive programming: Validate inputs
                if (registry == null) {
                    throw new IllegalStateException("HealthCheckRegistry is required but was null");
                }
                if (mongoTemplate == null) {
                    throw new IllegalStateException("MongoTemplate is required but was null");
                }
                if (properties == null) {
                    throw new IllegalStateException("Health monitoring properties are required but were null");
                }
                
                ValidatedHealthMonitoringProperties.MongoConfig mongoConfig = properties.getMongo();
                if (mongoConfig == null) {
                    logger.warn("MongoDB configuration is null, using defaults");
                    mongoConfig = new ValidatedHealthMonitoringProperties.MongoConfig();
                }
                
                // Determine component name with fallback
                String componentName = determineComponentName(mongoTemplate, mongoConfig);
                
                logger.info("Creating secure MongoDB health checker for component '{}' targeting database '{}'", 
                           componentName, mongoTemplate.getDb().getName());
                
                // Create the health checker
                HealthChecker mongoHealthChecker = new SecureMongoHealthChecker(componentName, mongoTemplate, mongoConfig);
                
                // Register directly into the registry
                registry.register(mongoHealthChecker);
                
                logger.info("✅ Successfully registered MongoDB health checker '{}' directly into HealthCheckRegistry", componentName);
                
            } catch (Exception e) {
                logger.error("❌ Failed to register MongoDB health checker into registry", e);
                throw new IllegalStateException("MongoDB health checker registration failed", e);
            }
        }
        
        /**
         * Determines an appropriate component name for the health checker.
         */
        private String determineComponentName(MongoTemplate mongoTemplate, 
                                            ValidatedHealthMonitoringProperties.MongoConfig config) {
            // Use configured bean name if available
            if (config.getMongoTemplateBeanName() != null && !config.getMongoTemplateBeanName().trim().isEmpty()) {
                return config.getMongoTemplateBeanName();
            }
            
            // Use database name as component identifier
            try {
                String dbName = mongoTemplate.getDb().getName();
                return "mongodb-" + dbName;
            } catch (Exception e) {
                logger.debug("Could not determine database name for component naming: {}", e.getMessage());
                return "mongodb-primary";
            }
        }
    }
}