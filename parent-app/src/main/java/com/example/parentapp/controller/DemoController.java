package com.example.parentapp.controller;

import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/demo")
public class DemoController {

    @Autowired(required = false)
    private MongoTemplate mongoTemplate;

    @Autowired(required = false)
    private AdminClient kafkaAdminClient;

    @Autowired
    private RestClient myRestClient;

    @Autowired
    private URI externalServiceUrl;

    @GetMapping("/mongo/ping")
    public Map<String, Object> mongoStatus() {
        try {
            if (mongoTemplate == null) {
                return Map.of("status", "MongoDB not configured", "available", false);
            }
            
            var result = mongoTemplate.getDb().runCommand(org.bson.Document.parse("{ping: 1}"));
            return Map.of(
                "status", "MongoDB connection successful",
                "available", true,
                "database", mongoTemplate.getDb().getName(),
                "ping", result.get("ok")
            );
        } catch (Exception e) {
            return Map.of(
                "status", "MongoDB connection failed: " + e.getMessage(),
                "available", false
            );
        }
    }

    @GetMapping("/kafka/info")
    public Map<String, Object> kafkaStatus() {
        try {
            if (kafkaAdminClient == null) {
                return Map.of("status", "Kafka not configured", "available", false);
            }
            
            var nodes = kafkaAdminClient.describeCluster().nodes().get();
            return Map.of(
                "status", "Kafka connection successful",
                "available", true,
                "nodeCount", nodes.size()
            );
        } catch (Exception e) {
            return Map.of(
                "status", "Kafka connection failed: " + e.getMessage(),
                "available", false
            );
        }
    }

    @GetMapping("/external/ping")
    public Map<String, Object> externalStatus() {
        try {
            var response = myRestClient.head()
                    .uri(externalServiceUrl)
                    .retrieve()
                    .toBodilessEntity();
            
            return Map.of(
                "status", "External service connection successful",
                "available", true,
                "httpStatus", response.getStatusCode().value(),
                "url", externalServiceUrl.toString()
            );
        } catch (Exception e) {
            return Map.of(
                "status", "External service connection failed: " + e.getMessage(),
                "available", false,
                "url", externalServiceUrl.toString()
            );
        }
    }
}