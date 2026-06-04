package com.aiflow.enums;

public enum NotificationType {
    TASK_REMIND("task_remind"),
    TIMEOUT_WARNING("timeout_warning"),
    APPROVAL_RESULT("approval_result"),
    SYSTEM_NOTICE("system_notice");

    private final String value;

    NotificationType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static NotificationType fromValue(String value) {
        for (NotificationType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown NotificationType value: " + value);
    }
}
