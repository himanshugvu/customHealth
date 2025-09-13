package com.example.health.checkers;

import com.example.health.config.HealthMonitoringProperties;
import com.example.health.core.AbstractHealthChecker;
import com.example.health.domain.HealthCheckResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Secure enterprise-grade database health checker.
 * 
 * This implementation provides robust database connectivity checking with:
 * - SQL injection prevention
 * - Resource leak protection  
 * - Connection pool awareness
 * - Configurable validation queries with whitelist validation
 * - Detailed connection metadata
 * - Proper resource management with try-with-resources
 * - Security hardening against malicious queries
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
public class SecureDatabaseHealthChecker extends AbstractHealthChecker {
    
    private static final String COMPONENT_TYPE = "database";
    
    // Security: Whitelist of allowed validation query patterns (prevents SQL injection)
    private static final Pattern SAFE_VALIDATION_QUERY_PATTERN = Pattern.compile(
        "^\\s*(SELECT\\s+1|SELECT\\s+CURRENT_TIMESTAMP|SELECT\\s+VERSION\\(\\)|VALUES\\s*\\(\\s*1\\s*\\))\\s*;?\\s*$",
        Pattern.CASE_INSENSITIVE
    );
    
    // Security: Default safe validation queries by database type
    private static final String DEFAULT_VALIDATION_QUERY = "SELECT 1";
    
    private final DataSource dataSource;
    private final String validationQuery;
    
    public SecureDatabaseHealthChecker(String componentName, DataSource dataSource, 
                                     HealthMonitoringProperties.DatabaseConfig config) {
        super(componentName, COMPONENT_TYPE, determineTimeout(config));
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource cannot be null");
        this.validationQuery = sanitizeValidationQuery(config.getValidationQuery());
    }
    
    private static Duration determineTimeout(HealthMonitoringProperties.DatabaseConfig config) {
        Duration timeout = config.getTimeout();
        if (timeout == null) {
            return Duration.ofSeconds(3);
        }
        
        // Security: Enforce maximum timeout to prevent DoS
        Duration maxTimeout = Duration.ofSeconds(30);
        return timeout.compareTo(maxTimeout) > 0 ? maxTimeout : timeout;
    }
    
