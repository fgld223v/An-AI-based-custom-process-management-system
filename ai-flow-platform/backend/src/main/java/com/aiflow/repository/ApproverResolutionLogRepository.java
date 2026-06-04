package com.aiflow.repository;

import com.aiflow.model.ApproverResolutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApproverResolutionLogRepository extends JpaRepository<ApproverResolutionLog, Long> {
}
