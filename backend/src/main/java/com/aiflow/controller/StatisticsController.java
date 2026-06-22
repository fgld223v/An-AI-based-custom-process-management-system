package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.NodeEfficiencyDTO;
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
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(required = false) String mode) {
        return ApiResponse.success(statisticsService.getTrend(start, end, granularity, mode));
    }

    /**
     * 节点效率排名：每个节点的平均停留时长 / 超时率
     */
    @GetMapping("/node-efficiency")
    public ApiResponse<NodeEfficiencyDTO> getNodeEfficiency() {
        return ApiResponse.success(statisticsService.getNodeEfficiency());
    }

    @GetMapping("/export")
    public void exportExcel(@RequestParam(defaultValue = "excel") String format,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                            jakarta.servlet.http.HttpServletResponse response) throws Exception {
        byte[] data = statisticsService.exportExcel(start, end);
        String filename = (start != null && end != null)
                ? "statistics_" + start + "_" + end + ".xlsx"
                : "statistics.xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);
        response.getOutputStream().write(data);
    }
}