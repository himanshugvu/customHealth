package com.example.health.autoconfigure;

import com.example.health.config.AppHealthProperties;
import com.example.health.health.MongoHealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;

@AutoConfiguration
@ConditionalOnClass(MongoTemplate.class)
@ConditionalOnProperty(prefix = "app.health", name = "enabled", havingValue = "true")
public class MongoHealthIndicatorAutoConfiguration {

    @Bean
    @ConditionalOnBean(MongoTemplate.class)
    @ConditionalOnProperty(prefix = "app.health.mongodb", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MongoHealthIndicator mongoHealthIndicator(MongoTemplate mongoTemplate, AppHealthProperties properties) {
        return new MongoHealthIndicator(mongoTemplate, properties);
    }
}