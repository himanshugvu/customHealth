# Kafka Configuration for Health Monitoring

## Problem
When Kafka is not available in your environment, the health monitoring library should not interfere with your application startup or cause connection errors.

## Solution
The health monitoring library supports flexible Kafka configuration that can be easily enabled/disabled.

## Configuration Options

### Option 1: Disable Kafka Health Check (Recommended for non-Kafka apps)

In your `app-health.yml` or `application.yml`:

```yaml
app:
  health:
    kafka:
      enabled: false  # Disable Kafka health monitoring
```

### Option 2: Enable Kafka Health Check (Only when Kafka is available)

```yaml
app:
  health:
    kafka:
      enabled: true
      adminClientBean: kafkaAdminClient  # Bean name for Kafka AdminClient
```

## Environment-Specific Configuration

### Development Environment (No Kafka)
```yaml
app:
  health:
    enabled: true
    db:
      enabled: true
    mongodb:
      enabled: true
    kafka:
      enabled: false  # Disable in dev
    external:
      services:
        - name: httpbin
          enabled: true
          restClientBean: myRestClient
          urlBean: externalServiceUrl
```

### Production Environment (With Kafka)
```yaml
app:
  health:
    enabled: true
    db:
      enabled: true
    mongodb:
      enabled: true
    kafka:
      enabled: true   # Enable in production
      adminClientBean: kafkaAdminClient
    external:
      services:
        - name: paymentService
          enabled: true
          restClientBean: paymentRestClient
          urlBean: paymentServiceUrl
```

## Bean Configuration

The library uses conditional bean creation. The Kafka AdminClient bean is only created when needed:

```java
@Bean(name = "kafkaAdminClient")
@ConditionalOnProperty(name = "app.health.kafka.enabled", havingValue = "true")
public AdminClient adminClient(@Value("${kafka.bootstrap.servers}") String bootstrapServers) {
    return AdminClient.create(Map.of("bootstrap.servers", bootstrapServers));
}
```

## Profile-Based Configuration

You can use Spring profiles to manage different environments:

### application-dev.yml (Development)
```yaml
app:
  health:
    kafka:
      enabled: false
```

### application-prod.yml (Production)
```yaml
app:
  health:
    kafka:
      enabled: true
      adminClientBean: kafkaAdminClient
kafka:
  bootstrap:
    servers: ${KAFKA_BOOTSTRAP_SERVERS:kafka-cluster:9092}
```

## Environment Variables Override

You can override configuration using environment variables:

```bash
# Disable Kafka health check
export APP_HEALTH_KAFKA_ENABLED=false

# Enable Kafka health check with custom servers
export APP_HEALTH_KAFKA_ENABLED=true
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

## Verification

After disabling Kafka, verify that:

1. **No Kafka Connection Errors**: Your application logs should not show Kafka connection attempts
2. **Fast Startup**: Application startup should be faster without Kafka connection delays  
3. **Clean Health Response**: The `/actuator/health/custom` endpoint should only show enabled services
4. **No AdminClient Bean**: The `kafkaAdminClient` bean should not be created

## Example Usage

### For Applications WITHOUT Kafka:
```yaml
app:
  health:
    enabled: true
    kafka:
      enabled: false  # This is the key setting
    db:
      enabled: true
    external:
      services:
        - name: userService
          enabled: true
          restClientBean: userRestClient
          urlBean: userServiceUrl
```

### For Applications WITH Kafka:
```yaml
app:
  health:
    enabled: true
    kafka:
      enabled: true   # Enable Kafka monitoring
      adminClientBean: kafkaAdminClient
    db:
      enabled: true
    external:
      services:
        - name: userService
          enabled: true
          restClientBean: userRestClient
          urlBean: userServiceUrl

kafka:
  bootstrap:
    servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
```

## Summary

- Set `app.health.kafka.enabled: false` to completely disable Kafka health monitoring
- The library automatically handles conditional bean creation
- No Kafka dependencies are loaded when disabled
- Application startup is not affected by Kafka unavailability when disabled
- You can easily switch between environments using Spring profiles or environment variables