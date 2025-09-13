package com.example.health.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

public class LatencyLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(LatencyLogger.class);

    public void logOnce(HealthContributor contributor) {
        if (contributor == null) {
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                logHealthContributor(contributor);
            } catch (Exception e) {
                logger.warn("startup_check=true error=\"Failed to execute startup health check logging\" error_type=\"{}\" error_message=\"{}\"", 
                           e.getClass().getSimpleName(), e.getMessage());
            }
        });
    }

    private void logHealthContributor(HealthContributor contributor) {
        if (contributor instanceof HealthIndicator healthIndicator) {
            logHealthIndicator("", healthIndicator);
        } else if (contributor instanceof CompositeHealthContributor composite) {
            composite.iterator().forEachRemaining(entry -> {
                String name = entry.getName();
                HealthContributor subContributor = entry.getContributor();
                if (subContributor instanceof HealthIndicator healthIndicator) {
                    logHealthIndicator(name, healthIndicator);
                } else if (subContributor instanceof CompositeHealthContributor subComposite) {
                    subComposite.iterator().forEachRemaining(subEntry -> {
                        String subName = subEntry.getName();
                        HealthContributor subSubContributor = subEntry.getContributor();
                        if (subSubContributor instanceof HealthIndicator subHealthIndicator) {
                            logHealthIndicator(name + "." + subName, subHealthIndicator);
                        }
                    });
                }
            });
        }
    }

    private void logHealthIndicator(String name, HealthIndicator indicator) {
        try {
            Health health = indicator.health();
            String status = health.getStatus().getCode();
            Object latencyMs = health.getDetails().get("latencyMs");
            Object error = health.getDetails().get("error");

            String componentName = name.isEmpty() ? "custom" : name;
            
            if (error != null) {
                logger.info("startup_check=true component=\"{}\" status={} latency_ms={} error=\"{}\"", 
                    componentName, status, latencyMs, error);
            } else {
                logger.info("startup_check=true component=\"{}\" status={} latency_ms={}", 
                    componentName, status, latencyMs);
            }
        } catch (Exception e) {
            logger.warn("startup_check=true component=\"{}\" error=\"Failed to check health\" error_type=\"{}\" error_message=\"{}\"", 
                       name, e.getClass().getSimpleName(), e.getMessage());
        }
    }
}