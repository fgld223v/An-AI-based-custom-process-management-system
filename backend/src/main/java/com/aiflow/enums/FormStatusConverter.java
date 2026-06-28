package com.aiflow.enums;

import jakarta.persistence.Converter;

/**
 * 表单状态JPA转换器，将FormStatus枚举与数据库值相互转换。
 */
@Converter(autoApply = false)
public class FormStatusConverter extends AbstractDatabaseEnumConverter<FormStatus> {

    public FormStatusConverter() {
        super(FormStatus.class);
    }
}
