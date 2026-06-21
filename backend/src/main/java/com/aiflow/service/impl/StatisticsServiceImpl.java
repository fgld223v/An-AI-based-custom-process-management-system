package com.aiflow.service.impl;

import com.aiflow.dto.NodeEfficiencyDTO;
import com.aiflow.dto.StatisticsOverviewDTO;
import com.aiflow.dto.StatisticsTrendDTO;
import com.aiflow.security.SecurityUtils;
import com.aiflow.service.StatisticsService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

        // 7. 今日新增实例数
        Number todayNew = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM process_instance WHERE deleted=0 AND DATE(created_at)=CURDATE()")
                .getSingleResult();

        // 8. 待处理任务数（仅统计当前用户的任务）
        Number pendingTasks = (Number) entityManager
                .createNativeQuery("""
                        SELECT COUNT(*) FROM task t
                        JOIN sys_user su ON t.assignee_id = su.id
                        WHERE t.deleted=0 AND t.status IN ('pending','processing')
                          AND su.id = :userId
                        """)
                .setParameter("userId", getCurrentUserId())
                .getSingleResult();

        return StatisticsOverviewDTO.builder()
                .totalInstances(totalInstances)
                .completionRate(completionRate)
                .avgDurationHours(avgDurationHours)
                .anomalyCount(anomalyCount != null ? anomalyCount.longValue() : 0L)
                .statusDistribution(statusDistribution)
                .bizTypeDistribution(bizTypeDistribution)
                .todayNewInstances(todayNew != null ? todayNew.longValue() : 0L)
                .pendingTaskCount(pendingTasks != null ? pendingTasks.longValue() : 0L)
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

        // 3. 按 bizTypeId 分组，填充与 labels 等长的 values 数组
        Map<Long, String> bizTypeMap = new LinkedHashMap<>(); // id -> name
        Map<Long, Map<String, Long>> bizPeriodCount = new LinkedHashMap<>(); // bizTypeId -> (period -> count)

        for (TrendRow row : trendRows) {
            bizTypeMap.putIfAbsent(row.bizTypeId, row.typeName);
            bizPeriodCount
                    .computeIfAbsent(row.bizTypeId, k -> new LinkedHashMap<>())
                    .put(row.period, row.cnt);
        }

        List<StatisticsTrendDTO.TrendSeries> series = new ArrayList<>();
        for (Map.Entry<Long, String> entry : bizTypeMap.entrySet()) {
            Long bizTypeId = entry.getKey();
            String bizTypeName = entry.getValue();
            Map<String, Long> periodMap = bizPeriodCount.getOrDefault(bizTypeId, Map.of());

            List<Long> values = new ArrayList<>();
            for (String label : labels) {
                values.add(periodMap.getOrDefault(label, 0L));
            }

            series.add(StatisticsTrendDTO.TrendSeries.builder()
                    .bizTypeId(bizTypeId)
                    .bizTypeName(bizTypeName)
                    .values(values)
                    .build());
        }

        return StatisticsTrendDTO.builder()
                .labels(labels)
                .series(series)
                .build();
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

    private Long getCurrentUserId() { Long uid = SecurityUtils.currentUserId(); return uid != null ? uid : 1L; }

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
    @Override
    public byte[] exportExcel(java.time.LocalDate start, java.time.LocalDate end) {
        try (org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            com.aiflow.dto.StatisticsOverviewDTO overview = getOverview();
            com.aiflow.dto.NodeEfficiencyDTO nodeEff = getNodeEfficiency();
            org.apache.poi.ss.usermodel.CellStyle hs = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font f = wb.createFont(); f.setBold(true); hs.setFont(f);
            hs.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
            hs.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Sheet sh1 = wb.createSheet("统计概览");
            int r = 0;
            addRow(sh1, r++, hs, "指标", "数值");
            addRow(sh1, r++, null, "实例总数", overview.getTotalInstances());
            addRow(sh1, r++, null, "办结率(%%)", overview.getCompletionRate());
            addRow(sh1, r++, null, "平均耗时(h)", overview.getAvgDurationHours());
            addRow(sh1, r++, null, "异常实例数", overview.getAnomalyCount());
            addRow(sh1, r++, null, "今日新增", overview.getTodayNewInstances());
            addRow(sh1, r++, null, "待处理任务", overview.getPendingTaskCount());
            sh1.autoSizeColumn(0); sh1.autoSizeColumn(1);
            r = r + 1;
            addRow(sh1, r++, hs, "---- 状态分布 ----", "");
            if (overview.getStatusDistribution() != null) {
                for (java.util.Map.Entry<String, Long> e : overview.getStatusDistribution().entrySet()) {
                    addRow(sh1, r++, null, e.getKey(), e.getValue());
                }
            }
            r = r + 1;
            addRow(sh1, r++, hs, "---- 业务类型分布 ----", "");
            if (overview.getBizTypeDistribution() != null) {
                for (com.aiflow.dto.StatisticsOverviewDTO.BizTypeCount bc : overview.getBizTypeDistribution()) {
                    addRow(sh1, r++, null, bc.getBizTypeName(), bc.getCount());
                }
            }
            for (int i = 0; i <= 1; i++) sh1.autoSizeColumn(i);
            org.apache.poi.ss.usermodel.Sheet sh3 = wb.createSheet("近30天趋势");
            java.time.LocalDate s = start != null ? start : java.time.LocalDate.now().minusDays(30);
            java.time.LocalDate e = end != null ? end : java.time.LocalDate.now();
            com.aiflow.dto.StatisticsTrendDTO trend = getTrend(s, e, "day", "summary");
            r = 0;
            addRow(sh3, r++, hs, "日期", "发起量", "办结量");
            if (trend != null && trend.getLabels() != null) {
                var init = trend.getSeries().stream().filter(se -> "发起量".equals(se.getBizTypeName())).findFirst().orElse(null);
                var done = trend.getSeries().stream().filter(se -> "办结量".equals(se.getBizTypeName())).findFirst().orElse(null);
                for (int i = 0; i < trend.getLabels().size(); i++) {
                    addRow(sh3, r++, null, trend.getLabels().get(i), init.getValues().get(i), done.getValues().get(i));
                }
            }
            for (int i = 0; i <= 2; i++) sh3.autoSizeColumn(i);
            org.apache.poi.ss.usermodel.Sheet sh2 = wb.createSheet("节点效率排名");
            r = 0;
            addRow(sh2, r++, hs, "排名", "节点名称", "总任务数", "超时数", "超时率(%%)", "平均耗时(h)");
            int rank = 1;
            if (nodeEff.getRankings() != null) {
                for (com.aiflow.dto.NodeEfficiencyDTO.NodeRanking nr : nodeEff.getRankings()) {
                    addRow(sh2, r++, null, String.valueOf(rank++), nr.getNodeName(), nr.getTotalCount(), nr.getTimeoutCount(), nr.getTimeoutRate(), nr.getAvgDwellHours());
                }
            }
            for (int i = 0; i <= 5; i++) sh2.autoSizeColumn(i);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Excel导出失败", e);
        }
    }

    private void addRow(org.apache.poi.ss.usermodel.Sheet sh, int row, org.apache.poi.ss.usermodel.CellStyle style, Object... vals) {
        org.apache.poi.ss.usermodel.Row r = sh.createRow(row);
        for (int i = 0; i < vals.length; i++) {
            org.apache.poi.ss.usermodel.Cell c = r.createCell(i);
            if (vals[i] instanceof Number n) c.setCellValue(n.doubleValue());
            else c.setCellValue(vals[i] != null ? vals[i].toString() : "");
            if (style != null) c.setCellStyle(style);
        }
    }
}