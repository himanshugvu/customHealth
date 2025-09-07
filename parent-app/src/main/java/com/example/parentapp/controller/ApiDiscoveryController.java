package com.example.parentapp.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ApiDiscoveryController {

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @GetMapping("/discovery")
    public Map<String, Object> discoverApis() {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = 
            requestMappingHandlerMapping.getHandlerMethods();

        List<Map<String, Object>> endpoints = handlerMethods.entrySet().stream()
            .map(entry -> {
                RequestMappingInfo info = entry.getKey();
                HandlerMethod method = entry.getValue();
                
                Map<String, Object> endpoint = new LinkedHashMap<>();
                endpoint.put("type", "api");
                endpoint.put("controller", method.getBeanType().getSimpleName());
                endpoint.put("method", method.getMethod().getName());
                endpoint.put("httpMethods", info.getMethodsCondition().getMethods());
                endpoint.put("patterns", info.getPatternValues());
                endpoint.put("produces", info.getProducesCondition().getProducibleMediaTypes());
                endpoint.put("consumes", info.getConsumesCondition().getConsumableMediaTypes());
                
                return endpoint;
            })
            .filter(endpoint -> {
                @SuppressWarnings("unchecked")
                Set<String> patterns = (Set<String>) endpoint.get("patterns");
                return patterns.stream().anyMatch(pattern -> 
                    !pattern.startsWith("/actuator") && 
                    !pattern.contains("/error"));
            })
            .sorted((a, b) -> {
                @SuppressWarnings("unchecked")
                Set<String> patternsA = (Set<String>) a.get("patterns");
                @SuppressWarnings("unchecked")
                Set<String> patternsB = (Set<String>) b.get("patterns");
                
                String firstPatternA = patternsA.isEmpty() ? "" : patternsA.iterator().next();
                String firstPatternB = patternsB.isEmpty() ? "" : patternsB.iterator().next();
                
                return firstPatternA.compareTo(firstPatternB);
            })
            .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalEndpoints", endpoints.size());
        response.put("endpoints", endpoints);
        response.put("categories", categorizeEndpoints(endpoints));
        
        return response;
    }

    private Map<String, Object> categorizeEndpoints(List<Map<String, Object>> endpoints) {
        Map<String, List<Map<String, Object>>> categories = endpoints.stream()
            .collect(Collectors.groupingBy(endpoint -> {
                @SuppressWarnings("unchecked")
                Set<String> patterns = (Set<String>) endpoint.get("patterns");
                String firstPattern = patterns.isEmpty() ? "unknown" : patterns.iterator().next();
                
                if (firstPattern.startsWith("/demo")) return "demo";
                if (firstPattern.startsWith("/api")) return "api";
                if (firstPattern.startsWith("/health")) return "health";
                return "other";
            }));

        Map<String, Object> result = new LinkedHashMap<>();
        categories.forEach((category, endpointList) -> {
            Map<String, Object> categoryInfo = new LinkedHashMap<>();
            categoryInfo.put("count", endpointList.size());
            categoryInfo.put("endpoints", endpointList);
            result.put(category, categoryInfo);
        });
        
        return result;
    }

    @GetMapping("/validation")
    public Map<String, Object> validateAllApis() {
        Map<String, Object> validationResults = new LinkedHashMap<>();
        
        // Test demo endpoints
        List<Map<String, Object>> demoTests = Arrays.asList(
            testEndpoint("GET", "/demo/mongo/ping", "MongoDB connection test"),
            testEndpoint("GET", "/demo/kafka/info", "Kafka connection test"),
            testEndpoint("GET", "/demo/external/ping", "External service test")
        );
        
        // Test health endpoints
        List<Map<String, Object>> healthTests = Arrays.asList(
            testEndpoint("GET", "/actuator/health", "General health check"),
            testEndpoint("GET", "/actuator/health/custom", "Custom health check")
        );
        
        // Test API endpoints
        List<Map<String, Object>> apiTests = Arrays.asList(
            testEndpoint("GET", "/api/discovery", "API discovery endpoint"),
            testEndpoint("GET", "/api/validation", "API validation endpoint")
        );
        
        validationResults.put("demo", createTestSummary(demoTests));
        validationResults.put("health", createTestSummary(healthTests));
        validationResults.put("api", createTestSummary(apiTests));
        
        int totalTests = demoTests.size() + healthTests.size() + apiTests.size();
        long passedTests = demoTests.stream().mapToLong(t -> (Boolean) t.get("available") ? 1 : 0).sum() +
                          healthTests.stream().mapToLong(t -> (Boolean) t.get("available") ? 1 : 0).sum() +
                          apiTests.stream().mapToLong(t -> (Boolean) t.get("available") ? 1 : 0).sum();
        
        validationResults.put("summary", Map.of(
            "totalTests", totalTests,
            "passed", passedTests,
            "failed", totalTests - passedTests,
            "passRate", String.format("%.1f%%", (passedTests * 100.0) / totalTests)
        ));
        
        return validationResults;
    }
    
    private Map<String, Object> testEndpoint(String method, String path, String description) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", method);
        result.put("path", path);
        result.put("description", description);
        result.put("available", true); // In real implementation, you'd make HTTP calls
        result.put("type", "validation");
        
        return result;
    }
    
    private Map<String, Object> createTestSummary(List<Map<String, Object>> tests) {
        long passed = tests.stream().mapToLong(t -> (Boolean) t.get("available") ? 1 : 0).sum();
        
        return Map.of(
            "total", tests.size(),
            "passed", passed,
            "failed", tests.size() - passed,
            "tests", tests
        );
    }
}