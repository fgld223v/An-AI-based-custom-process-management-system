package com.aiflow.service.impl;

import com.aiflow.dto.NotificationCreateRequest;
import com.aiflow.dto.NotificationDTO;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.SysUser;
import com.aiflow.repository.NotificationRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.service.NotificationService;
import com.aiflow.service.TaskUrgeService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

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
        if (!"running".equals(instance.getStatus()) || !hasText(instance.getFlowableProcessInstanceId())) {
            throw new IllegalStateException("仅运行中的流程实例支持催办。");
        }
        Task task = taskService.createTaskQuery()
                .processInstanceId(instance.getFlowableProcessInstanceId())
                .active()
                .singleResult();
        if (task == null) {
            throw new IllegalStateException("当前流程没有待办任务，无法催办。");
        }
        return createUrgeNotification(task, instance, false);
    }

    @Override
    @Transactional
    public boolean autoUrgeTask(Task task, ProcessInstance instance) {
        if (task == null || instance == null) {
            return false;
        }
        String targetType = targetType(task);
        boolean exists = notificationRepository
                .existsByTypeAndTargetTypeAndDeleted(NOTIFICATION_TYPE, targetType, 0);
        if (exists) {
            return false;
        }
        createUrgeNotification(task, instance, true);
        return true;
    }

    private NotificationDTO createUrgeNotification(Task task, ProcessInstance instance, boolean automatic) {
        NotificationCreateRequest request = new NotificationCreateRequest();
        request.setReceiverId(resolveReceiverId(task));
        request.setType(NOTIFICATION_TYPE);
        request.setTitle(automatic ? "任务超时催办" : "任务催办提醒");
        request.setContent(buildContent(task, instance, automatic));
        request.setTargetType(targetType(task));
        request.setTargetId(instance.getId());
        request.setTargetUrl("/tasks/" + task.getId());
        return notificationService.createNotification(request);
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

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
