package com.example.health.autoconfigure;

import com.example.health.config.AppHealthProperties;
import com.example.health.health.KafkaHealthIndicator;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.apache.kafka.clients.admin.AdminClient;

@AutoConfiguration
@ConditionalOnClass(AdminClient.class)
@ConditionalOnProperty(prefix = "app.health", name = "enabled", havingValue = "true")
public class KafkaHealthIndicatorAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.health.kafka", name = "enabled", havingValue = "true")
    public KafkaHealthIndicator kafkaHealthIndicator(BeanFactory beanFactory, AppHealthProperties properties) {
        return new KafkaHealthIndicator(beanFactory, properties);
    }
}