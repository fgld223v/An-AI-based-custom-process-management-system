package com.aiflow.enums;

import jakarta.persistence.Converter;

/**
 * 流程片段状态JPA转换器，将FragmentStatus枚举与数据库值相互转换。
 */
@Converter(autoApply = false)
public class FragmentStatusConverter extends AbstractDatabaseEnumConverter<FragmentStatus> {

    public FragmentStatusConverter() {
        super(FragmentStatus.class);
    }
}
