package com.aiflow.service;

import com.aiflow.service.support.OptionalTableQuerySupport;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实例异常检测服务，查询指定流程实例集合是否存在异常，
 * 异常原因包括：任务超时、高风险超时预测、存在驳回记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstanceAnomalyService {

    private final EntityManager entityManager;
    private static final String ANOMALY_REASON_SEPARATOR = "\u3001";
    private static final String REASON_TIMEOUT = "\u4efb\u52a1\u8d85\u65f6";
    private static final String REASON_HIGH_RISK_TIMEOUT = "\u9ad8\u98ce\u9669\u8d85\u65f6";
    private static final String REASON_REJECTED = "\u5b58\u5728\u9a73\u56de";

    /**
     * 查询指定流程实例集合的异常信息。
     *
     * @param instanceIds 流程实例ID集合
     * @return instanceId -> 异常原因（多个原因用顿号分隔）
     */
    public Map<Long, String> findAnomalies(Collection<Long> instanceIds) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = queryAnomalyRows(instanceIds);
        Map<Long, String> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put(((Number) row[0]).longValue(), String.valueOf(row[1]));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> queryAnomalyRows(Collection<Long> instanceIds) {
        try {
            return (List<Object[]>) entityManager.createNativeQuery("""
                            SELECT anomaly.instance_id,
                                   GROUP_CONCAT(DISTINCT anomaly.reason SEPARATOR '%s') AS reasons
                            FROM (
                                SELECT t.instance_id, '%s' AS reason
                                FROM task t
                                WHERE t.deleted = 0 AND t.status = 'timeout'
                                UNION ALL
                                SELECT bp.instance_id, '%s' AS reason
                                FROM bottleneck_prediction bp
                                WHERE bp.prediction_level = 'high_prob_timeout'
                                UNION ALL
                                SELECT ar.instance_id, '%s' AS reason
                                FROM approval_record ar
                                WHERE ar.action = 'reject'
                            ) anomaly
                            WHERE anomaly.instance_id IN (:instanceIds)
                            GROUP BY anomaly.instance_id
                            """.formatted(
                            ANOMALY_REASON_SEPARATOR,
                            REASON_TIMEOUT,
                            REASON_HIGH_RISK_TIMEOUT,
                            REASON_REJECTED))
                    .setParameter("instanceIds", instanceIds)
                    .getResultList();
        } catch (RuntimeException ex) {
            if (!OptionalTableQuerySupport.isMissingOptionalTable(ex, "bottleneck_prediction")) {
                throw ex;
            }
            log.warn("bottleneck_prediction table is unavailable; anomaly reasons will skip prediction data");
            return (List<Object[]>) entityManager.createNativeQuery("""
                            SELECT anomaly.instance_id,
                                   GROUP_CONCAT(DISTINCT anomaly.reason SEPARATOR '%s') AS reasons
                            FROM (
                                SELECT t.instance_id, '%s' AS reason
                                FROM task t
                                WHERE t.deleted = 0 AND t.status = 'timeout'
                                UNION ALL
                                SELECT ar.instance_id, '%s' AS reason
                                FROM approval_record ar
                                WHERE ar.action = 'reject'
                            ) anomaly
                            WHERE anomaly.instance_id IN (:instanceIds)
                            GROUP BY anomaly.instance_id
                            """.formatted(
                            ANOMALY_REASON_SEPARATOR,
                            REASON_TIMEOUT,
                            REASON_REJECTED))
                    .setParameter("instanceIds", instanceIds)
                    .getResultList();
        }
    }
}
