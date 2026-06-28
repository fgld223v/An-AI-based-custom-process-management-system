package com.aiflow.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 流程片段状态枚举：草稿、已发布、已禁用。
 */
@Getter
@AllArgsConstructor
public enum FragmentStatus implements DatabaseEnum {
    DRAFT("draft"),
    PUBLISHED("published"),
    DISABLED("disabled");

    @JsonValue
    private final String value;
}
