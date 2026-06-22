package com.aiflow.integration;

import com.aiflow.dto.TimelineDTO;
import com.aiflow.model.ApprovalRecord;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.SysUser;
import com.aiflow.model.Notification;
import com.aiflow.repository.ApprovalRecordRepository;
import com.aiflow.repository.NotificationRepository;
import com.aiflow.service.ApprovalRecordService;
import com.aiflow.service.ProcessTimelineService;
import com.aiflow.service.WorkflowNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:approval-record;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@Import({ApprovalRecordService.class, ProcessTimelineService.class})
class ApprovalRecordTimelineJpaIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("com.aiflow.model")
    @EnableJpaRepositories("com.aiflow.repository")
    static class JpaTestApplication {
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ApprovalRecordRepository approvalRecordRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ApprovalRecordService approvalRecordService;

    @Autowired
    private ProcessTimelineService processTimelineService;

    @MockBean
    private WorkflowNotificationService workflowNotificationService;

    @Test
    void persistsIdempotentApprovalRecordAndBuildsTimelineFromDatabase() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 6, 22, 9, 0);
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 22, 10, 15);
        LocalDateTime endedAt = LocalDateTime.of(2026, 6, 22, 10, 30);

        SysUser approver = entityManager.persistAndFlush(SysUser.builder()
                .username("manager")
                .password("test-only")
                .nickname("Manager Zhang")
                .role("MANAGER")
                .systemRole("normal_user")
                .enabled(1)
                .createdTime(startedAt)
                .updatedTime(startedAt)
                .deleted(0)
                .build());

        ProcessInstance instance = entityManager.persistAndFlush(ProcessInstance.builder()
                .instanceCode("PI-INTEGRATION-001")
                .templateId(100L)
                .applicantId(200L)
                .title("Approval integration instance")
                .status("completed")
                .startedAt(startedAt)
                .endedAt(endedAt)
                .createdAt(startedAt)
                .updatedAt(endedAt)
                .deleted(0)
                .build());

        ApprovalRecord first = approvalRecordService.record(
                instance.getId(), "flowable-task-1", "Approve_Manager", approver.getId(),
                "approve", "Looks good", approvedAt);
        ApprovalRecord repeated = approvalRecordService.record(
                instance.getId(), "flowable-task-1", "Approve_Manager", approver.getId(),
                "approve", "Duplicate request", approvedAt.plusMinutes(1));

        entityManager.flush();
        entityManager.clear();

        assertThat(repeated.getId()).isEqualTo(first.getId());
        assertThat(approvalRecordRepository.count()).isEqualTo(1);
        ApprovalRecord persisted = approvalRecordRepository.findById(first.getId()).orElseThrow();
        assertThat(persisted.getCommentText()).isEqualTo("Looks good");
        assertThat(persisted.getApproverId()).isEqualTo(approver.getId());

        TimelineDTO timeline = processTimelineService.buildTimeline(instance.getId());
        assertThat(timeline.getNodes()).hasSize(3);
        assertThat(timeline.getNodes()).extracting(TimelineDTO.TimelineNode::getType)
                .containsExactly("start", "approval", "end");

        TimelineDTO.TimelineNode approvalNode = timeline.getNodes().get(1);
        assertThat(approvalNode.getNodeName()).isEqualTo("Approve_Manager");
        assertThat(approvalNode.getOperatorName()).isEqualTo("Manager Zhang");
        assertThat(approvalNode.getComment()).isEqualTo("Looks good");
        assertThat(approvalNode.getDuration()).isEqualTo("1h15m");
    }

    @Test
    void notificationTargetUrlSurvivesDatabaseReload() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 22, 11, 0);
        Notification saved = notificationRepository.saveAndFlush(Notification.builder()
                .receiverId(20L)
                .type("task_remind")
                .title("New task")
                .targetType("flowable_task:task-1")
                .targetId(100L)
                .targetUrl("/tasks/task-1")
                .isRead(false)
                .createdAt(now)
                .updatedAt(now)
                .deleted(0)
                .build());

        entityManager.clear();

        Notification reloaded = notificationRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getTargetUrl()).isEqualTo("/tasks/task-1");
    }
}
