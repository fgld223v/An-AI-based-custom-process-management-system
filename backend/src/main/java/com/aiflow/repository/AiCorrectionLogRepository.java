package com.aiflow.repository;

import com.aiflow.model.AiCorrectionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiCorrectionLogRepository extends JpaRepository<AiCorrectionLog, Long> {
}
