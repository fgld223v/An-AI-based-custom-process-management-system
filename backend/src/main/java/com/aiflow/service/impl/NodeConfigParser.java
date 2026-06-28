package com.aiflow.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 节点配置解析器 — 统一处理两种 nodeConfig JSON 格式。
 *
 * <p>前端 ProcessDesigner 保存的格式为 Map（键是 nodeId）：</p>
 * <pre>{@code { "Activity_1": { "nodeId": ..., "nodeKey": ..., "approvalMode": "ALL" } }}</pre>
 *
 * <p>部分旧代码/历史数据可能使用 List 格式：</p>
 * <pre>{@code [ { "nodeKey": "Activity_1", "businessType": "start" } ]}</pre>
 *
 * <p>本工具类先试 Map 格式（当前主力格式），失败后回退到 List 格式。</p>
 */
@Slf4j
@Component
public class NodeConfigParser {

    private final ObjectMapper objectMapper;

    public NodeConfigParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 按 nodeKey/nodeId 查找单个节点的配置。
     */
    public Map<String, Object> findNode(String nodeConfigJson, String nodeKey) {
        if (!hasText(nodeConfigJson) || !hasText(nodeKey)) return null;

        // 1. 尝试 Map 格式 — 前端主力格式
        Map<String, Map<String, Object>> map = parseAsMap(nodeConfigJson);
        if (map != null) {
            Map<String, Object> config = map.get(nodeKey);
            if (config != null) return config;
            for (Map.Entry<String, Map<String, Object>> entry : map.entrySet()) {
                Map<String, Object> v = entry.getValue();
                if (nodeKey.equals(stringValue(v.get("nodeKey")))
                        || nodeKey.equals(stringValue(v.get("nodeId")))) {
                    return v;
                }
            }
            return null;
        }

        // 2. 尝试 List 格式 — 旧格式兼容
        List<Map<String, Object>> list = parseAsList(nodeConfigJson);
        if (list != null) {
            for (Map<String, Object> item : list) {
                if (nodeKey.equals(stringValue(item.get("nodeKey")))
                        || nodeKey.equals(stringValue(item.get("nodeId")))) {
                    return item;
                }
            }
        }
        return null;
    }

    /** 获取节点配置中指定字段的值。 */
    public Object getField(String nodeConfigJson, String nodeKey, String fieldName) {
        Map<String, Object> node = findNode(nodeConfigJson, nodeKey);
        return node != null ? node.get(fieldName) : null;
    }

    /** 获取节点配置中指定字段的字符串值。 */
    public String getStringField(String nodeConfigJson, String nodeKey, String fieldName) {
        Object value = getField(nodeConfigJson, nodeKey, fieldName);
        return value != null ? value.toString().trim() : null;
    }

    /** 按顺序返回所有节点配置（用于需要遍历的场景，如查找上一个节点）。 */
    public List<Map<String, Object>> asOrderedList(String nodeConfigJson) {
        Map<String, Map<String, Object>> map = parseAsMap(nodeConfigJson);
        if (map != null) {
            return List.copyOf(map.values());
        }
        List<Map<String, Object>> list = parseAsList(nodeConfigJson);
        return list != null ? list : Collections.emptyList();
    }

    // ================================================================
    // 内部解析方法 — 先尝试 Map 格式，失败后降级到 List 格式
    // ================================================================

    /**
     * 尝试解析为 Map 格式 { "NodeId": {...}, ... }，解析失败返回 null。
     */
    Map<String, Map<String, Object>> parseAsMap(String json) {
        if (!hasText(json)) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Map<String, Object>>>() {});
        } catch (Exception e) {
            return null;  // 不是 Map 格式，返回 null 让调用方回退到 List 格式
        }
    }

    /**
     * 尝试解析为 List 格式 [ { "nodeKey": "...", ... }, ... ]，解析失败返回 null。
     */
    List<Map<String, Object>> parseAsList(String json) {
        if (!hasText(json)) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return null;  // JSON 格式无效
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString().trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
