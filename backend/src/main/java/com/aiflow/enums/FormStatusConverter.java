package com.aiflow.enums;

import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class FormStatusConverter extends AbstractDatabaseEnumConverter<FormStatus> {

    public FormStatusConverter() {
        super(FormStatus.class);
    }
}
