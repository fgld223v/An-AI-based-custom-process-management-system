package com.aiflow.service;

import com.aiflow.model.ApprovalRecord;
import com.aiflow.repository.ApprovalRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalRecordServiceTest {

    @Test
    void recordsFlowableTaskIdAndOperator() {
        ApprovalRecordRepository repository = mock(ApprovalRecordRepository.class);
        WorkflowNotificationService notificationService = mock(WorkflowNotificationService.class);
        when(repository.findByTaskIdAndAction("task-abc", "approve")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalRecord record = new ApprovalRecordService(repository, notificationService).record(
                10L, "task-abc", "Approve_1", 20L,
                "approve", "同意", LocalDateTime.now());

        assertThat(record.getTaskId()).isEqualTo("task-abc");
        assertThat(record.getApproverId()).isEqualTo(20L);
        assertThat(record.getAction()).isEqualTo("approve");
        verify(notificationService).notifyApprovalResult(record);
    }

    @Test
    void repeatedActionForSameTaskIsIdempotent() {
        ApprovalRecordRepository repository = mock(ApprovalRecordRepository.class);
        WorkflowNotificationService notificationService = mock(WorkflowNotificationService.class);
        ApprovalRecord existing = ApprovalRecord.builder()
                .id(1L).taskId("task-abc").action("approve").build();
        when(repository.findByTaskIdAndAction("task-abc", "approve"))
                .thenReturn(Optional.of(existing));

        ApprovalRecord result = new ApprovalRecordService(repository, notificationService).record(
                10L, "task-abc", "Approve_1", 20L,
                "approve", "同意", LocalDateTime.now());

        assertThat(result).isSameAs(existing);
        verify(repository, never()).save(any());
        verify(notificationService, never()).notifyApprovalResult(any());
    }
}
