package com.aiflow.enums;

public enum SystemRole {
    SUPER_ADMIN("super_admin"),
    BIZ_ADMIN("biz_admin"),
    NORMAL_USER("normal_user");

    private final String value;

    SystemRole(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SystemRole fromValue(String value) {
        for (SystemRole role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown SystemRole value: " + value);
    }
}
