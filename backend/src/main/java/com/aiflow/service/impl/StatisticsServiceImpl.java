package com.aiflow.service.impl;

import com.aiflow.dto.NodeEfficiencyDTO;
import com.aiflow.dto.StatisticsOverviewDTO;
import com.aiflow.dto.StatisticsTrendDTO;
import com.aiflow.service.StatisticsService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统计看板服务实现 — 使用原生 SQL 聚合查询，不依赖额外实体类
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsServiceImpl implements StatisticsService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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

        // 5. 各状态分布（draft / submitted / running / completed）
        @SuppressWarnings("unchecked")
        List<Object[]> statusRows = entityManager
                .createNativeQuery("""
                        SELECT pi.status, COUNT(*)
                        FROM process_instance pi
                        WHERE pi.deleted = 0
                        GROUP BY pi.status
                        """)
                .getResultList();
        Map<String, Long> statusDistribution = new LinkedHashMap<>();
        for (Object[] row : statusRows) {
            statusDistribution.put((String) row[0], ((Number) row[1]).longValue());
        }

        // 6. 各业务类型分布
        @SuppressWarnings("unchecked")
        List<Object[]> bizTypeRows = entityManager
                .createNativeQuery("""
                        SELECT COALESCE(pi.biz_type_id, 0),
                               COALESCE(btd.type_name, '未分类'),
                               COUNT(*)
                        FROM process_instance pi
                        LEFT JOIN biz_type_dict btd ON pi.biz_type_id = btd.id
                        WHERE pi.deleted = 0
                        GROUP BY pi.biz_type_id, btd.type_name
                        ORDER BY COUNT(*) DESC
                        """)
                .getResultList();
        List<StatisticsOverviewDTO.BizTypeCount> bizTypeDistribution = bizTypeRows.stream()
                .map(r -> StatisticsOverviewDTO.BizTypeCount.builder()
                        .bizTypeId(((Number) r[0]).longValue())
                        .bizTypeName((String) r[1])
                        .count(((Number) r[2]).longValue())
                        .build())
                .toList();

        return StatisticsOverviewDTO.builder()
                .totalInstances(totalInstances)
                .completionRate(completionRate)
                .avgDurationHours(avgDurationHours)
                .anomalyCount(anomalyCount != null ? anomalyCount.longValue() : 0L)
                .statusDistribution(statusDistribution)
                .bizTypeDistribution(bizTypeDistribution)
                .build();
    }

    @Override
    public StatisticsTrendDTO getTrend(LocalDate start, LocalDate end, String granularity, String mode) {
        boolean byWeek = "week".equalsIgnoreCase(granularity);
        List<String> labels = generateLabels(start, end, byWeek);

        // 概览模式：发起量 + 办结量
        if ("summary".equalsIgnoreCase(mode)) {
            return buildSummaryTrend(labels, start, end, byWeek);
        }

        // 默认模式：按业务类型分组
        return buildBizTypeTrend(labels, start, end, byWeek);
    }

    private StatisticsTrendDTO buildSummaryTrend(List<String> labels, LocalDate start, LocalDate end, boolean byWeek) {
        String sql = byWeek
                ? """
                    SELECT CONCAT(YEAR(MIN(pi.created_at)), '-W', LPAD(WEEK(MIN(pi.created_at), 1), 2, '0')) AS period,
                           COUNT(*) AS total_count,
                           COALESCE(SUM(CASE WHEN pi.status = 'completed' THEN 1 ELSE 0 END), 0) AS completed_count
                    FROM process_instance pi
                    WHERE pi.deleted = 0 AND pi.created_at >= :start AND pi.created_at < :end
                    GROUP BY YEAR(pi.created_at), WEEK(pi.created_at, 1)
                    ORDER BY MIN(pi.created_at)
                    """
                : """
                    SELECT MIN(DATE_FORMAT(pi.created_at, '%Y-%m-%d')) AS period,
                           COUNT(*) AS total_count,
                           COALESCE(SUM(CASE WHEN pi.status = 'completed' THEN 1 ELSE 0 END), 0) AS completed_count
                    FROM process_instance pi
                    WHERE pi.deleted = 0 AND pi.created_at >= :start AND pi.created_at < :end
                    GROUP BY DATE(pi.created_at)
                    ORDER BY MIN(pi.created_at)
                    """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("start", start.atStartOfDay())
                .setParameter("end", end.plusDays(1).atStartOfDay())
                .getResultList();

        Map<String, Long> totalMap = new LinkedHashMap<>();
        Map<String, Long> completedMap = new LinkedHashMap<>();
        for (Object[] r : rows) {
            String period = (String) r[0];
            totalMap.put(period, ((Number) r[1]).longValue());
            completedMap.put(period, ((Number) r[2]).longValue());
        }

        List<Long> totalValues = new ArrayList<>();
        List<Long> completedValues = new ArrayList<>();
        for (String label : labels) {
            totalValues.add(totalMap.getOrDefault(label, 0L));
            completedValues.add(completedMap.getOrDefault(label, 0L));
        }

        List<StatisticsTrendDTO.TrendSeries> series = List.of(
                StatisticsTrendDTO.TrendSeries.builder()
                        .bizTypeId(0L).bizTypeName("发起量").values(totalValues).build(),
                StatisticsTrendDTO.TrendSeries.builder()
                        .bizTypeId(-1L).bizTypeName("办结量").values(completedValues).build()
        );

        return StatisticsTrendDTO.builder().labels(labels).series(series).build();
    }

    private StatisticsTrendDTO buildBizTypeTrend(List<String> labels, LocalDate start, LocalDate end, boolean byWeek) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager
                .createNativeQuery(buildTrendQuery(byWeek))
                .setParameter("start", start.atStartOfDay())
                .setParameter("end", end.plusDays(1).atStartOfDay())
                .getResultList();

        List<TrendRow> trendRows = rows.stream().map(r -> new TrendRow(
                (String) r[0],
                ((Number) r[1]).longValue(),
                (String) r[2],
                ((Number) r[3]).longValue()
        )).collect(Collectors.toList());

        Map<Long, String> bizTypeMap = new LinkedHashMap<>();
        Map<Long, Map<String, Long>> bizPeriodCount = new LinkedHashMap<>();
        for (TrendRow row : trendRows) {
            bizTypeMap.putIfAbsent(row.bizTypeId, row.typeName);
            bizPeriodCount.computeIfAbsent(row.bizTypeId, k -> new LinkedHashMap<>()).put(row.period, row.cnt);
        }

        List<StatisticsTrendDTO.TrendSeries> series = new ArrayList<>();
        for (Map.Entry<Long, String> entry : bizTypeMap.entrySet()) {
            Map<String, Long> periodMap = bizPeriodCount.getOrDefault(entry.getKey(), Map.of());
            List<Long> values = new ArrayList<>();
            for (String label : labels) {
                values.add(periodMap.getOrDefault(label, 0L));
            }
            series.add(StatisticsTrendDTO.TrendSeries.builder()
                    .bizTypeId(entry.getKey()).bizTypeName(entry.getValue()).values(values).build());
        }

        return StatisticsTrendDTO.builder().labels(labels).series(series).build();
    }

    // --- helper ---

    private List<String> generateLabels(LocalDate start, LocalDate end, boolean byWeek) {
        List<String> labels = new ArrayList<>();
        if (byWeek) {
            WeekFields wf = WeekFields.ISO;
            LocalDate cursor = start;
            while (!cursor.isAfter(end)) {
                int week = cursor.get(wf.weekOfYear());
                int year = cursor.get(wf.weekBasedYear());
                // 处理跨年周：如果年的第一天周编号属于上一年，年份用 weekBasedYear
                String label = year + "-W" + String.format("%02d", week);
                if (labels.isEmpty() || !labels.get(labels.size() - 1).equals(label)) {
                    labels.add(label);
                }
                cursor = cursor.plusDays(1);
            }
        } else {
            LocalDate cursor = start;
            while (!cursor.isAfter(end)) {
                labels.add(cursor.format(DAY_FMT));
                cursor = cursor.plusDays(1);
            }
        }
        return labels;
    }

    private String buildTrendQuery(boolean byWeek) {
        if (byWeek) {
            return """
                    SELECT CONCAT(YEAR(MIN(pi.created_at)), '-W', LPAD(WEEK(MIN(pi.created_at), 1), 2, '0')) AS period,
                           COALESCE(pi.biz_type_id, 0) AS biz_type_id,
                           COALESCE(btd.type_name, '未分类') AS type_name,
                           COUNT(*) AS cnt
                    FROM process_instance pi
                    LEFT JOIN biz_type_dict btd ON pi.biz_type_id = btd.id
                    WHERE pi.deleted = 0
                      AND pi.created_at >= :start
                      AND pi.created_at < :end
                    GROUP BY YEAR(pi.created_at), WEEK(pi.created_at, 1), pi.biz_type_id, btd.type_name
                    ORDER BY period, biz_type_id
                    """;
        }
        return """
                SELECT MIN(DATE_FORMAT(pi.created_at, '%Y-%m-%d')) AS period,
                       COALESCE(pi.biz_type_id, 0) AS biz_type_id,
                       COALESCE(btd.type_name, '未分类') AS type_name,
                       COUNT(*) AS cnt
                FROM process_instance pi
                LEFT JOIN biz_type_dict btd ON pi.biz_type_id = btd.id
                WHERE pi.deleted = 0
                  AND pi.created_at >= :start
                  AND pi.created_at < :end
                GROUP BY DATE(pi.created_at), pi.biz_type_id, btd.type_name
                ORDER BY period, biz_type_id
                """;
    }

    private record TrendRow(String period, Long bizTypeId, String typeName, Long cnt) {}

    @Override
    public NodeEfficiencyDTO getNodeEfficiency() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager
                .createNativeQuery("""
                        SELECT t.node_key,
                               t.node_name,
                               COUNT(*) AS total_count,
                               COALESCE(SUM(CASE WHEN t.status = 'timeout' THEN 1 ELSE 0 END), 0) AS timeout_count,
                               COALESCE(AVG(CASE WHEN t.completed_at IS NOT NULL
                                   THEN TIMESTAMPDIFF(SECOND, t.created_at, t.completed_at) END), 0) AS avg_dwell_seconds
                        FROM task t
                        WHERE t.deleted = 0
                        GROUP BY t.node_key, t.node_name
                        ORDER BY avg_dwell_seconds DESC
                        """)
                .getResultList();

        List<NodeEfficiencyDTO.NodeRanking> rankings = rows.stream()
                .map(r -> {
                    long total = ((Number) r[2]).longValue();
                    long timeout = ((Number) r[3]).longValue();
                    double avgDwellSeconds = ((Number) r[4]).doubleValue();
                    double timeoutRate = total > 0
                            ? Math.round(timeout * 10000.0 / total) / 100.0
                            : 0.0;
                    double avgDwellHours = avgDwellSeconds > 0
                            ? Math.round(avgDwellSeconds / 36.0) / 100.0
                            : 0.0;

                    return NodeEfficiencyDTO.NodeRanking.builder()
                            .nodeKey((String) r[0])
                            .nodeName((String) r[1])
                            .totalCount(total)
                            .timeoutCount(timeout)
                            .avgDwellHours(avgDwellHours)
                            .timeoutRate(timeoutRate)
                            .build();
                })
                .toList();

        return NodeEfficiencyDTO.builder().rankings(rankings).build();
    }
}