package com.example.health.health;

import com.example.health.config.AppHealthProperties;
import com.example.health.core.AsyncHealthIndicator;
import org.springframework.boot.actuate.health.Health;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import java.time.Duration;

public final class DatabaseHealthIndicator extends AsyncHealthIndicator {

    private final DataSource dataSource;
    private final AppHealthProperties properties;

    public DatabaseHealthIndicator(DataSource dataSource, AppHealthProperties properties) {
        super(Duration.ofSeconds(3), "database"); // 3 second timeout for database check
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Override
    protected Health doHealthCheck() {
        long started = System.nanoTime();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            // Set query timeout to prevent hanging
            statement.setQueryTimeout(2); // 2 seconds for SQL query
            
            DatabaseMetaData metaData = connection.getMetaData();
            String dbType = metaData.getDatabaseProductName().toLowerCase();
            String dbVersion = metaData.getDatabaseProductVersion();
            
            statement.execute(properties.getDb().getValidationQuery());
            long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
            
            return Health.up()
                    .withDetail("type", dbType)
                    .withDetail("version", dbVersion)
                    .withDetail("validationQuery", properties.getDb().getValidationQuery())
                    .withDetail("latencyMs", ms)
                    .withDetail("component", "database")
                    .build();
        } catch (Exception e) {
            long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
            return Health.down()
                    .withDetail("type", "unknown")
                    .withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .withDetail("latencyMs", ms)
                    .withDetail("component", "database")
                    .build();
        }
    }
}