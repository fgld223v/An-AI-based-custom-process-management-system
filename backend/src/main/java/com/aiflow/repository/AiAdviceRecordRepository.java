package com.aiflow.repository;

import com.aiflow.model.AiAdviceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiAdviceRecordRepository extends JpaRepository<AiAdviceRecord, Long> {
}
