package com.aiflow.service.impl;

import com.aiflow.dto.NotificationCreateRequest;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.service.ApproverResolverService;
import com.aiflow.service.NotificationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 抄送（CC）通知委托。
 *
 * <p>在 BPMN SendTask 节点到达时自动执行：</p>
 * <ol>
 *   <li>从流程变量获取业务实例信息</li>
 *   <li>从模板 nodeConfig 解析抄送目标（通知对象、渠道、模板）</li>
 *   <li>为每个目标用户创建站内通知</li>
 *   <li>立即完成（抄送不阻塞流程）</li>
 * </ol>
 *
 * <p>Spring Bean 名称：{@code ccNotificationDelegate}，
 * BPMN XML 中通过 {@code delegateExpression="${ccNotificationDelegate}"} 引用。</p>
 */
@Slf4j
@Component("ccNotificationDelegate")
public class CcNotificationDelegate implements JavaDelegate {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ProcessInstanceRepository processInstanceRepository;

    @Autowired
    private ProcessTemplateRepository processTemplateRepository;

    @Autowired
    private ApproverResolverService approverResolverService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void execute(DelegateExecution execution) {
        String nodeKey = execution.getCurrentActivityId();
        if (nodeKey == null) {
            log.warn("CcNotificationDelegate: 无法获取当前活动 ID");
            return;
        }

        Object businessInstanceIdObj = execution.getVariable("businessInstanceId");
        Object templateIdObj = execution.getVariable("templateId");

        if (businessInstanceIdObj == null || templateIdObj == null) {
            log.warn("CcNotificationDelegate: 缺少 businessInstanceId 或 templateId，跳过抄送");
            return;
        }

        Long instanceId = toLong(businessInstanceIdObj);
        Long templateId = toLong(templateIdObj);

        // 获取业务实例信息
        ProcessInstance instance = processInstanceRepository
                .findByIdAndDeleted(instanceId, 0)
                .orElse(null);
        if (instance == null) {
            log.warn("CcNotificationDelegate: 流程实例 {} 不存在", instanceId);
            return;
        }

        // 解析抄送配置
        CcConfig ccConfig = resolveCcConfig(templateId, nodeKey);

        // 解析抄送目标用户
        List<Long> targetUserIds = resolveTargetUsers(ccConfig, instanceId, nodeKey);

        // 创建通知
        String instanceTitle = instance.getTitle() != null ? instance.getTitle() : "未知流程";
        String instanceCode = instance.getInstanceCode() != null ? instance.getInstanceCode() : "-";
        String notifyTitle = hasText(ccConfig.notifyTemplate)
                ? ccConfig.notifyTemplate
                : "流程抄送通知";
        String notifyContent = buildNotifyContent(instanceTitle, instanceCode, execution);

        for (Long userId : targetUserIds) {
            try {
                NotificationCreateRequest request = new NotificationCreateRequest();
                request.setReceiverId(userId);
                request.setType(ccConfig.isSystemNotice ? "system_notice" : "task_remind");
                request.setTitle(notifyTitle);
                request.setContent(notifyContent);
                request.setTargetType("process_instance");
                request.setTargetId(instanceId);
                request.setTargetUrl("/process/instances/" + instanceId);
                notificationService.createNotification(request);
                log.info("CcNotificationDelegate: 已发送抄送通知，receiverId={}，instanceId={}", userId, instanceId);
            } catch (Exception e) {
                log.error("CcNotificationDelegate: 抄送通知发送失败，receiverId={}", userId, e);
                // 不抛异常 — 抄送失败不应阻塞流程
            }
        }

        log.info("CcNotificationDelegate: 抄送节点 {} 处理完成，发送 {} 条通知",
                nodeKey, targetUserIds.size());
    }

    // ================================================================
    // 抄送配置解析
    // ================================================================

    private CcConfig resolveCcConfig(Long templateId, String nodeKey) {
        ProcessTemplate template = processTemplateRepository
                .findByIdAndDeleted(templateId, 0)
                .orElse(null);
        if (template == null || !hasText(template.getNodeConfig())) {
            return new CcConfig();
        }

        Map<String, Object> nodeConfig = findNodeConfig(template.getNodeConfig(), nodeKey);
        if (nodeConfig == null) {
            return new CcConfig();
        }

        CcConfig config = new CcConfig();
        config.notifyTarget = stringValue(nodeConfig.get("notifyTarget"));
        config.notifyChannel = stringValue(nodeConfig.get("notifyChannel"));
        config.notifyTemplate = stringValue(nodeConfig.get("notifyTemplate"));
        config.notifyTiming = stringValue(nodeConfig.get("notifyTiming"));
        config.isSystemNotice = "system_notice".equalsIgnoreCase(
                stringValue(nodeConfig.get("notifyType")));

        // 如果有 assigneeType/assigneeValue，也用于抄送目标
        if (!hasText(config.notifyTarget)) {
            String assigneeType = stringValue(nodeConfig.get("assigneeType"));
            String assigneeValue = stringValue(nodeConfig.get("assigneeValue"));
            if (hasText(assigneeType)) {
                config.notifyTarget = assigneeType;
                config.assigneeValue = assigneeValue;
            }
        }

        return config;
    }

