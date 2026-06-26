package com.aiflow.service.impl;

import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.service.ApprovalRecordService;
import com.aiflow.service.ApprovalVariableService;
import com.aiflow.service.WorkflowNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuleEvaluatorServiceImplTest {

    @Test
    void disabledApprovalRuleDoesNotAutoCompleteEvenWhenActionIsApprove() {
        TaskService taskService = mock(TaskService.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        ProcessInstanceRepository instanceRepository = mock(ProcessInstanceRepository.class);
        ProcessTemplateRepository templateRepository = mock(ProcessTemplateRepository.class);
        NodeConfigParser nodeConfigParser = mock(NodeConfigParser.class);
        ApprovalVariableService variableService = mock(ApprovalVariableService.class);
        ApprovalRecordService recordService = mock(ApprovalRecordService.class);
        WorkflowNotificationService notificationService = mock(WorkflowNotificationService.class);
        TaskQuery taskQuery = mock(TaskQuery.class, RETURNS_SELF);
        Task task = mock(Task.class);

        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));
        when(task.getTaskDefinitionKey()).thenReturn("Approve_Supervisor");
        when(task.getName()).thenReturn("Supervisor approval");
        when(templateRepository.findByIdAndDeleted(200L, 0)).thenReturn(Optional.of(
                ProcessTemplate.builder().id(200L).nodeConfig("{\"Approve_Supervisor\":{}} ").build()));
        when(nodeConfigParser.findNode(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("Approve_Supervisor"))).thenReturn(Map.of(
                        "businessType", "approval",
                        "approvalRule", Map.of(
                                "enabled", false,
                                "action", "approve",
                                "field", "leaveDays",
                                "operator", "<",
                                "value", 3)));

        ProcessInstance instance = ProcessInstance.builder()
                .id(100L)
                .templateId(200L)
                .flowableProcessInstanceId("flowable-100")
                .status("running")
                .formData("{\"leaveDays\":1}")
                .deleted(0)
                .build();
        RuleEvaluatorServiceImpl service = new RuleEvaluatorServiceImpl(
                taskService, runtimeService, instanceRepository, templateRepository,
                new ObjectMapper(), nodeConfigParser, variableService, recordService,
                notificationService);

        Task result = service.evaluateAndAutoComplete(instance);

        assertThat(result).isSameAs(task);
        assertThat(instance.getCurrentNodeKey()).isEqualTo("Approve_Supervisor");
        verify(taskService, never()).complete(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void refreshesStateFromFirstTaskWhenMultiInstanceHasMultipleTasks() {
        TaskService taskService = mock(TaskService.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        ProcessInstanceRepository instanceRepository = mock(ProcessInstanceRepository.class);
        ProcessTemplateRepository templateRepository = mock(ProcessTemplateRepository.class);
        NodeConfigParser nodeConfigParser = mock(NodeConfigParser.class);
        ApprovalVariableService variableService = mock(ApprovalVariableService.class);
        ApprovalRecordService recordService = mock(ApprovalRecordService.class);
        WorkflowNotificationService notificationService = mock(WorkflowNotificationService.class);
        TaskQuery taskQuery = mock(TaskQuery.class, RETURNS_SELF);
        Task firstTask = mock(Task.class);
        Task secondTask = mock(Task.class);

        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(firstTask, secondTask));
        when(firstTask.getTaskDefinitionKey()).thenReturn("Approve_Role");
        when(firstTask.getName()).thenReturn("Role approval");
        when(templateRepository.findByIdAndDeleted(200L, 0)).thenReturn(Optional.empty());
        ProcessInstance instance = ProcessInstance.builder()
                .id(100L)
                .templateId(200L)
                .flowableProcessInstanceId("flowable-100")
                .status("running")
                .deleted(0)
                .build();

        RuleEvaluatorServiceImpl service = new RuleEvaluatorServiceImpl(
                taskService, runtimeService, instanceRepository, templateRepository,
                new ObjectMapper(), nodeConfigParser, variableService, recordService,
                notificationService);

        Task result = service.evaluateAndAutoComplete(instance);

        assertThat(result).isSameAs(firstTask);
        assertThat(instance.getCurrentNodeKey()).isEqualTo("Approve_Role");
        verify(taskQuery, never()).singleResult();
        verify(instanceRepository).save(instance);
    }
}
