package com.aiflow.service;

import com.aiflow.common.BusinessException;
import com.aiflow.config.AiConfig;
import com.aiflow.dto.AiGenerateProcessResponse;
import com.aiflow.dto.NodeConfigItem;
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
public class AiProcessService {

    private final WebClient deepseekWebClient;
    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
        你是一个 BPMN 2.0 工作流生成专家。用户用自然语言描述流程，你需要生成标准 BPMN 2.0 XML 和节点配置 JSON。

        输出必须是合法的 JSON 对象，不要包裹在 Markdown 代码块中：

        {
          "bpmnXml": "<BPMN XML 字符串>",
          "nodeConfig": [
            {"nodeKey": "StartEvent_1", "nodeName": "开始", "businessType": "start"},
            {"nodeKey": "UserTask_1",   "nodeName": "经理审批", "businessType": "approval", "approvalMode": "SINGLE", "assignStrategy": "DIRECT_SUPERVISOR"},
            {"nodeKey": "EndEvent_1",   "nodeName": "结束", "businessType": "end"}
          ],
          "summary": "流程简短摘要"
        }

        BPMN XML 必须遵循以下格式（参照示例，使用 bpmn: 命名空间前缀）：

        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
          xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
          xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
          xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
          targetNamespace="http://ai-flow/process">
          <bpmn:process id="Process_Main" name="流程名称" isExecutable="true">
            <bpmn:startEvent id="StartEvent_1" name="开始">
              <bpmn:outgoing>Flow_1</bpmn:outgoing>
            </bpmn:startEvent>
            <bpmn:userTask id="UserTask_1" name="审批节点">
              <bpmn:incoming>Flow_1</bpmn:incoming>
              <bpmn:outgoing>Flow_2</bpmn:outgoing>
            </bpmn:userTask>
            <bpmn:endEvent id="EndEvent_1" name="结束">
              <bpmn:incoming>Flow_2</bpmn:incoming>
            </bpmn:endEvent>
            <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="UserTask_1" />
            <bpmn:sequenceFlow id="Flow_2" sourceRef="UserTask_1" targetRef="EndEvent_1" />
          </bpmn:process>
          <bpmndi:BPMNDiagram>
            <bpmndi:BPMNPlane bpmnElement="Process_Main">
              <bpmndi:BPMNShape bpmnElement="StartEvent_1">
                <dc:Bounds x="180" y="160" width="36" height="36" />
              </bpmndi:BPMNShape>
              <bpmndi:BPMNShape bpmnElement="UserTask_1">
                <dc:Bounds x="280" y="138" width="120" height="80" />
              </bpmndi:BPMNShape>
              <bpmndi:BPMNShape bpmnElement="EndEvent_1">
                <dc:Bounds x="470" y="160" width="36" height="36" />
              </bpmndi:BPMNShape>
              <bpmndi:BPMNEdge bpmnElement="Flow_1">
                <di:waypoint x="216" y="178" />
                <di:waypoint x="280" y="178" />
              </bpmndi:BPMNEdge>
              <bpmndi:BPMNEdge bpmnElement="Flow_2">
                <di:waypoint x="400" y="178" />
                <di:waypoint x="470" y="178" />
              </bpmndi:BPMNEdge>
            </bpmndi:BPMNPlane>
          </bpmndi:BPMNDiagram>
        </bpmn:definitions>

