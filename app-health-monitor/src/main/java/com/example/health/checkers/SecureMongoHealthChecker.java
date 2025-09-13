package com.example.health.checkers;

import com.example.health.config.ValidatedHealthMonitoringProperties;
import com.example.health.core.RobustAbstractHealthChecker;
import com.example.health.domain.HealthCheckResult;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Secure enterprise-grade MongoDB health checker.
 * 
 * This implementation provides comprehensive MongoDB connectivity checking with:
 * - Connection status verification with timeouts
 * - Database metadata collection with error handling
 * - Replica set awareness
 * - Performance metrics collection
 * - Security hardening and input validation
 * - Proper exception handling and resource management
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
public class SecureMongoHealthChecker extends RobustAbstractHealthChecker {
    
    private static final String COMPONENT_TYPE = "mongodb";
    
    // MongoDB commands - using immutable documents for thread safety
    private static final Document PING_COMMAND = new Document("ping", 1);
    private static final Document SERVER_STATUS_COMMAND = new Document("serverStatus", 1);
    private static final Document IS_MASTER_COMMAND = new Document("isMaster", 1);
    
    // Security: Maximum values to prevent DoS
    private static final int MAX_CONNECTION_INFO_VALUE = 100000;
    private static final long MAX_UPTIME_SECONDS = TimeUnit.DAYS.toSeconds(3650); // 10 years max
    
    private final MongoTemplate mongoTemplate;
    
    public SecureMongoHealthChecker(String componentName, MongoTemplate mongoTemplate, 
                                  ValidatedHealthMonitoringProperties.MongoConfig config) {
        super(componentName, COMPONENT_TYPE, determineTimeout(config));
        this.mongoTemplate = Objects.requireNonNull(mongoTemplate, "MongoTemplate cannot be null");
    }
    
    private static Duration determineTimeout(ValidatedHealthMonitoringProperties.MongoConfig config) {
        Duration timeout = config.getTimeout();
        if (timeout == null) {
            return Duration.ofSeconds(3);
        }
        
        // Security: Enforce maximum timeout to prevent DoS
        Duration maxTimeout = Duration.ofSeconds(30);
        return timeout.compareTo(maxTimeout) > 0 ? maxTimeout : timeout;
    }
    
    @Override
    protected HealthCheckResult doHealthCheck() throws Exception {
        Instant start = Instant.now();
        
        try {
            var database = mongoTemplate.getDb();
            
            // Primary health check: ping the database
            Document pingResult = executePingCommand(database);
            if (!isCommandSuccessful(pingResult)) {
                throw new RuntimeException("MongoDB ping command failed");
            }
            
            Duration elapsed = Duration.between(start, Instant.now());
            
            // Build result with safe metadata collection
            var resultBuilder = HealthCheckResult.success(getComponentName(), COMPONENT_TYPE, elapsed)
                    .withDetail("databaseName", sanitizeForLogging(database.getName()))
                    .withDetail("pingResult", "ok")
                    .withDetail("healthCheckMethod", "ping");
            
            // Collect additional metadata safely
            collectServerMetadata(database, resultBuilder);
            
            return resultBuilder.build();
            
        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            logger.error("MongoDB health check failed for component '{}': {}", 
                        getComponentName(), e.getMessage());
            
            return HealthCheckResult.failure(getComponentName(), COMPONENT_TYPE, elapsed, e)
                    .withDetail("errorType", e.getClass().getSimpleName())
                    .withDetail("healthCheckMethod", "ping")
                    .build();
        }
    }
    
