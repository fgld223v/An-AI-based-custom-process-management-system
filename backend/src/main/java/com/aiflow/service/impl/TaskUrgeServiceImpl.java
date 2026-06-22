package com.aiflow.service.impl;

import com.aiflow.dto.NotificationCreateRequest;
import com.aiflow.dto.NotificationDTO;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.SysUser;
import com.aiflow.repository.NotificationRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.security.SecurityUtils;
import com.aiflow.service.NotificationService;
import com.aiflow.service.TaskUrgeService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TaskUrgeServiceImpl implements TaskUrgeService {

    private static final String NOTIFICATION_TYPE = "task_remind";
    private static final String TARGET_TYPE_PREFIX = "flowable_task:";
    private static final Long DEFAULT_RECEIVER_ID = 1L;

    private final TaskService taskService;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final SysUserRepository sysUserRepository;

    @Override
    @Transactional
    public NotificationDTO urgeCurrentTask(Long processInstanceId) {
        ProcessInstance instance = processInstanceRepository
                .findByIdAndDeleted(processInstanceId, 0)
                .orElseThrow(() -> new IllegalArgumentException("流程实例不存在。"));
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null || !currentUserId.equals(instance.getApplicantId())) {
            throw new AccessDeniedException("only the applicant can urge this process instance");
        }
        if (!"running".equals(instance.getStatus()) || !hasText(instance.getFlowableProcessInstanceId())) {
            throw new IllegalStateException("仅运行中的流程实例支持催办。");
        }
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(instance.getFlowableProcessInstanceId())
                .active()
                .orderByTaskCreateTime().asc()
                .list();
        if (tasks.isEmpty()) {
            throw new IllegalStateException("当前流程没有待办任务，无法催办。");
        }
        List<NotificationDTO> notifications = new ArrayList<>();
        tasks.forEach(task -> notifications.addAll(createUrgeNotifications(task, instance, false)));
        if (notifications.isEmpty()) {
            throw new IllegalStateException("当前任务没有可通知的处理人。");
        }
        return notifications.get(0);
    }

    @Override
    @Transactional
    public boolean autoUrgeTask(Task task, ProcessInstance instance) {
        if (task == null || !isRunningInstance(instance)) {
            return false;
        }
        String targetType = targetType(task);
        boolean exists = notificationRepository
                .existsByTypeAndTargetTypeAndDeleted(NOTIFICATION_TYPE, targetType, 0);
        if (exists) {
            return false;
        }
        return !createUrgeNotifications(task, instance, true).isEmpty();
    }

    private List<NotificationDTO> createUrgeNotifications(
            Task task, ProcessInstance instance, boolean automatic) {
        return resolveReceiverIds(task).stream().map(receiverId -> {
            NotificationCreateRequest request = new NotificationCreateRequest();
            request.setReceiverId(receiverId);
            request.setType(NOTIFICATION_TYPE);
            request.setTitle(automatic ? "任务超时催办" : "任务催办提醒");
            request.setContent(buildContent(task, instance, automatic));
            request.setTargetType(targetType(task));
            request.setTargetId(instance.getId());
            request.setTargetUrl("/tasks/" + task.getId());
            return notificationService.createNotification(request);
        }).toList();
    }

    private String buildContent(Task task, ProcessInstance instance, boolean automatic) {
        String instanceTitle = hasText(instance.getTitle()) ? instance.getTitle() : "未知流程实例";
        String taskName = hasText(task.getName()) ? task.getName() : "未命名任务";
        String prefix = automatic ? "系统检测到该任务已等待较久，自动催办：" : "发起人已手动催办：";
        long elapsedHours = 0;
        if (task.getCreateTime() != null) {
            LocalDateTime createTime = task.getCreateTime().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
            elapsedHours = Math.max(0, Duration.between(createTime, LocalDateTime.now()).toHours());
        }
        return prefix + "流程「" + instanceTitle + "」中的任务「" + taskName + "」待处理"
                + (elapsedHours > 0 ? "已约 " + elapsedHours + " 小时" : "")
                + "，请及时处理。";
    }

    private String targetType(Task task) {
        return TARGET_TYPE_PREFIX + task.getId();
    }

    private List<Long> resolveReceiverIds(Task task) {
        String assignee = task.getAssignee();
        if (hasText(assignee)) {
            return List.of(resolveUserId(assignee));
        }
        Set<Long> candidateIds = new LinkedHashSet<>();
        for (IdentityLink link : taskService.getIdentityLinksForTask(task.getId())) {
            if (hasText(link.getUserId())) {
                candidateIds.add(resolveUserId(link.getUserId()));
            }
        }
        return candidateIds.isEmpty() ? List.of(DEFAULT_RECEIVER_ID) : List.copyOf(candidateIds);
    }

    private Long resolveUserId(String actor) {
        try {
            return Long.parseLong(actor.trim());
        } catch (NumberFormatException ignored) {
            Optional<SysUser> user = sysUserRepository.findByUsername(actor.trim());
            return user.map(SysUser::getId).orElse(DEFAULT_RECEIVER_ID);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isRunningInstance(ProcessInstance instance) {
        return instance != null
                && "running".equals(instance.getStatus())
                && hasText(instance.getFlowableProcessInstanceId());
    }
}
