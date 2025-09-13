package com.example.health.autoconfigure;

import com.example.health.checkers.ExternalServiceHealthChecker;
import com.example.health.config.HealthMonitoringProperties;
import com.example.health.core.HealthChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Enhanced auto-configuration for external service health checking.
 * 
 * This configuration class creates external service health checkers following
 * enterprise patterns with proper error handling, bean resolution,
 * and configuration validation.
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "app.health.monitoring", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EnhancedExternalServiceHealthAutoConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedExternalServiceHealthAutoConfiguration.class);
    
    /**
     * Creates external service health checkers for all configured services.
     */
    @Bean
    public List<HealthChecker> externalServiceHealthCheckers(BeanFactory beanFactory, 
                                                           HealthMonitoringProperties properties,
                                                           ConversionService conversionService) {
        
        List<HealthChecker> healthCheckers = new ArrayList<>();
        
        for (HealthMonitoringProperties.ExternalServiceConfig serviceConfig : properties.getExternalServices()) {
            if (!serviceConfig.isEnabled()) {
                logger.debug("Skipping disabled external service: {}", serviceConfig.getName());
                continue;
            }
            
            try {
                HealthChecker healthChecker = createExternalServiceHealthChecker(
                    beanFactory, serviceConfig, conversionService);
                healthCheckers.add(healthChecker);
                
                logger.info("Created external service health checker for '{}' targeting '{}'", 
                           serviceConfig.getName(), serviceConfig.getUrlBeanName());
                
            } catch (Exception e) {
                logger.error("Failed to create external service health checker for '{}': {}", 
                           serviceConfig.getName(), e.getMessage(), e);
                
                // In enterprise environments, we might want to fail fast
                throw new IllegalStateException(
                    "External service health checker configuration failed for: " + serviceConfig.getName(), e);
            }
        }
        
        logger.info("Created {} external service health checkers", healthCheckers.size());
        return healthCheckers;
    }
    
    /**
     * Creates a single external service health checker.
     */
    private HealthChecker createExternalServiceHealthChecker(BeanFactory beanFactory,
                                                           HealthMonitoringProperties.ExternalServiceConfig config,
                                                           ConversionService conversionService) {
        
        // Resolve RestClient bean
        RestClient restClient = resolveRestClientBean(beanFactory, config);
        
        // Resolve and convert URL bean
        URI serviceUri = resolveServiceUri(beanFactory, config, conversionService);
        
        return new ExternalServiceHealthChecker(config.getName(), restClient, serviceUri, config);
    }
    
    /**
     * Resolves the RestClient bean for the service.
     */
    private RestClient resolveRestClientBean(BeanFactory beanFactory, 
                                           HealthMonitoringProperties.ExternalServiceConfig config) {
        try {
            return beanFactory.getBean(config.getRestClientBeanName(), RestClient.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to resolve RestClient bean '" + config.getRestClientBeanName() + 
                "' for external service '" + config.getName() + "'", e);
        }
    }
    
    /**
     * Resolves and converts the service URI from the configured bean.
     */
    private URI resolveServiceUri(BeanFactory beanFactory, 
                                HealthMonitoringProperties.ExternalServiceConfig config,
                                ConversionService conversionService) {
        try {
            Object urlBean = beanFactory.getBean(config.getUrlBeanName());
            return convertToUri(urlBean, conversionService, config.getName());
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to resolve URL bean '" + config.getUrlBeanName() + 
                "' for external service '" + config.getName() + "'", e);
        }
    }
    
    /**
     * Converts various URL bean types to URI.
     */
    private URI convertToUri(Object urlBean, ConversionService conversionService, String serviceName) {
        if (urlBean instanceof URI uri) {
            return uri;
        }
        
        if (urlBean instanceof String str) {
            return URI.create(str);
        }
        
        if (urlBean instanceof Supplier<?> supplier) {
            Object supplied = supplier.get();
            if (supplied instanceof URI uri) {
                return uri;
            }
            if (supplied instanceof String str) {
                return URI.create(str);
            }
        }
        
        // Try Spring's conversion service
        if (conversionService.canConvert(urlBean.getClass(), URI.class)) {
            return conversionService.convert(urlBean, URI.class);
        }
        
        throw new IllegalArgumentException(
            "Cannot convert URL bean of type " + urlBean.getClass().getName() + 
            " to URI for external service '" + serviceName + "'. " +
            "Supported types: URI, String, Supplier<URI>, Supplier<String>");
    }
}