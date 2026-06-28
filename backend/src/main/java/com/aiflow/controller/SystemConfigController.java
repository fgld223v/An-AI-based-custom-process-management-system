package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.model.SystemConfig;
import com.aiflow.repository.SystemConfigRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统配置管理控制器，支持配置项的增删改查以及自动化规则的独立存取。
 */
@RestController
@RequestMapping("/api/system-config")
public class SystemConfigController {

    private static final String AUTOMATION_RULES_KEY = "automation.rules";
    private static final String AUTOMATION_RULES_NAME = "自动化策略规则";

    private final SystemConfigRepository systemConfigRepository;

    public SystemConfigController(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    /** 获取所有系统配置项 */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listAll() {
        List<SystemConfig> configs = systemConfigRepository.findByDeletedOrderByConfigKeyAsc(0);
        List<Map<String, Object>> result = configs.stream().map(c -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", c.getId());
            map.put("configKey", c.getConfigKey());
            map.put("configName", c.getConfigName());
            map.put("configValue", c.getConfigValue());
            map.put("valueType", c.getValueType());
            map.put("description", c.getDescription());
            map.put("editable", c.getEditable());
            map.put("updatedAt", c.getUpdatedAt());
            return map;
        }).collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    /** 获取单个配置项 */
    @GetMapping("/{configKey}")
    public ApiResponse<Map<String, Object>> getByKey(@PathVariable String configKey) {
        SystemConfig config = systemConfigRepository
                .findByConfigKeyAndDeleted(configKey, 0)
                .orElse(null);
        if (config == null) {
            return ApiResponse.fail(404, "配置项不存在");
        }
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", config.getId());
        map.put("configKey", config.getConfigKey());
        map.put("configName", config.getConfigName());
        map.put("configValue", config.getConfigValue());
        map.put("valueType", config.getValueType());
        map.put("description", config.getDescription());
        map.put("editable", config.getEditable());
        return ApiResponse.success(map);
    }

    /** 更新单个配置项 */
    @PutMapping("/{configKey}")
    public ApiResponse<Void> updateByKey(@PathVariable String configKey,
                                         @RequestBody Map<String, String> body) {
        SystemConfig config = systemConfigRepository
                .findByConfigKeyAndDeleted(configKey, 0)
                .orElse(null);
        if (config == null) {
            return ApiResponse.fail(404, "配置项不存在");
        }
        if (config.getEditable() != null && config.getEditable() == 0) {
            return ApiResponse.fail(403, "该配置项不可编辑");
        }
        String newValue = body.get("configValue");
        if (newValue != null) {
            config.setConfigValue(newValue);
            config.setUpdatedAt(LocalDateTime.now());
            systemConfigRepository.save(config);
        }
        return ApiResponse.success();
    }

    /** 新增配置项 */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, String> body) {
        String configKey = body.get("configKey");
        if (configKey == null || configKey.isBlank()) {
            return ApiResponse.fail(400, "configKey 不能为空");
        }
        if (systemConfigRepository.findByConfigKeyAndDeleted(configKey.trim(), 0).isPresent()) {
            return ApiResponse.fail(400, "配置键已存在");
        }
        SystemConfig config = new SystemConfig();
        config.setConfigKey(configKey.trim());
        config.setConfigName(body.getOrDefault("configName", configKey));
        config.setConfigValue(body.getOrDefault("configValue", ""));
        config.setValueType(body.getOrDefault("valueType", "string"));
        config.setDescription(body.getOrDefault("description", ""));
        config.setEditable(1);
        systemConfigRepository.save(config);

        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", config.getId());
        map.put("configKey", config.getConfigKey());
        map.put("configName", config.getConfigName());
        map.put("configValue", config.getConfigValue());
        map.put("valueType", config.getValueType());
        map.put("description", config.getDescription());
        map.put("editable", config.getEditable());
        return ApiResponse.success(map);
    }

    /** 删除配置项（软删除） */
    @DeleteMapping("/{configKey}")
    public ApiResponse<Void> deleteByKey(@PathVariable String configKey) {
        SystemConfig config = systemConfigRepository
                .findByConfigKeyAndDeleted(configKey, 0)
                .orElse(null);
        if (config == null) {
            return ApiResponse.fail(404, "配置项不存在");
        }
        config.setDeleted(1);
        config.setUpdatedAt(LocalDateTime.now());
        systemConfigRepository.save(config);
        return ApiResponse.success();
    }

    /** 获取自动化规则列表 */
    @GetMapping("/automation-rules")
    public ApiResponse<String> getAutomationRules() {
        String json = systemConfigRepository
                .findByConfigKeyAndDeleted(AUTOMATION_RULES_KEY, 0)
                .map(SystemConfig::getConfigValue)
                .orElse("[]");
        return ApiResponse.success(json);
    }

    /** 保存自动化规则列表 */
    @PutMapping("/automation-rules")
    public ApiResponse<Void> saveAutomationRules(@RequestBody Map<String, String> body) {
        String rulesJson = body.getOrDefault("rules", "[]");
        SystemConfig config = systemConfigRepository
                .findByConfigKeyAndDeleted(AUTOMATION_RULES_KEY, 0)
                .orElseGet(() -> {
                    SystemConfig c = new SystemConfig();
                    c.setConfigKey(AUTOMATION_RULES_KEY);
                    c.setConfigName(AUTOMATION_RULES_NAME);
                    c.setValueType("json");
                    c.setDescription("自动化审批策略规则配置（JSON数组）");
                    c.setEditable(1);
                    return c;
                });
        config.setConfigValue(rulesJson);
        systemConfigRepository.save(config);
        return ApiResponse.success();
    }
}
