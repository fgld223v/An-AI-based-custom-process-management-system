package com.aiflow.repository;

import com.aiflow.model.OperationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志 Repository。
 * D9 审计日志 AOP 使用。
 */
@Repository
public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    List<OperationLog> findByOperatorIdOrderByCreatedAtDesc(Long operatorId);

    List<OperationLog> findByOperationTypeOrderByCreatedAtDesc(String operationType);

    List<OperationLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);
}
