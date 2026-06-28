package com.aiflow.enums;

import jakarta.persistence.AttributeConverter;

import java.util.Arrays;

/**
 * 数据库枚举转换器抽象基类，实现JPA AttributeConverter，将枚举与数据库字符串值相互转换。
 *
 * @param <E> 枚举类型，必须同时实现 {@link DatabaseEnum} 接口
 */
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
