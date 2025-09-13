package com.example.health.autoconfigure;

import com.example.health.checkers.DatabaseHealthChecker;
import com.example.health.config.HealthMonitoringProperties;
import com.example.health.core.HealthChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Enhanced auto-configuration for database health checking.
 * 
 * This configuration class creates database health checkers following
 * enterprise patterns with proper error handling, bean resolution,
 * and configuration validation.
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(DataSource.class)
@ConditionalOnProperty(prefix = "app.health", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EnhancedDatabaseHealthAutoConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedDatabaseHealthAutoConfiguration.class);
    
    /**
     * Creates a database health checker when DataSource is available.
     */
    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "app.health.db", name = "enabled", havingValue = "true", matchIfMissing = true)
    public HealthChecker databaseHealthChecker(BeanFactory beanFactory, HealthMonitoringProperties properties) {
        
        HealthMonitoringProperties.DatabaseConfig dbConfig = properties.getDatabase();
        
        try {
            // Resolve DataSource bean
            DataSource dataSource = resolveDataSourceBean(beanFactory, dbConfig);
            
            // Determine component name
            String componentName = determineComponentName(dataSource, dbConfig);
            
            logger.info("Creating database health checker for component '{}' with validation query '{}'", 
                       componentName, dbConfig.getValidationQuery());
            
            return new DatabaseHealthChecker(componentName, dataSource, dbConfig);
            
        } catch (Exception e) {
            logger.error("Failed to create database health checker", e);
            throw new IllegalStateException("Database health checker configuration failed", e);
        }
    }
    
    /**
     * Resolves the DataSource bean using configured name or type-based lookup.
     */
    private DataSource resolveDataSourceBean(BeanFactory beanFactory, HealthMonitoringProperties.DatabaseConfig config) {
        if (config.getDataSourceBeanName() != null && !config.getDataSourceBeanName().trim().isEmpty()) {
            logger.debug("Resolving DataSource by bean name: {}", config.getDataSourceBeanName());
            return beanFactory.getBean(config.getDataSourceBeanName(), DataSource.class);
        } else {
            logger.debug("Resolving DataSource by type");
            return beanFactory.getBean(DataSource.class);
        }
    }
    
    /**
     * Determines an appropriate component name for the health checker.
     */
    private String determineComponentName(DataSource dataSource, HealthMonitoringProperties.DatabaseConfig config) {
        if (config.getDataSourceBeanName() != null && !config.getDataSourceBeanName().trim().isEmpty()) {
            return config.getDataSourceBeanName();
        }
        
        // Try to extract meaningful name from DataSource
        String className = dataSource.getClass().getSimpleName();
        if (className.toLowerCase().contains("hikari")) {
            return "hikaricp-database";
        } else if (className.toLowerCase().contains("tomcat")) {
            return "tomcat-database";
        } else if (className.toLowerCase().contains("h2")) {
            return "h2-database";
        } else {
            return "primary-database";
        }
    }
}