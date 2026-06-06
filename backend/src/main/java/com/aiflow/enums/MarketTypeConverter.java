package com.aiflow.enums;

import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class MarketTypeConverter extends AbstractDatabaseEnumConverter<MarketType> {

    public MarketTypeConverter() {
        super(MarketType.class);
    }
}
