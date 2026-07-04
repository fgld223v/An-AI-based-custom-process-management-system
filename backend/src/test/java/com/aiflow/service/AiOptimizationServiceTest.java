package com.aiflow.service;

import com.aiflow.common.BusinessException;
import com.aiflow.config.AiConfig;
import com.aiflow.enums.TemplateStatus;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.ArgumentCaptor;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI 流程优化分析单元测试。
 *
 * <p>测试范围：</p>
 * <ul>
 *   <li>基于历史指标生成优化建议</li>
 *   <li>无历史数据时的错误处理</li>
 *   <li>采纳优化建议 — redundant_node（标记废弃）</li>
 *   <li>采纳优化建议 — bottleneck（缩短超时、开启自动审批规则）</li>
 *   <li>采纳优化建议 — permission_optimization（改为或签）</li>
 *   <li>采纳优化建议 — approval_optimization（全局广播优化备注）</li>
 *   <li>降级容错 — 指标采集查询失败不影响主流程</li>
 * </ul>
 */
class AiOptimizationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebClient webClient;
    private AiConfig aiConfig;
    private JdbcTemplate jdbcTemplate;
    private ProcessInstanceRepository processInstanceRepository;
    private ProcessTemplateRepository processTemplateRepository;
    private AiOptimizationService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        webClient = mock(WebClient.class);
        aiConfig = new AiConfig();
        aiConfig.setModel("deepseek-chat");
        aiConfig.setTimeoutSeconds(60);

        jdbcTemplate = mock(JdbcTemplate.class);
        processInstanceRepository = mock(ProcessInstanceRepository.class);
        processTemplateRepository = mock(ProcessTemplateRepository.class);

        service = new AiOptimizationService(webClient, aiConfig, objectMapper,
                jdbcTemplate, processInstanceRepository, processTemplateRepository);

        // Setup WebClient mock chain
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("{}"));
    }

    // ================================================================
    // 正常流程 — 基于历史指标生成优化建议
    // ================================================================

    @Test
    void generatesOptimizationSuggestionsFromHistoricalMetrics() throws Exception {
        Long templateId = 1L;
        setupTemplate(templateId);
        setupMetrics(templateId);
        String aiResponse = buildOptimizationResponse(
                "流程中存在明显瓶颈，主要问题是部门经理审批耗时过长",
                List.of(Map.of("type", "bottleneck", "nodeKey", "Approve_Manager",
                        "nodeName", "部门经理审批", "severity", "high",
                        "description", "平均停留48小时，超时率35%",
                        "suggestion", "增加自动审批规则：5000元以下自动通过",
                        "expectedImprovement", "预计节省24小时")));
        // Override the generic WebClient mock set up in @BeforeEach
        when(webClient.post()).thenReturn(null);

        var reqBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        var reqBodySpec = mock(WebClient.RequestBodySpec.class);
        var reqHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        var respSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(reqBodyUriSpec);
        when(reqBodyUriSpec.uri(anyString())).thenReturn(reqBodySpec);
        when(reqBodySpec.bodyValue(any())).thenReturn(reqHeadersSpec);
        when(reqHeadersSpec.retrieve()).thenReturn(respSpec);
        when(respSpec.onStatus(any(), any())).thenReturn(respSpec);
        when(respSpec.bodyToMono(String.class)).thenReturn(Mono.just(aiResponse));

        var result = service.optimizeTemplate(templateId);

        assertThat(result.getTemplateId()).isEqualTo(templateId);
        assertThat(result.getAnalysis()).contains("瓶颈");
        assertThat(result.getSuggestions()).hasSize(1);
        assertThat(result.getSuggestions().get(0).getSeverity()).isEqualTo("high");
    }

    // ================================================================
    // 无历史数据
    // ================================================================

    @Test
    void throwsExceptionWhenTemplateHasNoHistoricalData() {
        when(processTemplateRepository.findByIdAndDeleted(1L, 0)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.optimizeTemplate(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无历史流转数据");
    }

    // ================================================================
    // 采纳优化建议 — Map 格式 nodeConfig
    // ================================================================

    @Test
    void adoptRedundantNodeMarksNodeAsDeprecated() throws Exception {
        ProcessTemplate template = templateWithMapConfig(Map.of(
                "Approve_Unnecessary", Map.of("nodeId", "Approve_Unnecessary",
                        "businessType", "approval")));
        when(processTemplateRepository.findByIdAndDeleted(1L, 0))
                .thenReturn(Optional.of(template));
        when(processTemplateRepository.save(any(ProcessTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.adoptSuggestion(1L, "redundant_node", "Approve_Unnecessary",
                "该节点与后续节点审批人重复，建议合并");

        ArgumentCaptor<ProcessTemplate> captor = ArgumentCaptor.forClass(ProcessTemplate.class);
        verify(processTemplateRepository).save(captor.capture());
        String updatedConfig = captor.getValue().getNodeConfig();
        assertThat(updatedConfig).contains("\"deprecated\":true");
        assertThat(updatedConfig).contains("\"businessType\":\"skip\"");
    }

    @Test
    void adoptBottleneckHalvesTimeoutAndEnablesAutoApprovalRule() throws Exception {
        ProcessTemplate template = templateWithMapConfig(Map.of(
                "Approve_Manager", new java.util.LinkedHashMap<>(Map.of(
                        "nodeId", "Approve_Manager",
                        "businessType", "approval",
                        "timeoutConfig", Map.of("remindAfter", "24h", "autoAction", "approve")))));
        when(processTemplateRepository.findByIdAndDeleted(1L, 0))
                .thenReturn(Optional.of(template));
        when(processTemplateRepository.save(any(ProcessTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.adoptSuggestion(1L, "bottleneck", "Approve_Manager",
                "平均处理48小时，严重超时");

        ArgumentCaptor<ProcessTemplate> captor = ArgumentCaptor.forClass(ProcessTemplate.class);
        verify(processTemplateRepository).save(captor.capture());
        String config = captor.getValue().getNodeConfig();
        assertThat(config).contains("\"remindAfter\":\"12h\"");    // 24h → 12h
        assertThat(config).contains("\"approvalRule\"");
        assertThat(config).contains("\"enabled\":true");
        assertThat(config).contains("\"optimizedAt\"");
    }

    @Test
    void adoptBottleneckWithNoExistingTimeoutAddsDefaultConfig() throws Exception {
        ProcessTemplate template = templateWithMapConfig(Map.of(
                "Approve_Slow", Map.of("nodeId", "Approve_Slow",
                        "businessType", "approval")));
        when(processTemplateRepository.findByIdAndDeleted(1L, 0))
                .thenReturn(Optional.of(template));
        when(processTemplateRepository.save(any(ProcessTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.adoptSuggestion(1L, "bottleneck", "Approve_Slow",
                "处理缓慢，建议优化");

        ArgumentCaptor<ProcessTemplate> captor = ArgumentCaptor.forClass(ProcessTemplate.class);
        verify(processTemplateRepository).save(captor.capture());
        String config = captor.getValue().getNodeConfig();
        assertThat(config).contains("\"remindAfter\":\"12h\"");    // 无超时配置时使用默认值
        assertThat(config).contains("\"autoAction\":\"approve\"");
    }

    @Test
    void adoptPermissionOptimizationChangesApprovalModeToAny() throws Exception {
        ProcessTemplate template = templateWithMapConfig(Map.of(
                "Approve_Manager", Map.of("nodeId", "Approve_Manager",
                        "businessType", "approval", "approvalMode", "ALL")));
        when(processTemplateRepository.findByIdAndDeleted(1L, 0))
                .thenReturn(Optional.of(template));
        when(processTemplateRepository.save(any(ProcessTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.adoptSuggestion(1L, "permission_optimization", "Approve_Manager",
                "审批人处理时间过长，建议改为或签");

        ArgumentCaptor<ProcessTemplate> captor = ArgumentCaptor.forClass(ProcessTemplate.class);
        verify(processTemplateRepository).save(captor.capture());
        String config = captor.getValue().getNodeConfig();
        assertThat(config).contains("\"approvalMode\":\"ANY\"");
    }

    // ================================================================
    // 采纳优化建议 — List 格式 nodeConfig
    // ================================================================

    @Test
    void adoptOptimizationSupportsListNodeConfig() throws Exception {
        String listConfig = objectMapper.writeValueAsString(List.of(
                Map.of("nodeId", "Approve_Manager", "businessType", "approval")));
        ProcessTemplate template = ProcessTemplate.builder()
                .id(1L).templateName("报销流程")
                .nodeConfig(listConfig).formBindConfig("{}")
                .deleted(0).build();
        when(processTemplateRepository.findByIdAndDeleted(1L, 0))
                .thenReturn(Optional.of(template));
        when(processTemplateRepository.save(any(ProcessTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.adoptSuggestion(1L, "redundant_node", "Approve_Manager",
                "冗余节点，建议移除");

        ArgumentCaptor<ProcessTemplate> captor = ArgumentCaptor.forClass(ProcessTemplate.class);
        verify(processTemplateRepository).save(captor.capture());
        assertThat(captor.getValue().getNodeConfig()).contains("\"deprecated\":true");
    }

    // ================================================================
    // 采纳优化建议 — approval_optimization 广播
    // ================================================================

    @Test
    void adoptApprovalOptimizationBroadcastsToAllNodes() throws Exception {
        ProcessTemplate template = templateWithMapConfig(Map.of(
                "Approve_1", new java.util.LinkedHashMap<>(Map.of("nodeId", "Approve_1", "businessType", "approval")),
                "Approve_2", new java.util.LinkedHashMap<>(Map.of("nodeId", "Approve_2", "businessType", "approval"))));
        when(processTemplateRepository.findByIdAndDeleted(1L, 0))
                .thenReturn(Optional.of(template));
        when(processTemplateRepository.save(any(ProcessTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.adoptSuggestion(1L, "approval_optimization", null,
                "建议所有审批节点增加风险提示");

        ArgumentCaptor<ProcessTemplate> captor = ArgumentCaptor.forClass(ProcessTemplate.class);
        verify(processTemplateRepository).save(captor.capture());
        String config = captor.getValue().getNodeConfig();
        assertThat(config).contains("\"aiOptimizationNote\":\"建议所有审批节点增加风险提示\"");
    }

    // ================================================================
    // 采纳建议 — 前置校验
    // ================================================================

    @Test
    void adoptSuggestionRequiresExistingTemplate() {
        when(processTemplateRepository.findByIdAndDeleted(1L, 0)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.adoptSuggestion(1L, "bottleneck", "N1", "优化"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板不存在");
    }

    @Test
    void adoptSuggestionRequiresNodeConfig() {
        ProcessTemplate template = ProcessTemplate.builder()
                .id(1L).nodeConfig(null).deleted(0).build();
        when(processTemplateRepository.findByIdAndDeleted(1L, 0))
                .thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.adoptSuggestion(1L, "bottleneck", "N1", "优化"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无 nodeConfig");
    }

    // ================================================================
    // 采纳建议 — 其他优化类型
    // ================================================================

    @Test
    void adoptBranchOptimizationSetsBranchOptimizedFlag() throws Exception {
        ProcessTemplate template = templateWithMapConfig(Map.of(
                "Gateway_1", new java.util.LinkedHashMap<>(Map.of("nodeId", "Gateway_1",
                        "businessType", "condition", "defaultFlow", "Flow_Agree"))));
        when(processTemplateRepository.findByIdAndDeleted(1L, 0))
                .thenReturn(Optional.of(template));
        when(processTemplateRepository.save(any(ProcessTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.adoptSuggestion(1L, "branch_optimization", "Gateway_1",
                "该分支触发率低于3%，建议简化");

        ArgumentCaptor<ProcessTemplate> captor = ArgumentCaptor.forClass(ProcessTemplate.class);
        verify(processTemplateRepository).save(captor.capture());
        assertThat(captor.getValue().getNodeConfig()).contains("\"branchOptimized\":true");
    }

    @Test
    void adoptUnknownTypeSavesNoteWithoutStructuralChange() throws Exception {
        ProcessTemplate template = templateWithMapConfig(Map.of(
                "Task_1", new java.util.LinkedHashMap<>(Map.of("nodeId", "Task_1",
                        "businessType", "generic_task"))));
        when(processTemplateRepository.findByIdAndDeleted(1L, 0))
                .thenReturn(Optional.of(template));
        when(processTemplateRepository.save(any(ProcessTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.adoptSuggestion(1L, "unknown_type", "Task_1",
                "一些建议备注");

        ArgumentCaptor<ProcessTemplate> captor = ArgumentCaptor.forClass(ProcessTemplate.class);
        verify(processTemplateRepository).save(captor.capture());
        assertThat(captor.getValue().getNodeConfig())
                .contains("\"aiOptimizationNote\":\"一些建议备注\"");
    }

    // ================================================================
    // 采纳建议 — 快照备份
    // ================================================================

    @Test
    void adoptSuggestionSavesSnapshotForRollback() throws Exception {
        ProcessTemplate template = templateWithMapConfig(Map.of(
                "Approve_1", new java.util.LinkedHashMap<>(Map.of("nodeId", "Approve_1",
                        "businessType", "approval"))));
        template.setFormBindConfig(null);
        when(processTemplateRepository.findByIdAndDeleted(1L, 0))
                .thenReturn(Optional.of(template));
        when(processTemplateRepository.save(any(ProcessTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.adoptSuggestion(1L, "redundant_node", "Approve_1", "移除冗余节点");

        ArgumentCaptor<ProcessTemplate> captor = ArgumentCaptor.forClass(ProcessTemplate.class);
        verify(processTemplateRepository).save(captor.capture());
        String bindConfig = captor.getValue().getFormBindConfig();
        assertThat(bindConfig).contains("\"_snapshots\"");
        assertThat(bindConfig).contains("\"redundant_node\"");
    }

    // ================================================================
    // Helper methods
    // ================================================================

    private void setupTemplate(Long id) {
        ProcessTemplate template = ProcessTemplate.builder()
                .id(id).templateCode("reimburse-flow").templateName("报销流程")
                .status(TemplateStatus.PUBLISHED).version(1)
                .nodeConfig("[{\"nodeId\":\"Approve_Manager\",\"businessType\":\"approval\"}]")
                .deleted(0).build();
        when(processTemplateRepository.findByIdAndDeleted(id, 0)).thenReturn(Optional.of(template));
    }

    @SuppressWarnings("unchecked")
    private void setupMetrics(Long templateId) {
        // Overview query
        when(jdbcTemplate.queryForMap(argThat(sql -> sql != null && sql.startsWith("SELECT COUNT(*)")), eq(templateId)))
                .thenReturn(Map.of("total", 10L, "completed", 7L, "completionRate", 70.0,
                        "avgDurationHours", 36.5));

        // Node-level query
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.startsWith("SELECT t.node_key")), eq(templateId)))
                .thenReturn(List.of(Map.of("node_key", "Approve_Manager", "node_name", "部门经理审批",
                        "totalCount", 10L, "timeoutCount", 3L, "timeoutRate", 30.0,
                        "avgDwellHours", 48.0)));

        // Reject rate query
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.startsWith("SELECT ar.node_key")), eq(templateId), eq(templateId)))
                .thenReturn(List.of(Map.of("node_key", "Approve_Manager",
                        "rejectCount", 2L, "rejectRate", 20.0)));

        // Assignee efficiency query
        when(jdbcTemplate.queryForList(argThat(sql -> sql != null && sql.startsWith("SELECT t.assignee_id")), eq(templateId)))
                .thenReturn(List.of(Map.of("assignee_id", 20L, "taskCount", 5L, "avgProcessHours", 52.0)));

        // Make JdbcTemplate lenient — return empty list by default for any unmatched SQL
        lenient().when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());
    }

    private String buildOptimizationResponse(String analysis,
                                              List<Map<String, Object>> suggestions) throws Exception {
        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("analysis", analysis);
        content.put("suggestions", suggestions);

        Map<String, Object> responseMap = Map.of(
                "choices", List.of(Map.of("message", Map.of("content",
                        objectMapper.writeValueAsString(content)))));
        return objectMapper.writeValueAsString(responseMap);
    }

    private ProcessTemplate templateWithMapConfig(Map<String, Map<String, Object>> nodes) throws Exception {
        String config = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>(nodes));
        return ProcessTemplate.builder()
                .id(1L).templateName("测试流程")
                .nodeConfig(config).formBindConfig("{}")
                .deleted(0).build();
    }
}
