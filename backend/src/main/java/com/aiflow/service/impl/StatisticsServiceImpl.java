package com.aiflow.service.impl;

import com.aiflow.dto.StatisticsOverviewDTO;
import com.aiflow.service.StatisticsService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 统计看板服务实现 — 使用原生 SQL 聚合查询，不依赖额外实体类
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsServiceImpl implements StatisticsService {

    private final EntityManager entityManager;

    @Override
    public StatisticsOverviewDTO getOverview() {

        // 1. 实例总数
        Long totalInstances = ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM process_instance WHERE deleted = 0")
                .getSingleResult()).longValue();

        // 2. 办结率 = completed / total * 100
        //     使用 COALESCE 防止空表时 SUM 返回 NULL
        Object[] rateRow = (Object[]) entityManager
                .createNativeQuery("""
                        SELECT COUNT(*),
                               COALESCE(SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END), 0)
                        FROM process_instance
                        WHERE deleted = 0
                        """)
                .getSingleResult();
        long total = ((Number) rateRow[0]).longValue();
        long completed = ((Number) rateRow[1]).longValue();
        double completionRate = total > 0
                ? Math.round(completed * 10000.0 / total) / 100.0
                : 0.0;

        // 3. 平均审批耗时（小时）— 仅统计已完成且有起止时间的实例
        Number avgSeconds = (Number) entityManager
                .createNativeQuery("""
                        SELECT AVG(TIMESTAMPDIFF(SECOND, started_at, ended_at))
                        FROM process_instance
                        WHERE deleted = 0
                          AND status = 'completed'
                          AND started_at IS NOT NULL
                          AND ended_at IS NOT NULL
                        """)
                .getSingleResult();
        double avgDurationHours = avgSeconds != null
                ? Math.round(avgSeconds.doubleValue() / 36.0) / 100.0   // /3600 * 100 / 100 → 保留2位小数
                : 0.0;

        // 4. 异常数 — 超时任务 + 高度瓶颈预警 + 驳回审批，按 instance_id 去重
        Number anomalyCount = (Number) entityManager
                .createNativeQuery("""
                        SELECT COUNT(DISTINCT si.instance_id) FROM (
                            SELECT t.instance_id FROM task t
                             WHERE t.deleted = 0 AND t.status = 'timeout'
                            UNION
                            SELECT bp.instance_id FROM bottleneck_prediction bp
                             WHERE bp.prediction_level = 'high_prob_timeout'
                            UNION
                            SELECT ar.instance_id FROM approval_record ar
                             WHERE ar.action = 'reject'
                        ) si
                        """)
                .getSingleResult();

        return StatisticsOverviewDTO.builder()
                .totalInstances(totalInstances)
                .completionRate(completionRate)
                .avgDurationHours(avgDurationHours)
                .anomalyCount(anomalyCount != null ? anomalyCount.longValue() : 0L)
                .build();
    }
}