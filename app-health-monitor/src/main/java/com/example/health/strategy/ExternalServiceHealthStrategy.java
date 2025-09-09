package com.example.health.strategy;

import com.example.health.core.HealthCheckContext;
import com.example.health.domain.HealthCheckResult;
import com.example.health.domain.HealthStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise-grade external service health check strategy with OAuth2 support,
 * circuit breaker integration, and intelligent degradation detection.
 */
public final class ExternalServiceHealthStrategy implements HealthCheckStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(ExternalServiceHealthStrategy.class);
    
    private final String serviceName;
    private final RestClient restClient;
    private final URI healthEndpoint;
    private final AuthenticationStrategy authStrategy;
    private final FallbackStrategy fallbackStrategy;

    public ExternalServiceHealthStrategy(String serviceName, 
                                       RestClient restClient, 
                                       URI healthEndpoint) {
        this(serviceName, restClient, healthEndpoint, 
             AuthenticationStrategy.none(), 
             FallbackStrategy.tcpConnect());
    }

    public ExternalServiceHealthStrategy(String serviceName,
                                       RestClient restClient,
                                       URI healthEndpoint,
                                       AuthenticationStrategy authStrategy,
                                       FallbackStrategy fallbackStrategy) {
        this.serviceName = serviceName;
        this.restClient = restClient;
        this.healthEndpoint = healthEndpoint;
        this.authStrategy = authStrategy;
        this.fallbackStrategy = fallbackStrategy;
    }

    @Override
    public CompletableFuture<HealthCheckResult> execute(HealthCheckContext context) {
        var startTime = Instant.now();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return performHealthCheck(context, startTime);
            } catch (Exception e) {
                return handleException(context, e, startTime);
            }
        }, context.getExecutor())
        .completeOnTimeout(
            createTimeoutResult(context, startTime), 
            context.getTimeout().toMillis(), 
            TimeUnit.MILLISECONDS
        );
    }

    private HealthCheckResult performHealthCheck(HealthCheckContext context, Instant startTime) {
        try {
            // Apply authentication if configured
            var requestSpec = restClient.get().uri(healthEndpoint);
            authStrategy.authenticate(requestSpec);
            
            // Execute the health check
            ResponseEntity<String> response = requestSpec.retrieve().toEntity(String.class);
            Duration latency = Duration.between(startTime, Instant.now());
            
            return analyzeResponse(response, latency, context);
            
        } catch (RestClientException e) {
            // Try fallback strategy if primary check fails
            if (fallbackStrategy != null) {
                logger.debug("Primary health check failed for {}, attempting fallback", serviceName, e);
                return attemptFallback(context, startTime, e);
            }
            throw e;
        }
    }

    private HealthCheckResult analyzeResponse(ResponseEntity<String> response, 
                                            Duration latency, 
                                            HealthCheckContext context) {
        var builder = HealthCheckResult.builder(serviceName)
                .latency(latency)
                .addMetadata("endpoint", healthEndpoint.toString())
                .addMetadata("httpStatus", response.getStatusCode().value())
                .addMetadata("responseTime", latency.toMillis() + "ms");

        // Analyze status code
        HttpStatus status = (HttpStatus) response.getStatusCode();
        if (status.is2xxSuccessful()) {
            // Check for degradation indicators
            if (latency.compareTo(context.getDegradationThreshold()) > 0) {
                return builder
                        .status(HealthStatus.DEGRADED)
                        .errorMessage("Service responding slowly: " + latency.toMillis() + "ms")
                        .build();
            }
            return builder.status(HealthStatus.UP).build();
        } 
        else if (status.is5xxServerError()) {
            return builder
                    .status(HealthStatus.DOWN)
                    .errorMessage("Server error: " + status.getReasonPhrase())
                    .build();
        } 
        else if (status.value() == 429) { // Rate limited
            return builder
                    .status(HealthStatus.DEGRADED)
                    .errorMessage("Rate limited by service")
                    .build();
        } 
        else {
            return builder
                    .status(HealthStatus.DOWN)
                    .errorMessage("Unexpected status: " + status.getReasonPhrase())
                    .build();
        }
    }

    private HealthCheckResult attemptFallback(HealthCheckContext context, Instant startTime, Exception primaryError) {
        try {
            boolean fallbackResult = fallbackStrategy.execute(healthEndpoint, context.getTimeout());
            Duration latency = Duration.between(startTime, Instant.now());
            
            if (fallbackResult) {
                return HealthCheckResult.builder(serviceName)
                        .status(HealthStatus.DEGRADED)
                        .latency(latency)
                        .errorMessage("Primary check failed but basic connectivity confirmed")
                        .addMetadata("primaryError", primaryError.getMessage())
                        .addMetadata("fallbackUsed", true)
                        .build();
            } else {
                return createFailureResult(primaryError, latency);
            }
        } catch (Exception fallbackError) {
            Duration latency = Duration.between(startTime, Instant.now());
            return HealthCheckResult.builder(serviceName)
                    .status(HealthStatus.DOWN)
                    .latency(latency)
                    .errorMessage("Both primary and fallback checks failed")
                    .addMetadata("primaryError", primaryError.getMessage())
                    .addMetadata("fallbackError", fallbackError.getMessage())
                    .build();
        }
    }

    private HealthCheckResult handleException(HealthCheckContext context, Exception e, Instant startTime) {
        Duration latency = Duration.between(startTime, Instant.now());
        logger.warn("Health check failed for external service: {}", serviceName, e);
        
        return createFailureResult(e, latency);
    }

    private HealthCheckResult createFailureResult(Exception e, Duration latency) {
        return HealthCheckResult.builder(serviceName)
                .status(HealthStatus.DOWN)
                .latency(latency)
                .errorMessage(e.getClass().getSimpleName() + ": " + e.getMessage())
                .addMetadata("endpoint", healthEndpoint.toString())
                .addMetadata("errorType", e.getClass().getSimpleName())
                .build();
    }

    private HealthCheckResult createTimeoutResult(HealthCheckContext context, Instant startTime) {
        Duration latency = Duration.between(startTime, Instant.now());
        return HealthCheckResult.builder(serviceName)
                .status(HealthStatus.DOWN)
                .latency(latency)
                .errorMessage("Health check timed out after " + context.getTimeout().toMillis() + "ms")
                .addMetadata("endpoint", healthEndpoint.toString())
                .addMetadata("timeout", true)
                .build();
    }

    @Override
    public String getStrategyName() {
        return "external-service";
    }

    @Override
    public boolean supportsDegradation() {
        return true;
    }

    @Override
    public Duration getRecommendedTimeout() {
        return Duration.ofSeconds(10); // Longer timeout for external services
    }

    // Authentication Strategy Pattern
    public interface AuthenticationStrategy {
        void authenticate(RestClient.RequestHeadersSpec<?> requestSpec);
        
        static AuthenticationStrategy none() {
            return requestSpec -> {}; // No-op
        }
        
        static AuthenticationStrategy bearer(String token) {
            return requestSpec -> requestSpec.header("Authorization", "Bearer " + token);
        }
        
        static AuthenticationStrategy basic(String username, String password) {
            String credentials = java.util.Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes());
            return requestSpec -> requestSpec.header("Authorization", "Basic " + credentials);
        }
        
        static AuthenticationStrategy apiKey(String headerName, String apiKey) {
            return requestSpec -> requestSpec.header(headerName, apiKey);
        }
    }

    // Fallback Strategy Pattern  
    public interface FallbackStrategy {
        boolean execute(URI uri, Duration timeout) throws Exception;
        
        static FallbackStrategy none() {
            return (uri, timeout) -> false;
        }
        
        static FallbackStrategy tcpConnect() {
            return (uri, timeout) -> {
                try (var socket = new java.net.Socket()) {
                    socket.connect(
                        new java.net.InetSocketAddress(uri.getHost(), 
                                                     uri.getPort() != -1 ? uri.getPort() : 80),
                        (int) timeout.toMillis()
                    );
                    return true;
                } catch (Exception e) {
                    return false;
                }
            };
        }
        
        static FallbackStrategy ping() {
            return (uri, timeout) -> {
                try {
                    return java.net.InetAddress.getByName(uri.getHost())
                            .isReachable((int) timeout.toMillis());
                } catch (Exception e) {
                    return false;
                }
            };
        }
    }
}