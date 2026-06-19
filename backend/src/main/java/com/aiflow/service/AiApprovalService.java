package com.aiflow.service;

import com.aiflow.common.BusinessException;
import com.aiflow.config.AiConfig;
import com.aiflow.dto.AiApprovalRequest;
import com.aiflow.dto.AiApprovalResponse;
import com.aiflow.dto.NotificationCreateRequest;
import com.aiflow.model.*;
import com.aiflow.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiApprovalService {

    private final WebClient deepseekWebClient;
    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper;
    private final ProcessInstanceRepository processInstanceRepository;
    private final FormSubmissionRepository formSubmissionRepository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final AiAdviceRecordRepository aiAdviceRecordRepository;
    private final ApproverResolverService approverResolverService;
    private final NotificationService notificationService;

    private static final String SYSTEM_PROMPT = """
        你是企业审批辅助专家。根据流程上下文和表单数据给出审批建议。

        输出必须是合法的 JSON，不要包裹在 Markdown 代码块中：
        {
          "suggestion": "approve",
          "reason": "该申请金额在授权范围内，申请人历史信用良好",
          "confidence": 0.88,
          "riskPoints": ["供应商合作年限不足1年"]
        }

        规则：
        1. suggestion：approve（建议通过）、reject（建议驳回）、supplement（建议补充材料）
        2. reason：简洁明确的理由，作为审批意见的参考
        3. confidence：0~1 的置信度
        4. riskPoints：风险提示列表，无风险时为空数组
        5. 综合考虑金额、申请人、业务类型等因素
        """;

    public AiApprovalResponse suggest(AiApprovalRequest request) {
        Long instanceId = request.getInstanceId();
        String nodeKey = request.getNodeKey();
        log.info("AI 审批建议：instanceId={}, nodeKey={}", instanceId, nodeKey);

        // 1. 查询流程上下文
        ProcessInstance instance = processInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new BusinessException("流程实例不存在"));
        ProcessTemplate template = processTemplateRepository.findById(instance.getTemplateId())
                .orElseThrow(() -> new BusinessException("流程模板不存在"));

        // 2. 查询当前节点表单数据
        FormSubmission submission = formSubmissionRepository
                .findByProcessInstanceIdAndNodeKeyAndDeleted(instanceId, nodeKey, 0)
                .orElse(null);
        String formData = submission != null && submission.getFormDataJson() != null
                ? submission.getFormDataJson() : "{}";

        // 3. 查询节点配置
        String nodeConfigJson = template.getNodeConfig();
        String nodeName = nodeKey;
        try {
            if (nodeConfigJson != null && !nodeConfigJson.isBlank()) {
                Map<String, Object> nodeConfigMap = objectMapper.readValue(nodeConfigJson,
                        new TypeReference<Map<String, Object>>() {});
                Object nodeObj = nodeConfigMap.get(nodeKey);
                if (nodeObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> nodeMap = (Map<String, Object>) nodeObj;
                    nodeName = (String) nodeMap.getOrDefault("nodeName", nodeName);
                }
            }
        } catch (Exception ignored) { /* ignore */ }

        // 4. 构建 Prompt
        String userPrompt = String.format("""
            流程：%s
            当前节点：%s
            表单数据：%s
            请给出审批建议。""",
                template.getTemplateName(), nodeName, formData);

        // 5. 调用 DeepSeek
        AiApprovalResponse response = callDeepSeek(userPrompt);

        // 6. 保存到 ai_advice_record
        saveAdviceRecord(instanceId, nodeKey, response);

        // 7. 推送通知给当前审批人（通过 ApproverResolverService 获取）
        try {
            List<Long> approvers = approverResolverService.resolveApprovers(
                    instanceId, nodeKey, "DIRECT_SUPERVISOR", "");
            for (Long approverId : approvers) {
                NotificationCreateRequest nReq = new NotificationCreateRequest();
                nReq.setReceiverId(approverId);
                nReq.setType("ai_suggestion");
                nReq.setTitle("AI 已生成审批建议");
                nReq.setContent(response.getReason());
                nReq.setTargetType("task");
                nReq.setTargetUrl("/tasks/todo");
                notificationService.createNotification(nReq);
            }
        } catch (Exception e) {
            log.warn("推送 AI 建议通知失败: {}", e.getMessage());
        }

        return response;
    }

    private AiApprovalResponse callDeepSeek(String userPrompt) {
        Map<String, Object> requestBody = Map.of(
                "model", aiConfig.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", 0.3,
                "max_tokens", 2048);

        String responseBody;
        try {
            responseBody = deepseekWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(),
                            cr -> cr.bodyToMono(String.class)
                                    .map(b -> new RuntimeException("DeepSeek 错误：" + b)))
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(aiConfig.getTimeoutSeconds()))
                    .block();
        } catch (Exception e) {
            log.error("DeepSeek 调用失败", e);
            throw new BusinessException("AI 服务调用失败：" + e.getMessage());
        }

        if (responseBody == null) throw new BusinessException("AI 返回为空");

        String json;
        try {
            Map<String, Object> map = objectMapper.readValue(responseBody,
                    new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            var choices = (List<Map<String, Object>>) map.get("choices");
            if (choices == null || choices.isEmpty())
                throw new BusinessException("AI 返回格式异常");
            var msg = (Map<String, Object>) choices.get(0).get("message");
            json = (String) msg.get("content");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("AI 响应解析失败");
        }

        if (json == null) throw new BusinessException("AI 返回内容为空");
        json = json.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("```(?:json)?\\s*\\n?", "");
            json = json.replaceFirst("\\n?```\\s*$", "");
        }

        try {
            return objectMapper.readValue(json, AiApprovalResponse.class);
        } catch (Exception e) {
            log.error("解析 AI 建议 JSON 失败: {}", json);
            throw new BusinessException("AI 建议格式有误，请重试");
        }
    }

    private void saveAdviceRecord(Long instanceId, String nodeKey, AiApprovalResponse resp) {
        AiAdviceRecord record = new AiAdviceRecord();
        record.setInstanceId(instanceId);
        record.setNodeKey(nodeKey);
        record.setAdviceType(mapAdviceType(resp.getSuggestion()));
        record.setAdviceContent(resp.getReason());
        record.setConfidence(resp.getConfidence());
        record.setModelName(aiConfig.getModel());
        record.setModelVersion("1.0");
        try {
            record.setRiskPoints(resp.getRiskPoints() != null
                    ? objectMapper.writeValueAsString(resp.getRiskPoints()) : null);
        } catch (Exception ignored) { /* ignore */ }

        // 查找关联的业务 task ID
        try {
            String businessKey = "instance_" + instanceId;
            record.setTaskId(instanceId); // 简化：使用 instanceId
        } catch (Exception ignored) { /* ignore */ }

        aiAdviceRecordRepository.save(record);
        log.info("AI 建议已保存：instanceId={}, adviceType={}", instanceId, record.getAdviceType());
    }

    private String mapAdviceType(String suggestion) {
        if (suggestion == null) return "verify";
        return switch (suggestion) {
            case "approve" -> "pass";
            case "reject" -> "reject";
            case "supplement" -> "verify";
            default -> "verify";
        };
    }
}
