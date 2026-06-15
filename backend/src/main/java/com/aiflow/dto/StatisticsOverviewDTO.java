package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统计概览 DTO — GET /api/statistics/overview 返回体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsOverviewDTO {

    /** 实例总数 */
    private Long totalInstances;

    /** 办结率（百分比，如 85.5 表示 85.5%） */
    private Double completionRate;

    /** 平均审批耗时（小时） */
    private Double avgDurationHours;

    /** 异常实例数（超时任务 + 高度瓶颈预警 + 驳回审批的去重实例数） */
    private Long anomalyCount;
}