package com.example.health.health;

import com.example.health.config.AppHealthProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.beans.factory.BeanFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.kafka.clients.admin.AdminClient;
import java.time.Duration;

/**
 * Kafka health indicator that works with AdminClient bean.
 * Uses Spring Boot's conditional pattern - only created when AdminClient is available.
 */
public final class KafkaHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(KafkaHealthIndicator.class);
    
    private final AdminClient adminClient;

    public KafkaHealthIndicator(BeanFactory beanFactory, AppHealthProperties properties) {
        String beanName = properties.getKafka().getAdminClientBean();
        this.adminClient = beanFactory.getBean(beanName, AdminClient.class);
    }

    @Override
    public Health health() {
        long started = System.nanoTime();
        
        try {
            var nodes = adminClient.describeCluster().nodes().get();
            
            long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
            
            logger.info("component=kafka status=UP latency_ms={} node_count={}", 
                       ms, nodes.size());
            
            return Health.up()
                    .withDetail("type", "kafka")
                    .withDetail("nodeCount", nodes.size())
                    .withDetail("latencyMs", ms)
                    .withDetail("component", "kafka")
                    .build();
                    
        } catch (Exception e) {
            long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
            
            logger.error("component=kafka status=DOWN latency_ms={} error_type=\"{}\" error_message=\"{}\"", 
                        ms, e.getClass().getSimpleName(), e.getMessage());
            
            return Health.down()
                    .withDetail("type", "kafka")
                    .withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .withDetail("latencyMs", ms)
                    .withDetail("component", "kafka")
                    .build();
        }
    }
}