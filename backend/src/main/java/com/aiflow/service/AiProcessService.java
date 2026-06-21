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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        示例1 — 简单请假审批流程：
        {
          "bpmnXml": "<BPMN XML 字符串（见下方XML格式要求）>",
          "nodeConfig": [
            {"nodeKey": "StartEvent_1", "nodeName": "开始", "businessType": "start"},
            {"nodeKey": "UserTask_1",   "nodeName": "填写请假申请", "businessType": "form_fill"},
            {"nodeKey": "UserTask_2",   "nodeName": "主管审批", "businessType": "approval", "approvalMode": "SINGLE", "assignStrategy": "DIRECT_SUPERVISOR"},
            {"nodeKey": "EndEvent_1",   "nodeName": "结束", "businessType": "end"}
          ],
          "summary": "员工提交请假申请后由主管审批"
        }

        示例2 — 带条件分支的报销流程：
        {
          "bpmnXml": "<BPMN XML 字符串>",
          "nodeConfig": [
            {"nodeKey": "StartEvent_1", "nodeName": "开始", "businessType": "start"},
            {"nodeKey": "UserTask_1",   "nodeName": "填写报销单", "businessType": "form_fill"},
            {"nodeKey": "Gateway_1",    "nodeName": "金额判断", "businessType": "condition"},
            {"nodeKey": "UserTask_2",   "nodeName": "部门经理审批", "businessType": "approval", "approvalMode": "SINGLE", "assignStrategy": "DEPARTMENT_MANAGER"},
            {"nodeKey": "UserTask_3",   "nodeName": "总经理审批", "businessType": "approval", "approvalMode": "SINGLE", "assignStrategy": "ROLE"},
            {"nodeKey": "ServiceTask_1","nodeName": "财务打款", "businessType": "system_action"},
            {"nodeKey": "ServiceTask_2","nodeName": "抄送申请人", "businessType": "notify", "notifyTarget": "APPLICANT", "notifyChannel": "in_app"},
            {"nodeKey": "EndEvent_1",   "nodeName": "结束", "businessType": "end"}
          ],
          "summary": "员工提交报销单，根据金额走不同审批路径"
        }

        示例3 — 带会签的采购审批流程：
        {
          "bpmnXml": "<BPMN XML 字符串>",
          "nodeConfig": [
            {"nodeKey": "StartEvent_1", "nodeName": "开始", "businessType": "start"},
            {"nodeKey": "UserTask_1",   "nodeName": "提交采购需求", "businessType": "form_fill"},
            {"nodeKey": "UserTask_2",   "nodeName": "部门经理审核", "businessType": "approval", "approvalMode": "SINGLE", "assignStrategy": "DEPARTMENT_MANAGER"},
            {"nodeKey": "UserTask_3",   "nodeName": "财务会签", "businessType": "approval", "approvalMode": "ALL", "assignStrategy": "ROLE"},
            {"nodeKey": "UserTask_4",   "nodeName": "总经理批准", "businessType": "approval", "approvalMode": "SINGLE", "assignStrategy": "ROLE"},
            {"nodeKey": "EndEvent_1",   "nodeName": "结束", "businessType": "end"}
          ],
          "summary": "采购需求经部门审核、财务会签、总经理批准后完成"
        }

        ═══════════════════════════════════════════════════════════════
        【最重要】节点类型关键词匹配规则 — 必须严格遵守，优先级最高！
        ═══════════════════════════════════════════════════════════════

        规则1 — 审批类关键词 → businessType 必须为 "approval"：
          节点名包含以下任一关键词 → 必须是 approval，对应 bpmn:userTask
          「审批」「审核」「批准」「核准」「复核」「签批」「阅批」「同意」「否决」「驳回」

        规则2 — 填写类关键词 → businessType 必须为 "form_fill"：
          节点名包含以下任一关键词 → 必须是 form_fill，对应 bpmn:userTask
          「填写」「提交」「录入」「上传」「申请」「补录」「登记」「申报」

        规则3 — 通知类关键词 → businessType 必须为 "notify"：
          节点名包含以下任一关键词 → 必须是 notify，对应 bpmn:serviceTask
          「通知」「抄送」「知会」「提醒」「推送」

        规则4 — 判断类关键词 → businessType 必须为 "condition"：
          节点名包含以下任一关键词 → 必须是 condition，对应 bpmn:exclusiveGateway
          「判断」「选择」「分支」「条件」

        ═══════════════════════════════════════════════════════════════
        常见错误示例（绝对不要这样输出！）：
        ═══════════════════════════════════════════════════════════════
        ❌ 错误：{"nodeName": "主管审批", "businessType": "form_fill"}
           原因：节点名含「审批」，必须是 approval
        ❌ 错误：{"nodeName": "经理审核", "businessType": "form_fill"}
           原因：节点名含「审核」，必须是 approval
        ❌ 错误：{"nodeName": "填写报销单", "businessType": "approval"}
           原因：节点名含「填写」，必须是 form_fill
        ❌ 错误：{"nodeName": "提交申请材料", "businessType": "approval"}
           原因：节点名含「提交」「申请」，必须是 form_fill
        ❌ 错误：{"nodeName": "通知结果", "businessType": "system_action"}
           原因：节点名含「通知」，必须是 notify

        ═══════════════════════════════════════════════════════════════
        businessType 完整说明：
        ═══════════════════════════════════════════════════════════════
        - start：流程开始节点 → bpmn:startEvent
        - form_fill：表单填写节点 → bpmn:userTask（填写/提交数据，用户是提交人自己）
        - approval：审批处理节点 → bpmn:userTask（审核/批准/驳回他人申请，必须设置 approvalMode 和 assignStrategy）
        - condition：条件分支/排他网关 → bpmn:exclusiveGateway（必须设置 conditionExpression）
        - parallel：并行网关 → bpmn:parallelGateway
        - notify：抄送通知节点 → bpmn:serviceTask（设置 notifyTarget、notifyChannel）
        - system_action：系统自动处理节点 → bpmn:serviceTask
        - end：流程结束节点 → bpmn:endEvent

        form_fill vs approval 的本质区别：
        - form_fill = 申请人的动作（自己填写、自己提交数据）
        - approval = 审批人的动作（审核别人的申请、做批准/驳回决策）
        - 典型顺序：start → form_fill → approval → ... → end
        - 一句话：填写/提交 = form_fill，审批/审核 = approval

        BPMN XML 必须遵循以下格式（使用 bpmn: 命名空间前缀）：

        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
          xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
          xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
          xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
          targetNamespace="http://ai-flow/process">
          <bpmn:process id="Process_Main" name="流程名称" isExecutable="true">
            <!-- 按照 nodeConfig 的顺序生成对应的 BPMN 元素 -->
            <!-- form_fill 和 approval 都使用 bpmn:userTask -->
            <!-- notify 和 system_action 使用 bpmn:serviceTask -->
            <!-- condition 使用 bpmn:exclusiveGateway -->
            <!-- 每个流元素必须有 <bpmn:incoming> 和 <bpmn:outgoing> -->
          </bpmn:process>
          <bpmndi:BPMNDiagram><!-- DI 布局 --></bpmndi:BPMNDiagram>
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
        7. 【关键】审批节点（businessType="approval"）必须设置：
           - approvalMode：SINGLE（单人审批）/ ALL（会签-全部通过）/ ANY（或签-任一通过）
           - assignStrategy：DIRECT_SUPERVISOR（直属主管）/ DEPARTMENT_MANAGER（部门经理）/ ROLE（按角色）/ SPECIFIC_USERS（指定人员）
        8. 抄送节点使用 bpmn:serviceTask，businessType="notify"，设置 notifyTarget（APPLICANT/APPROVER/USER）、notifyChannel（in_app/email/both）
        9. 排他网关 businessType="condition"
        10. 流程保持简洁，不超过 8 个节点
        11. 【反复强调】填写/申请/提交/录入 = form_fill；审批/审核/批准/复核 = approval。这是铁律，不许混用！
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
            "temperature", 0.1,
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

        // 7. 后处理：校验并修正 nodeConfig 中的 businessType 分类错误
        validateAndCorrectNodeConfig(result);

        log.info("流程生成成功，节点数：{}", result.getNodeConfig() != null ? result.getNodeConfig().size() : 0);
        return result;
    }

    /**
     * 后处理验证和修正 AI 生成的 nodeConfig。
     *
     * <p>四重保障（从强到弱）：</p>
     * <ol>
     *   <li>白名单校验 — 不在 8 种合法 businessType 内的直接修正</li>
     *   <li>BPMN 元素类型校验 — 从 XML 解析实际元素类型，与 businessType 交叉验证</li>
     *   <li>节点名关键词匹配 — 审批/填写/通知等关键词强关联</li>
     *   <li>角色名推断 — 节点名含经理/主管/总监等角色词的 userTask，推断为 approval</li>
     * </ol>
     */
    private void validateAndCorrectNodeConfig(AiGenerateProcessResponse result) {
        List<NodeConfigItem> nodeConfig = result.getNodeConfig();
        if (nodeConfig == null || nodeConfig.isEmpty()) {
            return;
        }

        String bpmnXml = result.getBpmnXml();

        // Step 0: 从 BPMN XML 解析每个节点 key 的实际元素类型
        Map<String, String> elementTypeMap = parseBpmnElementTypes(bpmnXml);

        boolean xmlModified = false;

        for (NodeConfigItem node : nodeConfig) {
            String nodeName = node.getNodeName();
            String businessType = node.getBusinessType();
            String nodeKey = node.getNodeKey();

            if (nodeName == null || nodeKey == null) {
                continue;
            }

            String bpmnElementType = elementTypeMap.get(nodeKey);
            String correctedType = null;
            String reason = null;

            // ═══════════════════════════════════════════════════════════
            // 第1重：白名单校验 — 非法的 businessType 一律修正
            // ═══════════════════════════════════════════════════════════
            if (businessType == null || !VALID_BUSINESS_TYPES.contains(businessType)) {
                correctedType = inferBusinessType(nodeName, bpmnElementType, nodeConfig);
                reason = "businessType 值「" + businessType + "」不在合法范围内，自动推断";
            }
            // ═══════════════════════════════════════════════════════════
            // 第2重：BPMN 元素类型与 businessType 交叉验证
            // ═══════════════════════════════════════════════════════════
            else if (bpmnElementType != null) {
                correctedType = crossValidate(nodeName, businessType, bpmnElementType);
                if (correctedType != null) {
                    reason = "businessType「" + businessType + "」与 BPMN 元素类型「" + bpmnElementType + "」不一致";
                }
            }

            // 执行修正
            if (correctedType != null && !correctedType.equals(businessType)) {
                log.warn("【节点类型修正】节点「{}」(key={}) businessType 从「{}」自动修正为「{}」，原因：{}",
                        nodeName, nodeKey, businessType, correctedType, reason);
                node.setBusinessType(correctedType);
                businessType = correctedType;
            }

            // ═══════════════════════════════════════════════════════════
            // 第3重：userTask 的 form_fill vs approval 专项区分
            // ═══════════════════════════════════════════════════════════
            if ("userTask".equals(bpmnElementType)) {
                String subType = determineUserTaskSubtype(nodeName, nodeConfig);
                if (subType != null && !subType.equals(businessType)
                        && !"start".equals(businessType) && !"end".equals(businessType)) {
                    log.warn("【userTask子类型修正】节点「{}」(key={}) businessType 从「{}」修正为「{}」",
                            nodeName, nodeKey, businessType, subType);
                    node.setBusinessType(subType);
                    businessType = subType;
                }
            }

            // ═══════════════════════════════════════════════════════════
            // 第4重：serviceTask 的 notify vs system_action 专项区分
            // ═══════════════════════════════════════════════════════════
            if ("serviceTask".equals(bpmnElementType)) {
                if (!"notify".equals(businessType) && !"system_action".equals(businessType)
                        && !"start".equals(businessType) && !"end".equals(businessType)) {
                    // serviceTask 只能是 notify 或 system_action
                    String svcType = determineServiceTaskSubtype(nodeName);
                    log.warn("【serviceTask子类型修正】节点「{}」(key={}) businessType 从「{}」修正为「{}」",
                            nodeName, nodeKey, businessType, svcType);
                    node.setBusinessType(svcType);
                    businessType = svcType;
                }
            }

            // ═══════════════════════════════════════════════════════════
            // 补充审批配置
            // ═══════════════════════════════════════════════════════════
            if ("approval".equals(businessType)) {
                if (node.getApprovalMode() == null || node.getApprovalMode().isEmpty()) {
                    node.setApprovalMode("SINGLE");
                    log.warn("【审批配置补充】节点「{}」(key={}) 缺少 approvalMode，默认设为 SINGLE", nodeName, nodeKey);
                }
                if (node.getAssignStrategy() == null || node.getAssignStrategy().isEmpty()) {
                    node.setAssignStrategy("DIRECT_SUPERVISOR");
                    log.warn("【审批配置补充】节点「{}」(key={}) 缺少 assignStrategy，默认设为 DIRECT_SUPERVISOR", nodeName, nodeKey);
                }

                // BPMN XML 修正：approval 对应的 BPMN 元素必须是 userTask
                if (bpmnXml != null && nodeKey != null && bpmnXml.contains("id=\"" + nodeKey + "\"")) {
                    if (!bpmnXml.contains("<bpmn:userTask") || !xmlContainsElement(bpmnXml, "bpmn:userTask", nodeKey)) {
                        // 找到该节点的 XML 片段，如果是 serviceTask 则改为 userTask
                        String svcPattern = "<bpmn:serviceTask[^>]*id=\"" + Pattern.quote(nodeKey) + "\"[^>]*>";
                        if (Pattern.compile(svcPattern).matcher(bpmnXml).find()) {
                            bpmnXml = bpmnXml.replaceAll(
                                    "<bpmn:serviceTask([^>]*id=\"" + Pattern.quote(nodeKey) + "\"[^>]*)>",
                                    "<bpmn:userTask$1>");
                            bpmnXml = bpmnXml.replaceAll(
                                    "</bpmn:serviceTask>\\s*(?=<bpmn:sequenceFlow[^>]*sourceRef=\"" + Pattern.quote(nodeKey) + "\")",
                                    "</bpmn:userTask>");
                            xmlModified = true;
                            log.warn("  同时修正 BPMN XML：serviceTask → userTask（key={}）", nodeKey);
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════
            // gateway 类型校验
            // ═══════════════════════════════════════════════════════════
            if ("exclusiveGateway".equals(bpmnElementType) && !"condition".equals(businessType)
                    && !"start".equals(businessType) && !"end".equals(businessType)) {
                log.warn("【Gateway类型修正】节点「{}」(key={}) 是 exclusiveGateway，businessType 从「{}」修正为 condition",
                        nodeName, nodeKey, businessType);
                node.setBusinessType("condition");
                businessType = "condition";
            }
            if ("parallelGateway".equals(bpmnElementType) && !"parallel".equals(businessType)
                    && !"start".equals(businessType) && !"end".equals(businessType)) {
                log.warn("【Gateway类型修正】节点「{}」(key={}) 是 parallelGateway，businessType 从「{}」修正为 parallel",
                        nodeName, nodeKey, businessType);
                node.setBusinessType("parallel");
                businessType = "parallel";
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // 结构性检查 — 确保有 start 和 end
        // ═══════════════════════════════════════════════════════════════
        boolean hasStart = nodeConfig.stream().anyMatch(n -> "start".equals(n.getBusinessType()));
        boolean hasEnd = nodeConfig.stream().anyMatch(n -> "end".equals(n.getBusinessType()));

        if (!hasStart) {
            log.warn("【结构修正】nodeConfig 缺少 start 节点，前置补充");
            NodeConfigItem startNode = new NodeConfigItem();
            startNode.setNodeKey("StartEvent_Auto");
            startNode.setNodeName("开始");
            startNode.setBusinessType("start");
            nodeConfig.add(0, startNode);
        }
        if (!hasEnd) {
            log.warn("【结构修正】nodeConfig 缺少 end 节点，后置补充");
            NodeConfigItem endNode = new NodeConfigItem();
            endNode.setNodeKey("EndEvent_Auto");
            endNode.setNodeName("结束");
            endNode.setBusinessType("end");
            nodeConfig.add(endNode);
        }

        if (xmlModified && bpmnXml != null) {
            result.setBpmnXml(bpmnXml);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Helper methods for BPMN XML parsing
    // ═══════════════════════════════════════════════════════════════

    /** 合法的 businessType 白名单 */
    private static final Set<String> VALID_BUSINESS_TYPES = Set.of(
            "start", "form_fill", "approval", "condition",
            "parallel", "notify", "system_action", "end", "generic_task"
    );

    /** BPMN 元素类型到 businessType 类别的映射 */
    private static final Map<String, String> BPMN_ELEMENT_CATEGORY = Map.of(
            "startEvent", "start",
            "endEvent", "end",
            "userTask", "userTask",       // 可能是 form_fill 或 approval
            "serviceTask", "serviceTask", // 可能是 notify 或 system_action
            "exclusiveGateway", "condition",
            "parallelGateway", "parallel"
    );

    /** 审批类关键词（节点名包含这些词 → 强烈指向 approval） */
    private static final Set<String> APPROVAL_KEYWORDS = Set.of(
            "审批", "审核", "批准", "核准", "复核", "签批", "阅批",
            "同意", "否决", "驳回", "会签", "或签", "审定", "审阅"
    );

    /** 表单填写类关键词（节点名包含这些词 → 强烈指向 form_fill） */
    private static final Set<String> FORM_FILL_KEYWORDS = Set.of(
            "填写", "提交", "录入", "上传", "申请", "补录", "登记", "申报",
            "发起", "填报", "报批"
    );

    /** 通知类关键词 → notify */
    private static final Set<String> NOTIFY_KEYWORDS = Set.of(
            "通知", "抄送", "知会", "提醒", "推送"
    );

    /** 系统动作类关键词 → system_action */
    private static final Set<String> SYSTEM_ACTION_KEYWORDS = Set.of(
            "打款", "同步", "计算", "自动", "调用", "生成", "校验", "验证", "归档"
    );

    /** 角色/身份词（节点名含这些 → 倾向于是 approval 而非 form_fill） */
    private static final Set<String> ROLE_KEYWORDS = Set.of(
            "经理", "主管", "总监", "总经理", "负责人", "领导", "主任",
            "副总", "总裁", "部长", "处长", "科长", "组长", "管理员"
    );

    /** 从 BPMN XML 解析 nodeKey → BPMN 元素类型的映射 */
    private Map<String, String> parseBpmnElementTypes(String bpmnXml) {
        Map<String, String> map = new HashMap<>();
        if (bpmnXml == null) {
            return map;
        }
        // 匹配 <bpmn:ELEMENT_TYPE ... id="NODE_KEY" ...> 或带有其他属性的情况
        Pattern pattern = Pattern.compile("<bpmn:(\\w+)[^>]*\\s+id=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(bpmnXml);
        while (matcher.find()) {
            String elementType = matcher.group(1);
            String id = matcher.group(2);
            // 只关心主要流元素
            if (Set.of("startEvent", "endEvent", "userTask", "serviceTask",
                    "exclusiveGateway", "parallelGateway").contains(elementType)) {
                map.put(id, elementType);
            }
        }
        log.debug("从 BPMN XML 解析到 {} 个流元素", map.size());
        return map;
    }

    /** 检查 BPMN XML 中某个 nodeKey 对应的元素是否为指定类型 */
    private boolean xmlContainsElement(String bpmnXml, String elementName, String nodeKey) {
        Pattern p = Pattern.compile("<" + Pattern.quote(elementName) + "[^>]*id=\"" + Pattern.quote(nodeKey) + "\"");
        return p.matcher(bpmnXml).find();
    }

    // ═══════════════════════════════════════════════════════════════
    // Inference logic
    // ═══════════════════════════════════════════════════════════════

    /**
     * 当 businessType 不在白名单内时，综合推断正确类型。
     */
    private String inferBusinessType(String nodeName, String bpmnElementType, List<NodeConfigItem> allNodes) {
        // 1. BPMN 元素类型是确定性的
        if ("startEvent".equals(bpmnElementType)) return "start";
        if ("endEvent".equals(bpmnElementType)) return "end";
        if ("exclusiveGateway".equals(bpmnElementType)) return "condition";
        if ("parallelGateway".equals(bpmnElementType)) return "parallel";

        // 2. userTask → 需区分 form_fill 和 approval
        if ("userTask".equals(bpmnElementType)) {
            return determineUserTaskSubtype(nodeName, allNodes);
        }

        // 3. serviceTask → 需区分 notify 和 system_action
        if ("serviceTask".equals(bpmnElementType)) {
            return determineServiceTaskSubtype(nodeName);
        }

        // 4. 兜底：用关键词
        if (containsAny(nodeName, APPROVAL_KEYWORDS)) return "approval";
        if (containsAny(nodeName, FORM_FILL_KEYWORDS)) return "form_fill";
        if (containsAny(nodeName, NOTIFY_KEYWORDS)) return "notify";

        return "generic_task";
    }

    /**
     * 交叉验证 businessType 与 BPMN 元素类型是否一致，不一致则返回正确类型。
     */
    private String crossValidate(String nodeName, String businessType, String bpmnElementType) {
        // startEvent 必须对应 start
        if ("startEvent".equals(bpmnElementType) && !"start".equals(businessType)) {
            return "start";
        }
        // endEvent 必须对应 end
        if ("endEvent".equals(bpmnElementType) && !"end".equals(businessType)) {
            return "end";
        }
        // exclusiveGateway 必须对应 condition
        if ("exclusiveGateway".equals(bpmnElementType) && !"condition".equals(businessType)) {
            return "condition";
        }
        // parallelGateway 必须对应 parallel
        if ("parallelGateway".equals(bpmnElementType) && !"parallel".equals(businessType)) {
            return "parallel";
        }
        // userTask 只能是 form_fill 或 approval
        if ("userTask".equals(bpmnElementType)
                && !"form_fill".equals(businessType) && !"approval".equals(businessType)
                && !"generic_task".equals(businessType)) {
            return determineUserTaskSubtype(nodeName, null);
        }
        // serviceTask 只能是 notify 或 system_action
        if ("serviceTask".equals(bpmnElementType)
                && !"notify".equals(businessType) && !"system_action".equals(businessType)) {
            return determineServiceTaskSubtype(nodeName);
        }
        return null; // 一致，无需修正
    }

    /**
     * userTask 子类型判定：form_fill vs approval。
     * 优先级：关键词 > 角色名 > 位置推断
     */
    private String determineUserTaskSubtype(String nodeName, List<NodeConfigItem> allNodes) {
        // 第1优先级：关键词强匹配
        if (containsAny(nodeName, APPROVAL_KEYWORDS)) return "approval";
        if (containsAny(nodeName, FORM_FILL_KEYWORDS)) return "form_fill";

        // 第2优先级：含角色词 → 很可能是审批
        if (containsAny(nodeName, ROLE_KEYWORDS)) return "approval";

        // 第3优先级：位置推断 — 如果前面已经有 form_fill，当前大概率是 approval
        if (allNodes != null) {
            boolean hasFormFillBefore = allNodes.stream()
                    .anyMatch(n -> "form_fill".equals(n.getBusinessType()));
            if (hasFormFillBefore) return "approval";
            // 第一个 userTask → 默认为 form_fill
            return "form_fill";
        }

        // 兜底：默认为 approval（保守策略，审批节点配置可被手动调整）
        return "approval";
    }

    /**
     * serviceTask 子类型判定：notify vs system_action。
     */
    private String determineServiceTaskSubtype(String nodeName) {
        if (containsAny(nodeName, NOTIFY_KEYWORDS)) return "notify";
        if (containsAny(nodeName, SYSTEM_ACTION_KEYWORDS)) return "system_action";
        // 兜底
        return "system_action";
    }

    /** 检查字符串是否包含集合中任一关键词 */
    private boolean containsAny(String text, Set<String> keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

}
