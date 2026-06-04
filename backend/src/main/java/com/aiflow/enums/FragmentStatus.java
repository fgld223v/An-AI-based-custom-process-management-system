package com.aiflow.enums;

public enum FragmentStatus {
    DRAFT("draft"),
    PUBLISHED("published"),
    DISABLED("disabled");

    private final String value;

    FragmentStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static FragmentStatus fromValue(String value) {
        for (FragmentStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown FragmentStatus value: " + value);
    }
}
