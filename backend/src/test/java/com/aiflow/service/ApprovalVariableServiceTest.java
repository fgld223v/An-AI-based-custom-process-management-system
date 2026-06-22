package com.aiflow.service;

import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApprovalVariableServiceTest {

    @Test
    void buildsStandardAndNodeScopedApprovalVariables() {
        RuntimeService runtimeService = mock(RuntimeService.class);
        Map<String, Object> existingForms = new LinkedHashMap<>();
        existingForms.put("Start_1", Map.of("amount", 100));
        when(runtimeService.getVariables("process-1"))
                .thenReturn(Map.of("allFormData", existingForms));
        ApprovalVariableService service = new ApprovalVariableService(runtimeService);

        Map<String, Object> variables = service.build(
                "process-1", "Approve_Manager", "approve", "同意",
                false, null, LocalDateTime.of(2026, 6, 21, 10, 0));

        assertThat(variables)
                .containsEntry("approvalResult", "agree")
                .containsEntry("approved", true)
                .containsEntry("rejected", false)
                .containsEntry("Approve_Manager_result", "agree")
                .containsEntry("Approve_Manager_approved", true);
        assertThat(stringObjectMap(variables.get("allFormData")))
                .containsKeys("Start_1", "Approve_Manager");
        assertThat(stringObjectMap(variables.get("approvalResults")))
                .containsKey("Approve_Manager");
    }

    @Test
    void rejectVariablesUseTheSameProtocol() {
        RuntimeService runtimeService = mock(RuntimeService.class);
        when(runtimeService.getVariables("process-1")).thenReturn(Map.of());

        Map<String, Object> variables = new ApprovalVariableService(runtimeService).build(
                "process-1", "Approve_1", "reject", "材料不足",
                false, null, LocalDateTime.now());

        assertThat(variables)
                .containsEntry("approvalResult", "reject")
                .containsEntry("approved", false)
                .containsEntry("rejected", true);
    }

    @Test
    void approvalResultIsRequired() {
        RuntimeService runtimeService = mock(RuntimeService.class);
        ApprovalVariableService service = new ApprovalVariableService(runtimeService);

        assertThatThrownBy(() -> service.build(
                "process-1", "Approve_1", null, null,
                false, null, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("approval result");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> stringObjectMap(Object value) {
        return (Map<String, Object>) value;
    }
}
