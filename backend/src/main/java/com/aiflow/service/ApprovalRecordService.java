package com.aiflow.service;

import com.aiflow.model.ApprovalRecord;
import com.aiflow.repository.ApprovalRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.aiflow.service.ApprovalVariableService.ACTION_APPROVE;
import static com.aiflow.service.ApprovalVariableService.ACTION_REJECT;

/**
 * 审批记录服务，负责记录每一次审批操作（通过/驳回），
 * 并在记录后触发审批结果通知。
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ApprovalRecordService {

    private final ApprovalRecordRepository approvalRecordRepository;
    private final WorkflowNotificationService workflowNotificationService;

    /**
     * 记录一条审批操作。同一 taskId+action 只会保留一条记录（幂等）。
     *
     * @param instanceId 流程实例ID
     * @param taskId     当前任务ID
     * @param nodeKey    节点标识
     * @param approverId 审批人ID
     * @param action     操作类型（approve / reject）
     * @param comment    审批意见
     * @param operatedAt 操作时间，为null时使用当前时间
     * @return 已保存的审批记录
     */
    public ApprovalRecord record(Long instanceId,
                                 String taskId,
                                 String nodeKey,
                                 Long approverId,
                                 String action,
                                 String comment,
                                 LocalDateTime operatedAt) {
        if (instanceId == null) throw new IllegalArgumentException("instanceId must not be null");
        if (!hasText(taskId)) throw new IllegalArgumentException("taskId must not be blank");
        if (!hasText(nodeKey)) throw new IllegalArgumentException("nodeKey must not be blank");
        if (!ACTION_APPROVE.equals(action) && !ACTION_REJECT.equals(action)) {
            throw new IllegalArgumentException("unsupported approval action: " + action);
        }
        ApprovalRecord existing = approvalRecordRepository.findByTaskIdAndAction(taskId, action).orElse(null);
        if (existing != null) return existing;

        LocalDateTime now = operatedAt == null ? LocalDateTime.now() : operatedAt;
        ApprovalRecord record = ApprovalRecord.builder()
                .instanceId(instanceId)
                .taskId(taskId.trim())
                .nodeKey(nodeKey.trim())
                .approverId(approverId)
                .action(action)
                .commentText(normalize(comment))
                .operatedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        ApprovalRecord saved = approvalRecordRepository.save(record);
        workflowNotificationService.notifyApprovalResult(saved);
        return saved;
    }

    private String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
