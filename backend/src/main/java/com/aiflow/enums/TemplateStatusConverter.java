package com.aiflow.enums;

import jakarta.persistence.Converter;

/**
 * 模板状态JPA转换器，将TemplateStatus枚举与数据库值相互转换。
 */
@Converter(autoApply = false)
public class TemplateStatusConverter extends AbstractDatabaseEnumConverter<TemplateStatus> {

    public TemplateStatusConverter() {
        super(TemplateStatus.class);
    }
}