    /**
     * Execute ping command with proper error handling.
     */
    private Document executePingCommand(com.mongodb.client.MongoDatabase database) {
        try {
            return database.runCommand(PING_COMMAND);
        } catch (Exception e) {
            logger.debug("MongoDB ping command failed: {}", e.getMessage());
            throw new RuntimeException("MongoDB ping failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if MongoDB command was successful.
     */
    private boolean isCommandSuccessful(Document result) {
        if (result == null) {
            return false;
        }
        
        Object ok = result.get("ok");
        if (ok instanceof Number number) {
            return Double.compare(number.doubleValue(), 1.0) == 0;
        }
        return false;
    }
    
    /**
     * Safely collect server metadata with comprehensive error handling.
     */
    private void collectServerMetadata(com.mongodb.client.MongoDatabase database, 
                                     HealthCheckResult.Builder builder) {
        // Collect server status (non-critical)
        try {
            Document serverStatus = database.runCommand(SERVER_STATUS_COMMAND);
            if (serverStatus != null) {
                addServerStatusDetails(builder, serverStatus);
            }
        } catch (Exception e) {
            logger.debug("Could not retrieve server status: {}", e.getMessage());
            builder.withDetail("serverStatusAvailable", false);
        }
        
        // Collect replica set information (non-critical)
        try {
            Document isMasterResult = database.runCommand(IS_MASTER_COMMAND);
            if (isMasterResult != null) {
                addReplicaSetDetails(builder, isMasterResult);
            }
        } catch (Exception e) {
            logger.debug("Could not retrieve replica set information: {}", e.getMessage());
            builder.withDetail("replicaSetInfoAvailable", false);
        }
    }
    
    /**
     * Add server status details with validation and sanitization.
     */
    private void addServerStatusDetails(HealthCheckResult.Builder builder, Document serverStatus) {
        try {
            // Add version information
            Object version = serverStatus.get("version");
            if (version != null) {
                builder.withDetail("mongoVersion", sanitizeForLogging(version.toString()));
            }
            
            // Add uptime information with validation
            Object uptime = serverStatus.get("uptime");
            if (uptime instanceof Number number) {
                long uptimeSeconds = number.longValue();
                // Security: Validate uptime is reasonable
                if (uptimeSeconds >= 0 && uptimeSeconds <= MAX_UPTIME_SECONDS) {
                    builder.withDetail("serverUptimeSeconds", uptimeSeconds);
                }
            }
            
            // Add connection information with validation
            Document connections = serverStatus.get("connections", Document.class);
            if (connections != null) {
                addConnectionDetails(builder, connections);
            }
            
            // Add host information
            Object host = serverStatus.get("host");
            if (host != null) {
                builder.withDetail("hostInfo", sanitizeForLogging(host.toString()));
            }
            
        } catch (Exception e) {
            logger.debug("Could not extract server status details: {}", e.getMessage());
        }
    }
    
    /**
     * Add connection details with validation.
     */
    private void addConnectionDetails(HealthCheckResult.Builder builder, Document connections) {
        Object current = connections.get("current");
        Object available = connections.get("available");
        Object totalCreated = connections.get("totalCreated");
        
        // Validate and add current connections
        if (current instanceof Number currentNum) {
            int currentConnections = currentNum.intValue();
            if (currentConnections >= 0 && currentConnections <= MAX_CONNECTION_INFO_VALUE) {
                builder.withDetail("currentConnections", currentConnections);
            }
        }
        
        // Validate and add available connections
        if (available instanceof Number availableNum) {
            int availableConnections = availableNum.intValue();
            if (availableConnections >= 0 && availableConnections <= MAX_CONNECTION_INFO_VALUE) {
                builder.withDetail("availableConnections", availableConnections);
            }
        }
        
        // Validate and add total created connections
        if (totalCreated instanceof Number totalNum) {
            long totalConnections = totalNum.longValue();
            if (totalConnections >= 0) {
                builder.withDetail("totalCreatedConnections", totalConnections);
            }
        }
    }
    
    /**
     * Add replica set details with validation.
     */
    private void addReplicaSetDetails(HealthCheckResult.Builder builder, Document isMasterResult) {
        try {
            // Add replica set name
            Object setName = isMasterResult.get("setName");
            if (setName != null) {
                builder.withDetail("replicaSetName", sanitizeForLogging(setName.toString()));
            }
            
            // Add master/primary status
            Object isMaster = isMasterResult.get("ismaster");
            if (isMaster instanceof Boolean master) {
                builder.withDetail("isPrimary", master);
            }
            
            // Add secondary status
            Object isSecondary = isMasterResult.get("secondary");
            if (isSecondary instanceof Boolean secondary) {
                builder.withDetail("isSecondary", secondary);
            }
            
            // Add read-only status
            Object readOnly = isMasterResult.get("readOnly");
            if (readOnly instanceof Boolean ro) {
                builder.withDetail("isReadOnly", ro);
            }
            
            // Add election ID (for primary nodes)
            Object electionId = isMasterResult.get("electionId");
            if (electionId != null) {
                builder.withDetail("hasElectionId", true);
            }
            
        } catch (Exception e) {
            logger.debug("Could not extract replica set details: {}", e.getMessage());
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