    /**
     * 解析抄送目标用户列表。
     */
    private List<Long> resolveTargetUsers(CcConfig config, Long instanceId, String nodeKey) {
        if (!hasText(config.notifyTarget)) {
            return resolveDefaultCcTargets(instanceId);
        }

        return switch (config.notifyTarget.toUpperCase()) {
            case "APPLICANT" -> resolveApplicant(instanceId);
            case "APPROVER" -> resolveApproverUsers(instanceId, nodeKey);
            case "USER" -> resolveSpecificUsers(config.assigneeValue);
            case "ROLE" -> approverResolverService.resolveApprovers(
                    instanceId, nodeKey, "ROLE", config.assigneeValue);
            default -> resolveDefaultCcTargets(instanceId);
        };
    }

    private List<Long> resolveApplicant(Long instanceId) {
        ProcessInstance instance = processInstanceRepository
                .findByIdAndDeleted(instanceId, 0)
                .orElse(null);
        if (instance != null && instance.getApplicantId() != null) {
            return List.of(instance.getApplicantId());
        }
        return List.of();
    }

    private List<Long> resolveApproverUsers(Long instanceId, String nodeKey) {
        // 查找模板中上一个审批节点的处理人
        return approverResolverService.resolveApprovers(
                instanceId, nodeKey, "DEPARTMENT_MANAGER", null);
    }

    private List<Long> resolveSpecificUsers(String assignValue) {
        if (!hasText(assignValue)) return List.of();
        try {
            List<Object> raw = objectMapper.readValue(assignValue,
                    new TypeReference<List<Object>>() {});
            return raw.stream()
                    .map(obj -> obj instanceof Number ? ((Number) obj).longValue() : null)
                    .filter(id -> id != null)
                    .toList();
        } catch (Exception e) {
            // 尝试作为逗号分隔的单个 ID
            try {
                return List.of(Long.parseLong(assignValue.trim()));
            } catch (NumberFormatException ignored) {
                return List.of();
            }
        }
    }

    /**
     * 兜底：通知流程发起人。
     */
    private List<Long> resolveDefaultCcTargets(Long instanceId) {
        return resolveApplicant(instanceId);
    }

    private String buildNotifyContent(String instanceTitle, String instanceCode,
                                      DelegateExecution execution) {
        String timestamp = LocalDateTime.now().format(DT_FMT);
        return String.format(
                "流程【%s】（编号：%s）已到达抄送节点。\n处理时间：%s",
                instanceTitle, instanceCode, timestamp);
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    private Map<String, Object> findNodeConfig(String nodeConfigJson, String nodeKey) {
        try {
            Map<String, Map<String, Object>> map = objectMapper.readValue(
                    nodeConfigJson, new TypeReference<Map<String, Map<String, Object>>>() {});
            Map<String, Object> config = map.get(nodeKey);
            if (config != null) return config;
            for (Map.Entry<String, Map<String, Object>> entry : map.entrySet()) {
                Object nk = entry.getValue().get("nodeKey");
                Object nid = entry.getValue().get("nodeId");
                if (nodeKey.equals(stringValue(nk)) || nodeKey.equals(stringValue(nid))) {
                    return entry.getValue();
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    private Long toLong(Object obj) {
        if (obj instanceof Number num) return num.longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString().trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    // ================================================================
    // 内部类
    // ================================================================

    private static class CcConfig {
        String notifyTarget;    // APPLICANT / APPROVER / USER / ROLE
        String notifyChannel;   // IN_APP / EMAIL / SMS / WE_COM
        String notifyTemplate;  // 通知模板/标题
        String notifyTiming;    // ON_ENTER / ON_COMPLETE / ON_REJECT
        String assigneeValue;   // 指定用户 ID 或角色编码
        boolean isSystemNotice; // 是否系统通知类型
    }
}
