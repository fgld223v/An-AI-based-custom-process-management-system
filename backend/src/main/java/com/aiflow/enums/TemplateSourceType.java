package com.aiflow.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TemplateSourceType implements DatabaseEnum {
    AI_GENERATED("ai_generated"),
    MANUAL("manual"),
    MARKET_COPY("market_copy"),
    FRAGMENT_COMBO("fragment_combo");

    @JsonValue
    private final String value;
}
