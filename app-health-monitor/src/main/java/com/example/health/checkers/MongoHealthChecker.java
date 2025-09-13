package com.example.health.checkers;

import com.example.health.config.HealthMonitoringProperties;
import com.example.health.core.AbstractHealthChecker;
import com.example.health.domain.HealthCheckResult;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Enterprise-grade MongoDB health checker.
 * 
 * This implementation provides comprehensive MongoDB connectivity checking with:
 * - Connection status verification
 * - Database metadata collection
 * - Replica set awareness
 * - Performance metrics collection
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
public class MongoHealthChecker extends AbstractHealthChecker {
    
    private static final String COMPONENT_TYPE = "mongodb";
    private static final Document PING_COMMAND = new Document("ping", 1);
    private static final Document SERVER_STATUS_COMMAND = new Document("serverStatus", 1);
    
    private final MongoTemplate mongoTemplate;
    
    public MongoHealthChecker(String componentName, MongoTemplate mongoTemplate, HealthMonitoringProperties.MongoConfig config) {
        super(componentName, COMPONENT_TYPE, determineTimeout(config));
        this.mongoTemplate = Objects.requireNonNull(mongoTemplate, "MongoTemplate cannot be null");
    }
    
    private static Duration determineTimeout(HealthMonitoringProperties.MongoConfig config) {
        return config.getTimeout() != null ? config.getTimeout() : Duration.ofSeconds(3);
    }
    
    @Override
    protected HealthCheckResult doHealthCheck() throws Exception {
        Instant start = Instant.now();
        
        try {
            var database = mongoTemplate.getDb();
            
            // Perform ping to verify connectivity
            Document pingResult = database.runCommand(PING_COMMAND);
            if (!isCommandSuccessful(pingResult)) {
                throw new RuntimeException("MongoDB ping command failed");
            }
            
            // Collect server information
            Document serverStatus = null;
            try {
                serverStatus = database.runCommand(SERVER_STATUS_COMMAND);
            } catch (Exception e) {
                logger.debug("Could not retrieve server status: {}", e.getMessage());
            }
            
            Duration elapsed = Duration.between(start, Instant.now());
            
            var resultBuilder = HealthCheckResult.success(getComponentName(), COMPONENT_TYPE, elapsed)
                    .withDetail("databaseName", database.getName())
                    .withDetail("pingResult", "ok");
            
            if (serverStatus != null) {
                addServerStatusDetails(resultBuilder, serverStatus);
            }
            
            return resultBuilder.build();
            
        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            logger.error("MongoDB health check failed for component '{}': {}", getComponentName(), e.getMessage());
            
            return HealthCheckResult.failure(getComponentName(), COMPONENT_TYPE, elapsed, e)
                    .withDetail("errorType", e.getClass().getSimpleName())
                    .build();
        }
    }
    
    private boolean isCommandSuccessful(Document result) {
        Object ok = result.get("ok");
        if (ok instanceof Number number) {
            return number.doubleValue() == 1.0;
        }
        return false;
    }
    
    private void addServerStatusDetails(HealthCheckResult.Builder builder, Document serverStatus) {
        try {
            // Add version information
            Object version = serverStatus.get("version");
            if (version != null) {
                builder.withDetail("mongoVersion", version.toString());
            }
            
            // Add uptime information
            Object uptime = serverStatus.get("uptime");
            if (uptime instanceof Number number) {
                builder.withDetail("serverUptimeSeconds", number.longValue());
            }
            
            // Add connection information
            Document connections = serverStatus.get("connections", Document.class);
            if (connections != null) {
                Object current = connections.get("current");
                Object available = connections.get("available");
                if (current instanceof Number currentNum && available instanceof Number availableNum) {
                    builder.withDetail("currentConnections", currentNum.intValue());
                    builder.withDetail("availableConnections", availableNum.intValue());
                }
            }
            
            // Add replica set information if available
            Document repl = serverStatus.get("repl", Document.class);
            if (repl != null) {
                Object setName = repl.get("setName");
                Object isMaster = repl.get("ismaster");
                Object isSecondary = repl.get("secondary");
                
                if (setName != null) {
                    builder.withDetail("replicaSetName", setName.toString());
                }
                if (isMaster instanceof Boolean master) {
                    builder.withDetail("isPrimary", master);
                }
                if (isSecondary instanceof Boolean secondary) {
                    builder.withDetail("isSecondary", secondary);
                }
            }
            
        } catch (Exception e) {
            logger.debug("Could not extract all server status details: {}", e.getMessage());
        }
    }
}