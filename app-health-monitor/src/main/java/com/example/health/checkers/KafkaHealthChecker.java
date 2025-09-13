package com.example.health.checkers;

import com.example.health.config.HealthMonitoringProperties;
import com.example.health.core.AbstractHealthChecker;
import com.example.health.domain.HealthCheckResult;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.Node;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise-grade Kafka health checker.
 * 
 * This implementation provides comprehensive Kafka cluster health checking with:
 * - Cluster connectivity verification
 * - Broker availability checking
 * - Controller status monitoring
 * - Cluster metadata collection
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
public class KafkaHealthChecker extends AbstractHealthChecker {
    
    private static final String COMPONENT_TYPE = "kafka";
    
    private final AdminClient adminClient;
    
    public KafkaHealthChecker(String componentName, AdminClient adminClient, HealthMonitoringProperties.KafkaConfig config) {
        super(componentName, COMPONENT_TYPE, determineTimeout(config));
        this.adminClient = Objects.requireNonNull(adminClient, "AdminClient cannot be null");
    }
    
    private static Duration determineTimeout(HealthMonitoringProperties.KafkaConfig config) {
        return config.getTimeout() != null ? config.getTimeout() : Duration.ofSeconds(5);
    }
    
    @Override
    protected HealthCheckResult doHealthCheck() throws Exception {
        Instant start = Instant.now();
        
        try {
            // Get cluster description with timeout
            DescribeClusterResult clusterResult = adminClient.describeCluster();
            
            // Wait for results with timeout
            long timeoutMs = getTimeout().toMillis();
            Collection<Node> nodes = clusterResult.nodes().get(timeoutMs, TimeUnit.MILLISECONDS);
            Node controller = clusterResult.controller().get(timeoutMs, TimeUnit.MILLISECONDS);
            String clusterId = clusterResult.clusterId().get(timeoutMs, TimeUnit.MILLISECONDS);
            
            Duration elapsed = Duration.between(start, Instant.now());
            
            return HealthCheckResult.success(getComponentName(), COMPONENT_TYPE, elapsed)
                    .withDetail("clusterId", clusterId)
                    .withDetail("nodeCount", nodes.size())
                    .withDetail("controllerNodeId", controller.id())
                    .withDetail("controllerHost", controller.host())
                    .withDetail("controllerPort", controller.port())
                    .withDetail("brokerNodes", formatBrokerNodes(nodes))
                    .build();
            
        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            logger.error("Kafka health check failed for component '{}': {}", getComponentName(), e.getMessage());
            
            return HealthCheckResult.failure(getComponentName(), COMPONENT_TYPE, elapsed, e)
                    .withDetail("errorType", e.getClass().getSimpleName())
                    .withDetail("isTimeout", isTimeoutRelated(e))
                    .build();
        }
    }
    
    private String formatBrokerNodes(Collection<Node> nodes) {
        return nodes.stream()
                .map(node -> String.format("%d@%s:%d", node.id(), node.host(), node.port()))
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("none");
    }
    
    private boolean isTimeoutRelated(Exception e) {
        return e instanceof java.util.concurrent.TimeoutException ||
               e.getCause() instanceof java.util.concurrent.TimeoutException ||
               e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout");
    }
}