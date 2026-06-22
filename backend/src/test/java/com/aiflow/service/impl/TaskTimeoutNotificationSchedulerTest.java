package com.aiflow.service.impl;

import com.aiflow.model.ProcessInstance;
import com.aiflow.repository.NotificationRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.service.NotificationService;
import com.aiflow.service.RuleEvaluatorService;
import com.aiflow.service.ApprovalRecordService;
import com.aiflow.service.ApprovalVariableService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskTimeoutNotificationSchedulerTest {

    @Mock
    private TaskService taskService;
    @Mock
    private RuntimeService runtimeService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private RuleEvaluatorService ruleEvaluatorService;
    @Mock
    private ApprovalVariableService approvalVariableService;
    @Mock
    private ApprovalRecordService approvalRecordService;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private ProcessInstanceRepository processInstanceRepository;
    @Mock
    private ProcessTemplateRepository processTemplateRepository;
    @Mock
    private SysUserRepository sysUserRepository;
    @Mock
    private NodeConfigParser nodeConfigParser;
    @Mock
    private TaskQuery taskQuery;
    @Mock
    private Task task;

    @InjectMocks
    private TaskTimeoutNotificationScheduler scheduler;

    @Test
    void scanTimeoutTasksSkipsCompletedBusinessInstance() {
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));
        when(task.getProcessInstanceId()).thenReturn("flowable-1");

        ProcessInstance completed = ProcessInstance.builder()
                .id(1L)
                .status("completed")
                .flowableProcessInstanceId("flowable-1")
                .deleted(0)
                .build();
        when(processInstanceRepository.findByFlowableProcessInstanceIdAndDeleted("flowable-1", 0))
                .thenReturn(Optional.of(completed));

        scheduler.scanTimeoutTasks();

        verify(notificationService, never()).createNotification(any());
        verify(taskService, never()).complete(any(), anyMap());
        verify(ruleEvaluatorService, never()).evaluateAndAutoComplete(any());
    }

    @Test
    void configuredTimeoutRejectUsesUnifiedVariablesAndPersistsRecord() {
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));
        when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(task.getId()).thenReturn("task-1");
        when(task.getProcessInstanceId()).thenReturn("flowable-1");
        when(task.getTaskDefinitionKey()).thenReturn("Approve_1");
        when(task.getCreateTime()).thenReturn(new Date(0));

        ProcessInstance running = ProcessInstance.builder()
                .id(1L).templateId(2L).status("running")
                .flowableProcessInstanceId("flowable-1").deleted(0).build();
        when(processInstanceRepository.findByFlowableProcessInstanceIdAndDeleted("flowable-1", 0))
                .thenReturn(Optional.of(running));
        com.aiflow.model.ProcessTemplate template = com.aiflow.model.ProcessTemplate.builder()
                .id(2L).nodeConfig("{}").build();
        when(processTemplateRepository.findByIdAndDeleted(2L, 0)).thenReturn(Optional.of(template));
        when(nodeConfigParser.findNode("{}", "Approve_1")).thenReturn(Map.of(
                "timeoutConfig", Map.of("remindAfter", "1h", "autoAction", "auto_reject")));
        when(notificationRepository.existsByTypeAndTargetTypeAndDeleted(any(), any(), eq(0)))
                .thenReturn(true);
        when(approvalVariableService.build(eq("flowable-1"), eq("Approve_1"), eq("reject"),
                any(), eq(true), any(), any())).thenReturn(new java.util.HashMap<>());

        scheduler.scanTimeoutTasks();

        verify(taskService).complete(eq("task-1"), anyMap());
        verify(approvalRecordService).record(eq(1L), eq("task-1"), eq("Approve_1"),
                eq(null), eq("reject"), any(), any());
        verify(ruleEvaluatorService).evaluateAndAutoComplete(running);
    }
}
