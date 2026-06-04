package com.aiflow.enums;

public enum InstanceStatus {
    RUNNING("running"),
    PENDING_MODIFY("pending_modify"),
    COMPLETED("completed"),
    REJECTED("rejected"),
    CANCELLED("cancelled");

    private final String value;

    InstanceStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static InstanceStatus fromValue(String value) {
        for (InstanceStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown InstanceStatus value: " + value);
    }
}
