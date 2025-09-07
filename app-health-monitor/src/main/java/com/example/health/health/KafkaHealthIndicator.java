package com.example.health.health;

import com.example.health.config.AppHealthProperties;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.time.Duration;

public final class KafkaHealthIndicator implements HealthIndicator {

    private final AdminClient adminClient;

    public KafkaHealthIndicator(BeanFactory beanFactory, AppHealthProperties properties) {
        this.adminClient = beanFactory.getBean(properties.getKafka().getAdminClientBean(), AdminClient.class);
    }

    @Override
    public Health health() {
        long started = System.nanoTime();
        try {
            var nodes = adminClient.describeCluster().nodes().get();
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