package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 趋势统计 DTO — GET /api/statistics/trend 返回体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsTrendDTO {

    /** 时间轴标签（如 ["2026-06-01", "2026-06-02", ...] 或 ["2026-W23", ...]） */
    private List<String> labels;

    /** 各业务类型的数据序列 */
    private List<TrendSeries> series;

    /**
     * 单条趋势线 — 某个业务类型在每个时间点的数值
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendSeries {
        private Long bizTypeId;
        private String bizTypeName;
        /** 与 labels 一一对应 */
        private List<Long> values;
    }
}