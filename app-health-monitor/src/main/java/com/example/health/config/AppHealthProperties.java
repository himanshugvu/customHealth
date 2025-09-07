package com.example.health.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.health")
public class AppHealthProperties {
    
    private boolean enabled = false;
    private boolean startupLog = true;
    private DatabaseConfig db = new DatabaseConfig();
    private KafkaConfig kafka = new KafkaConfig();
    private ExternalConfig external = new ExternalConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isStartupLog() {
        return startupLog;
    }

    public void setStartupLog(boolean startupLog) {
        this.startupLog = startupLog;
    }

    public Boolean getStartupLog() {
        return startupLog;
    }

    public DatabaseConfig getDb() {
        return db;
    }

    public void setDb(DatabaseConfig db) {
        this.db = db;
    }

    public KafkaConfig getKafka() {
        return kafka;
    }

    public void setKafka(KafkaConfig kafka) {
        this.kafka = kafka;
    }

    public ExternalConfig getExternal() {
        return external;
    }

    public void setExternal(ExternalConfig external) {
        this.external = external;
    }

    public static class DatabaseConfig {
        private boolean enabled = true;
        private String validationQuery = "SELECT 1";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getValidationQuery() {
            return validationQuery;
        }

        public void setValidationQuery(String validationQuery) {
            this.validationQuery = validationQuery;
        }
    }

    public static class KafkaConfig {
        private boolean enabled = false;
        private String adminClientBean;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAdminClientBean() {
            return adminClientBean;
        }

        public void setAdminClientBean(String adminClientBean) {
            this.adminClientBean = adminClientBean;
        }
    }

    public static class ExternalConfig {
        private List<ServiceConfig> services = new ArrayList<>();

        public List<ServiceConfig> getServices() {
            return services;
        }

        public void setServices(List<ServiceConfig> services) {
            this.services = services;
        }
    }

    public static class ServiceConfig {
        private String name;
        private boolean enabled = true;
        private String restClientBean;
        private String urlBean;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRestClientBean() {
            return restClientBean;
        }

        public void setRestClientBean(String restClientBean) {
            this.restClientBean = restClientBean;
        }

        public String getUrlBean() {
            return urlBean;
        }

        public void setUrlBean(String urlBean) {
            this.urlBean = urlBean;
        }
    }
}