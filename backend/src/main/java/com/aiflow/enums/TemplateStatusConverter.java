package com.aiflow.enums;

import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class TemplateStatusConverter extends AbstractDatabaseEnumConverter<TemplateStatus> {

    public TemplateStatusConverter() {
        super(TemplateStatus.class);
    }
}
