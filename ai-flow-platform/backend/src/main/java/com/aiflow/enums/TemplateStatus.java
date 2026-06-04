package com.aiflow.enums;

public enum TemplateStatus {
    DRAFT("draft"),
    REVIEWING("reviewing"),
    PUBLISHED("published"),
    DISABLED("disabled");

    private final String value;

    TemplateStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static TemplateStatus fromValue(String value) {
        for (TemplateStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown TemplateStatus value: " + value);
    }
}
