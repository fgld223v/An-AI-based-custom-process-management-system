package com.aiflow.enums;

public enum MarketType {
    TEMPLATE("template"),
    FRAGMENT("fragment");

    private final String value;

    MarketType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MarketType fromValue(String value) {
        for (MarketType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown MarketType value: " + value);
    }
}
