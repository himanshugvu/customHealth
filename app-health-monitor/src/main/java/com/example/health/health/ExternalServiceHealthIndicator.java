package com.example.health.health;

import com.example.health.core.AsyncHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;

public final class ExternalServiceHealthIndicator extends AsyncHealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(ExternalServiceHealthIndicator.class);
    
    private final String name;
    private final RestClient restClient;
    private final URI uri;
    private final String servicePath;

    public ExternalServiceHealthIndicator(String name, RestClient restClient, URI uri) {
        super(Duration.ofSeconds(5), "external"); // 5 second timeout for external service check
        this.name = name;
        this.restClient = restClient;
        this.uri = uri;
        this.servicePath = uri.getPath() != null ? uri.getPath() : "/";
    }

    public String getName() {
        return name;
    }

    @Override
    protected Health doHealthCheck() {
        long started = System.nanoTime();
        try {
            var response = restClient.head()
                    .uri(uri)
                    .retrieve()
                    .toBodilessEntity();
            
            long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
            
            if (response.getStatusCode().is2xxSuccessful()) {
                int port = uri.getPort() != -1 ? uri.getPort() : getDefaultPort(uri.getScheme());
                logger.info("component=external service_name=\"{}\" status=UP endpoint=\"{}\" host=\"{}\" port={} http_status={} latency_ms={}", 
                           name, uri.toString(), uri.getHost(), port, response.getStatusCode().value(), ms);
                
                return Health.up()
                        .withDetail("type", "external")
                        .withDetail("serviceName", name)
                        .withDetail("endpoint", uri.toString())
                        .withDetail("route", servicePath)
                        .withDetail("host", uri.getHost())
                        .withDetail("port", port)
                        .withDetail("status", response.getStatusCode().value())
                        .withDetail("latencyMs", ms)
                        .withDetail("component", "external")
                        .build();
            } else {
                int port = uri.getPort() != -1 ? uri.getPort() : getDefaultPort(uri.getScheme());
                logger.warn("component=external service_name=\"{}\" status=DOWN endpoint=\"{}\" host=\"{}\" port={} http_status={} latency_ms={}", 
                           name, uri.toString(), uri.getHost(), port, response.getStatusCode().value(), ms);
                
                return Health.down()
                        .withDetail("type", "external")
                        .withDetail("serviceName", name)
                        .withDetail("endpoint", uri.toString())
                        .withDetail("route", servicePath)
                        .withDetail("host", uri.getHost())
                        .withDetail("port", port)
                        .withDetail("status", response.getStatusCode().value())
                        .withDetail("latencyMs", ms)
                        .withDetail("component", "external")
                        .build();
            }
        } catch (Exception e) {
            long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
            int port = uri.getPort() != -1 ? uri.getPort() : getDefaultPort(uri.getScheme());
            
            logger.error("component=external service_name=\"{}\" status=DOWN endpoint=\"{}\" host=\"{}\" port={} latency_ms={} error_type=\"{}\" error_message=\"{}\"", 
                        name, uri.toString(), uri.getHost(), port, ms, e.getClass().getSimpleName(), e.getMessage());
            
            return Health.down()
                    .withDetail("type", "external")
                    .withDetail("serviceName", name)
                    .withDetail("endpoint", uri.toString())
                    .withDetail("route", servicePath)
                    .withDetail("host", uri.getHost())
                    .withDetail("port", port)
                    .withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .withDetail("latencyMs", ms)
                    .withDetail("component", "external")
                    .build();
        }
    }
    
    private int getDefaultPort(String scheme) {
        return switch (scheme != null ? scheme.toLowerCase() : "http") {
            case "https" -> 443;
            case "http" -> 80;
            default -> -1;
        };
    }
}