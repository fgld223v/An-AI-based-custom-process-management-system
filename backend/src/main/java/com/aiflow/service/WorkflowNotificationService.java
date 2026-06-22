package com.aiflow.service;

import com.aiflow.dto.NotificationCreateRequest;
import com.aiflow.model.ApprovalRecord;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.SysUser;
import com.aiflow.repository.NotificationRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowNotificationService {

    private static final String TASK_REMIND = "task_remind";
    private static final String APPROVAL_RESULT = "approval_result";
    private static final String PROCESS_COMPLETED = "process_completed";

    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final SysUserRepository sysUserRepository;

    public void notifyTaskCreated(String taskId, Long instanceId, String assignee, String taskName) {
        Long receiverId = resolveUserId(assignee);
        if (!hasText(taskId) || instanceId == null || receiverId == null) return;
        String targetType = "flowable_task:" + taskId.trim();
        createIfAbsent(receiverId, TASK_REMIND, targetType,
                "新待办任务",
                "您有新的待办任务：" + defaultText(taskName, "待处理任务"),
                instanceId, "/tasks/" + taskId.trim());
    }

    public void notifyApprovalResult(ApprovalRecord record) {
        if (record == null || record.getInstanceId() == null || !hasText(record.getTaskId())) return;
        ProcessInstance instance = processInstanceRepository
                .findByIdAndDeleted(record.getInstanceId(), 0).orElse(null);
        if (instance == null || instance.getApplicantId() == null) return;
        boolean approved = "approve".equals(record.getAction());
        String targetType = "approval_result:" + record.getTaskId() + ":" + record.getAction();
        createIfAbsent(instance.getApplicantId(), APPROVAL_RESULT, targetType,
                approved ? "审批已通过" : "审批已驳回",
                "流程“" + defaultText(instance.getTitle(), instance.getInstanceCode()) + "”"
                        + (approved ? "已通过当前审批节点" : "在当前审批节点被驳回"),
                instance.getId(), "/process/instances/" + instance.getId());
    }

    public void notifyProcessCompleted(ProcessInstance instance) {
        if (instance == null || instance.getId() == null || instance.getApplicantId() == null) return;
        String targetType = "process_completed:" + instance.getId();
        createIfAbsent(instance.getApplicantId(), PROCESS_COMPLETED, targetType,
                "流程已完成",
                "流程“" + defaultText(instance.getTitle(), instance.getInstanceCode()) + "”已完成",
                instance.getId(), "/process/instances/" + instance.getId());
    }

    private void createIfAbsent(Long receiverId, String type, String targetType,
                                String title, String content, Long targetId, String targetUrl) {
        try {
            if (notificationRepository.existsByTypeAndReceiverIdAndTargetTypeAndDeleted(
                    type, receiverId, targetType, 0)) {
                return;
            }
            NotificationCreateRequest request = new NotificationCreateRequest();
            request.setReceiverId(receiverId);
            request.setType(type);
            request.setTitle(title);
            request.setContent(content);
            request.setTargetType(targetType);
            request.setTargetId(targetId);
            request.setTargetUrl(targetUrl);
            notificationService.createNotification(request);
        } catch (RuntimeException ex) {
            log.warn("Failed to create workflow notification type={}, target={}: {}",
                    type, targetType, ex.getMessage());
        }
    }

    private Long resolveUserId(String actor) {
        if (!hasText(actor)) return null;
        try {
            return Long.parseLong(actor.trim());
        } catch (NumberFormatException ignored) {
            return sysUserRepository.findByUsername(actor.trim()).map(SysUser::getId).orElse(null);
        }
    }

    private String defaultText(String value, String fallback) {
        return hasText(value) ? value.trim() : defaultText(fallback, "未命名流程");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
