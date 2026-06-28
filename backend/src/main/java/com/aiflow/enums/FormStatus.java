package com.aiflow.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 表单状态枚举
 * 定义表单定义在其生命周期中的状态流转。
 */
@Getter
@AllArgsConstructor
public enum FormStatus implements DatabaseEnum {

    /** 草稿 -- 编辑中，尚未发布 */
    DRAFT("draft"),

    /** 已发布 -- 正式启用，可被流程模板绑定使用 */
    PUBLISHED("published"),

    /** 已停用 -- 不再允许新流程使用 */
    DISABLED("disabled");

    @JsonValue
    private final String value;
}
