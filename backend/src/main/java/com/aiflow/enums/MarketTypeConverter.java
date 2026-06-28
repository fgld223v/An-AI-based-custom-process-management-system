package com.aiflow.enums;

import jakarta.persistence.Converter;

/**
 * 市场类型JPA转换器，将MarketType枚举与数据库值相互转换。
 */
@Converter(autoApply = false)
public class MarketTypeConverter extends AbstractDatabaseEnumConverter<MarketType> {

    public MarketTypeConverter() {
        super(MarketType.class);
    }
}
