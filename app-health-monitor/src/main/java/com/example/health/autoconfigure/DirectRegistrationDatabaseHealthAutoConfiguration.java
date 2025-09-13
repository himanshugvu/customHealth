package com.example.health.autoconfigure;

import com.example.health.checkers.SecureDatabaseHealthChecker;
import com.example.health.config.ValidatedHealthMonitoringProperties;
import com.example.health.core.HealthChecker;
import com.example.health.registry.HealthCheckRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;

/**
 * Direct registration database health checker auto-configuration.
 * 
 * This configuration directly registers database health checkers into the HealthCheckRegistry
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
@ConditionalOnClass(DataSource.class)
@ConditionalOnProperty(prefix = "app.health", name = "enabled", havingValue = "true", matchIfMissing = true)
@Order(1900) // Register after registry initialization but before other services
public class DirectRegistrationDatabaseHealthAutoConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(DirectRegistrationDatabaseHealthAutoConfiguration.class);
    
    /**
     * Constructor that immediately registers database health checker into the registry.
     * No beans, no InitializingBean - just direct registration.
     */
    public DirectRegistrationDatabaseHealthAutoConfiguration(
            HealthCheckRegistry registry,
            DataSource dataSource,
            ValidatedHealthMonitoringProperties properties) {
        
        logger.info("DirectRegistrationDatabaseHealthAutoConfiguration instantiated - registering database health checker directly");
        
        try {
            // Check if database health check is enabled
            ValidatedHealthMonitoringProperties.DatabaseConfig dbConfig = properties.getDatabase();
            if (dbConfig == null) {
                logger.warn("Database configuration is null, using defaults");
                dbConfig = new ValidatedHealthMonitoringProperties.DatabaseConfig();
            }
            
            if (!dbConfig.isEnabled()) {
                logger.info("Database health checking is disabled, skipping registration");
                return;
            }
            
            // Defensive programming: Validate inputs
            if (registry == null) {
                throw new IllegalStateException("HealthCheckRegistry is required but was null");
            }
            if (dataSource == null) {
                throw new IllegalStateException("DataSource is required but was null");
            }
            if (properties == null) {
                throw new IllegalStateException("Health monitoring properties are required but were null");
            }
            
            // Determine component name with fallback
            String componentName = determineComponentName(dataSource, dbConfig);
            
            logger.info("Creating secure database health checker for component '{}'", componentName);
            
            // Create and register the health checker immediately
            HealthChecker databaseHealthChecker = new SecureDatabaseHealthChecker(componentName, dataSource, dbConfig);
            registry.register(databaseHealthChecker);
            
            logger.info("✅ Successfully registered database health checker '{}' directly in constructor", componentName);
            
        } catch (Exception e) {
            logger.error("❌ Failed to register database health checker in constructor", e);
            throw new IllegalStateException("Database health checker registration failed", e);
        }
    }
    
    /**
     * Determines an appropriate component name for the health checker.
     */
    private String determineComponentName(DataSource dataSource, 
                                        ValidatedHealthMonitoringProperties.DatabaseConfig config) {
        
        // Use configured bean name if available
        if (config.getDataSourceBeanName() != null && !config.getDataSourceBeanName().trim().isEmpty()) {
            return config.getDataSourceBeanName();
        }
        
        // Try to extract database information from DataSource
        try {
            String url = dataSource.getConnection().getMetaData().getURL();
            if (url != null) {
                // Extract database type from JDBC URL
                if (url.contains("h2")) {
                    return "database-h2";
                } else if (url.contains("mysql")) {
                    return "database-mysql";
                } else if (url.contains("postgresql")) {
                    return "database-postgresql";
                } else if (url.contains("oracle")) {
                    return "database-oracle";
                } else if (url.contains("sqlserver")) {
                    return "database-sqlserver";
                }
            }
        } catch (Exception e) {
            logger.debug("Could not extract database information for component naming: {}", e.getMessage());
        }
        
        return "database-primary";
    }
}