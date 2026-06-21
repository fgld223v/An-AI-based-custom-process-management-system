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
            {"nodeKey": "UserTask_1",   "nodeName": "填写申请表", "businessType": "form_fill"},
            {"nodeKey": "UserTask_2",   "nodeName": "经理审批", "businessType": "approval", "approvalMode": "SINGLE", "assignStrategy": "DIRECT_SUPERVISOR"},
            {"nodeKey": "EndEvent_1",   "nodeName": "结束", "businessType": "end"}
          ],
          "summary": "流程简短摘要"
        }

        重要的 businessType 说明（必须理解并正确使用）：
        - start：流程开始节点，对应 bpmn:startEvent
        - form_fill：表单填写节点（如填写申请、提交材料、补录信息等），对应 bpmn:userTask。任何需要用户填写/提交数据的环节都应该用 form_fill，不要用 approval
        - approval：审批处理节点（如经理审批、总监审批等），对应 bpmn:userTask。只有明确涉及审批决策的环节才用 approval
        - condition：条件分支/排他网关，对应 bpmn:exclusiveGateway
        - parallel：并行网关，对应 bpmn:parallelGateway
        - notify：抄送通知节点，对应 bpmn:serviceTask，设置 notifyTarget、notifyChannel
        - system_action：系统自动处理节点，对应 bpmn:serviceTask
        - end：流程结束节点，对应 bpmn:endEvent

        区分 form_fill 和 approval 的原则：
        - 如果用户需要填写信息、提交申请、上传材料 → 使用 form_fill
        - 如果用户需要审核/批准/驳回他人的申请 → 使用 approval
        - 一个典型的审批流程应该是：start → form_fill（填写申请）→ approval（审批）→ end
        - 不要把填写申请也标记为 approval！

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
            <bpmn:userTask id="UserTask_1" name="填写申请">
              <bpmn:incoming>Flow_1</bpmn:incoming>
              <bpmn:outgoing>Flow_2</bpmn:outgoing>
            </bpmn:userTask>
            <bpmn:userTask id="UserTask_2" name="审批节点">
              <bpmn:incoming>Flow_2</bpmn:incoming>
              <bpmn:outgoing>Flow_3</bpmn:outgoing>
            </bpmn:userTask>
            <bpmn:endEvent id="EndEvent_1" name="结束">
              <bpmn:incoming>Flow_3</bpmn:incoming>
            </bpmn:endEvent>
            <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="UserTask_1" />
            <bpmn:sequenceFlow id="Flow_2" sourceRef="UserTask_1" targetRef="UserTask_2" />
            <bpmn:sequenceFlow id="Flow_3" sourceRef="UserTask_2" targetRef="EndEvent_1" />
          </bpmn:process>
          <bpmndi:BPMNDiagram>
            <bpmndi:BPMNPlane bpmnElement="Process_Main">
              <bpmndi:BPMNShape bpmnElement="StartEvent_1">
                <dc:Bounds x="180" y="160" width="36" height="36" />
              </bpmndi:BPMNShape>
              <bpmndi:BPMNShape bpmnElement="UserTask_1">
                <dc:Bounds x="280" y="138" width="120" height="80" />
              </bpmndi:BPMNShape>
              <bpmndi:BPMNShape bpmnElement="UserTask_2">
                <dc:Bounds x="470" y="138" width="120" height="80" />
              </bpmndi:BPMNShape>
              <bpmndi:BPMNShape bpmnElement="EndEvent_1">
                <dc:Bounds x="660" y="160" width="36" height="36" />
              </bpmndi:BPMNShape>
              <bpmndi:BPMNEdge bpmnElement="Flow_1">
                <di:waypoint x="216" y="178" />
                <di:waypoint x="280" y="178" />
              </bpmndi:BPMNEdge>
              <bpmndi:BPMNEdge bpmnElement="Flow_2">
                <di:waypoint x="400" y="178" />
                <di:waypoint x="470" y="178" />
              </bpmndi:BPMNEdge>
              <bpmndi:BPMNEdge bpmnElement="Flow_3">
                <di:waypoint x="590" y="178" />
                <di:waypoint x="660" y="178" />
              </bpmndi:BPMNEdge>
            </bpmndi:BPMNPlane>
          </bpmndi:BPMNDiagram>
        </bpmn:definitions>

        必须遵守：
        1. 所有元素使用 bpmn: 前缀（如 bpmn:userTask、bpmn:startEvent、bpmn:exclusiveGateway、bpmn:serviceTask）
        2. 每个流元素必须有 <bpmn:incoming> 和 <bpmn:outgoing> 子元素
        3. 排他网关必须包含条件表达式，如 <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression"><![CDATA[${days <= 3}]]></bpmn:conditionExpression>
        4. sequenceFlow 按从左到右顺序编号：Flow_1、Flow_2...
        5. DI 布局规则（保证流程图整齐）：
           - 节点从左到右水平排列，每个节点水平间距至少 150px
           - 开始事件和结束事件 y 坐标固定为 160；用户任务 y=138；排他网关 y=148；服务任务 y=158
           - 第一个节点 x=180，之后每个节点 x 递增 150~200px
           - waypoint 必须与对应 BPMNShape 的 x/y 对齐，不要出现交叉连线
           - 如果有分支路径（排他网关），主路径在上半部分（y 较小），分支路径在下半部分（y 较大），两条路径在汇聚点合并
        6. nodeConfig 的 nodeKey 必须与 BPMN 元素的 id 完全一致
        7. 【关键】审批节点（businessType="approval"）必须设置 approvalMode（SINGLE/ALL/ANY）和 assignStrategy（DEPARTMENT_MANAGER/DIRECT_SUPERVISOR/SPECIFIC_USERS/ROLE）。不设置会导致流程卡死！
        8. 抄送节点使用 bpmn:serviceTask，businessType="notify"，设置 notifyTarget（APPLICANT/APPROVER/USER）、notifyChannel（in_app/email/both）
        9. 排他网关 businessType="condition"
        10. 流程保持简洁，不超过 8 个节点
        11. 重要：填写申请/提交材料环节用 form_fill，审批决策环节用 approval，不要混用！
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
            throw new BusinessException("AI 生成的格式有误，请重试。原始输出: " + truncate(json, 300));
        }

        // 6. 校验 BPMN XML 合法性
        String bpmnXml = result.getBpmnXml();
        boolean hasDefinitions = bpmnXml != null &&
                (bpmnXml.contains("<definitions") || bpmnXml.contains("<bpmn:definitions"));
        boolean hasProcess = bpmnXml != null &&
                (bpmnXml.contains("<process") || bpmnXml.contains("<bpmn:process"));
        if (!hasDefinitions || !hasProcess) {
            log.error("AI 生成的 BPMN XML 不合法：{}", bpmnXml);
            throw new BusinessException("AI 生成的 BPMN XML 不合法，缺少 definitions 或 process 标签，请重试");
        }
        if (!bpmnXml.contains("isExecutable=\"true\"")) {
            log.warn("BPMN XML 缺少 isExecutable=true，尝试修复");
            bpmnXml = bpmnXml.replaceFirst("<bpmn:process\\s", "<bpmn:process isExecutable=\"true\" ");
            bpmnXml = bpmnXml.replaceFirst("<process\\s", "<process isExecutable=\"true\" ");
            result.setBpmnXml(bpmnXml);
        }

        // ====== 7. 后处理：规范化 nodeConfig ======
        List<NodeConfigItem> nodes = result.getNodeConfig();
        if (nodes == null || nodes.isEmpty()) {
            throw new BusinessException("AI 未生成节点配置，请重试");
        }

        // 7a. 提取 BPMN XML 中所有元素 ID
        java.util.Set<String> bpmnIds = extractBpmnElementIds(bpmnXml);
        log.info("BPMN 元素 ID 集合：{}", bpmnIds);

        // 7b. 交叉校验 nodeConfig.nodeKey ↔ BPMN element id
        for (NodeConfigItem node : nodes) {
            if (node.getNodeKey() == null || node.getNodeKey().isBlank()) {
                throw new BusinessException("nodeConfig 中存在空 nodeKey，请重试");
            }
            if (!bpmnIds.contains(node.getNodeKey())) {
                // 尝试按节点名称模糊匹配
                String matchedId = findBpmnIdByName(bpmnXml, node.getNodeName());
                if (matchedId != null && bpmnIds.contains(matchedId)) {
                    log.warn("nodeKey={} 在 BPMN 中不存在，已按节点名自动修正为 {}", node.getNodeKey(), matchedId);
                    node.setNodeKey(matchedId);
                } else {
                    log.warn("nodeKey={} 在 BPMN XML 中找不到对应元素，流程可能发布失败", node.getNodeKey());
                }
            }
        }

        // 7c. 审批节点兜底：缺少 assignStrategy 时默认 DEPARTMENT_MANAGER
        for (NodeConfigItem node : nodes) {
            if ("approval".equals(node.getBusinessType())) {
                if (node.getApprovalMode() == null || node.getApprovalMode().isBlank()) {
                    node.setApprovalMode("SINGLE");
                    log.info("审批节点 {} 未设置 approvalMode，默认 SINGLE", node.getNodeKey());
                }
                if (node.getAssignStrategy() == null || node.getAssignStrategy().isBlank()) {
                    node.setAssignStrategy("DEPARTMENT_MANAGER");
                    log.info("审批节点 {} 未设置 assignStrategy，默认 DEPARTMENT_MANAGER", node.getNodeKey());
                }
            }
        }

        // 7d. 校验 sequenceFlow 连通性
        validateSequenceFlowConnectivity(bpmnXml, bpmnIds);

        // 7e. 确保 nodeConfig 中的 nodeKey 唯一
        java.util.Set<String> seenKeys = new java.util.HashSet<>();
        for (NodeConfigItem node : nodes) {
            if (!seenKeys.add(node.getNodeKey())) {
                log.warn("nodeConfig 中存在重复 nodeKey: {}", node.getNodeKey());
            }
        }

        log.info("流程生成成功，节点数：{}（校验通过）", nodes.size());
        return result;
    }

    // ====== 辅助方法 ======

    /** 提取 BPMN XML 中所有带 id 属性的元素 ID */
    private java.util.Set<String> extractBpmnElementIds(String xml) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\sid\\s*=\\s*\"([^\"]+)\"")
                .matcher(xml);
        while (m.find()) {
            ids.add(m.group(1));
        }
        return ids;
    }

    /** 按节点名称在 BPMN XML 中查找匹配的元素 ID */
    private String findBpmnIdByName(String xml, String nodeName) {
        if (nodeName == null || nodeName.isBlank()) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "<(?:bpmn:)?(?:userTask|startEvent|endEvent|serviceTask|sendTask|exclusiveGateway|parallelGateway)\\s[^>]*\\bid\\s*=\\s*\"([^\"]+)\"[^>]*\\bname\\s*=\\s*\"" +
                        java.util.regex.Pattern.quote(nodeName) + "\"",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xml);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /** 校验 sequenceFlow 的 sourceRef 和 targetRef 指向存在的元素 */
    private void validateSequenceFlowConnectivity(String xml, java.util.Set<String> knownIds) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "<(?:bpmn:)?sequenceFlow\\s[^>]*\\bsourceRef\\s*=\\s*\"([^\"]+)\"[^>]*\\btargetRef\\s*=\\s*\"([^\"]+)\""
        ).matcher(xml);
        int flowCount = 0;
        int brokenCount = 0;
        while (m.find()) {
            flowCount++;
            String src = m.group(1);
            String tgt = m.group(2);
            if (!knownIds.contains(src)) {
                log.warn("sequenceFlow sourceRef=\"{}\" 在 BPMN 中找不到对应元素", src);
                brokenCount++;
            }
            if (!knownIds.contains(tgt)) {
                log.warn("sequenceFlow targetRef=\"{}\" 在 BPMN 中找不到对应元素", tgt);
                brokenCount++;
            }
        }
        if (flowCount == 0) {
            log.warn("BPMN XML 中未找到任何 sequenceFlow 元素，流程可能无法流转");
        }
        if (brokenCount > 0) {
            log.warn("BPMN sequenceFlow 连通性校验：{} 条连线中有 {} 条引用了不存在的元素", flowCount, brokenCount);
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

}
