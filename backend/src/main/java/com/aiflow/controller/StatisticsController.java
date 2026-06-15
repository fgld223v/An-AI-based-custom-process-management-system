package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.StatisticsOverviewDTO;
import com.aiflow.dto.StatisticsTrendDTO;
import com.aiflow.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

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

    /**
     * 趋势统计：按时间范围 + 天/周聚合，按业务类型分组
     *
     * @param start       开始日期，格式 yyyy-MM-dd
     * @param end         结束日期，格式 yyyy-MM-dd
     * @param granularity 聚合粒度，day（默认）| week
     */
    @GetMapping("/trend")
    public ApiResponse<StatisticsTrendDTO> getTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "day") String granularity) {
        return ApiResponse.success(statisticsService.getTrend(start, end, granularity));
    }
}