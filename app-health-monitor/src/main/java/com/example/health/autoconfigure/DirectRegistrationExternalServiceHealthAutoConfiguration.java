package com.example.health.autoconfigure;

import com.example.health.checkers.ExternalServiceHealthChecker;
import com.example.health.config.ValidatedHealthMonitoringProperties;
import com.example.health.core.HealthChecker;
import com.example.health.registry.HealthCheckRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.ConversionService;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.function.Supplier;

/**
 * Direct registration external service health checker auto-configuration.
 * 
 * This configuration directly registers external service health checkers into the HealthCheckRegistry
 * in its constructor for each configured external service.
 * 
 * Features:
 * - Constructor-based direct registration
 * - No bean creation whatsoever
 * - Immediate registration on instantiation
 * - Support for multiple external services
 * - Dynamic service configuration
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "app.health", name = "enabled", havingValue = "true", matchIfMissing = true)
@Order(2100) // Register after registry initialization and other core services
public class DirectRegistrationExternalServiceHealthAutoConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(DirectRegistrationExternalServiceHealthAutoConfiguration.class);
    
    // Security: Maximum number of external services to prevent resource exhaustion
    private static final int MAX_EXTERNAL_SERVICES = 50;
    
    /**
     * Constructor that immediately registers all external service health checkers into the registry.
     * No beans, no InitializingBean - just direct registration.
     */
    public DirectRegistrationExternalServiceHealthAutoConfiguration(
            HealthCheckRegistry registry,
            BeanFactory beanFactory,
            ValidatedHealthMonitoringProperties properties,
            ConversionService conversionService) {
        
        logger.info("DirectRegistrationExternalServiceHealthAutoConfiguration instantiated - registering external services directly");
        
        try {
            // Defensive programming: Validate inputs
            if (registry == null) {
                throw new IllegalStateException("HealthCheckRegistry is required but was null");
            }
            if (beanFactory == null) {
                throw new IllegalStateException("BeanFactory is required but was null");
            }
            if (properties == null) {
                throw new IllegalStateException("Health monitoring properties are required but were null");
            }
            
            var externalServices = properties.getExternalServices();
            if (externalServices == null || externalServices.isEmpty()) {
                logger.info("No external services configured, skipping external service health checker registration");
                return;
            }
            
            // Security: Validate service count
            if (externalServices.size() > MAX_EXTERNAL_SERVICES) {
                throw new IllegalStateException(
                    "Too many external services configured: " + externalServices.size() + 
                    ". Maximum allowed: " + MAX_EXTERNAL_SERVICES);
            }
            
            logger.info("Processing {} configured external services for health checking", externalServices.size());
            
            int registeredCount = 0;
            int skippedCount = 0;
            
            // Register each external service individually
            for (ValidatedHealthMonitoringProperties.ExternalServiceConfig serviceConfig : externalServices) {
                try {
                    if (!serviceConfig.isEnabled()) {
                        logger.debug("Skipping disabled external service: {}", serviceConfig.getName());
                        skippedCount++;
                        continue;
                    }
                    
                    // Create and register the health checker for this service
                    HealthChecker healthChecker = createExternalServiceHealthChecker(serviceConfig, beanFactory, conversionService);
                    registry.register(healthChecker);
                    
                    registeredCount++;
                    logger.info("✅ Registered external service health checker: '{}' -> {}", 
                               serviceConfig.getName(), serviceConfig.getUrlBeanName());
                    
                } catch (Exception e) {
                    logger.error("❌ Failed to register external service health checker for '{}': {}", 
                               serviceConfig.getName(), e.getMessage(), e);
                    
                    // Continue processing other services rather than failing completely
                    skippedCount++;
                }
            }
            
            logger.info("✅ External service health checker registration completed: {} registered, {} skipped", 
                       registeredCount, skippedCount);
            
        } catch (Exception e) {
            logger.error("❌ Failed to register external service health checkers in constructor", e);
            throw new IllegalStateException("External service health checker registration failed", e);
        }
    }
    
    /**
     * Creates a single external service health checker.
     */
    private HealthChecker createExternalServiceHealthChecker(
            ValidatedHealthMonitoringProperties.ExternalServiceConfig config,
            BeanFactory beanFactory,
            ConversionService conversionService) {
        
        // Validate service configuration
        validateServiceConfig(config);
        
        // Resolve RestClient bean
        RestClient restClient = resolveRestClientBean(config, beanFactory);
        
        // Resolve and convert URL bean
        URI serviceUri = resolveServiceUri(config, beanFactory, conversionService);
        
        return new ExternalServiceHealthChecker(config.getName(), restClient, serviceUri, config);
    }
    
    /**
     * Validates external service configuration.
     */
    private void validateServiceConfig(ValidatedHealthMonitoringProperties.ExternalServiceConfig config) {
        if (config.getName() == null || config.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("External service name cannot be null or empty");
        }
        
        if (config.getRestClientBeanName() == null || config.getRestClientBeanName().trim().isEmpty()) {
            throw new IllegalArgumentException("RestClient bean name cannot be null or empty for service: " + config.getName());
        }
        
        if (config.getUrlBeanName() == null || config.getUrlBeanName().trim().isEmpty()) {
            throw new IllegalArgumentException("URL bean name cannot be null or empty for service: " + config.getName());
        }
    }
    
    /**
     * Resolves the RestClient bean for the service.
     */
    private RestClient resolveRestClientBean(ValidatedHealthMonitoringProperties.ExternalServiceConfig config, BeanFactory beanFactory) {
        try {
            String beanName = config.getRestClientBeanName();
            logger.debug("Resolving RestClient bean: {}", beanName);
            
            return beanFactory.getBean(beanName, RestClient.class);
            
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to resolve RestClient bean '" + config.getRestClientBeanName() + 
                "' for external service '" + config.getName() + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Resolves and converts the service URI from the configured bean.
     */
    private URI resolveServiceUri(ValidatedHealthMonitoringProperties.ExternalServiceConfig config, BeanFactory beanFactory, ConversionService conversionService) {
        try {
            String urlBeanName = config.getUrlBeanName();
            logger.debug("Resolving URL bean: {}", urlBeanName);
            
            Object urlBean = beanFactory.getBean(urlBeanName);
            return convertToUri(urlBean, config.getName(), conversionService);
            
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to resolve URL bean '" + config.getUrlBeanName() + 
                "' for external service '" + config.getName() + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Converts various URL bean types to URI.
     */
    private URI convertToUri(Object urlBean, String serviceName, ConversionService conversionService) {
        if (urlBean == null) {
            throw new IllegalArgumentException("URL bean cannot be null for service: " + serviceName);
        }
        
        // Direct URI
        if (urlBean instanceof URI uri) {
            return uri;
        }
        
        // String URL
        if (urlBean instanceof String url) {
            return URI.create(url);
        }
        
        // Supplier<URI>
        if (urlBean instanceof Supplier<?> supplier) {
            Object supplied = supplier.get();
            if (supplied instanceof URI uri) {
                return uri;
            } else if (supplied instanceof String url) {
                return URI.create(url);
            }
        }
        
        // Try conversion service
        if (conversionService != null && conversionService.canConvert(urlBean.getClass(), URI.class)) {
            URI converted = conversionService.convert(urlBean, URI.class);
            if (converted != null) {
                return converted;
            }
        }
        
        // Fallback: toString() conversion
        try {
            return URI.create(urlBean.toString());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Cannot convert URL bean of type " + urlBean.getClass().getSimpleName() + 
                " to URI for service: " + serviceName + ". Value: " + urlBean, e);
        }
    }
}