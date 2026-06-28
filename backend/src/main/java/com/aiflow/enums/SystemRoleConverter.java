package com.aiflow.enums;

import jakarta.persistence.Converter;

/**
 * 系统角色JPA转换器，将SystemRole枚举与数据库值相互转换。
 */
@Converter(autoApply = false)
public class SystemRoleConverter extends AbstractDatabaseEnumConverter<SystemRole> {

    public SystemRoleConverter() {
        super(SystemRole.class);
    }
}
