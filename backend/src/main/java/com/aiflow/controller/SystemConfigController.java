package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.model.SystemConfig;
import com.aiflow.repository.SystemConfigRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/system-config")
public class SystemConfigController {

    private static final String AUTOMATION_RULES_KEY = "automation.rules";
    private static final String AUTOMATION_RULES_NAME = "自动化策略规则";

    private final SystemConfigRepository systemConfigRepository;

    public SystemConfigController(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    /**
     * 获取自动化规则列表（JSON 数组）
     */
    @GetMapping("/automation-rules")
    public ApiResponse<String> getAutomationRules() {
        String json = systemConfigRepository
                .findByConfigKeyAndDeleted(AUTOMATION_RULES_KEY, 0)
                .map(SystemConfig::getConfigValue)
                .orElse("[]");
        return ApiResponse.success(json);
    }

    /**
     * 保存自动化规则列表（完整替换）
     */
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
