package com.example.health.autoconfigure;

import com.example.health.checkers.SecureDatabaseHealthChecker;
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

import javax.sql.DataSource;

/**
 * Direct registration database health checker auto-configuration.
 * 
 * This configuration directly registers database health checkers into the HealthCheckRegistry
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
@ConditionalOnClass(DataSource.class)
@ConditionalOnProperty(prefix = "app.health", name = "enabled", havingValue = "true", matchIfMissing = true)
@Order(1900) // Register after registry initialization but before other services
public class DirectRegistrationDatabaseHealthAutoConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(DirectRegistrationDatabaseHealthAutoConfiguration.class);
    
    public DirectRegistrationDatabaseHealthAutoConfiguration() {
        logger.info("DirectRegistrationDatabaseHealthAutoConfiguration instantiated - will register directly into HealthCheckRegistry");
    }
    
    /**
     * Creates a registrar that adds database health checker directly to the registry.
     */
    @Bean
    @ConditionalOnBean({DataSource.class, HealthCheckRegistry.class})
    @ConditionalOnProperty(prefix = "app.health.db", name = "enabled", 
                          havingValue = "true", matchIfMissing = true)
    public DatabaseHealthCheckerRegistrar databaseHealthCheckerRegistrar(
            HealthCheckRegistry registry,
            DataSource dataSource,
            ValidatedHealthMonitoringProperties properties) {
        
        logger.info("Creating database health checker registrar for direct registry registration");
        return new DatabaseHealthCheckerRegistrar(registry, dataSource, properties);
    }
    
    /**
     * Database health checker registrar that directly registers into the registry.
     * This approach eliminates the need for ObjectProvider discovery patterns.
     */
    public static class DatabaseHealthCheckerRegistrar implements InitializingBean {
        
        private static final Logger logger = LoggerFactory.getLogger(DatabaseHealthCheckerRegistrar.class);
        
        private final HealthCheckRegistry registry;
        private final DataSource dataSource;
        private final ValidatedHealthMonitoringProperties properties;
        
        public DatabaseHealthCheckerRegistrar(HealthCheckRegistry registry,
                                            DataSource dataSource,
                                            ValidatedHealthMonitoringProperties properties) {
            this.registry = registry;
            this.dataSource = dataSource;
            this.properties = properties;
        }
        
        @Override
        public void afterPropertiesSet() {
            logger.info("Registering database health checker directly into HealthCheckRegistry");
            
            try {
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
                
                ValidatedHealthMonitoringProperties.DatabaseConfig dbConfig = properties.getDatabase();
                if (dbConfig == null) {
                    logger.warn("Database configuration is null, using defaults");
                    dbConfig = new ValidatedHealthMonitoringProperties.DatabaseConfig();
                }
                
                // Determine component name with fallback
                String componentName = determineComponentName(dataSource, dbConfig);
                
                logger.info("Creating secure database health checker for component '{}'", componentName);
                
                // Create the health checker
                HealthChecker databaseHealthChecker = new SecureDatabaseHealthChecker(componentName, dataSource, dbConfig);
                
                // Register directly into the registry
                registry.register(databaseHealthChecker);
                
                logger.info("✅ Successfully registered database health checker '{}' directly into HealthCheckRegistry", componentName);
                
            } catch (Exception e) {
                logger.error("❌ Failed to register database health checker into registry", e);
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
}