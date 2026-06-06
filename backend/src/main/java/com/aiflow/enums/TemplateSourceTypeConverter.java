package com.aiflow.enums;

import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class TemplateSourceTypeConverter extends AbstractDatabaseEnumConverter<TemplateSourceType> {

    public TemplateSourceTypeConverter() {
        super(TemplateSourceType.class);
    }
}
