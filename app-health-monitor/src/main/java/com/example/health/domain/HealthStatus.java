package com.example.health.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum HealthStatus {
    UP("UP", 100),
    DEGRADED("DEGRADED", 50),  // New intermediate state
    DOWN("DOWN", 0),
    UNKNOWN("UNKNOWN", -1);

    private final String code;
    private final int severity;

    HealthStatus(String code, int severity) {
        this.code = code;
        this.severity = severity;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public int getSeverity() {
        return severity;
    }

    @JsonCreator
    public static HealthStatus fromCode(String code) {
        for (HealthStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown health status code: " + code);
    }

    public boolean isHealthy() {
        return this == UP;
    }

    public boolean isDegraded() {
        return this == DEGRADED;
    }

    public boolean isUnhealthy() {
        return this == DOWN || this == UNKNOWN;
    }

    public boolean isWorseOrEqualTo(HealthStatus other) {
        return this.severity <= other.severity;
    }

    public boolean isBetterThan(HealthStatus other) {
        return this.severity > other.severity;
    }

    public static HealthStatus worst(HealthStatus... statuses) {
        HealthStatus worst = UP;
        for (HealthStatus status : statuses) {
            if (status.isWorseOrEqualTo(worst)) {
                worst = status;
            }
        }
        return worst;
    }

    @Override
    public String toString() {
        return code;
    }
}