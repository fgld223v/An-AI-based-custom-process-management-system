package com.aiflow.service;

import com.aiflow.common.BusinessException;
import com.aiflow.config.AiConfig;
import com.aiflow.dto.AiApprovalSuggestionDTO;
import com.aiflow.model.FormSubmission;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.FormSubmissionRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

/**
 * AI 智能审批建议服务。
 *
 * <p>在审批节点，调用 DeepSeek 分析表单提交数据和流程上下文，
 * 给出审批建议（通过/驳回/补充材料）、推理依据、置信度和风险点。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiApprovalService {

    private final WebClient deepseekWebClient;
    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper;
    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final FormSubmissionRepository formSubmissionRepository;

    private static final String SYSTEM_PROMPT = """
        你是一个企业流程审批决策辅助专家。你需要根据表单数据和流程上下文，给出专业的审批建议。

        输出必须是合法的 JSON 对象，不要包裹在 Markdown 代码块中：
        {
          "suggestion": "approve",
          "reason": "请假天数3天，属于正常范围，理由充分，建议通过",
          "confidence": 0.92,
          "riskPoints": []
        }

        规则：
        1. suggestion 取值：approve（建议通过）、reject（建议驳回）、supplement（建议补充材料/退回修改）
        2. reason 要具体，引用实际数据说明判断依据
        3. confidence 0.0-1.0，表示对建议的把握程度
        4. riskPoints 列出需要注意的风险点（空数组表示无风险），如：["请假天数偏长(15天)","金额超过常规范围","缺少发票凭证"]
        5. 审批结果字段为"agree"时倾向 approve，"reject"时倾向 reject

        常见判断逻辑：
        - 请假：<=3天通常通过，3-7天需关注原因，>7天需严格审查
        - 报销：金额在合理范围内通过，缺少凭证建议补充材料
        - 采购：金额与市场价格匹配通过，异常高价需注意
        - 无异常数据、理由充分 → approve
        - 数据缺失、格式错误、金额异常 → supplement
        - 明显违规、超出权限、弄虚作假 → reject
        """;

    @Transactional(readOnly = true)
    public AiApprovalSuggestionDTO suggest(Long instanceId, String nodeKey) {
        // 1. 查询流程实例和模板
        ProcessInstance instance = processInstanceRepository
                .findByIdAndDeleted(instanceId, 0)
                .orElseThrow(() -> new IllegalArgumentException("流程实例不存在"));

        ProcessTemplate template = processTemplateRepository
                .findByIdAndDeleted(instance.getTemplateId(), 0)
                .orElse(null);

        // 2. 收集所有已提交的表单数据
        List<FormSubmission> submissions = formSubmissionRepository
                .findByProcessInstanceIdAndDeletedOrderByUpdatedAtDescCreatedAtDesc(instanceId, 0);

        List<Map<String, Object>> submissionList = new ArrayList<>();
        for (FormSubmission sub : submissions) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("nodeKey", sub.getNodeKey());
            info.put("nodeName", sub.getNodeName());
            info.put("businessType", sub.getBusinessType());
            if (sub.getFormDataJson() != null && !sub.getFormDataJson().isBlank()) {
                try {
                    Map<String, Object> data = objectMapper.readValue(
                            sub.getFormDataJson(), new TypeReference<Map<String, Object>>() {});
                    info.put("formData", data);
                } catch (Exception e) {
                    info.put("formData", sub.getFormDataJson());
                }
            }
            submissionList.add(info);
        }

        // 3. 构建发给 AI 的上下文
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("instanceTitle", instance.getTitle());
        context.put("instanceCode", instance.getInstanceCode());
        context.put("currentNodeKey", nodeKey);
        context.put("currentNodeName", instance.getCurrentNodeName());
        context.put("templateName", template != null ? template.getTemplateName() : "未知模板");
        context.put("formSubmissions", submissionList);

        String userMessage;
        try {
            userMessage = objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            userMessage = "流程实例 " + instanceId + "，节点 " + nodeKey;
        }

        log.info("AI 审批建议请求：instanceId={}, nodeKey={}, 表单提交数={}",
                instanceId, nodeKey, submissions.size());

        // 4. 调用 DeepSeek
        Map<String, Object> requestBody = Map.of(
                "model", aiConfig.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMessage)
                ),
                "temperature", 0.2,
                "max_tokens", 2048
        );

        String responseBody;
        try {
            responseBody = deepseekWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(),
                            r -> r.bodyToMono(String.class)
                                    .map(b -> new RuntimeException("DeepSeek API 调用失败：" + b)))
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(aiConfig.getTimeoutSeconds()))
                    .block();
        } catch (Exception e) {
            log.error("DeepSeek API 调用异常", e);
            throw new BusinessException("AI 审批建议服务调用失败：" + e.getMessage());
        }

        if (responseBody == null) {
            throw new BusinessException("AI 审批建议服务返回为空");
        }

        // 5. 提取 AI 回复
        String json;
        try {
            Map<String, Object> respMap = objectMapper.readValue(responseBody,
                    new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new BusinessException("AI 审批建议返回格式异常");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            json = (String) message.get("content");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析 AI 审批建议响应失败", e);
            throw new BusinessException("AI 审批建议响应解析失败");
        }

        // 6. 清理 Markdown
        json = json.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("```(?:json)?\\s*\\n?", "");
            json = json.replaceFirst("\\n?```\\s*$", "");
        }

        // 7. 解析为 DTO
        AiApprovalSuggestionDTO result;
        try {
            result = objectMapper.readValue(json, AiApprovalSuggestionDTO.class);
        } catch (Exception e) {
            log.error("解析 AI 审批建议 JSON 失败：{}", json);
            throw new BusinessException("AI 审批建议格式有误，请重试");
        }

        // 8. 保存到 ai_advice_record 表（如果有对应的 AdviceRecordService）
        log.info("AI 审批建议生成成功：suggestion={}, confidence={}",
                result.getSuggestion(), result.getConfidence());

        return result;
    }
}
