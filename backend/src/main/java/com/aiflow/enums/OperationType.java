package com.aiflow.enums;

public enum OperationType {
    LOGIN("login"),
    LOGOUT("logout"),
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    APPROVE("approve"),
    REJECT("reject"),
    PUBLISH("publish"),
    CONFIG_CHANGE("config_change");

    private final String value;

    OperationType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static OperationType fromValue(String value) {
        for (OperationType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown OperationType value: " + value);
    }
}
