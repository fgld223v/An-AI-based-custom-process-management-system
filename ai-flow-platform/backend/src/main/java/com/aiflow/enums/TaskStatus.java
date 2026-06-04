package com.aiflow.enums;

public enum TaskStatus {
    PENDING("pending"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    DELEGATED("delegated"),
    TIMEOUT("timeout");

    private final String value;

    TaskStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static TaskStatus fromValue(String value) {
        for (TaskStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown TaskStatus value: " + value);
    }
}
