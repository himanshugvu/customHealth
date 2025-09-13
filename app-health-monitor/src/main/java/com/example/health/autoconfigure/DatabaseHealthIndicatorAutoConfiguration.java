package com.example.health.autoconfigure;

import com.example.health.config.AppHealthProperties;
import com.example.health.health.DatabaseHealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@AutoConfiguration
@ConditionalOnClass(DataSource.class)
@ConditionalOnProperty(prefix = "app.health", name = "enabled", havingValue = "true")
public class DatabaseHealthIndicatorAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "app.health.db", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DatabaseHealthIndicator databaseHealthIndicator(DataSource dataSource, AppHealthProperties properties) {
        return new DatabaseHealthIndicator(dataSource, properties);
    }
}