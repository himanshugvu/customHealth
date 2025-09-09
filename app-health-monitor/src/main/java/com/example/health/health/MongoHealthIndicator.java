package com.example.health.health;

import com.example.health.config.AppHealthProperties;
import com.example.health.core.AsyncHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Duration;

public final class MongoHealthIndicator extends AsyncHealthIndicator {

    private final MongoTemplate mongoTemplate;
    private final AppHealthProperties properties;

    public MongoHealthIndicator(MongoTemplate mongoTemplate, AppHealthProperties properties) {
        super(Duration.ofSeconds(3), "mongodb"); // 3 second timeout for MongoDB check
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
    }

    @Override
    protected Health doHealthCheck() {
        long started = System.nanoTime();
        try {
            var result = mongoTemplate.getDb().runCommand(org.bson.Document.parse("{ping: 1}"));
            long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
            
            return Health.up()
                    .withDetail("type", "mongodb")
                    .withDetail("database", mongoTemplate.getDb().getName())
                    .withDetail("operation", "ping")
                    .withDetail("latencyMs", ms)
                    .withDetail("component", "database")
                    .withDetail("pingResult", result.get("ok"))
                    .build();
        } catch (Exception e) {
            long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
            return Health.down()
                    .withDetail("type", "mongodb")
                    .withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .withDetail("latencyMs", ms)
                    .withDetail("component", "database")
                    .build();
        }
    }
}