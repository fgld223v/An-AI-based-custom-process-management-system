package com.aiflow.service.impl;

import com.aiflow.dto.NotificationCreateRequest;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.model.SysUser;
import com.aiflow.repository.NotificationRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.service.NotificationService;
import com.aiflow.service.RuleEvaluatorService;
import com.aiflow.service.ApprovalRecordService;
import com.aiflow.service.ApprovalVariableService;
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
import java.util.Map;
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
    private final RuleEvaluatorService ruleEvaluatorService;
    private final NotificationRepository notificationRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final SysUserRepository sysUserRepository;
    private final NodeConfigParser nodeConfigParser;
    private final ApprovalVariableService approvalVariableService;
    private final ApprovalRecordService approvalRecordService;

    @Value("${notification.timeout-threshold-hours:48}")
    private long timeoutThresholdHours;

    @Scheduled(fixedRateString = "${notification.timeout-scan-fixed-rate-ms:60000}")
    @Transactional
    public void scanTimeoutTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<Task> tasks = taskService.createTaskQuery()
                .active()
                .list();

        int notificationCount = 0;
        int completedCount = 0;
        for (Task task : tasks) {
            ProcessInstance instance = processInstanceRepository
                    .findByFlowableProcessInstanceIdAndDeleted(task.getProcessInstanceId(), 0)
                    .orElse(null);
            if (!isRunningInstance(instance)) {
                continue;
            }
            TimeoutPolicy policy = resolveTimeoutPolicy(task, instance);
            LocalDateTime taskCreatedAt = toLocalDateTime(task.getCreateTime());
            if (taskCreatedAt == null || taskCreatedAt.isAfter(now.minusHours(policy.thresholdHours()))) {
                continue;
            }
            LocalDateTime deadline = now.minusHours(policy.thresholdHours());
            if (createTimeoutNotificationIfAbsent(task, instance, deadline, policy.thresholdHours())) {
                notificationCount++;
            }
            if (autoCompleteTimeoutTask(task, instance, policy)) {
                completedCount++;
            }
        }
        if (notificationCount > 0 || completedCount > 0) {
            log.info("Timeout scan finished. notifications={}, autoCompleted={}", notificationCount, completedCount);
        }
    }

    private boolean createTimeoutNotificationIfAbsent(Task task, ProcessInstance instance,
                                                       LocalDateTime deadline, long thresholdHours) {
        if (!isRunningInstance(instance)) {
            return false;
        }
        String targetType = targetType(task);
        String targetUrl = "/tasks/" + task.getId();
        boolean exists = notificationRepository
                .existsByTypeAndTargetTypeAndDeleted(NOTIFICATION_TYPE, targetType, 0);
        if (exists) {
            return false;
        }

        Long receiverId = resolveReceiverId(task);
        LocalDateTime taskCreateTime = toLocalDateTime(task.getCreateTime());
        long elapsedHours = taskCreateTime == null ? timeoutThresholdHours : Duration.between(taskCreateTime, LocalDateTime.now()).toHours();

        NotificationCreateRequest request = new NotificationCreateRequest();
        request.setReceiverId(receiverId);
        request.setType(NOTIFICATION_TYPE);
        request.setTitle("任务超时提醒");
        request.setContent(buildContent(task, instance, elapsedHours, deadline, thresholdHours));
        request.setTargetType(targetType);
        request.setTargetId(instance.getId());
        request.setTargetUrl(targetUrl);
        notificationService.createNotification(request);
        return true;
    }

    private boolean autoCompleteTimeoutTask(Task task, ProcessInstance instance, TimeoutPolicy policy) {
        if (!isRunningInstance(instance)) {
            return false;
        }
        if (!policy.autoApprove() && !policy.autoReject()) {
            return false;
        }
        Task latestTask = taskService.createTaskQuery()
                .taskId(task.getId())
                .singleResult();
        if (latestTask == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        String result = policy.autoReject() ? "reject" : "agree";
        String action = policy.autoReject() ? "reject" : "approve";
        String opinion = policy.autoReject() ? "系统超时自动驳回" : "系统超时自动通过";
        String automaticReason = "任务超过配置时长 " + policy.thresholdHours() + " 小时未处理";
        Map<String, Object> variables = approvalVariableService.build(
                latestTask.getProcessInstanceId(), latestTask.getTaskDefinitionKey(), result,
                opinion, true, automaticReason, now);
        variables.put("timeoutAutoCompleted", true);
        variables.put("timeoutTaskId", latestTask.getId());
        variables.put("timeoutTaskName", latestTask.getName());

        try {
            taskService.addComment(latestTask.getId(), latestTask.getProcessInstanceId(), opinion);
            taskService.complete(latestTask.getId(), variables);
            approvalRecordService.record(instance.getId(), latestTask.getId(),
                    latestTask.getTaskDefinitionKey(), null, action,
                    opinion + "：" + automaticReason, now);
            ruleEvaluatorService.evaluateAndAutoComplete(instance);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to auto-complete timeout task {}: {}", latestTask.getId(), safeMessage(ex), ex);
            return false;
        }
    }

    private String targetType(Task task) {
        return TARGET_TYPE_PREFIX + task.getId();
    }

    private TimeoutPolicy resolveTimeoutPolicy(Task task, ProcessInstance instance) {
        ProcessTemplate template = processTemplateRepository
                .findByIdAndDeleted(instance.getTemplateId(), 0)
                .orElse(null);
        if (template == null || !hasText(template.getNodeConfig())) {
            return new TimeoutPolicy(timeoutThresholdHours, null);
        }
        Map<String, Object> node = nodeConfigParser.findNode(
                template.getNodeConfig(), task.getTaskDefinitionKey());
        if (node == null) {
            return new TimeoutPolicy(timeoutThresholdHours, null);
        }
        Map<String, Object> timeout = asMap(node.get("timeoutConfig"));
        long threshold = positiveLong(firstNonNull(
                node.get("remindAfterHours"), timeout.get("remindAfterHours"),
                timeout.get("remindAfter"), timeout.get("timeoutHours")), timeoutThresholdHours);
        String autoAction = stringValue(firstNonNull(node.get("autoAction"), timeout.get("autoAction")));
        return new TimeoutPolicy(threshold, autoAction);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private long positiveLong(Object value, long fallback) {
        try {
            if (value instanceof Number number) {
                long parsed = number.longValue();
                return parsed > 0 ? parsed : fallback;
            }
            String text = String.valueOf(value).trim().toLowerCase();
            long multiplier = 1;
            if (text.endsWith("d")) {
                multiplier = 24;
                text = text.substring(0, text.length() - 1).trim();
            } else if (text.endsWith("h")) {
                text = text.substring(0, text.length() - 1).trim();
            }
            long parsed = Long.parseLong(text) * multiplier;
            return parsed > 0 ? parsed : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString().trim();
    }

    private record TimeoutPolicy(long thresholdHours, String autoAction) {
        boolean autoApprove() {
            return "auto_approve".equalsIgnoreCase(autoAction) || "approve".equalsIgnoreCase(autoAction);
        }

        boolean autoReject() {
            return "auto_reject".equalsIgnoreCase(autoAction) || "reject".equalsIgnoreCase(autoAction);
        }
    }

    private String buildContent(Task task, ProcessInstance instance, long elapsedHours,
                                LocalDateTime deadline, long thresholdHours) {
        String instanceTitle = instance == null ? "未知流程实例" : instance.getTitle();
        String taskName = hasText(task.getName()) ? task.getName() : "未命名任务";
        return "流程「" + instanceTitle + "」中的任务「" + taskName + "」已超过 "
                + elapsedHours + " 小时未处理，请及时跟进。超时扫描阈值："
                + thresholdHours + " 小时，截止时间：" + deadline + "。";
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

    private boolean isRunningInstance(ProcessInstance instance) {
        return instance != null
                && "running".equals(instance.getStatus())
                && hasText(instance.getFlowableProcessInstanceId());
    }

    private String safeMessage(Exception ex) {
        return hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
