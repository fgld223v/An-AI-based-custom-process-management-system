package com.aiflow.service;

import com.aiflow.dto.StatisticsOverviewDTO;

/**
 * 统计看板服务接口
 */
public interface StatisticsService {

    /**
     * 统计概览：实例总数 / 办结率 / 平均审批耗时 / 异常实例数
     */
    StatisticsOverviewDTO getOverview();
}