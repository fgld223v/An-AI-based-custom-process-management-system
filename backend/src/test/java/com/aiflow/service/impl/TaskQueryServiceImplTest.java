package com.aiflow.service.impl;

import com.aiflow.dto.TaskDTO;
import com.aiflow.entity.UserEntity;
import com.aiflow.model.ApprovalRecord;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.ApprovalRecordRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.security.CurrentUser;
import com.aiflow.service.TaskAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskQueryServiceImplTest {

    private static final Long USER_ID = 2L;
    private static final String TASK_ID = "history-task-1";
    private static final String FLOWABLE_INSTANCE_ID = "flowable-instance-1";

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsFinishedTaskFromApprovalRecordWhenHistoricAssigneeIsMissing() {
        TestFixture fixture = fixture();
        HistoricTaskInstanceQuery assignedQuery = mock(HistoricTaskInstanceQuery.class, RETURNS_SELF);
        HistoricTaskInstanceQuery recordedQuery = mock(HistoricTaskInstanceQuery.class, RETURNS_SELF);
        when(fixture.historyService.createHistoricTaskInstanceQuery())
                .thenReturn(assignedQuery, recordedQuery);
        when(assignedQuery.list()).thenReturn(List.of());
        HistoricTaskInstance historicTask = historicTask();
        when(recordedQuery.singleResult()).thenReturn(historicTask);
        when(fixture.approvalRecordRepository
                .findByApproverIdAndTaskIdIsNotNullOrderByOperatedAtDesc(USER_ID))
                .thenReturn(List.of(approvalRecord()));

        List<TaskDTO> result = fixture.service.listDoneTasks();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTaskId()).isEqualTo(TASK_ID);
        assertThat(result.get(0).getAssignee()).isEqualTo(String.valueOf(USER_ID));
        assertThat(result.get(0).getStatus()).isEqualTo("completed");
    }

    @Test
    void permitsRecordedApproverToViewHistoricTaskWithoutHistoricAssignee() {
        TestFixture fixture = fixture();
        HistoricTaskInstanceQuery query = mock(HistoricTaskInstanceQuery.class, RETURNS_SELF);
        when(fixture.historyService.createHistoricTaskInstanceQuery()).thenReturn(query);
        HistoricTaskInstance historicTask = historicTask();
        when(query.singleResult()).thenReturn(historicTask);
        when(fixture.approvalRecordRepository.existsByTaskIdAndApproverId(TASK_ID, USER_ID))
                .thenReturn(true);

        TaskDTO result = fixture.service.getTask(TASK_ID);

        assertThat(result.getAssignee()).isEqualTo(String.valueOf(USER_ID));
        verify(fixture.taskAuthorizationService, never()).assertCanView(
                org.mockito.ArgumentMatchers.any(HistoricTaskInstance.class));
    }

    private TestFixture fixture() {
        authenticate();
        TaskService taskService = mock(TaskService.class);
        TaskQuery runtimeTaskQuery = mock(TaskQuery.class, RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(runtimeTaskQuery);
        when(runtimeTaskQuery.singleResult()).thenReturn(null);
        HistoryService historyService = mock(HistoryService.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ProcessTemplateRepository processTemplateRepository = mock(ProcessTemplateRepository.class);
        TaskAuthorizationService taskAuthorizationService = mock(TaskAuthorizationService.class);
        ApprovalRecordRepository approvalRecordRepository = mock(ApprovalRecordRepository.class);

        ProcessInstance instance = ProcessInstance.builder()
                .id(6L)
                .templateId(11L)
                .flowableProcessInstanceId(FLOWABLE_INSTANCE_ID)
                .instanceCode("PI-6")
                .title("Single department approval")
                .deleted(0)
                .build();
        when(processInstanceRepository
                .findByFlowableProcessInstanceIdAndDeleted(FLOWABLE_INSTANCE_ID, 0))
                .thenReturn(Optional.of(instance));
        when(processTemplateRepository.findByIdAndDeleted(11L, 0))
                .thenReturn(Optional.of(ProcessTemplate.builder().id(11L).deleted(0).build()));

        TaskQueryServiceImpl service = new TaskQueryServiceImpl(
                taskService, historyService, runtimeService,
                processInstanceRepository, processTemplateRepository,
                new ObjectMapper(), mock(FormBindConfigParser.class),
                taskAuthorizationService, approvalRecordRepository);
        return new TestFixture(service, historyService, taskAuthorizationService,
                approvalRecordRepository);
    }

    private HistoricTaskInstance historicTask() {
        HistoricTaskInstance task = mock(HistoricTaskInstance.class);
        when(task.getId()).thenReturn(TASK_ID);
        when(task.getName()).thenReturn("Manager approval");
        when(task.getTaskDefinitionKey()).thenReturn("Approve_Manager");
        when(task.getProcessInstanceId()).thenReturn(FLOWABLE_INSTANCE_ID);
        when(task.getAssignee()).thenReturn(null);
        when(task.getCreateTime()).thenReturn(new Date(1_000L));
        when(task.getEndTime()).thenReturn(new Date(2_000L));
        return task;
    }

    private ApprovalRecord approvalRecord() {
        return ApprovalRecord.builder()
                .instanceId(6L)
                .taskId(TASK_ID)
                .nodeKey("Approve_Manager")
                .approverId(USER_ID)
                .action("approve")
                .operatedAt(LocalDateTime.now())
                .build();
    }

    private void authenticate() {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setUsername("bizadmin");
        user.setEnabled(1);
        user.setDeleted(0);
        CurrentUser currentUser = new CurrentUser(
                user, List.of(new SimpleGrantedAuthority("ROLE_BUSINESS_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, null, currentUser.getAuthorities()));
    }

    private record TestFixture(
            TaskQueryServiceImpl service,
            HistoryService historyService,
            TaskAuthorizationService taskAuthorizationService,
            ApprovalRecordRepository approvalRecordRepository) {
    }
}
