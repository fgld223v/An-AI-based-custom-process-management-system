package com.aiflow.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 数据脱敏切面 —— 项目首个 AOP 切面。
 * 对 @DataMask 标记的方法返回值中的手机号、身份证号做脱敏处理。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DataMaskAspect {

    private final ObjectMapper objectMapper;

    /** 手机号：保留前3后4，中间4位变 **** */
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<=^|\\D)(1[3-9]\\d)\\d{4}(\\d{4})(?=\\D|$)");

    /** 身份证号：保留前4后4，中间10位变 **** */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile(
            "(?<=^|\\D)(\\d{4})\\d{10}(\\d{4})(?=\\D|$)");

    @Around("@annotation(com.aiflow.annotation.DataMask)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();

        if (result == null) {
            return null;
        }

        try {
            JsonNode root = objectMapper.valueToTree(result);
            maskJsonNode(root);
            return objectMapper.treeToValue(root, result.getClass());
        } catch (Exception e) {
            log.warn("数据脱敏失败，返回原始数据: {}", e.getMessage());
            return result;
        }
    }

    private void maskJsonNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            obj.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey().toLowerCase();
                JsonNode value = entry.getValue();

                if (value.isTextual()) {
                    String masked = maskIfSensitive(fieldName, value.asText());
                    if (!masked.equals(value.asText())) {
                        obj.set(entry.getKey(), new TextNode(masked));
                    }
                } else if (value.isObject() || value.isArray()) {
                    maskJsonNode(value);
                }
            });
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                maskJsonNode(arr.get(i));
            }
        }
        // primitives / null — skip
    }

    private String maskIfSensitive(String fieldName, String value) {
        if (isPhoneField(fieldName)) {
            return maskPhone(value);
        }
        if (isIdCardField(fieldName)) {
            return maskIdCard(value);
        }
        return value;
    }

    private boolean isPhoneField(String fieldName) {
        return fieldName.contains("phone") || fieldName.contains("mobile")
                || fieldName.contains("tel") || fieldName.contains("telephone");
    }

    private boolean isIdCardField(String fieldName) {
        return fieldName.contains("idcard") || fieldName.contains("id_card")
                || fieldName.contains("idnumber") || fieldName.contains("id_number")
                || fieldName.contains("identity") || fieldName.contains("idno");
    }

    private String maskPhone(String value) {
        if (value == null || value.length() != 11) {
            return value;
        }
        return value.substring(0, 3) + "****" + value.substring(7);
    }

    private String maskIdCard(String value) {
        if (value == null || value.length() < 15) {
            return value;
        }
        return value.substring(0, 4) + "**********" + value.substring(value.length() - 4);
    }
}
