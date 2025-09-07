package com.example.parentapp.dto;

public class HealthCheckResult {
    private String type;
    private String status;
    private Long latencyMs;
    private String component;
    
    // Database specific
    private String version;
    private String validationQuery;
    private String database;
    private String operation;
    private Object pingResult;
    
    // Kafka specific  
    private Integer nodeCount;
    
    // External service specific
    private String serviceName;
    private String endpoint;
    private String route;
    private String host;
    private Integer port;
    private Integer httpStatus;
    
    // Error handling
    private String error;

    public HealthCheckResult() {}

    public HealthCheckResult(String type, String status, Long latencyMs, String component) {
        this.type = type;
        this.status = status;
        this.latencyMs = latencyMs;
        this.component = component;
    }

    // Getters and Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }

    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getValidationQuery() { return validationQuery; }
    public void setValidationQuery(String validationQuery) { this.validationQuery = validationQuery; }

    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public Object getPingResult() { return pingResult; }
    public void setPingResult(Object pingResult) { this.pingResult = pingResult; }

    public Integer getNodeCount() { return nodeCount; }
    public void setNodeCount(Integer nodeCount) { this.nodeCount = nodeCount; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}