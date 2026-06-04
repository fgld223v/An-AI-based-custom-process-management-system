package com.aiflow.enums;

public enum TargetType {
    TEMPLATE("template"),
    INSTANCE("instance"),
    USER("user"),
    ROLE("role"),
    CONFIG("config");

    private final String value;

    TargetType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static TargetType fromValue(String value) {
        for (TargetType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TargetType value: " + value);
    }
}
