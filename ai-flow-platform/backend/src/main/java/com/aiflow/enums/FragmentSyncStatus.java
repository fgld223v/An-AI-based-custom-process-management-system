package com.aiflow.enums;

public enum FragmentSyncStatus {
    SYNCED("synced"),
    PENDING_UPDATE("pending_update"),
    UNBOUND("unbound");

    private final String value;

    FragmentSyncStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static FragmentSyncStatus fromValue(String value) {
        for (FragmentSyncStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown FragmentSyncStatus value: " + value);
    }
}
