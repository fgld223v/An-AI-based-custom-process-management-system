package com.aiflow.enums;

import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class SystemRoleConverter extends AbstractDatabaseEnumConverter<SystemRole> {

    public SystemRoleConverter() {
        super(SystemRole.class);
    }
}
