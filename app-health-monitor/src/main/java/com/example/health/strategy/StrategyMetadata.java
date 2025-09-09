package com.example.health.strategy;

import java.time.Duration;
import java.util.Objects;

public final class StrategyMetadata {
    private final String name;
    private final String description;
    private final boolean supportsDegradation;
    private final Duration recommendedTimeout;
    private final String version;

    private StrategyMetadata(Builder builder) {
        this.name = Objects.requireNonNull(builder.name);
        this.description = Objects.requireNonNull(builder.description);
        this.supportsDegradation = builder.supportsDegradation;
        this.recommendedTimeout = Objects.requireNonNull(builder.recommendedTimeout);
        this.version = builder.version;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean supportsDegradation() { return supportsDegradation; }
    public Duration getRecommendedTimeout() { return recommendedTimeout; }
    public String getVersion() { return version; }

    public static final class Builder {
        private String name;
        private String description;
        private boolean supportsDegradation = false;
        private Duration recommendedTimeout = Duration.ofSeconds(5);
        private String version = "1.0";

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder supportsDegradation(boolean supportsDegradation) { this.supportsDegradation = supportsDegradation; return this; }
        public Builder recommendedTimeout(Duration recommendedTimeout) { this.recommendedTimeout = recommendedTimeout; return this; }
        public Builder version(String version) { this.version = version; return this; }

        public StrategyMetadata build() { return new StrategyMetadata(this); }
    }
}