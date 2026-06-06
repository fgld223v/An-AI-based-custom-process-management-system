package com.aiflow.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SystemRole implements DatabaseEnum {
    SUPER_ADMIN("super_admin"),
    BIZ_ADMIN("biz_admin"),
    NORMAL_USER("normal_user");

    @JsonValue
    private final String value;
}
