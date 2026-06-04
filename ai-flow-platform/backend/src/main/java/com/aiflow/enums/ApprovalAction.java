package com.aiflow.enums;

public enum ApprovalAction {
    APPROVE("approve"),
    REJECT("reject"),
    SUPPLEMENT("supplement");

    private final String value;

    ApprovalAction(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ApprovalAction fromValue(String value) {
        for (ApprovalAction action : values()) {
            if (action.value.equals(value)) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown ApprovalAction value: " + value);
    }
}
