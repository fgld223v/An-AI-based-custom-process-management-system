package com.aiflow.enums;

import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class FragmentSyncStatusConverter extends AbstractDatabaseEnumConverter<FragmentSyncStatus> {

    public FragmentSyncStatusConverter() {
        super(FragmentSyncStatus.class);
    }
}
