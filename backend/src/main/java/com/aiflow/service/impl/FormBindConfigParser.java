package com.aiflow.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 表单绑定配置解析器 — 统一从 formBindConfig 中按节点 key 解析 formId。
 *
 * <p>标准格式（推荐）：</p>
 * <pre>{@code { "UserTask_1": {"formId": 1}, "StartEvent_1": {"formId": 2} }}</pre>
 *
 * <p>历史遗留格式（兼容）：</p>
 * <pre>{@code { "formId": 1 }}</pre>
 * 这种格式整条模板只绑了一个表单，不区分节点。解析时直接返回该 formId。
 *
 * <p>错误处理策略：所有解析失败静默返回 null，不阻塞业务流程。</p>
 */
@Slf4j
@Component
public class FormBindConfigParser {

    private static final int MAX_JSON_LENGTH = 10000;

    private final ObjectMapper objectMapper;

    public FormBindConfigParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 从 formBindConfig JSON 中按 taskDefinitionKey 解析 formId。
     *
     * @param json               formBindConfig JSON 字符串
     * @param taskDefinitionKey  Flowable 任务定义 key（节点 ID）
     * @return formId，解析失败或无匹配时返回 null
     */
    public Long resolveFormId(String json, String taskDefinitionKey) {
        if (!hasText(json)) {
            return null;
        }
        if (json.length() > MAX_JSON_LENGTH) {
            log.warn("formBindConfig 长度 {} 超过上限 {}，已忽略", json.length(), MAX_JSON_LENGTH);
            return null;
        }

        // 1. 尝试标准格式：Map<nodeKey, {formId: N}>
        Map<String, Map<String, Object>> standardFormat = parseAsNestedMap(json);
        if (standardFormat != null) {
            Map<String, Object> nodeBinding = standardFormat.get(taskDefinitionKey);
            if (nodeBinding != null) {
                return extractFormId(nodeBinding);
            }
            return null;
        }

        // 2. 尝试扁平格式：{formId: N}（历史遗留，整模板共享一个表单）
        Map<String, Object> flatFormat = parseAsFlatMap(json);
        if (flatFormat != null && flatFormat.containsKey("formId")) {
            log.warn("formBindConfig 使用了旧版扁平格式 {{formId: N}}，建议迁移为 {{nodeKey: {{formId: N}}}} 格式");
            return extractFormId(flatFormat);
        }

        // 3. 能解析为 JSON 对象但格式对不上 — 不是致命错误，只是没有对应节点的绑定
        if (flatFormat != null || standardFormat != null) {
            return null;
        }

        log.warn("formBindConfig JSON 格式无法识别，已跳过解析");
        return null;
    }

    /**
     * 校验 formBindConfig 格式是否合法。用于保存/发布前的检查。
     *
     * @return null 表示格式合法；返回错误描述字符串表示校验不通过
     */
    public String validate(String json) {
        if (!hasText(json)) {
            return null; // 空值允许，表示不绑定表单
        }
        if (json.length() > MAX_JSON_LENGTH) {
            return "formBindConfig 长度超过上限 (" + json.length() + " > " + MAX_JSON_LENGTH + ")";
        }

        // 必须是 JSON 对象（不能是数组、纯数字、纯字符串）
        try {
            if (!json.trim().startsWith("{")) {
                return "formBindConfig 必须是 JSON 对象格式，不能是数组或标量";
            }
            objectMapper.readTree(json);
        } catch (Exception e) {
            return "formBindConfig 不是合法的 JSON";
        }

        // 尝试解析为已知格式
        Map<String, Map<String, Object>> standard = parseAsNestedMap(json);
        if (standard != null) {
            // 标准格式 — 校验每个节点的 formId 是数字
            for (Map.Entry<String, Map<String, Object>> entry : standard.entrySet()) {
                Object formIdObj = entry.getValue().get("formId");
                if (formIdObj != null && !(formIdObj instanceof Number)) {
                    return "节点 " + entry.getKey() + " 的 formId 必须是数字";
                }
            }
            return null;
        }

        Map<String, Object> flat = parseAsFlatMap(json);
        if (flat != null) {
            // 扁平格式 — 兼容但不推荐，打 warn
            log.warn("formBindConfig 使用了扁平格式，建议迁移为标准格式");
            return null;
        }

        return "formBindConfig 格式不正确：需为 {nodeKey: {formId: N}} 或 {formId: N}";
    }

    // ================================================================
    // 公开解析方法 — 供外部按需调用，不抛异常
    // ================================================================

    /**
     * 尝试按标准格式 {{nodeKey: {formId: N}}} 解析，失败返回 null。
     */
    public Map<String, Map<String, Object>> tryParseAsNestedMap(String json) {
        return parseAsNestedMap(json);
    }

    /**
     * 尝试按扁平格式 {formId: N} 解析，失败返回 null。
     */
    public Map<String, Object> tryParseAsFlatMap(String json) {
        return parseAsFlatMap(json);
    }

    // ================================================================
    // 内部解析方法
    // ================================================================

    private Map<String, Map<String, Object>> parseAsNestedMap(String json) {
        if (!hasText(json)) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Map<String, Object>>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> parseAsFlatMap(String json) {
        if (!hasText(json)) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private Long extractFormId(Map<String, Object> binding) {
        Object formIdObj = binding.get("formId");
        if (formIdObj instanceof Number num) {
            return num.longValue();
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
