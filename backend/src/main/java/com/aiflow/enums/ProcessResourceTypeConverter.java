package com.aiflow.enums;

import jakarta.persistence.Converter;

/**
 * 流程资源类型JPA转换器，将ProcessResourceType枚举与数据库值相互转换。
 */
@Converter(autoApply = false)
public class ProcessResourceTypeConverter extends AbstractDatabaseEnumConverter<ProcessResourceType> {

    public ProcessResourceTypeConverter() {
        super(ProcessResourceType.class);
    }
}
