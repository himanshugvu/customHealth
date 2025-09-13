package com.example.health.health;

import com.example.health.config.AppHealthProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * MongoDB health indicator that works with MongoTemplate bean.
 * Uses Spring Boot's conditional pattern - only created when MongoTemplate is available.
 */
public final class MongoHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(MongoHealthIndicator.class);
    
    private final MongoTemplate mongoTemplate;

    public MongoHealthIndicator(MongoTemplate mongoTemplate, AppHealthProperties properties) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Health health() {
        long started = System.nanoTime();
        
        try {
            // Perform a simple ping operation
            var db = mongoTemplate.getDb();
            var result = db.runCommand(new org.bson.Document("ping", 1));
            
            long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
            
            logger.info("component=mongodb status=UP latency_ms={} database=\"{}\"", 
                       ms, db.getName());
            
            return Health.up()
                    .withDetail("type", "mongodb")
                    .withDetail("database", db.getName())
                    .withDetail("latencyMs", ms)
                    .withDetail("component", "mongodb")
                    .build();
                    
        } catch (Exception e) {
            long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
            
            logger.error("component=mongodb status=DOWN latency_ms={} error_type=\"{}\" error_message=\"{}\"", 
                        ms, e.getClass().getSimpleName(), e.getMessage());
            
            return Health.down()
                    .withDetail("type", "mongodb")
                    .withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .withDetail("latencyMs", ms)
                    .withDetail("component", "mongodb")
                    .build();
        }
    }
}