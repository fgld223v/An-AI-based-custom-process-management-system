package com.aiflow.enums;

public enum UserStatus {
    DISABLED("disabled", "停用"),
    NORMAL("normal", "正常"),
    LOCKED("locked", "锁定");

    private final String value;
    private final String description;

    UserStatus(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public static UserStatus fromValue(String value) {
        for (UserStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown UserStatus value: " + value);
    }
}
