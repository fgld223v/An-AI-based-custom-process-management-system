package com.aiflow.enums;

import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ProcessResourceTypeConverter extends AbstractDatabaseEnumConverter<ProcessResourceType> {

    public ProcessResourceTypeConverter() {
        super(ProcessResourceType.class);
    }
}
