package com.aiflow.enums;

import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class FragmentStatusConverter extends AbstractDatabaseEnumConverter<FragmentStatus> {

    public FragmentStatusConverter() {
        super(FragmentStatus.class);
    }
}
