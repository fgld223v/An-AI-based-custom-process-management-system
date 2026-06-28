package com.aiflow.repository;

import com.aiflow.model.ApprovalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 审批记录Repository：按任务ID和审批人查询审批记录。
 */
public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, Long> {

    Optional<ApprovalRecord> findByTaskIdAndAction(String taskId, String action);

    List<ApprovalRecord> findByApproverIdAndTaskIdIsNotNullOrderByOperatedAtDesc(Long approverId);

    boolean existsByTaskIdAndApproverId(String taskId, Long approverId);
}