    /**
     * Security: Sanitize and validate the validation query to prevent SQL injection.
     */
    private static String sanitizeValidationQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return DEFAULT_VALIDATION_QUERY;
        }
        
        String trimmedQuery = query.trim();
        
        // Security check: Only allow safe validation queries
        if (!SAFE_VALIDATION_QUERY_PATTERN.matcher(trimmedQuery).matches()) {
            throw new IllegalArgumentException(
                "Validation query is not allowed for security reasons. " +
                "Only simple SELECT statements like 'SELECT 1' are permitted: " + query);
        }
        
        return trimmedQuery;
    }
    
    @Override
    protected HealthCheckResult doHealthCheck() throws Exception {
        Instant start = Instant.now();
        
        // Use try-with-resources to ensure proper resource cleanup
        try (Connection connection = acquireConnection()) {
            
            // Verify connection is valid with short timeout
            if (!connection.isValid(1)) {
                throw new SQLException("Connection validation failed");
            }
            
            // Execute validation query if provided
            executeValidationQuery(connection);
            
            Duration elapsed = Duration.between(start, Instant.now());
            
            return buildSuccessResult(connection, elapsed);
            
        } catch (SQLException e) {
            Duration elapsed = Duration.between(start, Instant.now());
            logger.error("Database health check failed for component '{}': {}", 
                        getComponentName(), e.getMessage());
            
            return HealthCheckResult.failure(getComponentName(), COMPONENT_TYPE, elapsed, e)
                    .withDetail("validationQuery", validationQuery)
                    .withDetail("errorCode", e.getErrorCode())
                    .withDetail("sqlState", e.getSQLState())
                    .withDetail("errorType", e.getClass().getSimpleName())
                    .build();
        }
    }
    
    /**
     * Security: Acquire connection with proper error handling.
     */
    private Connection acquireConnection() throws SQLException {
        try {
            Connection connection = dataSource.getConnection();
            
            // Security: Set connection to read-only for health checks
            if (!connection.isReadOnly()) {
                connection.setReadOnly(true);
            }
            
            // Security: Disable auto-commit for health checks
            if (connection.getAutoCommit()) {
                connection.setAutoCommit(false);
            }
            
            return connection;
            
        } catch (SQLException e) {
            logger.debug("Failed to acquire database connection: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * Security: Execute validation query using PreparedStatement to prevent SQL injection.
     */
    private void executeValidationQuery(Connection connection) throws SQLException {
        if (validationQuery == null || validationQuery.trim().isEmpty()) {
            return;
        }
        
        // Use PreparedStatement for additional security
        try (PreparedStatement statement = connection.prepareStatement(validationQuery)) {
            // Security: Set query timeout to prevent long-running queries
            statement.setQueryTimeout(2);
            
            // Execute query but don't process results for performance
            statement.execute();
            
        } catch (SQLException e) {
            logger.debug("Validation query execution failed: {}", e.getMessage());
            throw new SQLException("Validation query failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Build success result with safe metadata extraction.
     */
    private HealthCheckResult buildSuccessResult(Connection connection, Duration elapsed) {
        return HealthCheckResult.success(getComponentName(), COMPONENT_TYPE, elapsed)
                .withDetail("validationQuery", validationQuery)
                .withDetail("databaseProduct", getSafeDatabaseProductName(connection))
                .withDetail("databaseVersion", getSafeDatabaseVersion(connection))
                .withDetail("catalogName", getSafeCatalogName(connection))
                .withDetail("connectionReadOnly", getSafeReadOnlyStatus(connection))
                .withDetail("validationMethod", "PreparedStatement")
                .build();
    }
    
    /**
     * Security: Safe database product name extraction with error handling.
     */
    private String getSafeDatabaseProductName(Connection connection) {
        try {
            String productName = connection.getMetaData().getDatabaseProductName();
            return productName != null ? productName : "unknown";
        } catch (SQLException e) {
            logger.debug("Could not retrieve database product name: {}", e.getMessage());
            return "unknown";
        }
    }
    
    /**
     * Security: Safe database version extraction with error handling.
     */
    private String getSafeDatabaseVersion(Connection connection) {
        try {
            String version = connection.getMetaData().getDatabaseProductVersion();
            // Security: Sanitize version string to prevent log injection
            return version != null ? sanitizeForLogging(version) : "unknown";
        } catch (SQLException e) {
            logger.debug("Could not retrieve database version: {}", e.getMessage());
            return "unknown";
        }
    }
    
    /**
     * Security: Safe catalog name extraction with error handling.
     */
    private String getSafeCatalogName(Connection connection) {
        try {
            String catalog = connection.getCatalog();
            return catalog != null ? sanitizeForLogging(catalog) : "default";
        } catch (SQLException e) {
            logger.debug("Could not retrieve catalog name: {}", e.getMessage());
            return "unknown";
        }
    }
    
    /**
     * Security: Safe read-only status extraction with error handling.
     */
    private boolean getSafeReadOnlyStatus(Connection connection) {
        try {
            return connection.isReadOnly();
        } catch (SQLException e) {
            logger.debug("Could not retrieve read-only status: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Security: Sanitize strings for logging to prevent log injection attacks.
     */
    private static String sanitizeForLogging(String input) {
        if (input == null) {
            return "null";
        }
        
        // Remove control characters and limit length
        String sanitized = input.replaceAll("[\\r\\n\\t\\x00-\\x1F\\x7F]", "_");
        
        // Limit length to prevent log pollution
        int maxLength = 100;
        if (sanitized.length() > maxLength) {
            return sanitized.substring(0, maxLength - 3) + "...";
        }
        
        return sanitized;
    }
}