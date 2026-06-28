package com.aiflow.repository;

import com.aiflow.model.AiAdviceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AI审批建议记录Repository：按实例和节点查询审批建议记录。
 */
@Repository
public interface AiAdviceRecordRepository extends JpaRepository<AiAdviceRecord, Long> {

    List<AiAdviceRecord> findByInstanceIdOrderByCreatedAtDesc(Long instanceId);

    List<AiAdviceRecord> findByInstanceIdAndNodeKeyOrderByCreatedAtDesc(Long instanceId, String nodeKey);
}
