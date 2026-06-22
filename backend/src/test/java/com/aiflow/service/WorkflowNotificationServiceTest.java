package com.aiflow.service;

import com.aiflow.dto.NotificationCreateRequest;
import com.aiflow.model.ApprovalRecord;
import com.aiflow.model.ProcessInstance;
import com.aiflow.repository.NotificationRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.SysUserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowNotificationServiceTest {

    @Test
    void taskCreationNotifiesFinalAssigneeWithTaskLink() {
        Fixture fixture = fixture();

        fixture.service.notifyTaskCreated("task-1", 100L, "20", "Manager approval");

        NotificationCreateRequest request = capture(fixture.notificationService);
        assertThat(request.getReceiverId()).isEqualTo(20L);
        assertThat(request.getType()).isEqualTo("task_remind");
        assertThat(request.getTargetType()).isEqualTo("flowable_task:task-1");
        assertThat(request.getTargetId()).isEqualTo(100L);
        assertThat(request.getTargetUrl()).isEqualTo("/tasks/task-1");
    }

    @Test
    void approvalAndCompletionNotifyApplicantWithProcessLink() {
        Fixture fixture = fixture();
        ProcessInstance instance = ProcessInstance.builder()
                .id(100L).applicantId(30L).title("Expense request").deleted(0).build();
        when(fixture.processInstanceRepository.findByIdAndDeleted(100L, 0))
                .thenReturn(Optional.of(instance));

        fixture.service.notifyApprovalResult(ApprovalRecord.builder()
                .instanceId(100L).taskId("task-1").action("reject").build());
        fixture.service.notifyProcessCompleted(instance);

        ArgumentCaptor<NotificationCreateRequest> captor =
                ArgumentCaptor.forClass(NotificationCreateRequest.class);
        verify(fixture.notificationService, org.mockito.Mockito.times(2))
                .createNotification(captor.capture());
        assertThat(captor.getAllValues()).extracting(NotificationCreateRequest::getType)
                .containsExactly("approval_result", "process_completed");
        assertThat(captor.getAllValues()).allSatisfy(request -> {
            assertThat(request.getReceiverId()).isEqualTo(30L);
            assertThat(request.getTargetId()).isEqualTo(100L);
            assertThat(request.getTargetUrl()).isEqualTo("/process/instances/100");
        });
    }

    @Test
    void duplicateWorkflowEventDoesNotCreateAnotherNotification() {
        Fixture fixture = fixture();
        when(fixture.notificationRepository.existsByTypeAndReceiverIdAndTargetTypeAndDeleted(
                eq("task_remind"), eq(20L), eq("flowable_task:task-1"), eq(0)))
                .thenReturn(true);

        fixture.service.notifyTaskCreated("task-1", 100L, "20", "Approval");

        verify(fixture.notificationService, never()).createNotification(any());
    }

    private NotificationCreateRequest capture(NotificationService notificationService) {
        ArgumentCaptor<NotificationCreateRequest> captor =
                ArgumentCaptor.forClass(NotificationCreateRequest.class);
        verify(notificationService).createNotification(captor.capture());
        return captor.getValue();
    }

    private Fixture fixture() {
        NotificationService notificationService = mock(NotificationService.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        SysUserRepository sysUserRepository = mock(SysUserRepository.class);
        WorkflowNotificationService service = new WorkflowNotificationService(
                notificationService, notificationRepository,
                processInstanceRepository, sysUserRepository);
        return new Fixture(service, notificationService, notificationRepository,
                processInstanceRepository);
    }

    private record Fixture(WorkflowNotificationService service,
                           NotificationService notificationService,
                           NotificationRepository notificationRepository,
                           ProcessInstanceRepository processInstanceRepository) {
    }
}