        必须遵守：
        1. 所有元素使用 bpmn: 前缀（如 bpmn:userTask、bpmn:startEvent、bpmn:exclusiveGateway、bpmn:serviceTask）
        2. 每个流元素必须有 <bpmn:incoming> 和 <bpmn:outgoing> 子元素
        3. 排他网关必须包含条件表达式，如 <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression"><![CDATA[${days <= 3}]]></bpmn:conditionExpression>
        4. sequenceFlow 按从左到右编号：Flow_1、Flow_2...
        5. DI 布局：节点从左到右排列，垂直间隔约 100px。开始和结束 y=160，用户任务 y=138
        6. nodeConfig 的 nodeKey 必须与 BPMN 元素的 id 完全一致
        7. 审批节点必须设置 approvalMode（SINGLE/ALL/ANY）+ assignStrategy（DEPARTMENT_MANAGER/DIRECT_SUPERVISOR/SPECIFIC_USERS）
        8. 抄送节点使用 bpmn:serviceTask，businessType="notify"，设置 notifyTarget（APPLICANT/APPROVER/USER）、notifyChannel（in_app/email/both）
        9. 排他网关 businessType="condition"
        10. 流程保持简洁，不超过 8 个节点
        """;

    public AiGenerateProcessResponse generateProcess(String description) {
        log.info("开始生成流程，用户输入长度：{}", description.length());

        // 1. 构建请求体
        Map<String, Object> requestBody = Map.of(
            "model", aiConfig.getModel(),
            "messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", description)
            ),
            "temperature", 0.3,
            "max_tokens", 4096
        );

        // 2. 调用 DeepSeek API
        String responseBody;
        try {
            responseBody = deepseekWebClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .map(body -> new RuntimeException("DeepSeek API 调用失败：" + body)))
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(aiConfig.getTimeoutSeconds()))
                .block();
        } catch (Exception e) {
            log.error("DeepSeek API 调用异常", e);
            throw new BusinessException("AI 服务调用失败：" + e.getMessage());
        }

        if (responseBody == null) {
            throw new BusinessException("AI 服务返回为空");
        }

        // 3. 提取 DeepSeek 回复中的 JSON
        String json;
        try {
            // DeepSeek 返回格式与 OpenAI 一致：choices[0].message.content
            Map<String, Object> responseMap = objectMapper.readValue(responseBody,
                new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new BusinessException("AI 服务返回数据格式异常：无 choices");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            json = (String) message.get("content");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析 DeepSeek 响应失败", e);
            throw new BusinessException("AI 服务响应解析失败");
        }

        // 4. 清理可能的 Markdown 代码块包裹
        json = json.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("```(?:json)?\\s*\\n?", "");
            json = json.replaceFirst("\\n?```\\s*$", "");
        }

        // 5. 解析为响应对象
        AiGenerateProcessResponse result;
        try {
            result = objectMapper.readValue(json, AiGenerateProcessResponse.class);
        } catch (Exception e) {
            log.error("解析 AI 生成的 JSON 失败，原始内容：{}", json);
            throw new BusinessException("AI 生成的格式有误，请重试");
        }

        // 6. 校验 BPMN XML 合法性
        String bpmnXml = result.getBpmnXml();
        // 兼容 bpmn:definitions 和 definitions 两种写法
        boolean hasDefinitions = bpmnXml.contains("<definitions") || bpmnXml.contains("<bpmn:definitions");
        boolean hasProcess = bpmnXml.contains("<process") || bpmnXml.contains("<bpmn:process");
        if (bpmnXml == null || !hasDefinitions || !hasProcess) {
            log.error("AI 生成的 BPMN XML 不合法：{}", bpmnXml);
            throw new BusinessException("AI 生成的 BPMN XML 不合法，缺少 definitions 或 process 标签，请重试");
        }
        if (!bpmnXml.contains("isExecutable=\"true\"")) {
            log.warn("BPMN XML 缺少 isExecutable=true，尝试修复");
            bpmnXml = bpmnXml.replaceFirst("<bpmn:process\\s", "<bpmn:process isExecutable=\"true\" ");
            bpmnXml = bpmnXml.replaceFirst("<process\\s", "<process isExecutable=\"true\" ");
            result.setBpmnXml(bpmnXml);
        }

        log.info("流程生成成功，节点数：{}", result.getNodeConfig() != null ? result.getNodeConfig().size() : 0);
        return result;
    }

    private static final String FORM_SYSTEM_PROMPT = """
        你是一个智能表单设计专家。用户会用自然语言描述需要采集的数据，你需要将其转换为标准的表单字段配置 JSON。

        输出必须是合法的 JSON 对象，不要包裹在 Markdown 代码块中。格式固定为：

        {
          "formName": "表单名称",
          "formCode": "form_英文编码",
          "fields": [
            {
              "field": "字段标识(英文驼峰)",
              "label": "字段显示名称",
              "type": "text|textarea|number|select|radio|checkbox|date|datetime|upload",
              "required": true|false,
              "placeholder": "占位提示文字",
              "options": [{"label": "选项1", "value": "1"}]  仅 select/radio/checkbox 需要
            }
          ],
          "summary": "表单用途的简要说明"
        }

        规则：
        1. field 使用英文驼峰命名，如 leaveReason、startDate、leaveDays
        2. type 取值严格限制：text(单行文本)、textarea(多行文本)、number(数字)、select(下拉单选)、radio(单选)、checkbox(多选)、date(日期)、datetime(日期时间)、upload(附件)
        3. select/radio/checkbox 必须提供 options 数组
        4. 合理的 required 判断：关键信息必填，备注类选填
        5. 表单名称和编码要贴合业务场景
        """;

    public Map<String, Object> generateForm(String description) {
        log.info("开始生成表单，用户输入长度：{}", description.length());

        Map<String, Object> requestBody = Map.of(
            "model", aiConfig.getModel(),
            "messages", List.of(
                Map.of("role", "system", "content", FORM_SYSTEM_PROMPT),
                Map.of("role", "user", "content", description)
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

        String json;
        try {
            Map<String, Object> respMap = objectMapper.readValue(responseBody, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
            if (choices == null || choices.isEmpty()) throw new BusinessException("AI 返回格式异常");
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            json = (String) msg.get("content");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析响应失败", e);
            throw new BusinessException("AI 服务响应解析失败");
        }

        json = json.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("```(?:json)?\\s*\\n?", "");
            json = json.replaceFirst("\\n?```\\s*$", "");
        }

        try {
            Map<String, Object> result = objectMapper.readValue(json, new TypeReference<>() {});
            log.info("表单生成成功，字段数：{}", result.get("fields") instanceof List<?> l ? l.size() : 0);
            return result;
        } catch (Exception e) {
            log.error("解析AI生成的表单JSON失败：{}", json);
            throw new BusinessException("AI 生成的表单格式有误，请重试");
        }
    }
}
