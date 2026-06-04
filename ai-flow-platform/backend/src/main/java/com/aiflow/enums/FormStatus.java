package com.aiflow.enums;

public enum FormStatus {
    DRAFT("draft"),
    PUBLISHED("published");

    private final String value;

    FormStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static FormStatus fromValue(String value) {
        for (FormStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown FormStatus value: " + value);
    }
}
