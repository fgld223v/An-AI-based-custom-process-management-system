package com.aiflow.service;

import com.aiflow.common.BusinessException;
import com.aiflow.config.AiConfig;
import com.aiflow.dto.AiGenerateProcessResponse;
import com.aiflow.dto.NodeConfigItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AI 流程构建引擎单元测试 — 测试 DeepSeek 大模型生成 BPMN 2.0 XML 和节点配置。
 */
class AiProcessServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiConfig aiConfig;
    private AiProcessService service;

    @BeforeEach
    void setUp() {
        aiConfig = new AiConfig();
        aiConfig.setModel("deepseek-chat");
        aiConfig.setTimeoutSeconds(60);
    }

    private AiProcessService createServiceWithWebClient(String aiResponseBody) {
        WebClient webClient = mock(WebClient.class);
        @SuppressWarnings("unchecked")
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        @SuppressWarnings("unchecked")
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        @SuppressWarnings("unchecked")
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(aiResponseBody));

        return new AiProcessService(webClient, aiConfig, objectMapper);
    }

    private AiProcessService createServiceThatThrows(RuntimeException exception) {
        WebClient webClient = mock(WebClient.class);
        @SuppressWarnings("unchecked")
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        @SuppressWarnings("unchecked")
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        @SuppressWarnings("unchecked")
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(exception));

        return new AiProcessService(webClient, aiConfig, objectMapper);
    }

    private AiProcessService createServiceWithEmptyResponse() {
        WebClient webClient = mock(WebClient.class);
        @SuppressWarnings("unchecked")
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        @SuppressWarnings("unchecked")
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        @SuppressWarnings("unchecked")
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.empty());

        return new AiProcessService(webClient, aiConfig, objectMapper);
    }

    // ================================================================
    // 正常流程
    // ================================================================

    @Test
    void generatesValidProcessFromNaturalLanguageDescription() throws Exception {
        service = createServiceWithWebClient(
                aiSuccessResponse(validBpmnXml(), validNodeConfig()));

        AiGenerateProcessResponse result = service.generateProcess("员工提交请假申请，主管审批");

        assertThat(result.getBpmnXml()).contains("<bpmn:definitions")
                .contains("<bpmn:process")
                .contains("isExecutable=\"true\"");
        assertThat(result.getNodeConfig()).hasSize(4);
        assertThat(result.getNodeConfig()).extracting(NodeConfigItem::getBusinessType)
                .containsExactly("start", "form_fill", "approval", "end");
        assertThat(result.getSummary()).isEqualTo("员工请假审批流程");
    }

    @Test
    void cleansMarkdownCodeBlockWrapping() throws Exception {
        // Markdown wrapping is around the content JSON (inner), not the API wrapper (outer)
        String innerContent = objectMapper.writeValueAsString(Map.of(
                "bpmnXml", validBpmnXml(),
                "nodeConfig", objectMapper.readValue(validNodeConfig(), List.class),
                "summary", "员工请假审批流程"));
        String markdownWrapped = "```json\n" + innerContent + "\n```";
        service = createServiceWithWebClient(outerApiResponse(markdownWrapped));

        AiGenerateProcessResponse result = service.generateProcess("请假审批");

        assertThat(result.getBpmnXml()).contains("<bpmn:definitions");
        assertThat(result.getNodeConfig()).hasSize(4);
    }

    @Test
    void cleansMarkdownCodeBlockWithoutLanguageTag() throws Exception {
        String innerContent = objectMapper.writeValueAsString(Map.of(
                "bpmnXml", validBpmnXml(),
                "nodeConfig", objectMapper.readValue(validNodeConfig(), List.class),
                "summary", "员工请假审批流程"));
        String markdownWrapped = "```\n" + innerContent + "\n```";
        service = createServiceWithWebClient(outerApiResponse(markdownWrapped));

        AiGenerateProcessResponse result = service.generateProcess("请假审批");

        assertThat(result.getBpmnXml()).contains("<bpmn:definitions");
    }

    /** Build a DeepSeek API wrapper where the content field is the given string. */
    private String outerApiResponse(String contentValue) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", contentValue)))));
    }

    // ================================================================
    // API 错误处理
    // ================================================================

    @Test
    void wrapsApiCallFailureAsBusinessException() {
        service = createServiceThatThrows(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> service.generateProcess("请假审批"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AI 服务调用失败");
    }

    @Test
    void rejectsNullApiResponse() {
        service = createServiceWithEmptyResponse();

        assertThatThrownBy(() -> service.generateProcess("请假审批"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("返回为空");
    }

    @Test
    void rejectsResponseWithEmptyChoices() throws Exception {
        String response = objectMapper.writeValueAsString(Map.of("choices", List.of()));
        service = createServiceWithWebClient(response);

        assertThatThrownBy(() -> service.generateProcess("请假审批"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无 choices");
    }

    // ================================================================
    // BPMN XML 校验
    // ================================================================

    @Test
    void rejectsBpmnXmlWithoutDefinitionsTag() throws Exception {
        String badXml = "<bpmn:process id=\"P1\" isExecutable=\"true\"><bpmn:startEvent id=\"S1\"/></bpmn:process>";
        service = createServiceWithWebClient(aiSuccessResponse(badXml, validNodeConfig()));

        assertThatThrownBy(() -> service.generateProcess("请假审批"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少 definitions 或 process 标签");
    }

    @Test
    void rejectsBpmnXmlWithoutProcessTag() throws Exception {
        String badXml = "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"/>";
        service = createServiceWithWebClient(aiSuccessResponse(badXml, validNodeConfig()));

        assertThatThrownBy(() -> service.generateProcess("请假审批"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少 definitions 或 process 标签");
    }

    @Test
    void autoFixesMissingIsExecutableAttribute() throws Exception {
        String xmlWithoutExecutable = "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                + "<bpmn:process id=\"P1\"><bpmn:startEvent id=\"S1\"/><bpmn:endEvent id=\"E1\"/></bpmn:process>"
                + "</bpmn:definitions>";
        service = createServiceWithWebClient(aiSuccessResponse(xmlWithoutExecutable, validNodeConfig()));

        AiGenerateProcessResponse result = service.generateProcess("请假审批");

        assertThat(result.getBpmnXml()).contains("isExecutable=\"true\"");
    }

    // ================================================================
    // businessType 四重保障修正机制
    // ================================================================

    @Test
    void correctsIllegalBusinessTypeToInferredType() throws Exception {
        String nodeConfig = objectMapper.writeValueAsString(List.of(
                Map.of("nodeKey", "StartEvent_1", "nodeName", "开始", "businessType", "start"),
                Map.of("nodeKey", "UserTask_1", "nodeName", "填写请假申请", "businessType", "invalid_type"),
                Map.of("nodeKey", "UserTask_2", "nodeName", "主管审批", "businessType", "approval",
                        "approvalMode", "SINGLE", "assignStrategy", "DIRECT_SUPERVISOR"),
                Map.of("nodeKey", "EndEvent_1", "nodeName", "结束", "businessType", "end")));
        String xml = createBpmnWithElements(Map.of(
                "StartEvent_1", "startEvent", "UserTask_1", "userTask",
                "UserTask_2", "userTask", "EndEvent_1", "endEvent"));
        service = createServiceWithWebClient(aiSuccessResponse(xml, nodeConfig));

        AiGenerateProcessResponse result = service.generateProcess("请假审批");

        assertThat(result.getNodeConfig()).extracting(NodeConfigItem::getBusinessType)
                .containsExactly("start", "form_fill", "approval", "end");
    }

    @Test
    void correctsApprovalNodeWithFormFillKeywordToFormFill() throws Exception {
        String nodeConfig = objectMapper.writeValueAsString(List.of(
                Map.of("nodeKey", "StartEvent_1", "nodeName", "开始", "businessType", "start"),
                Map.of("nodeKey", "UserTask_1", "nodeName", "填写请假申请", "businessType", "approval",
                        "approvalMode", "SINGLE", "assignStrategy", "DIRECT_SUPERVISOR"),
                Map.of("nodeKey", "EndEvent_1", "nodeName", "结束", "businessType", "end")));
        String xml = createBpmnWithElements(Map.of(
                "StartEvent_1", "startEvent", "UserTask_1", "userTask", "EndEvent_1", "endEvent"));
        service = createServiceWithWebClient(aiSuccessResponse(xml, nodeConfig));

        AiGenerateProcessResponse result = service.generateProcess("请假审批");

        assertThat(result.getNodeConfig()).extracting(NodeConfigItem::getBusinessType)
                .containsExactly("start", "form_fill", "end");
    }

    @Test
    void correctsFormFillNodeWithApprovalKeywordToApproval() throws Exception {
        String nodeConfig = objectMapper.writeValueAsString(List.of(
                Map.of("nodeKey", "StartEvent_1", "nodeName", "开始", "businessType", "start"),
                Map.of("nodeKey", "UserTask_1", "nodeName", "主管审批", "businessType", "form_fill"),
                Map.of("nodeKey", "EndEvent_1", "nodeName", "结束", "businessType", "end")));
        String xml = createBpmnWithElements(Map.of(
                "StartEvent_1", "startEvent", "UserTask_1", "userTask", "EndEvent_1", "endEvent"));
        service = createServiceWithWebClient(aiSuccessResponse(xml, nodeConfig));

        AiGenerateProcessResponse result = service.generateProcess("请假审批");

        assertThat(result.getNodeConfig()).extracting(NodeConfigItem::getBusinessType)
                .containsExactly("start", "approval", "end");
    }

    @Test
    void correctsConditionGatewayBusinessType() throws Exception {
        String nodeConfig = objectMapper.writeValueAsString(List.of(
                Map.of("nodeKey", "StartEvent_1", "nodeName", "开始", "businessType", "start"),
                Map.of("nodeKey", "Gateway_1", "nodeName", "金额判断", "businessType", "approval"),
                Map.of("nodeKey", "EndEvent_1", "nodeName", "结束", "businessType", "end")));
        String xml = createBpmnWithElements(Map.of(
                "StartEvent_1", "startEvent", "Gateway_1", "exclusiveGateway", "EndEvent_1", "endEvent"));
        service = createServiceWithWebClient(aiSuccessResponse(xml, nodeConfig));

        AiGenerateProcessResponse result = service.generateProcess("金额审批");

        assertThat(result.getNodeConfig()).extracting(NodeConfigItem::getBusinessType)
                .contains("condition");
    }

    @Test
    void correctsParallelGatewayBusinessType() throws Exception {
        String nodeConfig = objectMapper.writeValueAsString(List.of(
                Map.of("nodeKey", "StartEvent_1", "nodeName", "开始", "businessType", "start"),
                Map.of("nodeKey", "Gateway_1", "nodeName", "并行分支", "businessType", "form_fill"),
                Map.of("nodeKey", "EndEvent_1", "nodeName", "结束", "businessType", "end")));
        String xml = createBpmnWithElements(Map.of(
                "StartEvent_1", "startEvent", "Gateway_1", "parallelGateway", "EndEvent_1", "endEvent"));
        service = createServiceWithWebClient(aiSuccessResponse(xml, nodeConfig));

        AiGenerateProcessResponse result = service.generateProcess("并行审批");

        assertThat(result.getNodeConfig()).extracting(NodeConfigItem::getBusinessType)
                .contains("parallel");
    }

    @Test
    void correctsServiceTaskWithNotifyKeywordToNotify() throws Exception {
        String nodeConfig = objectMapper.writeValueAsString(List.of(
                Map.of("nodeKey", "StartEvent_1", "nodeName", "开始", "businessType", "start"),
                Map.of("nodeKey", "ServiceTask_1", "nodeName", "通知申请人", "businessType", "approval"),
                Map.of("nodeKey", "EndEvent_1", "nodeName", "结束", "businessType", "end")));
        String xml = createBpmnWithElements(Map.of(
                "StartEvent_1", "startEvent", "ServiceTask_1", "serviceTask", "EndEvent_1", "endEvent"));
        service = createServiceWithWebClient(aiSuccessResponse(xml, nodeConfig));

        AiGenerateProcessResponse result = service.generateProcess("通知流程");

        assertThat(result.getNodeConfig()).extracting(NodeConfigItem::getBusinessType)
                .contains("notify");
    }

    // ================================================================
    // 审批节点配置补充 + 结构性补充 + 复杂分支
    // ================================================================

    @Test
    void fillsDefaultApprovalModeAndAssignStrategy() throws Exception {
        String nodeConfig = objectMapper.writeValueAsString(List.of(
                Map.of("nodeKey", "StartEvent_1", "nodeName", "开始", "businessType", "start"),
                Map.of("nodeKey", "UserTask_1", "nodeName", "主管审批", "businessType", "approval"),
                Map.of("nodeKey", "EndEvent_1", "nodeName", "结束", "businessType", "end")));
        String xml = createBpmnWithElements(Map.of(
                "StartEvent_1", "startEvent", "UserTask_1", "userTask", "EndEvent_1", "endEvent"));
        service = createServiceWithWebClient(aiSuccessResponse(xml, nodeConfig));

        AiGenerateProcessResponse result = service.generateProcess("审批流程");

        List<NodeConfigItem> nodes = result.getNodeConfig();
        NodeConfigItem approval = nodes.stream()
                .filter(n -> "approval".equals(n.getBusinessType()))
                .findFirst().orElseThrow();
        assertThat(approval.getApprovalMode()).isEqualTo("SINGLE");
        assertThat(approval.getAssignStrategy()).isEqualTo("DIRECT_SUPERVISOR");
    }

    @Test
    void addsMissingStartAndEndNodes() throws Exception {
        String nodeConfig = objectMapper.writeValueAsString(List.of(
                Map.of("nodeKey", "UserTask_1", "nodeName", "填写请假申请", "businessType", "form_fill"),
                Map.of("nodeKey", "UserTask_2", "nodeName", "主管审批", "businessType", "approval",
                        "approvalMode", "SINGLE", "assignStrategy", "DIRECT_SUPERVISOR")));
        String xml = createBpmnWithElements(Map.of("UserTask_1", "userTask", "UserTask_2", "userTask"));
        service = createServiceWithWebClient(aiSuccessResponse(xml, nodeConfig));

        AiGenerateProcessResponse result = service.generateProcess("请假审批");

        List<String> types = result.getNodeConfig().stream()
                .map(NodeConfigItem::getBusinessType).toList();
        assertThat(types.get(0)).isEqualTo("start");
        assertThat(types.get(types.size() - 1)).isEqualTo("end");
    }

    @Test
    void generatesProcessWithComplexBranchingStructure() throws Exception {
        String nodeConfig = objectMapper.writeValueAsString(List.of(
                Map.of("nodeKey", "StartEvent_1", "nodeName", "开始", "businessType", "start"),
                Map.of("nodeKey", "UserTask_1", "nodeName", "填写报销单", "businessType", "form_fill"),
                Map.of("nodeKey", "Gateway_1", "nodeName", "金额判断", "businessType", "condition"),
                Map.of("nodeKey", "UserTask_2", "nodeName", "部门经理审批", "businessType", "approval",
                        "approvalMode", "SINGLE", "assignStrategy", "DEPARTMENT_MANAGER"),
                Map.of("nodeKey", "UserTask_3", "nodeName", "总经理审批", "businessType", "approval",
                        "approvalMode", "SINGLE", "assignStrategy", "ROLE"),
                Map.of("nodeKey", "EndEvent_1", "nodeName", "结束", "businessType", "end")));
        String xml = createBpmnWithElements(Map.of(
                "StartEvent_1", "startEvent", "UserTask_1", "userTask", "Gateway_1", "exclusiveGateway",
                "UserTask_2", "userTask", "UserTask_3", "userTask", "EndEvent_1", "endEvent"));
        service = createServiceWithWebClient(aiSuccessResponse(xml, nodeConfig));

        AiGenerateProcessResponse result = service.generateProcess("金额报销审批");

        assertThat(result.getNodeConfig()).hasSize(6);
        assertThat(result.getBpmnXml()).contains("<bpmn:definitions", "<bpmn:process");
    }

    // ================================================================
    // Helper methods
    // ================================================================

    private String aiSuccessResponse(String bpmnXml, String nodeConfig) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = Map.of(
                "choices", List.of(Map.of("message", Map.of("content",
                        objectMapper.writeValueAsString(Map.of(
                                "bpmnXml", bpmnXml,
                                "nodeConfig", objectMapper.readValue(nodeConfig, List.class),
                                "summary", "员工请假审批流程"))))));
        return objectMapper.writeValueAsString(responseMap);
    }

    @SuppressWarnings("unchecked")
    private String validNodeConfig() throws Exception {
        return objectMapper.writeValueAsString(List.of(
                Map.of("nodeKey", "StartEvent_1", "nodeName", "开始", "businessType", "start"),
                Map.of("nodeKey", "UserTask_1", "nodeName", "填写请假申请", "businessType", "form_fill"),
                Map.of("nodeKey", "UserTask_2", "nodeName", "主管审批", "businessType", "approval",
                        "approvalMode", "SINGLE", "assignStrategy", "DIRECT_SUPERVISOR"),
                Map.of("nodeKey", "EndEvent_1", "nodeName", "结束", "businessType", "end")));
    }

    private String validBpmnXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n"
                + "  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "  targetNamespace=\"http://ai-flow/process\">\n"
                + "  <bpmn:process id=\"Process_Main\" name=\"请假审批\" isExecutable=\"true\">\n"
                + "    <bpmn:startEvent id=\"StartEvent_1\"><bpmn:outgoing>Flow_1</bpmn:outgoing></bpmn:startEvent>\n"
                + "    <bpmn:userTask id=\"UserTask_1\" name=\"填写请假申请\">"
                + "<bpmn:incoming>Flow_1</bpmn:incoming><bpmn:outgoing>Flow_2</bpmn:outgoing></bpmn:userTask>\n"
                + "    <bpmn:userTask id=\"UserTask_2\" name=\"主管审批\">"
                + "<bpmn:incoming>Flow_2</bpmn:incoming><bpmn:outgoing>Flow_3</bpmn:outgoing></bpmn:userTask>\n"
                + "    <bpmn:endEvent id=\"EndEvent_1\"><bpmn:incoming>Flow_3</bpmn:incoming></bpmn:endEvent>\n"
                + "    <bpmn:sequenceFlow id=\"Flow_1\" sourceRef=\"StartEvent_1\" targetRef=\"UserTask_1\"/>\n"
                + "    <bpmn:sequenceFlow id=\"Flow_2\" sourceRef=\"UserTask_1\" targetRef=\"UserTask_2\"/>\n"
                + "    <bpmn:sequenceFlow id=\"Flow_3\" sourceRef=\"UserTask_2\" targetRef=\"EndEvent_1\"/>\n"
                + "  </bpmn:process>\n"
                + "</bpmn:definitions>";
    }

    private String createBpmnWithElements(Map<String, String> idToType) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n");
        sb.append("  targetNamespace=\"http://ai-flow/process\">\n");
        sb.append("  <bpmn:process id=\"Process_Main\" isExecutable=\"true\">\n");
        for (Map.Entry<String, String> entry : idToType.entrySet()) {
            String tag = switch (entry.getValue()) {
                case "startEvent" -> "startEvent";
                case "endEvent" -> "endEvent";
                case "exclusiveGateway" -> "exclusiveGateway";
                case "parallelGateway" -> "parallelGateway";
                default -> entry.getValue();
            };
            sb.append("    <bpmn:").append(tag).append(" id=\"").append(entry.getKey()).append("\"/>\n");
        }
        sb.append("  </bpmn:process>\n");
        sb.append("</bpmn:definitions>");
        return sb.toString();
    }
}
