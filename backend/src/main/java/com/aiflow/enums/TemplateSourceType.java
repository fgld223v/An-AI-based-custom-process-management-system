package com.aiflow.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 模板来源类型枚举
 * 标识流程模板的创建来源，用于统计和溯源。
 */
@Getter
@AllArgsConstructor
public enum TemplateSourceType implements DatabaseEnum {

    /** AI 生成 -- 通过 AI 对话自动生成的模板 */
    AI_GENERATED("ai_generated"),

    /** 手动创建 -- 用户通过流程编辑器手动搭建 */
    MANUAL("manual"),

    /** 市场复制 -- 从模板市场复制而来 */
    MARKET_COPY("market_copy"),

    /** 片段组合 -- 由多个流程片段组合拼装而成 */
    FRAGMENT_COMBO("fragment_combo");

    @JsonValue
    private final String value;
}
