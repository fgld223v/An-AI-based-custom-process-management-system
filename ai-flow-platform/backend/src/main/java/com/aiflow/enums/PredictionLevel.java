package com.aiflow.enums;

public enum PredictionLevel {
    HIGH_PROB_TIMEOUT("high_prob_timeout"),
    POSSIBLE_TIMEOUT("possible_timeout"),
    NONE("none");

    private final String value;

    PredictionLevel(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static PredictionLevel fromValue(String value) {
        for (PredictionLevel level : values()) {
            if (level.value.equals(value)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown PredictionLevel value: " + value);
    }
}
