package com.aiflow.enums;

import jakarta.persistence.AttributeConverter;

import java.util.Arrays;

public abstract class AbstractDatabaseEnumConverter<E extends Enum<E> & DatabaseEnum> implements AttributeConverter<E, String> {

    private final Class<E> enumType;

    protected AbstractDatabaseEnumConverter(Class<E> enumType) {
        this.enumType = enumType;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return Arrays.stream(enumType.getEnumConstants())
                .filter(item -> item.getValue().equals(dbData))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown " + enumType.getSimpleName() + " value: " + dbData));
    }
}
