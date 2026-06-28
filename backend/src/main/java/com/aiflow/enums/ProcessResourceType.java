package com.aiflow.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 流程资源类型枚举：系统模板、业务流程。
 */
@Getter
@AllArgsConstructor
public enum ProcessResourceType implements DatabaseEnum {
    SYSTEM_TEMPLATE("system_template"),
    BUSINESS_PROCESS("business_process");

    @JsonValue
    private final String value;
}
