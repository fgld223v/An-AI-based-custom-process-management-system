package com.aiflow.enums;

public enum ConfigValueType {
    STRING("string"),
    INT("int"),
    FLOAT("float"),
    BOOL("bool"),
    JSON("json");

    private final String value;

    ConfigValueType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ConfigValueType fromValue(String value) {
        for (ConfigValueType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ConfigValueType value: " + value);
    }
}
