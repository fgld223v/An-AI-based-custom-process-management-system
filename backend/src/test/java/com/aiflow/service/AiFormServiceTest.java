package com.aiflow.service;

import com.aiflow.common.BusinessException;
import com.aiflow.config.AiConfig;
import com.aiflow.dto.AiGenerateFormResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AI 表单生成服务单元测试 — 测试 DeepSeek 生成表单字段配置、审批字段过滤、日期范围规则注入。
 */
@SuppressWarnings("unchecked")
class AiFormServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiConfig aiConfig;

    @BeforeEach
    void setUp() {
        aiConfig = new AiConfig();
        aiConfig.setModel("deepseek-chat");
        aiConfig.setTimeoutSeconds(60);
    }

    private AiFormService createServiceWithWebClient(String aiResponseBody) {
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

        return new AiFormService(webClient, aiConfig, objectMapper);
    }

    private AiFormService createServiceThatThrows(RuntimeException e) {
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
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(e));

        return new AiFormService(webClient, aiConfig, objectMapper);
    }

    private AiFormService createServiceWithEmptyResponse() {
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
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.empty());

        return new AiFormService(webClient, aiConfig, objectMapper);
    }

    // ================================================================
    // 正常表单生成
    // ================================================================

    @Test
    void generatesFormFieldsAndSchemaFromDescription() throws Exception {
        AiFormService service = createServiceWithWebClient(
                buildAiResponse(validFieldList(), validFormSchema()));

        AiGenerateFormResponse result = service.generateForm("员工请假申请表单");

        assertThat(result.getFieldList()).contains("applicant").contains("leaveType").contains("startDate");
        assertThat(result.getFormSchema()).contains("vertical").contains("基本信息");
    }

    @Test
    void cleansMarkdownCodeBlockWrapping() throws Exception {
        Map<String, Object> inner = new java.util.LinkedHashMap<>();
        inner.put("fieldList", validFieldList());
        inner.put("formSchema", validFormSchema());
        String innerJson = objectMapper.writeValueAsString(inner);
        String response = objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("message",
                        Map.of("content", "```json\n" + innerJson + "\n```")))));
        AiFormService service = createServiceWithWebClient(response);

        AiGenerateFormResponse result = service.generateForm("请假表单");

        assertThat(result.getFieldList()).contains("applicant");
    }

    @Test
    void cleansMarkdownCodeBlockWithoutLanguageTag() throws Exception {
        Map<String, Object> inner = new java.util.LinkedHashMap<>();
        inner.put("fieldList", validFieldList());
        inner.put("formSchema", validFormSchema());
        String innerJson = objectMapper.writeValueAsString(inner);
        String response = objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("message",
                        Map.of("content", "```\n" + innerJson + "\n```")))));
        AiFormService service = createServiceWithWebClient(response);

        AiGenerateFormResponse result = service.generateForm("请假表单");

        assertThat(result.getFieldList()).contains("applicant");
    }

    // ================================================================
    // 错误处理
    // ================================================================

    @Test
    void wrapsApiCallFailureAsBusinessException() {
        AiFormService service = createServiceThatThrows(new RuntimeException("Timeout"));

        assertThatThrownBy(() -> service.generateForm("请假表单"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AI 服务调用失败");
    }

    @Test
    void rejectsNullApiResponse() {
        AiFormService service = createServiceWithEmptyResponse();

        assertThatThrownBy(() -> service.generateForm("请假表单"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("返回为空");
    }

    @Test
    void rejectsResponseWithEmptyChoices() throws Exception {
        AiFormService service = createServiceWithWebClient(
                objectMapper.writeValueAsString(Map.of("choices", List.of())));

        assertThatThrownBy(() -> service.generateForm("请假表单"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无 choices");
    }

    // ================================================================
    // fieldList 格式兼容
    // ================================================================

    @Test
    void handlesFieldListAsJsonArray() throws Exception {
        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("fieldList", validFieldList());  // 直接传数组
        content.put("formSchema", validFormSchema());
        AiFormService service = createServiceWithWebClient(wrapAsAiResponse(content));

        AiGenerateFormResponse result = service.generateForm("请假表单");

        assertThat(result.getFieldList()).contains("applicant");
    }

    @Test
    void handlesFieldListAsJsonString() throws Exception {
        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("fieldList", objectMapper.writeValueAsString(validFieldList()));  // 字符串格式
        content.put("formSchema", validFormSchema());
        AiFormService service = createServiceWithWebClient(wrapAsAiResponse(content));

        AiGenerateFormResponse result = service.generateForm("请假表单");

        assertThat(result.getFieldList()).contains("applicant");
    }

    @Test
    void rejectsInvalidFieldListType() throws Exception {
        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("fieldList", 12345);  // 数字 — 非法格式
        content.put("formSchema", validFormSchema());
        AiFormService service = createServiceWithWebClient(wrapAsAiResponse(content));

        assertThatThrownBy(() -> service.generateForm("请假表单"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("fieldList 格式异常");
    }

    // ================================================================
    // 审批字段过滤
    // ================================================================

    @Test
    void stripsApprovalFieldsFromGeneratedForm() throws Exception {
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(createField("applicant", "申请人", "text"));
        fields.add(createField("amount", "报销金额", "number"));
        fields.add(createField("approvalResult", "审批结果", "select"));
        fields.add(createField("approvalComment", "审批意见", "textarea"));
        fields.add(createField("reason", "事由", "textarea"));

        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("fieldList", fields);
        content.put("formSchema", validFormSchema());
        AiFormService service = createServiceWithWebClient(wrapAsAiResponse(content));

        AiGenerateFormResponse result = service.generateForm("报销表单");

        String fieldList = result.getFieldList();
        assertThat(fieldList).contains("applicant").contains("amount").contains("reason");
        assertThat(fieldList).doesNotContain("approvalResult").doesNotContain("approvalComment");
    }

    @Test
    void stripsChineseApprovalKeywordFields() throws Exception {
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(createField("name", "姓名", "text"));
        fields.add(createField("dept", "部门", "text"));
        fields.add(createField("auditComment", "审核结果", "select"));

        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("fieldList", fields);
        content.put("formSchema", validFormSchema());
        AiFormService service = createServiceWithWebClient(wrapAsAiResponse(content));

        AiGenerateFormResponse result = service.generateForm("测试表单");

        assertThat(result.getFieldList()).contains("name").contains("dept").doesNotContain("auditComment");
    }

    @Test
    void throwsExceptionWhenAllFieldsAreApprovalFields() throws Exception {
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(createField("approvalResult", "审批结果", "select"));
        fields.add(createField("approvalComment", "审批意见", "textarea"));

        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("fieldList", fields);
        content.put("formSchema", validFormSchema());
        AiFormService service = createServiceWithWebClient(wrapAsAiResponse(content));

        assertThatThrownBy(() -> service.generateForm("纯审批表单"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("所有字段均为审批类字段");
    }

    // ================================================================
    // 日期范围规则自动注入
    // ================================================================

    @Test
    void injectsEndDateGreaterThanStartDateRule() throws Exception {
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(createField("startDate", "开始日期", "date"));
        fields.add(createField("endDate", "结束日期", "date"));

        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("fieldList", fields);
        content.put("formSchema", validFormSchema());
        AiFormService service = createServiceWithWebClient(wrapAsAiResponse(content));

        AiGenerateFormResponse result = service.generateForm("请假表单");

        assertThat(result.getFieldList()).contains("\"op\":\"gte\"")
                .contains("\"targetField\":\"startDate\"")
                .contains("\"结束日期必须≥开始日期\"");
    }

    @Test
    void doesNotDuplicateExistingDateRangeRule() throws Exception {
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(createField("startDate", "开始日期", "date"));
        Map<String, Object> endField = createField("endDate", "结束日期", "date");
        endField.put("rules", List.of(Map.of("op", "gte", "targetField", "startDate",
                "targetLabel", "开始日期", "message", "已有规则")));
        fields.add(endField);

        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("fieldList", fields);
        content.put("formSchema", validFormSchema());
        AiFormService service = createServiceWithWebClient(wrapAsAiResponse(content));

        AiGenerateFormResponse result = service.generateForm("表单");

        String fieldList = result.getFieldList();
        int count = 0, idx = 0;
        while ((idx = fieldList.indexOf("\"op\":\"gte\"", idx)) >= 0) { count++; idx++; }
        assertThat(count).isEqualTo(1);
    }

    // ================================================================
    // formSchema 兜底
    // ================================================================

    @Test
    void fallsBackWhenFormSchemaIsMissing() throws Exception {
        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("fieldList", validFieldList());
        AiFormService service = createServiceWithWebClient(wrapAsAiResponse(content));

        AiGenerateFormResponse result = service.generateForm("请假表单");

        assertThat(result.getFormSchema()).contains("vertical").contains("sections");
    }

    @Test
    void rejectsMalformedFieldListJson() throws Exception {
        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("fieldList", "not valid json {{{");
        content.put("formSchema", validFormSchema());
        AiFormService service = createServiceWithWebClient(wrapAsAiResponse(content));

        assertThatThrownBy(() -> service.generateForm("表单"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("格式异常");
    }

    // ================================================================
    // 复杂表单（带 select options）
    // ================================================================

    @Test
    void generatesComplexFormWithSelectOptions() throws Exception {
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(createField("applicant", "申请人", "text"));
        Map<String, Object> leaveType = createField("leaveType", "请假类型", "select");
        leaveType.put("options", List.of(
                Map.of("label", "事假", "value", "事假"),
                Map.of("label", "病假", "value", "病假"),
                Map.of("label", "年假", "value", "年假")));
        fields.add(leaveType);

        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("fieldList", fields);
        content.put("formSchema", validFormSchema());
        AiFormService service = createServiceWithWebClient(wrapAsAiResponse(content));

        AiGenerateFormResponse result = service.generateForm("请假表单");

        assertThat(result.getFieldList()).contains("\"options\":[")
                .contains("\"label\":\"事假\"").contains("\"value\":\"病假\"");
    }

    // ================================================================
    // Helper methods
    // ================================================================

    private String buildAiResponse(Object fieldList, Object formSchema) throws Exception {
        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("fieldList", fieldList);
        content.put("formSchema", formSchema);
        return wrapAsAiResponse(content);
    }

    private String wrapAsAiResponse(Map<String, Object> content) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("message",
                        Map.of("content", objectMapper.writeValueAsString(content))))));
    }

    private List<Map<String, Object>> validFieldList() {
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(createField("applicant", "申请人", "text"));
        fields.add(createField("leaveType", "请假类型", "select"));
        fields.add(createField("startDate", "开始日期", "date"));
        fields.add(createField("endDate", "结束日期", "date"));
        fields.add(createField("days", "请假天数", "number"));
        fields.add(createField("reason", "请假原因", "textarea"));
        return fields;
    }

    private Map<String, Object> validFormSchema() {
        return Map.of("layout", "vertical", "sections", List.of(
                Map.of("title", "基本信息", "fields",
                        List.of("applicant", "leaveType", "startDate", "endDate", "days")),
                Map.of("title", "补充信息", "fields", List.of("reason"))));
    }

    private Map<String, Object> createField(String field, String label, String type) {
        Map<String, Object> f = new java.util.LinkedHashMap<>();
        f.put("field", field);
        f.put("label", label);
        f.put("type", type);
        f.put("required", true);
        return f;
    }
}
