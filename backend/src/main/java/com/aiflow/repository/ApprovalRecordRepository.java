package com.aiflow.repository;

import com.aiflow.model.ApprovalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, Long> {
}
