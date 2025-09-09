package com.example.health.resilience;

import com.example.health.domain.HealthCheckResult;
import com.example.health.domain.HealthStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Enterprise-grade circuit breaker implementation for health checks.
 * Prevents cascading failures and implements adaptive recovery strategies.
 */
public final class CircuitBreakerManager {
    
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerManager.class);
    
    private final ConcurrentHashMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final CircuitBreakerConfig defaultConfig;

    public CircuitBreakerManager() {
        this.defaultConfig = CircuitBreakerConfig.builder().build();
    }

    public CircuitBreakerManager(CircuitBreakerConfig defaultConfig) {
        this.defaultConfig = defaultConfig;
    }

    /**
     * Gets or creates a circuit breaker for the specified health check.
     */
    public CircuitBreaker getCircuitBreaker(String checkName) {
        return circuitBreakers.computeIfAbsent(checkName, 
            name -> new CircuitBreaker(name, defaultConfig));
    }

    /**
     * Executes a health check with circuit breaker protection.
     */
    public HealthCheckResult executeWithCircuitBreaker(String checkName, 
                                                      java.util.function.Supplier<HealthCheckResult> healthCheck) {
        CircuitBreaker breaker = getCircuitBreaker(checkName);
        return breaker.execute(healthCheck);
    }

    /**
     * Gets current state of all circuit breakers.
     */
    public java.util.Map<String, CircuitBreakerState> getAllStates() {
        return circuitBreakers.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    java.util.Map.Entry::getKey,
                    entry -> entry.getValue().getState()
                ));
    }

    public static final class CircuitBreaker {
        private final String name;
        private final CircuitBreakerConfig config;
        private final AtomicReference<CircuitBreakerState> state;
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicLong lastFailureTime = new AtomicLong(0);
        private final AtomicLong lastSuccessTime = new AtomicLong(0);
        private final AtomicLong stateTransitionTime = new AtomicLong(System.currentTimeMillis());

        public CircuitBreaker(String name, CircuitBreakerConfig config) {
            this.name = name;
            this.config = config;
            this.state = new AtomicReference<>(CircuitBreakerState.CLOSED);
        }

        public HealthCheckResult execute(java.util.function.Supplier<HealthCheckResult> healthCheck) {
            CircuitBreakerState currentState = state.get();
            
            switch (currentState) {
                case OPEN:
                    return handleOpenState();
                case HALF_OPEN:
                    return handleHalfOpenState(healthCheck);
                case CLOSED:
                default:
                    return handleClosedState(healthCheck);
            }
        }

        private HealthCheckResult handleOpenState() {
            long timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime.get();
            
            if (timeSinceLastFailure >= config.getWaitDurationInOpenState().toMillis()) {
                logger.info("Circuit breaker {} transitioning from OPEN to HALF_OPEN after {} ms", 
                           name, timeSinceLastFailure);
                transitionTo(CircuitBreakerState.HALF_OPEN);
                return createCircuitBreakerResult(
                    "Circuit breaker transitioning to HALF_OPEN state", 
                    HealthStatus.DEGRADED
                );
            }
            
            return createCircuitBreakerResult(
                "Circuit breaker is OPEN - calls not permitted", 
                HealthStatus.DOWN
            );
        }

        private HealthCheckResult handleHalfOpenState(java.util.function.Supplier<HealthCheckResult> healthCheck) {
            try {
                HealthCheckResult result = healthCheck.get();
                
                if (result.isHealthy()) {
                    recordSuccess();
                    
                    if (successCount.get() >= config.getPermittedNumberOfCallsInHalfOpenState()) {
                        logger.info("Circuit breaker {} transitioning from HALF_OPEN to CLOSED after {} successful calls", 
                                   name, successCount.get());
                        transitionTo(CircuitBreakerState.CLOSED);
                        resetCounters();
                    }
                } else {
                    recordFailure();
                    logger.warn("Circuit breaker {} transitioning from HALF_OPEN to OPEN due to failure", name);
                    transitionTo(CircuitBreakerState.OPEN);
                }
                
                return result;
                
            } catch (Exception e) {
                recordFailure();
                logger.warn("Circuit breaker {} transitioning from HALF_OPEN to OPEN due to exception", name, e);
                transitionTo(CircuitBreakerState.OPEN);
                
                return HealthCheckResult.builder(name)
                        .status(HealthStatus.DOWN)
                        .latency(Duration.ZERO)
                        .errorMessage("Circuit breaker exception: " + e.getMessage())
                        .build();
            }
        }

        private HealthCheckResult handleClosedState(java.util.function.Supplier<HealthCheckResult> healthCheck) {
            try {
                HealthCheckResult result = healthCheck.get();
                
                if (result.isHealthy()) {
                    recordSuccess();
                } else {
                    recordFailure();
                    
                    if (shouldOpenCircuit()) {
                        logger.warn("Circuit breaker {} transitioning from CLOSED to OPEN after {} failures in {} ms", 
                                   name, failureCount.get(), config.getSlidingWindowSize().toMillis());
                        transitionTo(CircuitBreakerState.OPEN);
                    }
                }
                
                return result;
                
            } catch (Exception e) {
                recordFailure();
                
                if (shouldOpenCircuit()) {
                    logger.warn("Circuit breaker {} transitioning from CLOSED to OPEN due to exception threshold", name, e);
                    transitionTo(CircuitBreakerState.OPEN);
                }
                
                return HealthCheckResult.builder(name)
                        .status(HealthStatus.DOWN)
                        .latency(Duration.ZERO)
                        .errorMessage("Health check failed: " + e.getMessage())
                        .build();
            }
        }

        private boolean shouldOpenCircuit() {
            long windowStartTime = System.currentTimeMillis() - config.getSlidingWindowSize().toMillis();
            
            // Reset counters if we're outside the sliding window
            if (lastFailureTime.get() < windowStartTime) {
                resetCounters();
                return false;
            }
            
            int currentFailures = failureCount.get();
            double failureRate = (double) currentFailures / (currentFailures + successCount.get());
            
            return currentFailures >= config.getMinimumNumberOfCalls() && 
                   failureRate >= config.getFailureRateThreshold();
        }

        private void recordSuccess() {
            successCount.incrementAndGet();
            lastSuccessTime.set(System.currentTimeMillis());
        }

        private void recordFailure() {
            failureCount.incrementAndGet();
            lastFailureTime.set(System.currentTimeMillis());
        }

        private void transitionTo(CircuitBreakerState newState) {
            CircuitBreakerState oldState = state.getAndSet(newState);
            stateTransitionTime.set(System.currentTimeMillis());
            
            if (newState == CircuitBreakerState.CLOSED) {
                resetCounters();
            }
            
            logger.info("Circuit breaker {} transitioned from {} to {}", name, oldState, newState);
        }

        private void resetCounters() {
            failureCount.set(0);
            successCount.set(0);
        }

        private HealthCheckResult createCircuitBreakerResult(String message, HealthStatus status) {
            return HealthCheckResult.builder(name)
                    .status(status)
                    .latency(Duration.ZERO)
                    .errorMessage(message)
                    .addMetadata("circuitBreakerState", state.get().toString())
                    .addMetadata("failureCount", failureCount.get())
                    .addMetadata("successCount", successCount.get())
                    .build();
        }

        public CircuitBreakerState getState() {
            return state.get();
        }

        public String getName() {
            return name;
        }

        public int getFailureCount() {
            return failureCount.get();
        }

        public int getSuccessCount() {
            return successCount.get();
        }
    }

    public enum CircuitBreakerState {
        CLOSED,    // Normal operation - calls allowed
        OPEN,      // Failing fast - calls not allowed  
        HALF_OPEN  // Testing if service recovered - limited calls allowed
    }

    public static final class CircuitBreakerConfig {
        private final double failureRateThreshold;
        private final int minimumNumberOfCalls;
        private final Duration slidingWindowSize;
        private final Duration waitDurationInOpenState;
        private final int permittedNumberOfCallsInHalfOpenState;

        private CircuitBreakerConfig(Builder builder) {
            this.failureRateThreshold = builder.failureRateThreshold;
            this.minimumNumberOfCalls = builder.minimumNumberOfCalls;
            this.slidingWindowSize = builder.slidingWindowSize;
            this.waitDurationInOpenState = builder.waitDurationInOpenState;
            this.permittedNumberOfCallsInHalfOpenState = builder.permittedNumberOfCallsInHalfOpenState;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public double getFailureRateThreshold() { return failureRateThreshold; }
        public int getMinimumNumberOfCalls() { return minimumNumberOfCalls; }
        public Duration getSlidingWindowSize() { return slidingWindowSize; }
        public Duration getWaitDurationInOpenState() { return waitDurationInOpenState; }
        public int getPermittedNumberOfCallsInHalfOpenState() { return permittedNumberOfCallsInHalfOpenState; }

        public static final class Builder {
            private double failureRateThreshold = 0.5; // 50%
            private int minimumNumberOfCalls = 5;
            private Duration slidingWindowSize = Duration.ofMinutes(1);
            private Duration waitDurationInOpenState = Duration.ofSeconds(30);
            private int permittedNumberOfCallsInHalfOpenState = 3;

            public Builder failureRateThreshold(double failureRateThreshold) {
                this.failureRateThreshold = failureRateThreshold;
                return this;
            }

            public Builder minimumNumberOfCalls(int minimumNumberOfCalls) {
                this.minimumNumberOfCalls = minimumNumberOfCalls;
                return this;
            }

            public Builder slidingWindowSize(Duration slidingWindowSize) {
                this.slidingWindowSize = slidingWindowSize;
                return this;
            }

            public Builder waitDurationInOpenState(Duration waitDurationInOpenState) {
                this.waitDurationInOpenState = waitDurationInOpenState;
                return this;
            }

            public Builder permittedNumberOfCallsInHalfOpenState(int permittedNumberOfCallsInHalfOpenState) {
                this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
                return this;
            }

            public CircuitBreakerConfig build() {
                return new CircuitBreakerConfig(this);
            }
        }
    }
}