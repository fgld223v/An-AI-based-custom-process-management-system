package com.aiflow.service.impl;

import com.aiflow.model.ProcessInstance;
import com.aiflow.repository.NotificationRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.service.NotificationService;
import com.aiflow.service.RuleEvaluatorService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.any;
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
    private NotificationRepository notificationRepository;
    @Mock
    private ProcessInstanceRepository processInstanceRepository;
    @Mock
    private ProcessTemplateRepository processTemplateRepository;
    @Mock
    private SysUserRepository sysUserRepository;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private TaskQuery taskQuery;
    @Mock
    private Task task;

    @InjectMocks
    private TaskTimeoutNotificationScheduler scheduler;

    @Test
    void scanTimeoutTasksSkipsCompletedBusinessInstance() {
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskCreatedBefore(any(Date.class))).thenReturn(taskQuery);
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
}
