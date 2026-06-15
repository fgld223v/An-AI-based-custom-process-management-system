package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.StatisticsOverviewDTO;
import com.aiflow.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计看板接口
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    /**
     * 统计概览：实例总数 / 办结率 / 平均审批耗时 / 异常实例数
     */
    @GetMapping("/overview")
    public ApiResponse<StatisticsOverviewDTO> getOverview() {
        return ApiResponse.success(statisticsService.getOverview());
    }
}