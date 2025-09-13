package com.example.health.checkers;

import com.example.health.config.HealthMonitoringProperties;
import com.example.health.core.AbstractHealthChecker;
import com.example.health.domain.HealthCheckResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Enterprise-grade external service health checker.
 * 
 * This implementation provides robust HTTP-based service health checking with:
 * - Configurable HTTP methods
 * - Response validation
 * - Retry mechanisms
 * - Detailed response metadata
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
public class ExternalServiceHealthChecker extends AbstractHealthChecker {
    
    private static final String COMPONENT_TYPE = "external";
    
    private final RestClient restClient;
    private final URI serviceUri;
    private final int maxRetries;
    
    public ExternalServiceHealthChecker(String serviceName, RestClient restClient, URI serviceUri, 
                                      HealthMonitoringProperties.ExternalServiceConfig config) {
        super(serviceName, COMPONENT_TYPE, determineTimeout(config));
        this.restClient = Objects.requireNonNull(restClient, "RestClient cannot be null");
        this.serviceUri = Objects.requireNonNull(serviceUri, "Service URI cannot be null");
        this.maxRetries = config.getRetries();
    }
    
    private static Duration determineTimeout(HealthMonitoringProperties.ExternalServiceConfig config) {
        return config.getTimeout() != null ? config.getTimeout() : Duration.ofSeconds(5);
    }
    
    @Override
    protected HealthCheckResult doHealthCheck() throws Exception {
        Instant start = Instant.now();
        Exception lastException = null;
        
        // Retry logic
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                ResponseEntity<Void> response = restClient.head()
                        .uri(serviceUri)
                        .retrieve()
                        .toBodilessEntity();
                
                Duration elapsed = Duration.between(start, Instant.now());
                HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    return createSuccessResult(elapsed, response, attempt);
                } else {
                    return createFailureResult(elapsed, response, attempt, null);
                }
                
            } catch (Exception e) {
                lastException = e;
                
                if (attempt < maxRetries) {
                    logger.debug("Health check attempt {} failed for '{}', retrying: {}", 
                               attempt + 1, getComponentName(), e.getMessage());
                    
                    // Brief pause between retries
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Health check interrupted", ie);
                    }
                } else {
                    Duration elapsed = Duration.between(start, Instant.now());
                    return createFailureResult(elapsed, null, attempt, e);
                }
            }
        }
        
        // This should never be reached due to the retry logic above
        Duration elapsed = Duration.between(start, Instant.now());
        return createFailureResult(elapsed, null, maxRetries, lastException);
    }
    
    private HealthCheckResult createSuccessResult(Duration elapsed, ResponseEntity<Void> response, int attempt) {
        return HealthCheckResult.success(getComponentName(), COMPONENT_TYPE, elapsed)
                .withDetail("endpoint", serviceUri.toString())
                .withDetail("httpStatus", response.getStatusCode().value())
                .withDetail("statusText", getReasonPhrase(response.getStatusCode()))
                .withDetail("host", serviceUri.getHost())
                .withDetail("port", getPort())
                .withDetail("path", serviceUri.getPath())
                .withDetail("scheme", serviceUri.getScheme())
                .withDetail("attempts", attempt + 1)
                .withDetail("contentLength", getContentLength(response))
                .build();
    }
    
    private HealthCheckResult createFailureResult(Duration elapsed, ResponseEntity<Void> response, 
                                                int attempt, Exception error) {
        var builder = HealthCheckResult.failure(getComponentName(), COMPONENT_TYPE, elapsed, error)
                .withDetail("endpoint", serviceUri.toString())
                .withDetail("host", serviceUri.getHost())
                .withDetail("port", getPort())
                .withDetail("path", serviceUri.getPath())
                .withDetail("scheme", serviceUri.getScheme())
                .withDetail("attempts", attempt + 1);
        
        if (response != null) {
            builder.withDetail("httpStatus", response.getStatusCode().value())
                   .withDetail("statusText", getReasonPhrase(response.getStatusCode()));
        }
        
        if (error != null) {
            builder.withDetail("errorType", error.getClass().getSimpleName())
                   .withDetail("isConnectionError", isConnectionError(error))
                   .withDetail("isTimeoutError", isTimeoutError(error));
        }
        
        return builder.build();
    }
    
    private int getPort() {
        int port = serviceUri.getPort();
        if (port == -1) {
            return "https".equals(serviceUri.getScheme()) ? 443 : 80;
        }
        return port;
    }
    
    private String getContentLength(ResponseEntity<Void> response) {
        return response.getHeaders().getFirst("Content-Length");
    }
    
    private boolean isConnectionError(Exception e) {
        String message = e.getMessage();
        return message != null && (
                message.contains("Connection refused") ||
                message.contains("No route to host") ||
                message.contains("Connection timed out")
        );
    }
    
    private boolean isTimeoutError(Exception e) {
        return e instanceof java.net.SocketTimeoutException ||
               (e.getMessage() != null && e.getMessage().contains("timeout"));
    }
    
    /**
     * Gets the reason phrase for an HTTP status code (Spring Boot 3.x compatibility).
     */
    private String getReasonPhrase(org.springframework.http.HttpStatusCode statusCode) {
        if (statusCode instanceof org.springframework.http.HttpStatus status) {
            return status.getReasonPhrase();
        }
        return String.valueOf(statusCode.value());
    }
}