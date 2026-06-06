package com.aiflow.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TemplateStatus implements DatabaseEnum {
    DRAFT("draft"),
    REVIEWING("reviewing"),
    PUBLISHED("published"),
    DISABLED("disabled");

    @JsonValue
    private final String value;
}
