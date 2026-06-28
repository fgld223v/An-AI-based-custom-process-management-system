package com.aiflow.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 模板状态枚举
 * 定义流程模板在其生命周期中的状态流转，含审核环节。
 */
@Getter
@AllArgsConstructor
public enum TemplateStatus implements DatabaseEnum {

    /** 草稿 -- 编辑中，尚未提交审核 */
    DRAFT("draft"),

    /** 审核中 -- 已提交，等待管理员审核 */
    REVIEWING("reviewing"),

    /** 已发布 -- 审核通过，可创建流程实例 */
    PUBLISHED("published"),

    /** 已停用 -- 不再允许发起新流程 */
    DISABLED("disabled");

    @JsonValue
    private final String value;
}
