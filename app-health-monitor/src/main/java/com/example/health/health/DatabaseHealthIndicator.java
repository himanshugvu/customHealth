package com.example.health.health;

import com.example.health.config.AppHealthProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;

/**
 * Database health indicator that works with any DataSource bean.
 * Uses Spring Boot's conditional pattern - only created when DataSource is available.
 */
public final class DatabaseHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseHealthIndicator.class);
    
    private final DataSource dataSource;
    private final String validationQuery;

    public DatabaseHealthIndicator(DataSource dataSource, AppHealthProperties properties) {
        this.dataSource = dataSource;
        this.validationQuery = properties.getDb().getValidationQuery();
    }

    @Override
    public Health health() {
        long started = System.nanoTime();
        
        try (Connection connection = dataSource.getConnection()) {
            if (validationQuery != null && !validationQuery.trim().isEmpty()) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(validationQuery);
                }
            } else {
                // Use connection.isValid() as fallback
                connection.isValid(3);
            }
            
            long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
            
            logger.info("component=database status=UP latency_ms={} validation_query=\"{}\"", 
                       ms, validationQuery != null ? validationQuery : "isValid()");
            
            return Health.up()
                    .withDetail("type", "database")
                    .withDetail("validationQuery", validationQuery != null ? validationQuery : "isValid()")
                    .withDetail("latencyMs", ms)
                    .withDetail("component", "database")
                    .build();
                    
        } catch (Exception e) {
            long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
            
            logger.error("component=database status=DOWN latency_ms={} error_type=\"{}\" error_message=\"{}\"", 
                        ms, e.getClass().getSimpleName(), e.getMessage());
            
            return Health.down()
                    .withDetail("type", "database")
                    .withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .withDetail("latencyMs", ms)
                    .withDetail("component", "database")
                    .build();
        }
    }
}