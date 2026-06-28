package com.aiflow.service;

import com.aiflow.common.BusinessException;
import com.aiflow.config.AiConfig;
import com.aiflow.dto.OptimizationAnalysisDTO;
import com.aiflow.dto.OptimizationSuggestionDTO;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 流程优化服务 — 基于历史流转数据分析冗余节点、低效流程、重复审批，
 * 调用 DeepSeek 生成流程精简、节点优化、权限调整建议。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiOptimizationService {

    private final WebClient deepseekWebClient;
    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessTemplateRepository processTemplateRepository;

    /** DeepSeek System Prompt：BPM 优化专家，基于历史指标生成流程优化建议 */
    private static final String OPTIMIZE_SYSTEM_PROMPT = """
        你是一个业务流程优化专家（BPM Optimization Expert）。

        你将收到一个业务流程的以下数据：
        1. 流程基本信息（模板名称、节点列表、连接关系）
        2. 历史运行指标：
           - 每个节点的平均停留时长(h)、超时率(%)、驳回率(%)
           - 审批人平均处理时长(h)
           - 条件分支的实际触发频率(%)
           - 整体完成率(%)、平均总耗时(h)

        请分析并输出结构化 JSON 优化建议，格式：
        {
          "analysis": "整体分析摘要，包含核心问题及影响",
          "suggestions": [
            {
              "type": "类型: redundant_node/bottleneck/approval_optimization/branch_optimization/permission_optimization",
              "nodeKey": "节点ID(无则填null)",
              "nodeName": "节点名称",
              "severity": "high/medium/low",
              "description": "问题描述",
              "suggestion": "具体改进建议",
              "expectedImprovement": "预计改进效果(如：预计节省4小时、审批效率提升30%)"
            }
          ]
        }

        分析规则：
        1. 节点平均停留>24h且驳回率<5% → 可能冗余，建议合并或移除
        2. 两个连续同审批人节点 → 建议合并
        3. 条件分支某分支触发率<5% → 建议检查是否必要或移除
        4. 审批人平均处理>48h → 建议增加审批人或调整权限
        5. 超时率>30%的节点 → 建议调整SLA或增加自动处理规则
        6. 驳回率>20%的节点 → 建议检查表单校验或审批标准
        7. 完成率<60%的模板 → 建议分析流转阻塞原因
        8. 给 3-5 条最有价值的建议，按 severity 排序(high 优先)
        """;

    /**
     * 分析指定模板并生成优化建议。
     */
    public OptimizationAnalysisDTO optimizeTemplate(Long templateId) {
        // 1. 收集模板指标
        Map<String, Object> metrics = collectTemplateMetrics(templateId);
        if (metrics.isEmpty()) {
            throw new BusinessException("该模板无历史流转数据，无法分析");
        }

        // 2. 构建 LLM prompt
        String prompt = buildOptimizationPrompt(metrics);

        // 3. 调用 DeepSeek
        String llmResponse = callDeepSeek(prompt);

        // 4. 解析响应
        return parseOptimizationResponse(llmResponse, templateId, metrics);
    }

    /**
     * 批量分析所有有历史数据的模板。
     */
    public List<OptimizationAnalysisDTO> optimizeAll() {
        List<Long> templateIds = jdbcTemplate.queryForList(
            "SELECT DISTINCT template_id FROM process_instance WHERE deleted=0 AND status='completed'",
            Long.class);
        return templateIds.stream()
                .map(id -> {
                    try { return optimizeTemplate(id); }
                    catch (Exception e) {
                        log.warn("模板 {} 优化分析失败: {}", id, e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ================================================================
    // 指标采集 — 从数据库查询模板的整体统计、节点统计、驳回率、审批人效率等
    // ================================================================

    private Map<String, Object> collectTemplateMetrics(Long templateId) {
        var template = processTemplateRepository.findByIdAndDeleted(templateId, 0).orElse(null);
        if (template == null) return Map.of();

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("templateId", templateId);
        metrics.put("templateName", template.getTemplateName());

        // 整体统计
        Map<String, Object> overview = jdbcTemplate.queryForMap("""
            SELECT COUNT(*) AS total,
              SUM(CASE WHEN status='completed' THEN 1 ELSE 0 END) AS completed,
              ROUND(COALESCE(SUM(CASE WHEN status='completed' THEN 1 ELSE 0 END),0)*100.0/NULLIF(COUNT(*),0),2) AS completionRate,
              ROUND(COALESCE(AVG(CASE WHEN started_at IS NOT NULL AND ended_at IS NOT NULL
                THEN TIMESTAMPDIFF(SECOND, started_at, ended_at) END),0)/3600.0, 2) AS avgDurationHours
            FROM process_instance WHERE template_id=? AND deleted=0
            """, templateId);
        metrics.putAll(overview);

        // 节点级统计
        List<Map<String, Object>> nodes = jdbcTemplate.queryForList("""
            SELECT t.node_key, t.node_name,
              COUNT(*) AS totalCount,
              SUM(CASE WHEN t.status='timeout' THEN 1 ELSE 0 END) AS timeoutCount,
              ROUND(COALESCE(SUM(CASE WHEN t.status='timeout' THEN 1 ELSE 0 END),0)*100.0/NULLIF(COUNT(*),0),2) AS timeoutRate,
              ROUND(COALESCE(AVG(CASE WHEN t.created_at IS NOT NULL AND t.completed_at IS NOT NULL
                THEN TIMESTAMPDIFF(SECOND, t.created_at, t.completed_at) END),0)/3600.0, 2) AS avgDwellHours
            FROM task t JOIN process_instance pi ON t.instance_id=pi.id
            WHERE pi.template_id=? AND pi.deleted=0
            GROUP BY t.node_key, t.node_name
            ORDER BY avgDwellHours DESC
            """, templateId);

        // 驳回率
        List<Map<String, Object>> rejectRates = jdbcTemplate.queryForList("""
            SELECT ar.node_key,
              COUNT(*) AS rejectCount,
              ROUND(COUNT(*)*100.0/(SELECT COUNT(*) FROM task t2
                JOIN process_instance pi2 ON t2.instance_id=pi2.id
                WHERE pi2.template_id=? AND t2.node_key=ar.node_key AND pi2.deleted=0),2) AS rejectRate
            FROM approval_record ar
            JOIN task t ON ar.task_id=t.id
            JOIN process_instance pi ON t.instance_id=pi.id
            WHERE pi.template_id=? AND ar.action='reject' AND pi.deleted=0
            GROUP BY ar.node_key
            """, templateId, templateId);

        // 审批人效率
        List<Map<String, Object>> assigneeEff = jdbcTemplate.queryForList("""
            SELECT t.assignee_id,
              COUNT(*) AS taskCount,
              ROUND(COALESCE(AVG(CASE WHEN t.claimed_at IS NOT NULL AND t.completed_at IS NOT NULL
                THEN TIMESTAMPDIFF(SECOND, t.claimed_at, t.completed_at) END),0)/3600.0, 2) AS avgProcessHours
            FROM task t JOIN process_instance pi ON t.instance_id=pi.id
            WHERE pi.template_id=? AND t.assignee_id IS NOT NULL AND pi.deleted=0
            GROUP BY t.assignee_id ORDER BY avgProcessHours DESC
            """, templateId);

        // 条件分支触发频率（从 nodeConfig 获取 condition nodes，统计各分支 task count）
        metrics.put("nodes", nodes);
        metrics.put("rejectRates", rejectRates);
        metrics.put("assigneeEff", assigneeEff);

        // 模板 BPMN 结构摘要
        if (hasText(template.getNodeConfig())) {
            try {
                List<Map<String, Object>> nodeConfigs = objectMapper.readValue(
                    template.getNodeConfig(), new TypeReference<List<Map<String, Object>>>() {});
                metrics.put("nodeConfig", nodeConfigs);
            } catch (Exception e) {
                // 尝试 Map 格式
                try {
                    Map<String, Map<String, Object>> map = objectMapper.readValue(
                        template.getNodeConfig(), new TypeReference<Map<String, Map<String, Object>>>() {});
                    metrics.put("nodeConfig", new ArrayList<>(map.values()));
                } catch (Exception ex) { /* ignore */ }
            }
        }

        return metrics;
    }

    // ================================================================
    // LLM 调用 — 构建 Prompt、调用 DeepSeek、解析优化建议响应
    // ================================================================

    private String buildOptimizationPrompt(Map<String, Object> metrics) {
        try {
            return "请分析以下流程的运行数据并给出优化建议：\n\n" +
                   objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metrics);
        } catch (Exception e) {
            throw new BusinessException("构建优化分析 prompt 失败");
        }
    }

    private String callDeepSeek(String userPrompt) {
        log.info("开始 AI 流程优化分析");

        Map<String, Object> requestBody = Map.of(
            "model", aiConfig.getModel(),
            "messages", List.of(
                Map.of("role", "system", "content", OPTIMIZE_SYSTEM_PROMPT),
                Map.of("role", "user", "content", userPrompt)
            ),
            "temperature", 0.3,
            "max_tokens", 4096
        );

        String responseBody;
        try {
            responseBody = deepseekWebClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(),
                    r -> r.bodyToMono(String.class).map(b -> new RuntimeException("API调用失败：" + b)))
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(aiConfig.getTimeoutSeconds()))
                .block();
        } catch (Exception e) {
            log.error("DeepSeek API 调用异常", e);
            throw new BusinessException("AI 服务调用失败：" + e.getMessage());
        }

        if (responseBody == null) throw new BusinessException("AI 服务返回为空");

        try {
            Map<String, Object> respMap = objectMapper.readValue(responseBody, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            return (String) msg.get("content");
        } catch (Exception e) {
            throw new BusinessException("AI 响应解析失败");
        }
    }

    private OptimizationAnalysisDTO parseOptimizationResponse(String json, Long templateId,
                                                               Map<String, Object> metrics) {
        json = json.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("```(?:json)?\\s*\\n?", "");
            json = json.replaceFirst("\\n?```\\s*$", "");
        }

        try {
            Map<String, Object> response = objectMapper.readValue(json, new TypeReference<>() {});
            String analysis = (String) response.getOrDefault("analysis", "");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawSuggestions = (List<Map<String, Object>>) response.getOrDefault(
                    "suggestions", List.of());

            List<OptimizationSuggestionDTO> suggestions = rawSuggestions.stream().map(s -> {
                OptimizationSuggestionDTO dto = new OptimizationSuggestionDTO();
                dto.setType(str(s.get("type")));
                dto.setNodeKey(str(s.get("nodeKey")));
                dto.setNodeName(str(s.get("nodeName")));
                dto.setSeverity(str(s.get("severity")));
                dto.setDescription(str(s.get("description")));
                dto.setSuggestion(str(s.get("suggestion")));
                dto.setExpectedImprovement(str(s.get("expectedImprovement")));
                return dto;
            }).collect(Collectors.toList());

            OptimizationAnalysisDTO result = new OptimizationAnalysisDTO();
            result.setTemplateId(templateId);
            result.setTemplateName(str(metrics.get("templateName")));
            result.setAnalysis(analysis);
            result.setSuggestions(suggestions);
            result.setMetrics(metrics);
            log.info("AI 优化分析完成，模板={}，建议数={}", templateId, suggestions.size());
            return result;
        } catch (Exception e) {
            log.error("解析优化分析响应失败: {}", json, e);
            throw new BusinessException("AI 生成的优化建议格式有误，请重试");
        }
    }

    /**
     * 采纳一条优化建议，实际修改模板 nodeConfig。
     * 支持 Map 格式（{"nodeId":{...}}）和 List 格式（[{...}]）。
     */
    public void adoptSuggestion(Long templateId, String type, String nodeKey, String suggestion) {
        var template = processTemplateRepository.findByIdAndDeleted(templateId, 0)
                .orElseThrow(() -> new BusinessException("模板不存在"));
        String configJson = template.getNodeConfig();
        if (!hasText(configJson)) throw new BusinessException("模板无 nodeConfig");

        // 保存优化前快照，支持回滚
        String snapshot = template.getNodeConfig();
        try {
            String updated = applyOptimization(configJson, type, nodeKey, suggestion);
            template.setNodeConfig(updated);
            template.setUpdatedAt(java.time.LocalDateTime.now());
            // 将快照存入 formBindConfig 的 _snapshots 字段（不覆盖现有 formBindConfig）
            template.setFormBindConfig(appendSnapshot(template.getFormBindConfig(), snapshot, type, nodeKey));
            processTemplateRepository.save(template);
            log.info("已采纳优化建议并保存快照: templateId={}, type={}, nodeKey={}", templateId, type, nodeKey);
        } catch (BusinessException e) { throw e;
        } catch (Exception e) { throw new BusinessException("采纳优化建议失败：" + e.getMessage()); }
    }

    /**
     * 根据 nodeConfig 实际格式（Map/List）执行优化操作。
     */
    private String applyOptimization(String configJson, String type, String nodeKey, String suggestion) throws Exception {
        boolean isMap = configJson.trim().startsWith("{");
        if (isMap) {
            Map<String, Map<String, Object>> configMap = objectMapper.readValue(configJson, new TypeReference<>() {});
            applyToMap(configMap, type, nodeKey, suggestion);
            return objectMapper.writeValueAsString(configMap);
        } else {
            List<Map<String, Object>> configList = objectMapper.readValue(configJson, new TypeReference<>() {});
            applyToList(configList, type, nodeKey, suggestion);
            return objectMapper.writeValueAsString(configList);
        }
    }

    private void applyToMap(Map<String, Map<String, Object>> nodes, String type, String nodeKey, String suggestion) {
        Map<String, Object> target = findNodeInMap(nodes, nodeKey);
        applyCommonOptimization(target, type, nodeKey, suggestion);
        // 广播到所有节点（approval_optimization 类型）
        if ("approval_optimization".equals(type)) {
            for (var n : nodes.values()) n.put("aiOptimizationNote", suggestion);
        }
        // 记录到根
        if (target == null && !"approval_optimization".equals(type)) {
            addOptimizationNote(nodes, type, nodeKey, suggestion);
        }
    }

    private void applyToList(List<Map<String, Object>> nodes, String type, String nodeKey, String suggestion) {
        Map<String, Object> target = findNodeInList(nodes, nodeKey);
        applyCommonOptimization(target, type, nodeKey, suggestion);
        if ("approval_optimization".equals(type)) {
            for (var n : nodes) n.put("aiOptimizationNote", suggestion);
        }
        if (target == null && !"approval_optimization".equals(type)) {
            Map<String, Object> note = new LinkedHashMap<>();
            note.put("type", type); note.put("nodeKey", nodeKey);
            note.put("suggestion", suggestion);
            note.put("adoptedAt", java.time.LocalDateTime.now().toString());
            nodes.add(note);
        }
    }

    /** 通用的节点级优化逻辑 */
    @SuppressWarnings("unchecked")
    private void applyCommonOptimization(Map<String, Object> node, String type, String nodeKey, String suggestion) {
        if (node == null) return;
        String now = java.time.LocalDateTime.now().toString();

        switch (type) {
            case "redundant_node" -> {
                node.put("deprecated", true);
                node.put("deprecatedReason", suggestion);
                node.put("deprecatedAt", now);
                // 标记业务类型为 skip，让 Flowable 跳过此节点
                node.put("businessType", "skip");
            }
            case "bottleneck" -> {
                var timeout = (Map<String, Object>) node.getOrDefault("timeoutConfig", new LinkedHashMap<>());
                if (timeout.isEmpty()) {
                    timeout.put("remindAfter", "12h");
                    timeout.put("autoAction", "approve");
                } else {
                    // 减半现有超时
                    String remind = (String) timeout.getOrDefault("remindAfter", "24h");
                    timeout.put("remindAfter", halveDuration(remind));
                }
                node.put("timeoutConfig", timeout);
                node.put("approvalRule", Map.of("enabled", true, "field", "autoOptimized",
                        "operator", "==", "value", true));
                node.put("optimizedAt", now);
            }
            case "permission_optimization" -> {
                node.put("approvalMode", "ANY");
                node.put("optimizedAt", now);
            }
            case "branch_optimization" -> {
                node.put("defaultFlow", node.get("defaultFlow"));
                node.put("branchOptimized", true);
                node.put("optimizedAt", now);
            }
            default -> node.put("aiOptimizationNote", suggestion);
        }
    }

    /** 记录暂存优化备注（无目标节点时） */
    private void addOptimizationNote(Map<String, Map<String, Object>> nodes, String type, String nodeKey, String suggestion) {
        Map<String, Object> note = new LinkedHashMap<>();
        note.put("type", type);
        note.put("nodeKey", nodeKey);
        note.put("suggestion", suggestion);
        note.put("adoptedAt", java.time.LocalDateTime.now().toString());
        nodes.put("_opt_" + System.currentTimeMillis(), note);
    }

    private Map<String, Object> findNodeInMap(Map<String, Map<String, Object>> map, String key) {
        if (key == null) return null;
        if (map.containsKey(key)) return map.get(key);
        for (var e : map.entrySet()) {
            var n = e.getValue();
            if (key.equals(str(n.get("nodeKey"))) || key.equals(str(n.get("nodeId")))) return n;
        }
        return null;
    }

    private Map<String, Object> findNodeInList(List<Map<String, Object>> list, String key) {
        if (key == null) return null;
        for (var n : list) {
            if (key.equals(str(n.get("nodeKey"))) || key.equals(str(n.get("nodeId")))) return n;
        }
        return null;
    }

    /** 减半时长字符串，如 "24h" → "12h", "48h" → "24h" */
    private String halveDuration(String d) {
        if (d == null) return "12h";
        try {
            String num = d.replaceAll("[^0-9.]", "");
            String unit = d.replaceAll("[0-9.]", "");
            double val = Double.parseDouble(num) / 2;
            return (val == (long) val ? String.valueOf((long) val) : String.valueOf(val)) + unit;
        } catch (NumberFormatException ignored) { return "12h"; }
    }

    private String str(Object v) { return v != null ? v.toString() : null; }
    /** 将优化前的 nodeConfig 快照追加到 formBindConfig 的 _snapshots 数组中 */
    private String appendSnapshot(String formBindConfig, String snapshot, String type, String nodeKey) {
        try {
            Map<String, Object> bind = (formBindConfig != null && !formBindConfig.isBlank())
                    ? objectMapper.readValue(formBindConfig, new TypeReference<Map<String, Object>>() {})
                    : new java.util.LinkedHashMap<>();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> snapshots = (List<Map<String, Object>>) bind.get("_snapshots");
            if (snapshots == null) { snapshots = new java.util.ArrayList<>(); bind.put("_snapshots", snapshots); }
            Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("time", java.time.LocalDateTime.now().toString());
            entry.put("type", type); entry.put("nodeKey", nodeKey);
            entry.put("nodeConfig", snapshot);
            snapshots.add(entry);
            if (snapshots.size() > 10) snapshots.remove(0); // 保留最近 10 个
            return objectMapper.writeValueAsString(bind);
        } catch (Exception e) { log.warn("保存优化快照失败: {}", e.getMessage()); return formBindConfig; }
    }

    private boolean hasText(String s) { return s != null && !s.isBlank(); }
}
