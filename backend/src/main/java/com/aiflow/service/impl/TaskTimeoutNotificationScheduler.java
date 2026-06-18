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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
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
import java.util.HashMap;
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
    private final RuntimeService runtimeService;
    private final NotificationService notificationService;
    private final RuleEvaluatorService ruleEvaluatorService;
    private final NotificationRepository notificationRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final SysUserRepository sysUserRepository;
    private final ObjectMapper objectMapper;

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

        int notificationCount = 0;
        int completedCount = 0;
        for (Task task : tasks) {
            ProcessInstance instance = processInstanceRepository
                    .findByFlowableProcessInstanceIdAndDeleted(task.getProcessInstanceId(), 0)
                    .orElse(null);
            if (!isRunningInstance(instance)) {
                continue;
            }
            if (createTimeoutNotificationIfAbsent(task, instance, deadline)) {
                notificationCount++;
            }
            if (autoCompleteTimeoutTask(task, instance)) {
                completedCount++;
            }
        }
        if (notificationCount > 0 || completedCount > 0) {
            log.info("Timeout scan finished. notifications={}, autoCompleted={}", notificationCount, completedCount);
        }
    }

    private boolean createTimeoutNotificationIfAbsent(Task task, ProcessInstance instance, LocalDateTime deadline) {
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
        request.setContent(buildContent(task, instance, elapsedHours, deadline));
        request.setTargetType(targetType);
        request.setTargetId(instance.getId());
        request.setTargetUrl(targetUrl);
        notificationService.createNotification(request);
        return true;
    }

    private boolean autoCompleteTimeoutTask(Task task, ProcessInstance instance) {
        if (!isRunningInstance(instance)) {
            return false;
        }
        Task latestTask = taskService.createTaskQuery()
                .taskId(task.getId())
                .singleResult();
        if (latestTask == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> approvalData = new HashMap<>();
        approvalData.put("approvalResult", "agree");
        approvalData.put("approvalOpinion", "系统自动通过");
        approvalData.put("approved", true);
        approvalData.put("autoApproved", true);
        approvalData.put("autoApproveReason", "任务超过配置时长 " + timeoutThresholdHours + " 小时未处理");
        approvalData.put("autoApproveTime", now.toString());

        Map<String, Object> variables = new HashMap<>(approvalData);
        variables.put("timeoutAutoCompleted", true);
        variables.put("timeoutTaskId", latestTask.getId());
        variables.put("timeoutTaskName", latestTask.getName());

        try {
            Map<String, Object> existingVars = runtimeService.getVariables(latestTask.getProcessInstanceId());
            @SuppressWarnings("unchecked")
            Map<String, Object> allFormData = (Map<String, Object>) existingVars.getOrDefault(
                    "allFormData", new HashMap<>());
            allFormData.put(latestTask.getTaskDefinitionKey(), approvalData);
            variables.put("allFormData", allFormData);
        } catch (Exception ex) {
            Map<String, Object> allFormData = new HashMap<>();
            allFormData.put(latestTask.getTaskDefinitionKey(), approvalData);
            variables.put("allFormData", allFormData);
        }

        try {
            taskService.addComment(latestTask.getId(), latestTask.getProcessInstanceId(), "系统自动通过");
            taskService.complete(latestTask.getId(), variables);
            ruleEvaluatorService.evaluateAndAutoComplete(instance);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to auto-complete timeout task {}: {}", latestTask.getId(), safeMessage(ex), ex);
            return false;
        }
    }

    private void refreshProcessInstanceState(ProcessInstance instance, Task completedTask, LocalDateTime now) {
        Task nextTask = taskService.createTaskQuery()
                .processInstanceId(completedTask.getProcessInstanceId())
                .singleResult();
        if (nextTask != null) {
            instance.setCurrentNodeKey(nextTask.getTaskDefinitionKey());
            instance.setCurrentNodeName(nextTask.getName());
            instance.setCurrentBusinessType(resolveBusinessType(instance.getTemplateId(), nextTask.getTaskDefinitionKey()));
        } else {
            instance.setStatus("completed");
            instance.setEndedAt(now);
            instance.setCurrentNodeKey(null);
            instance.setCurrentNodeName(null);
            instance.setCurrentBusinessType(null);
        }
        instance.setUpdatedAt(now);
        processInstanceRepository.save(instance);
    }

    private String targetType(Task task) {
        return TARGET_TYPE_PREFIX + task.getId();
    }

    private String resolveBusinessType(Long templateId, String nodeKey) {
        ProcessTemplate template = processTemplateRepository
                .findByIdAndDeleted(templateId, 0)
                .orElse(null);
        if (template == null || !hasText(template.getNodeConfig())) {
            return null;
        }
        try {
            List<Map<String, Object>> nodes = objectMapper.readValue(
                    template.getNodeConfig(),
                    new TypeReference<List<Map<String, Object>>>() {}
            );
            for (Map<String, Object> node : nodes) {
                if (nodeKey.equals(node.get("nodeKey"))) {
                    Object businessType = node.get("businessType");
                    return businessType == null ? null : businessType.toString();
                }
            }
        } catch (Exception ignored) {
            // nodeConfig parse failure should not block timeout auto-flow.
        }
        return null;
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

    private boolean isRunningInstance(ProcessInstance instance) {
        return instance != null
                && "running".equals(instance.getStatus())
                && hasText(instance.getFlowableProcessInstanceId());
    }

    private String safeMessage(Exception ex) {
        return hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
