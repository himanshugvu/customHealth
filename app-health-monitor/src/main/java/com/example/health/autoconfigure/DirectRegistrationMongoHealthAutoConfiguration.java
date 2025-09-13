package com.example.health.autoconfigure;

import com.example.health.checkers.SecureMongoHealthChecker;
import com.example.health.config.ValidatedHealthMonitoringProperties;
import com.example.health.core.HealthChecker;
import com.example.health.registry.HealthCheckRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Direct registration MongoDB health checker auto-configuration.
 * 
 * This configuration directly registers MongoDB health checkers into the HealthCheckRegistry
 * in its constructor, eliminating the need for any beans or InitializingBean patterns.
 * 
 * Features:
 * - Constructor-based direct registration
 * - No bean creation whatsoever
 * - Immediate registration on instantiation
 * - Cleaner lifecycle management
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
    
    /**
     * Constructor that immediately registers MongoDB health checker into the registry.
     * No beans, no InitializingBean - just direct registration.
     */
    public DirectRegistrationMongoHealthAutoConfiguration(
            HealthCheckRegistry registry,
            MongoTemplate mongoTemplate,
            ValidatedHealthMonitoringProperties properties) {
        
        logger.info("DirectRegistrationMongoHealthAutoConfiguration instantiated - registering MongoDB health checker directly");
        
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
            
            if (!mongoConfig.isEnabled()) {
                logger.info("MongoDB health checking is disabled, skipping registration");
                return;
            }
            
            // Determine component name with fallback
            String componentName = determineComponentName(mongoTemplate, mongoConfig);
            
            logger.info("Creating secure MongoDB health checker for component '{}'", componentName);
            
            // Create and register the health checker immediately
            HealthChecker mongoHealthChecker = new SecureMongoHealthChecker(componentName, mongoTemplate, mongoConfig);
            registry.register(mongoHealthChecker);
            
            logger.info("✅ Successfully registered MongoDB health checker '{}' directly in constructor", componentName);
            
        } catch (Exception e) {
            logger.error("❌ Failed to register MongoDB health checker in constructor", e);
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