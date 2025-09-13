package com.example.health.registry;

import com.example.health.core.HealthChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

import org.springframework.beans.factory.InitializingBean;
import java.util.List;

/**
 * Auto-configuration for Health Check Registry.
 * 
 * This configuration class automatically sets up the HealthCheckRegistry and initializes it
 * with all available health checker beans, both individual and collections.
 * 
 * Features:
 * - Automatic discovery of all HealthChecker beans
 * - Support for both @Bean HealthChecker and @Bean List<HealthChecker>
 * - Proper initialization order
 * - Registry validation and statistics logging
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "app.health", name = "enabled", havingValue = "true", matchIfMissing = true)
@Order(1000) // Initialize after other health checker beans
public class HealthCheckRegistryConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckRegistryConfiguration.class);
    
    /**
     * Creates the health check registry bean.
     */
    @Bean
    public HealthCheckRegistry healthCheckRegistry() {
        logger.info("Creating Health Check Registry");
        return new HealthCheckRegistry();
    }
    
    /**
     * Initializes the registry for direct registration mode.
     */
    @Bean
    @Order(1500) // Initialize before auto-configurations register their beans
    public HealthCheckRegistryInitializer healthCheckRegistryInitializer(HealthCheckRegistry registry) {
        
        return new HealthCheckRegistryInitializer(registry);
    }
    
    /**
     * Finalizes the registry after all auto-configurations have registered.
     */
    @Bean
    @Order(5000) // Finalize after all auto-configurations
    public HealthCheckRegistryFinalizer healthCheckRegistryFinalizer(HealthCheckRegistry registry) {
        
        return new HealthCheckRegistryFinalizer(registry);
    }
    
    /**
     * Registry initializer component that handles the setup.
     */
    public static class HealthCheckRegistryInitializer implements InitializingBean {
        
        private static final Logger logger = LoggerFactory.getLogger(HealthCheckRegistryInitializer.class);
        
        private final HealthCheckRegistry registry;
        
        public HealthCheckRegistryInitializer(HealthCheckRegistry registry) {
            this.registry = registry;
        }
        
        @Override
        public void afterPropertiesSet() {
            logger.info("Initializing Health Check Registry for direct registration mode...");
            
            try {
                // Initialize the registry for direct registration
                registry.initializeForDirectRegistration();
                
                logger.info("Health Check Registry ready for direct registrations from auto-configurations");
                
            } catch (Exception e) {
                logger.error("Failed to initialize Health Check Registry", e);
                throw new IllegalStateException("Registry initialization failed", e);
            }
        }
        
        /**
         * Gets the initialized registry.
         */
        public HealthCheckRegistry getRegistry() {
            return registry;
        }
    }
    
    /**
     * Registry finalizer component that completes the setup after all registrations.
     */
    public static class HealthCheckRegistryFinalizer implements InitializingBean {
        
        private static final Logger logger = LoggerFactory.getLogger(HealthCheckRegistryFinalizer.class);
        
        private final HealthCheckRegistry registry;
        
        public HealthCheckRegistryFinalizer(HealthCheckRegistry registry) {
            this.registry = registry;
        }
        
        @Override
        public void afterPropertiesSet() {
            logger.info("Finalizing Health Check Registry after all auto-configuration registrations...");
            
            try {
                // Finalize the registry
                registry.finalizeRegistrations();
                
                // Validate the registry
                if (!registry.validateRegistry()) {
                    logger.error("Health Check Registry validation failed");
                    throw new IllegalStateException("Registry validation failed");
                }
                
                // Log final statistics
                HealthCheckRegistry.RegistryStatistics stats = registry.getStatistics();
                logger.info("Health Check Registry finalization completed successfully:");
                logger.info("  - Total health checkers: {}", stats.totalHealthCheckers());
                logger.info("  - Component types: {}", stats.componentTypes());
                logger.info("  - Registered types: {}", registry.getRegisteredTypes());
                
            } catch (Exception e) {
                logger.error("Failed to finalize Health Check Registry", e);
                throw new IllegalStateException("Registry finalization failed", e);
            }
        }
    }
}