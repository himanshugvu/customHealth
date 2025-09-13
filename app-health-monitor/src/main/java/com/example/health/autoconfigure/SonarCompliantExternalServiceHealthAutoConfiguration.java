package com.example.health.autoconfigure;

import com.example.health.checkers.ExternalServiceHealthChecker;
import com.example.health.config.ValidatedHealthMonitoringProperties;
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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Sonar-compliant auto-configuration for external service health checking.
 * 
 * This configuration class creates secure external service health checkers following
 * enterprise patterns with comprehensive quality improvements:
 * - Security hardening and input validation
 * - Proper error handling and resource management
 * - Defensive programming practices
 * - Performance optimizations
 * - Comprehensive logging and monitoring
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "app.health", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SonarCompliantExternalServiceHealthAutoConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(SonarCompliantExternalServiceHealthAutoConfiguration.class);
    
    // Security: Maximum number of external services to prevent resource exhaustion
    private static final int MAX_EXTERNAL_SERVICES = 50;
    
    /**
     * Creates external service health checkers for all configured services.
     */
    @Bean
    public List<HealthChecker> secureExternalServiceHealthCheckers(
            BeanFactory beanFactory, 
            ValidatedHealthMonitoringProperties properties,
            ConversionService conversionService) {
        
        // Defensive programming: Validate inputs
        if (beanFactory == null) {
            logger.warn("BeanFactory is null, no external service health checkers will be created");
            return Collections.emptyList();
        }
        if (properties == null) {
            logger.warn("Health monitoring properties are null, no external service health checkers will be created");
            return Collections.emptyList();
        }
        if (conversionService == null) {
            logger.warn("ConversionService is null, no external service health checkers will be created");
            return Collections.emptyList();
        }
        
        List<ValidatedHealthMonitoringProperties.ExternalServiceConfig> serviceConfigs = 
            properties.getExternalServices();
        
        if (serviceConfigs == null || serviceConfigs.isEmpty()) {
            logger.debug("No external services configured");
            return Collections.emptyList();
        }
        
        // Security: Limit number of external services
        if (serviceConfigs.size() > MAX_EXTERNAL_SERVICES) {
            logger.warn("Too many external services configured ({}), limiting to {}", 
                       serviceConfigs.size(), MAX_EXTERNAL_SERVICES);
            serviceConfigs = serviceConfigs.subList(0, MAX_EXTERNAL_SERVICES);
        }
        
        List<HealthChecker> healthCheckers = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;
        
        for (ValidatedHealthMonitoringProperties.ExternalServiceConfig serviceConfig : serviceConfigs) {
            if (serviceConfig == null) {
                logger.warn("Skipping null external service configuration");
                failureCount++;
                continue;
            }
            
            if (!serviceConfig.isEnabled()) {
                logger.debug("Skipping disabled external service: {}", serviceConfig.getName());
                continue;
            }
            
            try {
                HealthChecker healthChecker = createSecureExternalServiceHealthChecker(
                    beanFactory, serviceConfig, conversionService);
                healthCheckers.add(healthChecker);
                successCount++;
                
                logger.info("Created secure external service health checker for '{}' targeting '{}'", 
                           serviceConfig.getName(), serviceConfig.getUrlBeanName());
                
            } catch (Exception e) {
                failureCount++;
                logger.error("Failed to create external service health checker for '{}': {}", 
                           serviceConfig.getName(), e.getMessage(), e);
                
                // In enterprise environments, decide whether to fail fast or continue
                // For now, we continue to allow partial functionality
            }
        }
        
        logger.info("Created {} external service health checkers (success: {}, failure: {})", 
                   healthCheckers.size(), successCount, failureCount);
        
        return Collections.unmodifiableList(healthCheckers);
    }
    
    /**
     * Creates a single secure external service health checker.
     */
    private HealthChecker createSecureExternalServiceHealthChecker(
            BeanFactory beanFactory,
            ValidatedHealthMonitoringProperties.ExternalServiceConfig config,
            ConversionService conversionService) {
        
        // Validate service configuration
        validateServiceConfig(config);
        
        // Resolve RestClient bean
        RestClient restClient = resolveRestClientBean(beanFactory, config);
        
        // Resolve and convert URL bean
        URI serviceUri = resolveServiceUri(beanFactory, config, conversionService);
        
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
            throw new IllegalArgumentException(
                "RestClient bean name cannot be null or empty for service: " + config.getName());
        }
        
        if (config.getUrlBeanName() == null || config.getUrlBeanName().trim().isEmpty()) {
            throw new IllegalArgumentException(
                "URL bean name cannot be null or empty for service: " + config.getName());
        }
        
        // Security: Validate bean name formats
        if (!config.getRestClientBeanName().matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException(
                "Invalid RestClient bean name format for service " + config.getName() + ": " + 
                config.getRestClientBeanName());
        }
        
        if (!config.getUrlBeanName().matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException(
                "Invalid URL bean name format for service " + config.getName() + ": " + 
                config.getUrlBeanName());
        }
    }
    
    /**
     * Resolves the RestClient bean for the service.
     */
    private RestClient resolveRestClientBean(BeanFactory beanFactory, 
                                           ValidatedHealthMonitoringProperties.ExternalServiceConfig config) {
        try {
            String beanName = config.getRestClientBeanName().trim();
            logger.debug("Resolving RestClient bean '{}' for service '{}'", beanName, config.getName());
            
            return beanFactory.getBean(beanName, RestClient.class);
            
        } catch (org.springframework.beans.factory.NoSuchBeanDefinitionException e) {
            throw new IllegalStateException(
                "RestClient bean '" + config.getRestClientBeanName() + 
                "' not found for external service '" + config.getName() + "'", e);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to resolve RestClient bean '" + config.getRestClientBeanName() + 
                "' for external service '" + config.getName() + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Resolves and converts the service URI from the configured bean.
     */
    private URI resolveServiceUri(BeanFactory beanFactory, 
                                ValidatedHealthMonitoringProperties.ExternalServiceConfig config,
                                ConversionService conversionService) {
        try {
            String beanName = config.getUrlBeanName().trim();
            logger.debug("Resolving URL bean '{}' for service '{}'", beanName, config.getName());
            
            Object urlBean = beanFactory.getBean(beanName);
            return convertToUriSecurely(urlBean, conversionService, config.getName());
            
        } catch (org.springframework.beans.factory.NoSuchBeanDefinitionException e) {
            throw new IllegalStateException(
                "URL bean '" + config.getUrlBeanName() + 
                "' not found for external service '" + config.getName() + "'", e);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to resolve URL bean '" + config.getUrlBeanName() + 
                "' for external service '" + config.getName() + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Converts various URL bean types to URI with security validation.
     */
    private URI convertToUriSecurely(Object urlBean, ConversionService conversionService, String serviceName) {
        if (urlBean == null) {
            throw new IllegalArgumentException("URL bean is null for service: " + serviceName);
        }
        
        try {
            URI uri = convertToUri(urlBean, conversionService);
            validateUri(uri, serviceName);
            return uri;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Failed to convert URL bean to URI for service '" + serviceName + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Converts URL bean to URI.
     */
    private URI convertToUri(Object urlBean, ConversionService conversionService) throws URISyntaxException {
        if (urlBean instanceof URI uri) {
            return uri;
        }
        
        if (urlBean instanceof String str) {
            return new URI(str);
        }
        
        if (urlBean instanceof Supplier<?> supplier) {
            Object supplied = supplier.get();
            if (supplied instanceof URI uri) {
                return uri;
            }
            if (supplied instanceof String str) {
                return new URI(str);
            }
            throw new IllegalArgumentException("Supplier returned unsupported type: " + 
                                             supplied.getClass().getName());
        }
        
        // Try Spring's conversion service
        if (conversionService.canConvert(urlBean.getClass(), URI.class)) {
            return conversionService.convert(urlBean, URI.class);
        }
        
        throw new IllegalArgumentException(
            "Cannot convert URL bean of type " + urlBean.getClass().getName() + " to URI. " +
            "Supported types: URI, String, Supplier<URI>, Supplier<String>");
    }
    
    /**
     * Security: Validates URI for security concerns.
     */
    private void validateUri(URI uri, String serviceName) {
        if (uri == null) {
            throw new IllegalArgumentException("URI is null for service: " + serviceName);
        }
        
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("URI scheme is null for service: " + serviceName);
        }
        
        // Security: Only allow HTTP and HTTPS
        if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
            throw new IllegalArgumentException(
                "Unsupported URI scheme '" + scheme + "' for service: " + serviceName + 
                ". Only HTTP and HTTPS are allowed.");
        }
        
        String host = uri.getHost();
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("URI host is null or empty for service: " + serviceName);
        }
        
        // Security: Prevent localhost/internal access in production (configurable)
        if (isLocalhost(host)) {
            logger.warn("External service '{}' is configured to use localhost/internal host: {}", 
                       serviceName, host);
        }
        
        int port = uri.getPort();
        if (port != -1 && (port < 1 || port > 65535)) {
            throw new IllegalArgumentException(
                "Invalid port " + port + " for service: " + serviceName);
        }
    }
    
    /**
     * Check if host is localhost or internal.
     */
    private boolean isLocalhost(String host) {
        if (host == null) {
            return false;
        }
        
        String lowerHost = host.toLowerCase().trim();
        return lowerHost.equals("localhost") || 
               lowerHost.equals("127.0.0.1") || 
               lowerHost.equals("::1") ||
               lowerHost.startsWith("192.168.") ||
               lowerHost.startsWith("10.") ||
               lowerHost.startsWith("172.16.") ||
               lowerHost.equals("0.0.0.0");
    }
}