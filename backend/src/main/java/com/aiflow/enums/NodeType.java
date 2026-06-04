package com.aiflow.enums;

public enum NodeType {
    START_EVENT("start_event"),
    END_EVENT("end_event"),
    USER_TASK("user_task"),
    EXCLUSIVE_GATEWAY("exclusive_gateway"),
    PARALLEL_GATEWAY("parallel_gateway"),
    INCLUSIVE_GATEWAY("inclusive_gateway"),
    SUB_PROCESS("sub_process"),
    CC_NODE("cc_node");

    private final String value;

    NodeType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static NodeType fromValue(String value) {
        for (NodeType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown NodeType value: " + value);
    }
}
