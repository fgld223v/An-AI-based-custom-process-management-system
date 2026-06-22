package com.aiflow.repository;

import com.aiflow.model.ApprovalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, Long> {

    Optional<ApprovalRecord> findByTaskIdAndAction(String taskId, String action);

    List<ApprovalRecord> findByApproverIdAndTaskIdIsNotNullOrderByOperatedAtDesc(Long approverId);

    boolean existsByTaskIdAndApproverId(String taskId, Long approverId);
}
