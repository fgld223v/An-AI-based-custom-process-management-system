package com.aiflow.enums;

import jakarta.persistence.Converter;

/**
 * 流程片段同步状态JPA转换器，将FragmentSyncStatus枚举与数据库值相互转换。
 */
@Converter(autoApply = false)
public class FragmentSyncStatusConverter extends AbstractDatabaseEnumConverter<FragmentSyncStatus> {

    public FragmentSyncStatusConverter() {
        super(FragmentSyncStatus.class);
    }
}
