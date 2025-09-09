package com.example.health.health;

import com.example.health.config.AppHealthProperties;
import com.example.health.core.AsyncHealthIndicator;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.actuate.health.Health;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class KafkaHealthIndicator extends AsyncHealthIndicator {

    private final AdminClient adminClient;

    public KafkaHealthIndicator(BeanFactory beanFactory, AppHealthProperties properties) {
        super(Duration.ofSeconds(3), "kafka"); // 3 second timeout for Kafka check
        this.adminClient = beanFactory.getBean(properties.getKafka().getAdminClientBean(), AdminClient.class);
    }

    @Override
    protected Health doHealthCheck() {
        long started = System.nanoTime();
        try {
            // Use a shorter timeout for the Kafka call itself
            var nodes = adminClient.describeCluster().nodes().get(2, TimeUnit.SECONDS);
            long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
            
            return Health.up()
                    .withDetail("type", "kafka")
                    .withDetail("nodeCount", nodes.size())
                    .withDetail("latencyMs", ms)
                    .withDetail("component", "kafka")
                    .withDetail("operation", "describeCluster")
                    .build();
        } catch (Exception e) {
            long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
            return Health.down()
                    .withDetail("type", "kafka")
                    .withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .withDetail("latencyMs", ms)
                    .withDetail("component", "kafka")
                    .withDetail("operation", "describeCluster")
                    .build();
        }
    }
}