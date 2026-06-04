package com.aiflow.enums;

public enum TemplateSourceType {
    AI_GENERATED("ai_generated"),
    MANUAL("manual"),
    MARKET_COPY("market_copy"),
    FRAGMENT_COMBO("fragment_combo");

    private final String value;

    TemplateSourceType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static TemplateSourceType fromValue(String value) {
        for (TemplateSourceType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TemplateSourceType value: " + value);
    }
}
