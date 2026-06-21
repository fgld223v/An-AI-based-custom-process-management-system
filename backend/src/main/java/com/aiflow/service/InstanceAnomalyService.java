package com.aiflow.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstanceAnomalyService {

    private final EntityManager entityManager;

    public Map<Long, String> findAnomalies(Collection<Long> instanceIds) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            return Map.of();
        }
        @SuppressWarnings("unchecked")
        var rows = (java.util.List<Object[]>) entityManager.createNativeQuery("""
                        SELECT anomaly.instance_id,
                               GROUP_CONCAT(DISTINCT anomaly.reason SEPARATOR '、') AS reasons
                        FROM (
                            SELECT t.instance_id, '任务超时' AS reason
                            FROM task t
                            WHERE t.deleted = 0 AND t.status = 'timeout'
                            UNION ALL
                            SELECT bp.instance_id, '高风险超时' AS reason
                            FROM bottleneck_prediction bp
                            WHERE bp.prediction_level = 'high_prob_timeout'
                            UNION ALL
                            SELECT ar.instance_id, '存在驳回' AS reason
                            FROM approval_record ar
                            WHERE ar.action = 'reject'
                        ) anomaly
                        WHERE anomaly.instance_id IN (:instanceIds)
                        GROUP BY anomaly.instance_id
                        """)
                .setParameter("instanceIds", instanceIds)
                .getResultList();
        Map<Long, String> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put(((Number) row[0]).longValue(), String.valueOf(row[1]));
        }
        return result;
    }
}
