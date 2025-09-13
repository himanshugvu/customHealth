package com.example.health.autoconfigure;

import com.example.health.checkers.SecureDatabaseHealthChecker;
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

import javax.sql.DataSource;

/**
 * Sonar-compliant auto-configuration for database health checking.
 * 
 * This configuration class creates secure database health checkers following
 * enterprise patterns with comprehensive quality improvements:
 * - Security hardening against SQL injection
 * - Proper error handling and resource management
 * - Defensive programming practices
 * - Performance optimizations
 * - Comprehensive logging and monitoring
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(DataSource.class)
@ConditionalOnProperty(prefix = "app.health", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SonarCompliantDatabaseHealthAutoConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(SonarCompliantDatabaseHealthAutoConfiguration.class);
    
    /**
     * Creates a secure database health checker when DataSource is available.
     */
    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "app.health.db", name = "enabled", 
                          havingValue = "true", matchIfMissing = true)
    public HealthChecker secureDatabaseHealthChecker(BeanFactory beanFactory, 
                                                   ValidatedHealthMonitoringProperties properties) {
        
        // Defensive programming: Validate inputs
        if (beanFactory == null) {
            throw new IllegalStateException("BeanFactory is required but was null");
        }
        if (properties == null) {
            throw new IllegalStateException("Health monitoring properties are required but were null");
        }
        
        ValidatedHealthMonitoringProperties.DatabaseConfig dbConfig = properties.getDatabase();
        if (dbConfig == null) {
            logger.warn("Database configuration is null, using defaults");
            dbConfig = new ValidatedHealthMonitoringProperties.DatabaseConfig();
        }
        
        try {
            // Resolve DataSource bean with proper error handling
            DataSource dataSource = resolveDataSourceBean(beanFactory, dbConfig);
            
            // Determine component name with fallback
            String componentName = determineComponentName(dataSource, dbConfig);
            
            logger.info("Creating secure database health checker for component '{}' with validation query '{}'", 
                       componentName, dbConfig.getValidationQuery());
            
            return new SecureDatabaseHealthChecker(componentName, dataSource, dbConfig);
            
        } catch (Exception e) {
            logger.error("Failed to create secure database health checker: {}", e.getMessage(), e);
            throw new IllegalStateException("Database health checker configuration failed", e);
        }
    }
    
    /**
     * Resolves the DataSource bean with comprehensive error handling.
     */
    private DataSource resolveDataSourceBean(BeanFactory beanFactory, 
                                           ValidatedHealthMonitoringProperties.DatabaseConfig config) {
        try {
            if (config.getDataSourceBeanName() != null && !config.getDataSourceBeanName().trim().isEmpty()) {
                String beanName = config.getDataSourceBeanName().trim();
                logger.debug("Resolving DataSource by bean name: {}", beanName);
                
                // Security: Validate bean name format
                if (!beanName.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
                    throw new IllegalArgumentException("Invalid DataSource bean name format: " + beanName);
                }
                
                return beanFactory.getBean(beanName, DataSource.class);
            } else {
                logger.debug("Resolving DataSource by type");
                return beanFactory.getBean(DataSource.class);
            }
        } catch (org.springframework.beans.factory.NoSuchBeanDefinitionException e) {
            logger.error("DataSource bean not found: {}", e.getMessage());
            throw new IllegalStateException("Required DataSource bean is not available", e);
        } catch (Exception e) {
            logger.error("Failed to resolve DataSource bean: {}", e.getMessage());
            throw new IllegalStateException("DataSource resolution failed", e);
        }
    }
    
    /**
     * Determines an appropriate component name with security considerations.
     */
    private String determineComponentName(DataSource dataSource, 
                                        ValidatedHealthMonitoringProperties.DatabaseConfig config) {
        try {
            // Use configured bean name if available
            if (config.getDataSourceBeanName() != null && !config.getDataSourceBeanName().trim().isEmpty()) {
                return sanitizeComponentName(config.getDataSourceBeanName().trim());
            }
            
            // Extract meaningful name from DataSource class
            String className = dataSource.getClass().getSimpleName();
            String componentName = extractComponentNameFromClass(className);
            
            return sanitizeComponentName(componentName);
            
        } catch (Exception e) {
            logger.debug("Could not determine component name, using default: {}", e.getMessage());
            return "database";
        }
    }
    
    /**
     * Extract component name from DataSource class name.
     */
    private String extractComponentNameFromClass(String className) {
        if (className == null || className.trim().isEmpty()) {
            return "database";
        }
        
        String lowerClassName = className.toLowerCase();
        
        if (lowerClassName.contains("hikari")) {
            return "hikaricp-database";
        } else if (lowerClassName.contains("tomcat")) {
            return "tomcat-database";
        } else if (lowerClassName.contains("h2")) {
            return "h2-database";
        } else if (lowerClassName.contains("postgres")) {
            return "postgresql-database";
        } else if (lowerClassName.contains("mysql")) {
            return "mysql-database";
        } else if (lowerClassName.contains("oracle")) {
            return "oracle-database";
        } else if (lowerClassName.contains("sqlserver")) {
            return "sqlserver-database";
        } else {
            return "primary-database";
        }
    }
    
    /**
     * Security: Sanitize component name to prevent injection attacks.
     */
    private String sanitizeComponentName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "database";
        }
        
        // Remove non-alphanumeric characters except hyphens and underscores
        String sanitized = name.replaceAll("[^a-zA-Z0-9_-]", "-");
        
        // Ensure it starts with a letter
        if (!sanitized.matches("^[a-zA-Z].*")) {
            sanitized = "db-" + sanitized;
        }
        
        // Limit length
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 47) + "...";
        }
        
        return sanitized;
    }
}