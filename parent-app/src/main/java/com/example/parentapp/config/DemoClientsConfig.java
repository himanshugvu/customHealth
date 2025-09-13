package com.example.parentapp.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
// import org.apache.kafka.clients.admin.AdminClient; // Commented out for testing without Kafka
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Map;

@Configuration
public class DemoClientsConfig {

    @Bean(name = "externalServiceUrl")
    public URI externalServiceUrl(@Value("${external.service.url:http://localhost:8090/status/200}") String url) {
        return URI.create(url);
    }

    @Bean(name = "myRestClient")
    public RestClient restClient(CloseableHttpClient httpClient) {
        return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }

    @Bean
    public HttpClientConnectionManager connectionManager() {
        return PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(100)
                .setMaxConnPerRoute(20)
                .build();
    }

    @Bean
    public CloseableHttpClient httpClient(HttpClientConnectionManager connectionManager) {
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .evictExpiredConnections()
                .build();
    }

    // Kafka AdminClient bean commented out to test library without Kafka dependencies
    /*
    @Bean(name = "kafkaAdminClient")
    @ConditionalOnProperty(name = "app.health.kafka.enabled", havingValue = "true")
    public AdminClient adminClient(@Value("${kafka.bootstrap.servers:localhost:9092}") String bootstrapServers) {
        return AdminClient.create(Map.of("bootstrap.servers", bootstrapServers));
    }
    */
}