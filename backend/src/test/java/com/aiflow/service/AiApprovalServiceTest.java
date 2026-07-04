package com.aiflow.service;

import com.aiflow.common.BusinessException;
import com.aiflow.config.AiConfig;
import com.aiflow.dto.AiApprovalRequest;
import com.aiflow.dto.AiApprovalResponse;
import com.aiflow.model.*;
import com.aiflow.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI 审批建议生成单元测试 — 测试 DeepSeek 生成审批建议（通过/驳回/补充材料）。
 */
@SuppressWarnings("unchecked")
class AiApprovalServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiConfig aiConfig;
    private ProcessInstanceRepository processInstanceRepository;
    private FormSubmissionRepository formSubmissionRepository;
    private ProcessTemplateRepository processTemplateRepository;
    private AiAdviceRecordRepository aiAdviceRecordRepository;
    private ApproverResolverService approverResolverService;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        aiConfig = new AiConfig();
        aiConfig.setModel("deepseek-chat");
        aiConfig.setTimeoutSeconds(60);
        processInstanceRepository = mock(ProcessInstanceRepository.class);
        formSubmissionRepository = mock(FormSubmissionRepository.class);
        processTemplateRepository = mock(ProcessTemplateRepository.class);
        aiAdviceRecordRepository = mock(AiAdviceRecordRepository.class);
        approverResolverService = mock(ApproverResolverService.class);
        notificationService = mock(NotificationService.class);
    }

    private AiApprovalService createServiceWithWebClient(String aiResponseBody) {
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(aiResponseBody));

        return new AiApprovalService(webClient, aiConfig, objectMapper,
                processInstanceRepository, formSubmissionRepository, processTemplateRepository,
                aiAdviceRecordRepository, approverResolverService, notificationService);
    }

    private AiApprovalService createServiceThatThrows(RuntimeException exception) {
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(exception));

        return new AiApprovalService(webClient, aiConfig, objectMapper,
                processInstanceRepository, formSubmissionRepository, processTemplateRepository,
                aiAdviceRecordRepository, approverResolverService, notificationService);
    }

    // ================================================================
    // 正常审批建议生成
    // ================================================================

    @Test
    void suggestsApproveBasedOnFormData() throws Exception {
        setupInstanceAndTemplate();
        setupFormData("{\"amount\":5000,\"reason\":\"办公用品采购\"}");
        AiApprovalService service = createServiceWithWebClient(
                aiJson("approve", "金额在授权范围内，建议通过", 0.92, List.of("供应商合作年限较短")));

        AiApprovalRequest request = new AiApprovalRequest();
        request.setInstanceId(100L);
        request.setNodeKey("Approve_Manager");

        AiApprovalResponse result = service.suggest(request);

        assertThat(result.getSuggestion()).isEqualTo("approve");
        assertThat(result.getReason()).contains("金额在授权范围内");
        assertThat(result.getConfidence()).isEqualTo(0.92);
        assertThat(result.getRiskPoints()).containsExactly("供应商合作年限较短");
        verify(aiAdviceRecordRepository).save(any(AiAdviceRecord.class));
    }

    @Test
    void suggestsRejectForHighRiskApplication() throws Exception {
        setupInstanceAndTemplate();
        setupFormData("{\"amount\":500000,\"reason\":\"紧急采购\"}");
        AiApprovalService service = createServiceWithWebClient(
                aiJson("reject", "金额超出授权范围，建议驳回", 0.85, List.of("金额超出授权上限", "未提供比价材料")));

        AiApprovalRequest request = new AiApprovalRequest();
        request.setInstanceId(100L);
        request.setNodeKey("Approve_Manager");

        AiApprovalResponse result = service.suggest(request);

        assertThat(result.getSuggestion()).isEqualTo("reject");
        assertThat(result.getRiskPoints()).hasSize(2);
    }

    @Test
    void suggestsSupplementForIncompleteApplication() throws Exception {
        setupInstanceAndTemplate();
        setupFormData("{\"reason\":\"出差申请\"}");
        AiApprovalService service = createServiceWithWebClient(
                aiJson("supplement", "缺少出差目的地和日期信息，建议补充材料", 0.60, Collections.emptyList()));

        AiApprovalRequest request = new AiApprovalRequest();
        request.setInstanceId(100L);
        request.setNodeKey("Approve_Manager");

        AiApprovalResponse result = service.suggest(request);

        assertThat(result.getSuggestion()).isEqualTo("supplement");
    }

    @Test
    void cleansMarkdownCodeBlockFromAiResponse() throws Exception {
        setupInstanceAndTemplate();
        setupFormData("{\"amount\":1000}");
        // Markdown wrapping is around the content JSON (inner), not the API wrapper (outer)
        String innerContent = objectMapper.writeValueAsString(Map.of(
                "suggestion", "approve", "reason", "同意", "confidence", 0.95, "riskPoints", List.of()));
        String rawResponse = objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("message",
                        Map.of("content", "```json\n" + innerContent + "\n```")))));
        AiApprovalService service = createServiceWithWebClient(rawResponse);

        AiApprovalResponse result = service.suggest(createRequest());

        assertThat(result.getSuggestion()).isEqualTo("approve");
        assertThat(result.getConfidence()).isEqualTo(0.95);
    }

    // ================================================================
    // 错误处理
    // ================================================================

    @Test
    void throwsBusinessExceptionWhenInstanceNotFound() {
        when(processInstanceRepository.findById(100L)).thenReturn(Optional.empty());
        AiApprovalService service = createServiceWithWebClient("{}");

        AiApprovalRequest request = new AiApprovalRequest();
        request.setInstanceId(100L);
        request.setNodeKey("Approve_Manager");

        assertThatThrownBy(() -> service.suggest(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流程实例不存在");
    }

    @Test
    void throwsBusinessExceptionWhenTemplateNotFound() {
        ProcessInstance instance = ProcessInstance.builder().id(100L).templateId(999L).deleted(0).build();
        when(processInstanceRepository.findById(100L)).thenReturn(Optional.of(instance));
        when(processTemplateRepository.findById(999L)).thenReturn(Optional.empty());
        AiApprovalService service = createServiceWithWebClient("{}");

        AiApprovalRequest request = new AiApprovalRequest();
        request.setInstanceId(100L);
        request.setNodeKey("Approve_Manager");

        assertThatThrownBy(() -> service.suggest(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流程模板不存在");
    }

    @Test
    void wrapsApiCallFailureAsBusinessException() {
        setupInstanceAndTemplate();
        AiApprovalService service = createServiceThatThrows(new RuntimeException("Connection timeout"));

        AiApprovalRequest request = new AiApprovalRequest();
        request.setInstanceId(100L);
        request.setNodeKey("Approve_Manager");

        assertThatThrownBy(() -> service.suggest(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AI 服务调用失败");
    }

    // ================================================================
    // 表单数据提取 — 过滤审批元数据
    // ================================================================

    @Test
    void filtersApprovalOnlyFormDataToFindBusinessFields() throws Exception {
        setupInstanceAndTemplate();
        FormSubmission approvalOnly = FormSubmission.builder()
                .id(1L).processInstanceId(100L).businessType("approval")
                .formDataJson("{\"approvalResult\":\"approve\",\"comment\":\"同意\"}").deleted(0).build();
        FormSubmission businessData = FormSubmission.builder()
                .id(2L).processInstanceId(100L).businessType("form_fill")
                .formDataJson("{\"name\":\"张三\",\"amount\":5000,\"reason\":\"采购\"}").deleted(0).build();
        when(formSubmissionRepository
                .findByProcessInstanceIdAndDeletedOrderByUpdatedAtDescCreatedAtDesc(100L, 0))
                .thenReturn(List.of(approvalOnly, businessData));
        AiApprovalService service = createServiceWithWebClient(
                aiJson("approve", "建议通过", 0.80, List.of()));

        AiApprovalResponse result = service.suggest(new AiApprovalRequest() {{
            setInstanceId(100L); setNodeKey("Approve_Manager");
        }});

        assertThat(result.getSuggestion()).isEqualTo("approve");
    }

    // ================================================================
    // 建议记录持久化 — 类型映射
    // ================================================================

    @Test
    void savesAiAdviceRecordWithCorrectMappedType() throws Exception {
        setupInstanceAndTemplate();
        setupFormData("{\"amount\":3000}");
        AiApprovalService service = createServiceWithWebClient(
                aiJson("approve", "同意", 0.90, List.of()));

        service.suggest(createRequest());

        ArgumentCaptor<AiAdviceRecord> captor = ArgumentCaptor.forClass(AiAdviceRecord.class);
        verify(aiAdviceRecordRepository).save(captor.capture());
        AiAdviceRecord record = captor.getValue();
        assertThat(record.getInstanceId()).isEqualTo(100L);
        assertThat(record.getNodeKey()).isEqualTo("Approve_Manager");
        assertThat(record.getAdviceType()).isEqualTo("pass");   // approve → pass
        assertThat(record.getModelName()).isEqualTo("deepseek-chat");
    }

    @Test
    void mapsRejectSuggestionToRejectAdviceType() throws Exception {
        setupInstanceAndTemplate();
        setupFormData("{\"amount\":1000000}");
        AiApprovalService service = createServiceWithWebClient(
                aiJson("reject", "驳回", 0.95, List.of("超预算")));

        service.suggest(createRequest());

        ArgumentCaptor<AiAdviceRecord> captor = ArgumentCaptor.forClass(AiAdviceRecord.class);
        verify(aiAdviceRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getAdviceType()).isEqualTo("reject");
    }

    @Test
    void mapsSupplementSuggestionToVerifyAdviceType() throws Exception {
        setupInstanceAndTemplate();
        setupFormData("{}");
        AiApprovalService service = createServiceWithWebClient(
                aiJson("supplement", "请补充材料", 0.50, List.of()));

        service.suggest(createRequest());

        ArgumentCaptor<AiAdviceRecord> captor = ArgumentCaptor.forClass(AiAdviceRecord.class);
        verify(aiAdviceRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getAdviceType()).isEqualTo("verify");
    }

    @Test
    void mapsNullSuggestionToVerify() throws Exception {
        setupInstanceAndTemplate();
        setupFormData("{\"test\":1}");
        AiApprovalService service = createServiceWithWebClient(
                objectMapper.writeValueAsString(Map.of(
                        "choices", List.of(Map.of("message", Map.of("content",
                                objectMapper.writeValueAsString(Map.of(
                                        "reason", "无法确定", "confidence", 0.3,
                                        "riskPoints", List.of()))))))));

        service.suggest(createRequest());

        ArgumentCaptor<AiAdviceRecord> captor = ArgumentCaptor.forClass(AiAdviceRecord.class);
        verify(aiAdviceRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getAdviceType()).isEqualTo("verify");
    }

    // ================================================================
    // 低置信度 + 通知容错
    // ================================================================

    @Test
    void handlesLowConfidenceSuggestion() throws Exception {
        setupInstanceAndTemplate();
        setupFormData("{\"project\":\"未命名\"}");
        AiApprovalService service = createServiceWithWebClient(
                aiJson("supplement", "信息不足，难以做出准确判断", 0.25,
                        List.of("表单数据过少", "项目名称缺失")));

        AiApprovalResponse result = service.suggest(createRequest());

        assertThat(result.getConfidence()).isLessThan(0.5);
        assertThat(result.getRiskPoints()).hasSize(2);
    }

    @Test
    void returnsSuggestionEvenWhenNotificationFails() throws Exception {
        setupInstanceAndTemplate();
        setupFormData("{\"amount\":100}");
        AiApprovalService service = createServiceWithWebClient(
                aiJson("approve", "同意", 0.99, List.of()));
        when(approverResolverService.resolveApprovers(anyLong(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("审批人解析失败"));

        AiApprovalResponse result = service.suggest(createRequest());

        assertThat(result.getSuggestion()).isEqualTo("approve");
        verify(notificationService, never()).createNotification(any());
    }

    // ================================================================
    // Helper methods
    // ================================================================

    private void setupInstanceAndTemplate() {
        when(processInstanceRepository.findById(100L)).thenReturn(Optional.of(
                ProcessInstance.builder().id(100L).templateId(200L).applicantId(10L).deleted(0).build()));
        when(processTemplateRepository.findById(200L)).thenReturn(Optional.of(
                ProcessTemplate.builder().id(200L).templateName("报销审批")
                        .nodeConfig("{\"Approve_Manager\":{\"nodeName\":\"部门经理审批\"}}").deleted(0).build()));
    }

    private void setupFormData(String formDataJson) {
        when(formSubmissionRepository
                .findByProcessInstanceIdAndDeletedOrderByUpdatedAtDescCreatedAtDesc(100L, 0))
                .thenReturn(List.of(FormSubmission.builder()
                        .id(1L).processInstanceId(100L).businessType("form_fill")
                        .formDataJson(formDataJson).deleted(0).build()));
    }

    private AiApprovalRequest createRequest() {
        AiApprovalRequest r = new AiApprovalRequest();
        r.setInstanceId(100L);
        r.setNodeKey("Approve_Manager");
        return r;
    }

    private String aiJson(String suggestion, String reason, double confidence,
                          List<String> riskPoints) throws Exception {
        Map<String, Object> aiContent = new java.util.LinkedHashMap<>();
        aiContent.put("suggestion", suggestion);
        aiContent.put("reason", reason);
        aiContent.put("confidence", confidence);
        aiContent.put("riskPoints", riskPoints);
        return objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("message",
                        Map.of("content", objectMapper.writeValueAsString(aiContent))))));
    }
}
