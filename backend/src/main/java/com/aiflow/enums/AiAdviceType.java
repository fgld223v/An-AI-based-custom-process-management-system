package com.aiflow.enums;

public enum AiAdviceType {
    PASS("pass"),
    VERIFY("verify"),
    REJECT("reject"),
    RISK("risk");

    private final String value;

    AiAdviceType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AiAdviceType fromValue(String value) {
        for (AiAdviceType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown AiAdviceType value: " + value);
    }
}
