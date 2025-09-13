package com.example.health.checkers;

import com.example.health.config.HealthMonitoringProperties;
import com.example.health.core.AbstractHealthChecker;
import com.example.health.domain.HealthCheckResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Enterprise-grade database health checker.
 * 
 * This implementation provides robust database connectivity checking with:
 * - Connection pool awareness
 * - Configurable validation queries
 * - Detailed connection metadata
 * - Proper resource management
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
public class DatabaseHealthChecker extends AbstractHealthChecker {
    
    private static final String COMPONENT_TYPE = "database";
    
    private final DataSource dataSource;
    private final String validationQuery;
    
    public DatabaseHealthChecker(String componentName, DataSource dataSource, HealthMonitoringProperties.DatabaseConfig config) {
        super(componentName, COMPONENT_TYPE, determineTimeout(config));
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource cannot be null");
        this.validationQuery = Objects.requireNonNull(config.getValidationQuery(), "Validation query cannot be null");
    }
    
    private static Duration determineTimeout(HealthMonitoringProperties.DatabaseConfig config) {
        return config.getTimeout() != null ? config.getTimeout() : Duration.ofSeconds(3);
    }
    
    @Override
    protected HealthCheckResult doHealthCheck() throws Exception {
        Instant start = Instant.now();
        
        try (Connection connection = dataSource.getConnection()) {
            // Verify connection is valid
            if (!connection.isValid(1)) {
                throw new SQLException("Connection validation failed");
            }
            
            // Execute validation query if provided
            if (validationQuery != null && !validationQuery.trim().isEmpty()) {
                executeValidationQuery(connection);
            }
            
            Duration elapsed = Duration.between(start, Instant.now());
            
            return HealthCheckResult.success(getComponentName(), COMPONENT_TYPE, elapsed)
                    .withDetail("validationQuery", validationQuery)
                    .withDetail("databaseProduct", getDatabaseProductName(connection))
                    .withDetail("databaseVersion", getDatabaseVersion(connection))
                    .withDetail("catalogName", getCatalogName(connection))
                    .withDetail("autoCommit", connection.getAutoCommit())
                    .withDetail("readOnly", connection.isReadOnly())
                    .build();
            
        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            logger.error("Database health check failed for component '{}': {}", getComponentName(), e.getMessage());
            
            return HealthCheckResult.failure(getComponentName(), COMPONENT_TYPE, elapsed, e)
                    .withDetail("validationQuery", validationQuery)
                    .withDetail("errorType", e.getClass().getSimpleName())
                    .build();
        }
    }
    
    private void executeValidationQuery(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(2); // 2 second timeout for validation query
            statement.execute(validationQuery);
        }
    }
    
    private String getDatabaseProductName(Connection connection) {
        try {
            return connection.getMetaData().getDatabaseProductName();
        } catch (SQLException e) {
            logger.debug("Could not retrieve database product name: {}", e.getMessage());
            return "unknown";
        }
    }
    
    private String getDatabaseVersion(Connection connection) {
        try {
            return connection.getMetaData().getDatabaseProductVersion();
        } catch (SQLException e) {
            logger.debug("Could not retrieve database version: {}", e.getMessage());
            return "unknown";
        }
    }
    
    private String getCatalogName(Connection connection) {
        try {
            String catalog = connection.getCatalog();
            return catalog != null ? catalog : "default";
        } catch (SQLException e) {
            logger.debug("Could not retrieve catalog name: {}", e.getMessage());
            return "unknown";
        }
    }
}