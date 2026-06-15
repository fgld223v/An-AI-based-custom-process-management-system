package com.aiflow.service.impl;

import com.aiflow.dto.NotificationCreateRequest;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.SysUser;
import com.aiflow.repository.NotificationRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskTimeoutNotificationScheduler {

    private static final String NOTIFICATION_TYPE = "timeout_warning";
    private static final String TARGET_TYPE_PREFIX = "flowable_task:";
    private static final Long DEFAULT_RECEIVER_ID = 1L;

    private final TaskService taskService;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final SysUserRepository sysUserRepository;

    @Value("${notification.timeout-threshold-hours:48}")
    private long timeoutThresholdHours;

    @Scheduled(fixedRateString = "${notification.timeout-scan-fixed-rate-ms:60000}")
    @Transactional
    public void scanTimeoutTasks() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(timeoutThresholdHours);
        List<Task> tasks = taskService.createTaskQuery()
                .taskCreatedBefore(toDate(deadline))
                .active()
                .list();

        int createdCount = 0;
        for (Task task : tasks) {
            if (createTimeoutNotificationIfAbsent(task, deadline)) {
                createdCount++;
            }
        }
        if (createdCount > 0) {
            log.info("Created {} timeout task notifications.", createdCount);
        }
    }

    private boolean createTimeoutNotificationIfAbsent(Task task, LocalDateTime deadline) {
        String targetType = targetType(task);
        String targetUrl = "/tasks/" + task.getId();
        boolean exists = notificationRepository
                .existsByTypeAndTargetTypeAndDeleted(NOTIFICATION_TYPE, targetType, 0);
        if (exists) {
            return false;
        }

        ProcessInstance instance = processInstanceRepository
                .findByFlowableProcessInstanceIdAndDeleted(task.getProcessInstanceId(), 0)
                .orElse(null);
        Long businessInstanceId = instance == null ? null : instance.getId();
        Long receiverId = resolveReceiverId(task);
        LocalDateTime taskCreateTime = toLocalDateTime(task.getCreateTime());
        long elapsedHours = taskCreateTime == null ? timeoutThresholdHours : Duration.between(taskCreateTime, LocalDateTime.now()).toHours();

        NotificationCreateRequest request = new NotificationCreateRequest();
        request.setReceiverId(receiverId);
        request.setType(NOTIFICATION_TYPE);
        request.setTitle("任务超时提醒");
        request.setContent(buildContent(task, instance, elapsedHours, deadline));
        request.setTargetType(targetType);
        request.setTargetId(businessInstanceId);
        request.setTargetUrl(targetUrl);
        notificationService.createNotification(request);
        return true;
    }

    private String targetType(Task task) {
        return TARGET_TYPE_PREFIX + task.getId();
    }

    private String buildContent(Task task, ProcessInstance instance, long elapsedHours, LocalDateTime deadline) {
        String instanceTitle = instance == null ? "未知流程实例" : instance.getTitle();
        String taskName = hasText(task.getName()) ? task.getName() : "未命名任务";
        return "流程「" + instanceTitle + "」中的任务「" + taskName + "」已超过 "
                + elapsedHours + " 小时未处理，请及时跟进。超时扫描阈值："
                + timeoutThresholdHours + " 小时，截止时间：" + deadline + "。";
    }

    private Long resolveReceiverId(Task task) {
        String assignee = task.getAssignee();
        if (!hasText(assignee)) {
            return DEFAULT_RECEIVER_ID;
        }
        try {
            return Long.parseLong(assignee.trim());
        } catch (NumberFormatException ignored) {
            Optional<SysUser> user = sysUserRepository.findByUsername(assignee.trim());
            return user.map(SysUser::getId).orElse(DEFAULT_RECEIVER_ID);
        }
    }

    private Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    private LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null : date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
