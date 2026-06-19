package com.aiflow.service;

import com.aiflow.dto.NodeEfficiencyDTO;
import com.aiflow.dto.StatisticsOverviewDTO;
import com.aiflow.dto.StatisticsTrendDTO;

import java.time.LocalDate;

/**
 * 统计看板服务接口
 */
public interface StatisticsService {

    /**
     * 统计概览：实例总数 / 办结率 / 平均审批耗时 / 异常实例数
     */
    StatisticsOverviewDTO getOverview();

    /**
     * 趋势统计：按时间范围 + 天/周聚合，按业务类型分组
     *
     * @param start       开始日期（含）
     * @param end         结束日期（含）
     * @param granularity day | week
     * @param mode        null/bizType（默认）| summary（发起量+办结量汇总）
     */
    StatisticsTrendDTO getTrend(LocalDate start, LocalDate end, String granularity, String mode);

    /**
     * 节点效率排名：每个节点的平均停留时长 / 超时率
     */
    NodeEfficiencyDTO getNodeEfficiency();
}