package com.example.health.autoconfigure;

import com.example.health.checkers.SecureMongoHealthChecker;
import com.example.health.config.ValidatedHealthMonitoringProperties;
import com.example.health.core.HealthChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Enhanced auto-configuration for MongoDB health checking.
 * 
 * This configuration class creates MongoDB health checkers following
 * enterprise patterns with proper error handling, bean resolution,
 * and configuration validation.
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(MongoTemplate.class)
@ConditionalOnProperty(prefix = "app.health", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EnhancedMongoHealthAutoConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedMongoHealthAutoConfiguration.class);
    
    /**
     * Creates a MongoDB health checker when MongoTemplate is available.
     */
    @Bean
    @ConditionalOnBean(MongoTemplate.class)
    @ConditionalOnProperty(prefix = "app.health.mongodb", name = "enabled", havingValue = "true", matchIfMissing = true)
    public HealthChecker mongoHealthChecker(BeanFactory beanFactory, ValidatedHealthMonitoringProperties properties) {
        
        ValidatedHealthMonitoringProperties.MongoConfig mongoConfig = properties.getMongo();
        
        try {
            // Resolve MongoTemplate bean
            MongoTemplate mongoTemplate = resolveMongoTemplateBean(beanFactory, mongoConfig);
            
            // Determine component name
            String componentName = determineComponentName(mongoTemplate, mongoConfig);
            
            logger.info("Creating MongoDB health checker for component '{}' targeting database '{}'", 
                       componentName, mongoTemplate.getDb().getName());
            
            return new SecureMongoHealthChecker(componentName, mongoTemplate, mongoConfig);
            
        } catch (Exception e) {
            logger.error("Failed to create MongoDB health checker", e);
            throw new IllegalStateException("MongoDB health checker configuration failed", e);
        }
    }
    
    /**
     * Resolves the MongoTemplate bean using configured name or type-based lookup.
     */
    private MongoTemplate resolveMongoTemplateBean(BeanFactory beanFactory, ValidatedHealthMonitoringProperties.MongoConfig config) {
        if (config.getMongoTemplateBeanName() != null && !config.getMongoTemplateBeanName().trim().isEmpty()) {
            logger.debug("Resolving MongoTemplate by bean name: {}", config.getMongoTemplateBeanName());
            return beanFactory.getBean(config.getMongoTemplateBeanName(), MongoTemplate.class);
        } else {
            logger.debug("Resolving MongoTemplate by type");
            return beanFactory.getBean(MongoTemplate.class);
        }
    }
    
    /**
     * Determines an appropriate component name for the health checker.
     */
    private String determineComponentName(MongoTemplate mongoTemplate, ValidatedHealthMonitoringProperties.MongoConfig config) {
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