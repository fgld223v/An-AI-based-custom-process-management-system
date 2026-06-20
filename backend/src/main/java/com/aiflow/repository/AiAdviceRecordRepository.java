package com.aiflow.repository;

import com.aiflow.model.AiAdviceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiAdviceRecordRepository extends JpaRepository<AiAdviceRecord, Long> {

    List<AiAdviceRecord> findByInstanceIdOrderByCreatedAtDesc(Long instanceId);

    List<AiAdviceRecord> findByInstanceIdAndNodeKeyOrderByCreatedAtDesc(Long instanceId, String nodeKey);
}
