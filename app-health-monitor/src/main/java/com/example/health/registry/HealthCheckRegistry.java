package com.example.health.registry;

import com.example.health.core.HealthChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Enterprise Health Check Registry.
 * 
 * This registry provides centralized management of all health checkers in the application,
 * supporting both individual health checker beans and collections of health checkers.
 * 
 * Features:
 * - Automatic discovery of all HealthChecker beans
 * - Support for both single beans and List<HealthChecker> beans
 * - Dynamic registration and deregistration
 * - Thread-safe operations with concurrent access
 * - Ordering support via @Order annotations
 * - Health checker categorization by type and component
 * - Registry statistics and monitoring
 * - Graceful error handling and validation
 * 
 * Usage patterns supported:
 * 1. Single health checker beans: @Bean HealthChecker mongoHealthChecker()
 * 2. Collection beans: @Bean List<HealthChecker> externalServiceHealthCheckers()
 * 3. Programmatic registration: registry.register(healthChecker)
 * 4. Category-based retrieval: registry.getHealthCheckersByType("database")
 * 
 * @author Health Monitoring Library
 * @since 1.0.0
 */
@Component
public class HealthCheckRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckRegistry.class);
    
    // Thread-safe storage for health checkers
    private final Map<String, HealthChecker> healthCheckers = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> healthCheckersByType = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> healthCheckersByComponent = new ConcurrentHashMap<>();
    
    // Registry metadata
    private volatile boolean initialized = false;
    private volatile int totalRegistrations = 0;
    private volatile long lastUpdateTimestamp = System.currentTimeMillis();
    
    /**
     * Initializes the registry for direct registration mode.
     * Auto-configurations will register their health checkers directly.
     */
    public void initializeForDirectRegistration() {
        
        if (initialized) {
            logger.warn("Health check registry is already initialized");
            return;
        }
        
        logger.info("Initializing health check registry for direct registration mode...");
        
        try {
            initialized = true;
            lastUpdateTimestamp = System.currentTimeMillis();
            
            logger.info("Health check registry ready for direct registrations");
            
        } catch (Exception e) {
            logger.error("Failed to initialize health check registry", e);
            throw new IllegalStateException("Health check registry initialization failed", e);
        }
    }
    
    /**
     * Finalizes the registry after all auto-configurations have registered their beans.
     */
    public void finalizeRegistrations() {
        if (!initialized) {
            logger.warn("Registry not initialized, cannot finalize");
            return;
        }
        
        logger.info("Finalizing health check registry...");
        
        try {
            // Sort all health checkers by order
            sortHealthCheckersByOrder();
            
            lastUpdateTimestamp = System.currentTimeMillis();
            
            logger.info("Health check registry finalized with {} health checkers", 
                       healthCheckers.size());
            logRegistryStatistics();
            
        } catch (Exception e) {
            logger.error("Failed to finalize health check registry", e);
            throw new IllegalStateException("Health check registry finalization failed", e);
        }
    }
    
    /**
     * Registers individual health checker beans.
     */
    private void registerSingleHealthCheckers(ObjectProvider<HealthChecker> singleHealthCheckers) {
        singleHealthCheckers.orderedStream().forEach(healthChecker -> {
            try {
                register(healthChecker);
                logger.debug("Registered single health checker: {} (type: {})", 
                           healthChecker.getComponentName(), healthChecker.getComponentType());
            } catch (Exception e) {
                logger.error("Failed to register health checker: {}", healthChecker.getComponentName(), e);
            }
        });
    }
    
    /**
     * Registers health checker collections (List<HealthChecker> beans).
     */
    private void registerHealthCheckerCollections(ObjectProvider<List<HealthChecker>> healthCheckerCollections) {
        healthCheckerCollections.orderedStream().forEach(healthCheckerList -> {
            if (healthCheckerList != null) {
                healthCheckerList.forEach(healthChecker -> {
                    try {
                        register(healthChecker);
                        logger.debug("Registered collection health checker: {} (type: {})", 
                                   healthChecker.getComponentName(), healthChecker.getComponentType());
                    } catch (Exception e) {
                        logger.error("Failed to register collection health checker: {}", 
                                   healthChecker.getComponentName(), e);
                    }
                });
            }
        });
    }
    
    /**
     * Registers a single health checker.
     */
    public void register(HealthChecker healthChecker) {
        if (healthChecker == null) {
            throw new IllegalArgumentException("Health checker cannot be null");
        }
        
        String componentName = healthChecker.getComponentName();
        String componentType = healthChecker.getComponentType();
        
        if (componentName == null || componentName.trim().isEmpty()) {
            throw new IllegalArgumentException("Health checker component name cannot be null or empty");
        }
        
        // Check for duplicates
        if (healthCheckers.containsKey(componentName)) {
            logger.warn("Health checker with name '{}' already exists, replacing it", componentName);
        }
        
        // Register the health checker
        healthCheckers.put(componentName, healthChecker);
        totalRegistrations++;
        lastUpdateTimestamp = System.currentTimeMillis();
        
        // Update type categorization
        if (componentType != null && !componentType.trim().isEmpty()) {
            healthCheckersByType.computeIfAbsent(componentType, k -> ConcurrentHashMap.newKeySet())
                               .add(componentName);
        }
        
        // Update component categorization  
        healthCheckersByComponent.computeIfAbsent(componentName, k -> ConcurrentHashMap.newKeySet())
                                 .add(componentName);
        
        logger.debug("Registered health checker: {} (type: {}, total: {})", 
                    componentName, componentType, healthCheckers.size());
    }
    
    /**
     * Deregisters a health checker by component name.
     */
    public boolean deregister(String componentName) {
        if (componentName == null || componentName.trim().isEmpty()) {
            return false;
        }
        
        HealthChecker removed = healthCheckers.remove(componentName);
        if (removed != null) {
            lastUpdateTimestamp = System.currentTimeMillis();
            
            // Clean up categorizations
            String componentType = removed.getComponentType();
            if (componentType != null) {
                Set<String> typeSet = healthCheckersByType.get(componentType);
                if (typeSet != null) {
                    typeSet.remove(componentName);
                    if (typeSet.isEmpty()) {
                        healthCheckersByType.remove(componentType);
                    }
                }
            }
            
            healthCheckersByComponent.remove(componentName);
            
            logger.debug("Deregistered health checker: {} (remaining: {})", 
                        componentName, healthCheckers.size());
            return true;
        }
        
        return false;
    }
    
    /**
     * Gets all registered health checkers as an ordered list.
     */
    public List<HealthChecker> getAllHealthCheckers() {
        return healthCheckers.values().stream()
                .sorted(AnnotationAwareOrderComparator.INSTANCE)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets health checkers by component type.
     */
    public List<HealthChecker> getHealthCheckersByType(String componentType) {
        if (componentType == null || componentType.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        Set<String> componentNames = healthCheckersByType.get(componentType);
        if (componentNames == null || componentNames.isEmpty()) {
            return Collections.emptyList();
        }
        
        return componentNames.stream()
                .map(healthCheckers::get)
                .filter(Objects::nonNull)
                .sorted(AnnotationAwareOrderComparator.INSTANCE)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets a specific health checker by component name.
     */
    public Optional<HealthChecker> getHealthChecker(String componentName) {
        if (componentName == null || componentName.trim().isEmpty()) {
            return Optional.empty();
        }
        
        return Optional.ofNullable(healthCheckers.get(componentName));
    }
    
    /**
     * Gets all registered component types.
     */
    public Set<String> getRegisteredTypes() {
        return Collections.unmodifiableSet(healthCheckersByType.keySet());
    }
    
    /**
     * Gets all registered component names.
     */
    public Set<String> getRegisteredComponents() {
        return Collections.unmodifiableSet(healthCheckers.keySet());
    }
    
    /**
     * Checks if a health checker is registered.
     */
    public boolean isRegistered(String componentName) {
        return componentName != null && healthCheckers.containsKey(componentName);
    }
    
    /**
     * Gets the total number of registered health checkers.
     */
    public int getHealthCheckerCount() {
        return healthCheckers.size();
    }
    
    /**
     * Gets the total number of registered health checkers by type.
     */
    public int getHealthCheckerCountByType(String componentType) {
        Set<String> componentNames = healthCheckersByType.get(componentType);
        return componentNames != null ? componentNames.size() : 0;
    }
    
    /**
     * Gets registry statistics.
     */
    public RegistryStatistics getStatistics() {
        return new RegistryStatistics(
            healthCheckers.size(),
            totalRegistrations,
            healthCheckersByType.size(),
            initialized,
            lastUpdateTimestamp
        );
    }
    
    /**
     * Validates the registry state.
     */
    public boolean validateRegistry() {
        try {
            // Check for null health checkers
            long nullCount = healthCheckers.values().stream()
                    .mapToLong(hc -> hc == null ? 1 : 0)
                    .sum();
            
            if (nullCount > 0) {
                logger.error("Registry contains {} null health checkers", nullCount);
                return false;
            }
            
            // Check for health checkers with invalid names
            long invalidNameCount = healthCheckers.entrySet().stream()
                    .mapToLong(entry -> {
                        String key = entry.getKey();
                        HealthChecker value = entry.getValue();
                        return (key == null || key.trim().isEmpty() || 
                               value == null || 
                               !key.equals(value.getComponentName())) ? 1 : 0;
                    })
                    .sum();
            
            if (invalidNameCount > 0) {
                logger.error("Registry contains {} health checkers with invalid names", invalidNameCount);
                return false;
            }
            
            logger.debug("Registry validation passed: {} health checkers", healthCheckers.size());
            return true;
            
        } catch (Exception e) {
            logger.error("Registry validation failed", e);
            return false;
        }
    }
    
    /**
     * Sorts health checkers by their @Order annotation or Ordered interface.
     */
    private void sortHealthCheckersByOrder() {
        // Create a new sorted map to maintain order
        Map<String, HealthChecker> sortedHealthCheckers = new LinkedHashMap<>();
        healthCheckers.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(AnnotationAwareOrderComparator.INSTANCE))
                .forEachOrdered(entry -> sortedHealthCheckers.put(entry.getKey(), entry.getValue()));
        
        // Replace the existing map
        healthCheckers.clear();
        healthCheckers.putAll(sortedHealthCheckers);
    }
    
    /**
     * Logs registry statistics.
     */
    private void logRegistryStatistics() {
        RegistryStatistics stats = getStatistics();
        
        logger.info("=== Health Check Registry Statistics ===");
        logger.info("Total health checkers: {}", stats.totalHealthCheckers());
        logger.info("Total registrations: {}", stats.totalRegistrations());
        logger.info("Component types: {}", stats.componentTypes());
        logger.info("Initialized: {}", stats.initialized());
        logger.info("Last update: {}", new Date(stats.lastUpdateTimestamp()));
        
        // Log health checkers by type
        healthCheckersByType.forEach((type, components) -> 
            logger.info("Type '{}': {} health checkers", type, components.size()));
        
        logger.info("==========================================");
    }
    
    /**
     * Clears all registered health checkers.
     * WARNING: This should only be used for testing or shutdown scenarios.
     */
    public void clear() {
        logger.warn("Clearing all health checkers from registry");
        healthCheckers.clear();
        healthCheckersByType.clear();
        healthCheckersByComponent.clear();
        initialized = false;
        lastUpdateTimestamp = System.currentTimeMillis();
    }
    
    /**
     * Registry statistics record.
     */
    public record RegistryStatistics(
        int totalHealthCheckers,
        int totalRegistrations,
        int componentTypes,
        boolean initialized,
        long lastUpdateTimestamp
    ) {}
}