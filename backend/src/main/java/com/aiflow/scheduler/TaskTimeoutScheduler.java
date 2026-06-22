package com.aiflow.scheduler;

import com.aiflow.model.Notification;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.NotificationRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.service.impl.NodeConfigParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务超时调度器。
 * 每 5 分钟扫描一次 ACT_RU_TASK，检查节点配置的 remindAfter / autoAction，
 * 对超时任务发送催办通知或自动完成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskTimeoutScheduler {

    private final TaskService taskService;
    private final ProcessTemplateRepository processTemplateRepository;
    private final NotificationRepository notificationRepository;
    private final NodeConfigParser nodeConfigParser;
    private final ObjectMapper objectMapper;

    /** 默认超时阈值（小时），节点未配置 remindAfter 时使用 */
    private static final int DEFAULT_TIMEOUT_HOURS = 48;

    @Scheduled(fixedRate = 300_000) // 每 5 分钟
    public void scanTimeoutTasks() {
        List<Task> activeTasks = taskService.createTaskQuery().active().list();
        if (activeTasks.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        int remindedCount = 0;
        int autoCompletedCount = 0;

        for (Task task : activeTasks) {
            if (task.getCreateTime() == null) continue;
            long hoursSinceCreate = java.time.Duration.between(
                    task.getCreateTime().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime(),
                    now).toHours();

            // 尝试从流程变量获取 templateId
            Object templateIdObj = taskService.getVariable(task.getId(), "templateId");
            if (templateIdObj == null) {
                templateIdObj = taskService.getVariable(task.getProcessInstanceId(), "templateId");
            }
            if (templateIdObj == null) continue;

            Long templateId = templateIdObj instanceof Number ? ((Number) templateIdObj).longValue() : null;
            if (templateId == null) continue;

            ProcessTemplate template = processTemplateRepository.findByIdAndDeleted(templateId, 0).orElse(null);
            if (template == null || template.getNodeConfig() == null) continue;

            // 解析节点配置获取超时设置
            Map<String, Object> nodeConfig = parseNodeConfig(template.getNodeConfig(), task.getTaskDefinitionKey());
            if (nodeConfig == null) continue;

            int remindAfterHours = getIntField(nodeConfig, "remindAfterHours", DEFAULT_TIMEOUT_HOURS);
            String autoAction = getStringField(nodeConfig, "autoAction");

            if (hoursSinceCreate >= remindAfterHours) {
                // 检查是否已提醒过（避免重复）
                Object remindedObj = taskService.getVariable(task.getId(), "_timeout_reminded");
                if (remindedObj == null) {
                    // 发送催办通知
                    sendReminder(task, hoursSinceCreate);
                    taskService.setVariable(task.getId(), "_timeout_reminded", true);
                    remindedCount++;
                }

                // 自动完成
                if ("auto_approve".equals(autoAction) || "auto_reject".equals(autoAction)) {
                    Object autoDone = taskService.getVariable(task.getId(), "_timeout_auto_done");
                    if (autoDone == null) {
                        try {
                            Map<String, Object> vars = new java.util.HashMap<>();
                            vars.put("approvalResult", "auto_approve".equals(autoAction) ? "agree" : "reject");
                            vars.put("approvalComment", "系统自动处理：任务已超时 " + hoursSinceCreate + " 小时");
                            taskService.complete(task.getId(), vars);
                            taskService.setVariable(task.getId(), "_timeout_auto_done", true);
                            autoCompletedCount++;
                            log.info("超时自动完成：task={}, action={}, 超时={}h", task.getName(), autoAction, hoursSinceCreate);
                        } catch (Exception e) {
                            log.warn("超时自动完成失败：task={}, error={}", task.getId(), e.getMessage());
                        }
                    }
                }
            }
        }

        if (remindedCount > 0 || autoCompletedCount > 0) {
            log.info("超时扫描完成：提醒 {} 个，自动完成 {} 个", remindedCount, autoCompletedCount);
        }
    }

    private void sendReminder(Task task, long hours) {
        try {
            Object assigneeObj = task.getAssignee();
            if (assigneeObj == null) return;
            Long receiverId = Long.parseLong(assigneeObj.toString());
            Notification notif = Notification.builder()
                    .receiverId(receiverId)
                    .type("timeout_warning")
                    .title("任务超时提醒")
                    .content("任务「" + task.getName() + "」已超过 " + hours + " 小时未处理，请及时跟进。")
                    .targetType("task")
                    .isRead(false)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .deleted(0)
                    .build();
            notificationRepository.save(notif);
        } catch (Exception e) {
            log.warn("发送超时提醒失败: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseNodeConfig(String nodeConfigJson, String nodeKey) {
        try {
            Map<String, Map<String, Object>> map = objectMapper.readValue(nodeConfigJson,
                    new TypeReference<Map<String, Map<String, Object>>>() {});
            return map.get(nodeKey);
        } catch (Exception e) {
            try {
                List<Map<String, Object>> list = objectMapper.readValue(nodeConfigJson,
                        new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> item : list) {
                    if (nodeKey.equals(item.get("nodeKey")) || nodeKey.equals(item.get("nodeId"))) {
                        return item;
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private int getIntField(Map<String, Object> config, String key, int defaultVal) {
        Object timeoutObj = config.get("timeoutConfig");
        if (timeoutObj instanceof Map) {
            Object val = ((Map<String, Object>) timeoutObj).get(key.replace("Hours", ""));
            if (val == null) val = ((Map<String, Object>) timeoutObj).get(key);
            if (val instanceof Number) return ((Number) val).intValue();
        }
        Object direct = config.get(key);
        if (direct instanceof Number) return ((Number) direct).intValue();
        return defaultVal;
    }

    private String getStringField(Map<String, Object> config, String key) {
        Object timeoutObj = config.get("timeoutConfig");
        if (timeoutObj instanceof Map) {
            Object val = ((Map<String, Object>) timeoutObj).get(key);
            if (val != null) return val.toString();
        }
        Object direct = config.get(key);
        return direct != null ? direct.toString() : null;
    }
}
