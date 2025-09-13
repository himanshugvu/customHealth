package com.example.health.autoconfigure;

import com.example.health.actuator.EnterpriseHealthIndicator;
import com.example.health.config.ValidatedHealthMonitoringProperties;
import com.example.health.core.HealthChecker;
import com.example.health.service.OptimizedHealthCheckOrchestrator;
import com.example.health.service.StartupHealthReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

import java.util.List;

/**
 * Main auto-configuration for the Enterprise Health Monitoring Library.
 * 
 * This configuration class orchestrates the setup of the entire health monitoring
 * system, including:
 * - Health check orchestrator
 * - Spring Boot Actuator integration
 * - Startup health reporting
 * - Proper bean lifecycle management
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(ValidatedHealthMonitoringProperties.class)
@ConditionalOnProperty(prefix = "app.health", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HealthMonitoringAutoConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthMonitoringAutoConfiguration.class);
    
    /**
     * Creates the health check orchestrator that manages all health checkers.
     */
    @Bean
    public OptimizedHealthCheckOrchestrator healthCheckOrchestrator(ObjectProvider<HealthChecker> healthCheckersProvider,
                                                         ValidatedHealthMonitoringProperties properties) {
        List<HealthChecker> healthCheckers = healthCheckersProvider.orderedStream().toList();
        
        logger.info("Initializing Health Check Orchestrator with {} health checkers", healthCheckers.size());
        if (logger.isDebugEnabled()) {
            healthCheckers.forEach(checker -> 
                logger.debug("Registered health checker: {} (type: {}, enabled: {})", 
                           checker.getComponentName(), checker.getComponentType(), checker.isEnabled()));
        }
        
        return new OptimizedHealthCheckOrchestrator(healthCheckers, properties.getDefaultTimeout(), 4);
    }
    
    /**
     * Creates the main enterprise health indicator for Spring Boot Actuator integration.
     */
    @Bean(name = "enterpriseHealth")
    public EnterpriseHealthIndicator enterpriseHealthIndicator(OptimizedHealthCheckOrchestrator orchestrator) {
        logger.info("Creating Enterprise Health Indicator for Spring Boot Actuator integration");
        return new EnterpriseHealthIndicator("enterprise-health-monitor", orchestrator);
    }
    
    /**
     * Creates the startup health reporter for application startup logging.
     */
    @Bean
    public StartupHealthReporter startupHealthReporter(OptimizedHealthCheckOrchestrator orchestrator,
                                                     ValidatedHealthMonitoringProperties properties) {
        return new StartupHealthReporter(orchestrator, properties);
    }
    
    /**
     * Creates an application runner for startup health reporting.
     */
    @Bean
    @Order(1000) // Run after other initialization
    @ConditionalOnProperty(prefix = "app.health", name = "startupLogging", havingValue = "true", matchIfMissing = true)
    public ApplicationRunner startupHealthCheckRunner(StartupHealthReporter reporter) {
        logger.info("Enabling startup health check reporting");
        return args -> reporter.reportStartupHealth();
    }
}