package com.aiflow.enums;

import jakarta.persistence.Converter;

/**
 * 模板来源类型JPA转换器，将TemplateSourceType枚举与数据库值相互转换。
 */
@Converter(autoApply = false)
public class TemplateSourceTypeConverter extends AbstractDatabaseEnumConverter<TemplateSourceType> {

    public TemplateSourceTypeConverter() {
        super(TemplateSourceType.class);
    }
}
