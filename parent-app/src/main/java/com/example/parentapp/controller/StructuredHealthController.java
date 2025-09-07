package com.example.parentapp.controller;

import com.example.parentapp.dto.HealthCheckResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/health")
public class StructuredHealthController {

    @Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("custom")
    private HealthContributor customHealthContributor;

    @GetMapping("/structured")
    public Map<String, Object> getStructuredHealth() {
        Map<String, Object> response = new LinkedHashMap<>();
        List<HealthCheckResult> results = new ArrayList<>();
        
        if (customHealthContributor instanceof CompositeHealthContributor composite) {
            composite.iterator().forEachRemaining(entry -> {
                String name = entry.getName();
                HealthContributor contributor = entry.getContributor();
                
                if (contributor instanceof HealthIndicator healthIndicator) {
                    HealthCheckResult result = convertToHealthCheckResult(name, healthIndicator.health());
                    results.add(result);
                } else if (contributor instanceof CompositeHealthContributor subComposite) {
                    subComposite.iterator().forEachRemaining(subEntry -> {
                        String subName = subEntry.getName();
                        HealthContributor subContributor = subEntry.getContributor();
                        if (subContributor instanceof HealthIndicator subHealthIndicator) {
                            HealthCheckResult result = convertToHealthCheckResult(
                                name + "." + subName, 
                                subHealthIndicator.health()
                            );
                            results.add(result);
                        }
                    });
                }
            });
        }
        
        // Calculate overall status
        boolean allUp = results.stream().allMatch(r -> "UP".equals(r.getStatus()));
        String overallStatus = allUp ? "UP" : "DOWN";
        
        // Group results by type
        Map<String, List<HealthCheckResult>> groupedResults = new LinkedHashMap<>();
        results.forEach(result -> {
            String type = result.getType();
            groupedResults.computeIfAbsent(type, k -> new ArrayList<>()).add(result);
        });
        
        response.put("status", overallStatus);
        response.put("totalChecks", results.size());
        response.put("upChecks", results.stream().mapToInt(r -> "UP".equals(r.getStatus()) ? 1 : 0).sum());
        response.put("downChecks", results.stream().mapToInt(r -> "DOWN".equals(r.getStatus()) ? 1 : 0).sum());
        response.put("checks", results);
        response.put("groupedByType", groupedResults);
        
        return response;
    }
    
    private HealthCheckResult convertToHealthCheckResult(String name, Health health) {
        Map<String, Object> details = health.getDetails();
        
        HealthCheckResult result = new HealthCheckResult();
        result.setStatus(health.getStatus().getCode());
        result.setComponent((String) details.get("component"));
        result.setType((String) details.get("type"));
        
        if (details.get("latencyMs") instanceof Number latency) {
            result.setLatencyMs(latency.longValue());
        }
        
        // Database specific
        result.setVersion((String) details.get("version"));
        result.setValidationQuery((String) details.get("validationQuery"));
        result.setDatabase((String) details.get("database"));
        result.setOperation((String) details.get("operation"));
        result.setPingResult(details.get("pingResult"));
        
        // Kafka specific
        if (details.get("nodeCount") instanceof Number nodeCount) {
            result.setNodeCount(nodeCount.intValue());
        }
        
        // External service specific
        result.setServiceName((String) details.get("serviceName"));
        result.setEndpoint((String) details.get("endpoint"));
        result.setRoute((String) details.get("route"));
        result.setHost((String) details.get("host"));
        if (details.get("port") instanceof Number port) {
            result.setPort(port.intValue());
        }
        if (details.get("status") instanceof Number status) {
            result.setHttpStatus(status.intValue());
        }
        
        // Error handling
        result.setError((String) details.get("error"));
        
        return result;
    }
}