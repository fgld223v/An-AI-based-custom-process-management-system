package com.aiflow.service;

import com.aiflow.dto.TimelineDTO;
import com.aiflow.model.ProcessInstance;
import com.aiflow.repository.ProcessInstanceRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 流程时间线服务，根据流程实例的审批记录构造从发起到结束的完整时间线节点。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProcessTimelineService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ProcessInstanceRepository processInstanceRepository;
    private final EntityManager entityManager;

    public TimelineDTO buildTimeline(Long processInstanceId) {
        ProcessInstance instance = processInstanceRepository.findByIdAndDeleted(processInstanceId, 0)
                .orElseThrow(() -> new IllegalArgumentException("process instance not found"));
        List<TimelineDTO.TimelineNode> nodes = new ArrayList<>();
        nodes.add(TimelineDTO.TimelineNode.builder()
                .type("start")
                .nodeName("发起申请")
                .operatorName("申请人")
                .time(format(instance.getStartedAt() != null ? instance.getStartedAt() : instance.getCreatedAt()))
                .action("发起流程")
                .build());

        LocalDateTime previousTime = instance.getStartedAt() != null
                ? instance.getStartedAt() : instance.getCreatedAt();
        @SuppressWarnings("unchecked")
        List<Object[]> records = entityManager.createNativeQuery("""
                        SELECT ar.node_key, ar.action, ar.comment_text, ar.operated_at,
                               COALESCE(su.nickname,
                                        CASE WHEN ar.approver_id IS NULL THEN '系统'
                                             ELSE CONCAT('用户#', ar.approver_id) END) AS approver_name
                        FROM approval_record ar
                        LEFT JOIN sys_user su ON ar.approver_id = su.id
                        WHERE ar.instance_id = :instanceId
                        ORDER BY ar.operated_at ASC
                        """)
                .setParameter("instanceId", processInstanceId)
                .getResultList();

        for (Object[] record : records) {
            LocalDateTime operatedAt = ((Timestamp) record[3]).toLocalDateTime();
            nodes.add(TimelineDTO.TimelineNode.builder()
                    .type("approval")
                    .nodeName((String) record[0])
                    .operatorName((String) record[4])
                    .time(format(operatedAt))
                    .duration(duration(previousTime, operatedAt))
                    .action(actionLabel((String) record[1]))
                    .comment((String) record[2])
                    .build());
            previousTime = operatedAt;
        }

        if (instance.getEndedAt() != null) {
            nodes.add(TimelineDTO.TimelineNode.builder()
                    .type("end")
                    .nodeName("流程完成")
                    .operatorName("系统")
                    .time(format(instance.getEndedAt()))
                    .duration(duration(previousTime, instance.getEndedAt()))
                    .action("completed".equals(instance.getStatus()) ? "办结" : "终止")
                    .build());
        }
        return TimelineDTO.builder().nodes(nodes).build();
    }

    private String actionLabel(String action) {
        if (action == null) return "-";
        return switch (action) {
            case "approve" -> "通过";
            case "reject" -> "驳回";
            case "supplement" -> "补充材料";
            case "delegate" -> "转交";
            case "transfer" -> "移交";
            default -> action;
        };
    }

    private String format(LocalDateTime value) {
        return value == null ? "" : value.format(TIME_FORMATTER);
    }

    private String duration(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) return null;
        Duration duration = Duration.between(from, to);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        return hours > 0 ? hours + "h" + (minutes > 0 ? minutes + "m" : "") : minutes + "m";
    }
}
