package com.aiflow.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MarketType implements DatabaseEnum {
    TEMPLATE("template"),
    FRAGMENT("fragment");

    @JsonValue
    private final String value;
}
