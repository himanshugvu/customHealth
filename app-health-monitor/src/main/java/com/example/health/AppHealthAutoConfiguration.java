package com.example.health;

import com.example.health.config.AppHealthProperties;
import com.example.health.health.DatabaseHealthIndicator;
import com.example.health.health.ExternalServiceHealthIndicator;
import com.example.health.health.KafkaHealthIndicator;
import com.example.health.health.MongoHealthIndicator;
import com.example.health.health.ComprehensiveHealthIndicator;
import com.example.health.util.LatencyLogger;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import org.springframework.web.client.RestClient;
import org.springframework.data.mongodb.core.MongoTemplate;

import javax.sql.DataSource;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@AutoConfiguration
@EnableConfigurationProperties(AppHealthProperties.class)
@ConditionalOnProperty(prefix = "app.health", name = "enabled", havingValue = "true")
public class AppHealthAutoConfiguration {

    @Bean(name = "internalComposite")
    public CompositeHealthContributor internalHealthContributor(
            ObjectProvider<DatabaseHealthIndicator> dbIndicator,
            ObjectProvider<MongoHealthIndicator> mongoIndicator,
            ObjectProvider<KafkaHealthIndicator> kafkaIndicator,
            java.util.List<ExternalServiceHealthIndicator> externalIndicators) {
        
        Map<String, HealthContributor> components = new LinkedHashMap<>();
        
        dbIndicator.ifAvailable(indicator -> components.put("database", indicator));
        mongoIndicator.ifAvailable(indicator -> components.put("mongodb", indicator));
        kafkaIndicator.ifAvailable(indicator -> components.put("kafka", indicator));
        
        Map<String, HealthIndicator> externalMap = new LinkedHashMap<>();
        externalIndicators.forEach(indicator -> 
            externalMap.put(indicator.getName(), indicator)
        );
        
        if (!externalMap.isEmpty()) {
            components.put("external", CompositeHealthContributor.fromMap(externalMap));
        }
        
        return CompositeHealthContributor.fromMap(components);
    }

    @Bean(name = "custom")
    public HealthContributor customHealthContributor(
            @org.springframework.beans.factory.annotation.Qualifier("internalComposite") 
            CompositeHealthContributor internalComposite) {
        return new ComprehensiveHealthIndicator(internalComposite);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.health.db", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean(DataSource.class)
    public DatabaseHealthIndicator databaseHealthIndicator(DataSource dataSource, AppHealthProperties properties) {
        return new DatabaseHealthIndicator(dataSource, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.health.mongodb", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean(MongoTemplate.class)
    public MongoHealthIndicator mongoHealthIndicator(MongoTemplate mongoTemplate, AppHealthProperties properties) {
        return new MongoHealthIndicator(mongoTemplate, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.health.kafka", name = "enabled", havingValue = "true")
    public KafkaHealthIndicator kafkaHealthIndicator(BeanFactory beanFactory, AppHealthProperties properties) {
        return new KafkaHealthIndicator(beanFactory, properties);
    }

    @Bean
    public java.util.List<ExternalServiceHealthIndicator> externalServiceHealthIndicators(
            BeanFactory beanFactory, 
            AppHealthProperties properties, 
            ConversionService conversionService) {
        
        return properties.getExternal().getServices().stream()
            .filter(AppHealthProperties.ServiceConfig::isEnabled)
            .map(serviceConfig -> {
                try {
                    RestClient restClient = beanFactory.getBean(serviceConfig.getRestClientBean(), RestClient.class);
                    Object urlBean = beanFactory.getBean(serviceConfig.getUrlBean());
                    URI uri = convertToUri(urlBean, conversionService);
                    return new ExternalServiceHealthIndicator(serviceConfig.getName(), restClient, uri);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create external service health indicator for " + serviceConfig.getName(), e);
                }
            })
            .toList();
    }

    @Bean
    public LatencyLogger latencyLogger() {
        return new LatencyLogger();
    }

    @Bean
    public ApplicationRunner startupHealthLogger(LatencyLogger logger, 
                                                @org.springframework.beans.factory.annotation.Qualifier("internalComposite") CompositeHealthContributor internalComposite,
                                                AppHealthProperties properties) {
        return args -> {
            if (Boolean.TRUE.equals(properties.getStartupLog())) {
                // Execute health logging asynchronously to not slow down application startup
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        logger.logOnce(internalComposite);
                    } catch (Exception e) {
                        org.slf4j.LoggerFactory.getLogger(AppHealthAutoConfiguration.class)
                            .warn("Failed to execute startup health logging", e);
                    }
                });
            }
        };
    }

    private URI convertToUri(Object urlBean, ConversionService conversionService) {
        if (urlBean instanceof URI uri) {
            return uri;
        } else if (urlBean instanceof String str) {
            return URI.create(str);
        } else if (urlBean instanceof Supplier<?> supplier) {
            Object supplied = supplier.get();
            if (supplied instanceof URI uri) {
                return uri;
            } else if (supplied instanceof String str) {
                return URI.create(str);
            }
        }
        
        if (conversionService.canConvert(urlBean.getClass(), URI.class)) {
            return conversionService.convert(urlBean, URI.class);
        }
        
        throw new IllegalArgumentException("Cannot convert " + urlBean.getClass() + " to URI");
    }
}