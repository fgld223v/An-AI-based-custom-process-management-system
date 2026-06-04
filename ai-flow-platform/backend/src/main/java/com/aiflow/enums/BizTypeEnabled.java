package com.aiflow.enums;

public enum BizTypeEnabled {
    DISABLED("disabled", "停用"),
    ENABLED("enabled", "启用");

    private final String value;
    private final String description;

    BizTypeEnabled(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public static BizTypeEnabled fromValue(String value) {
        for (BizTypeEnabled status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown BizTypeEnabled value: " + value);
    }
}
