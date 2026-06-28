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
 * 统计看板控制器 -- 提供流程数据的多维度统计分析接口。
 *
 * <p>支持概览统计、趋势分析、节点效率排名以及 Excel 导出功能。
 *
 * <p>端点一览：
 * <ul>
 *   <li>GET /api/statistics/overview         -- 统计概览（实例总数/办结率/平均耗时/异常数）</li>
 *   <li>GET /api/statistics/trend             -- 趋势统计（按时间范围 + 聚合粒度，按业务类型分组）</li>
 *   <li>GET /api/statistics/node-efficiency    -- 节点效率排名（各节点平均停留时长/超时率）</li>
 *   <li>GET /api/statistics/export             -- 导出统计 Excel 报表</li>
 * </ul>
 *
 * <p>所有端点均需要登录，通常需要管理员或业务管理员权限。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    /**
     * 统计概览。
     *
     * <p>GET /api/statistics/overview -- 返回全局统计指标概览，包括：
     * 流程实例总数、办结率、平均审批耗时、异常实例数等。
     * 需要登录。
     *
     * @return 统计概览 DTO
     */
    @GetMapping("/overview")
    public ApiResponse<StatisticsOverviewDTO> getOverview() {
        return ApiResponse.success(statisticsService.getOverview());
    }

    /**
     * 趋势统计。
     *
     * <p>GET /api/statistics/trend -- 按时间范围进行趋势统计，支持按天/周聚合，
     * 可按业务类型分组展示。用于生成折线图或柱状图数据。需要登录。
     *
     * @param start       开始日期，格式 yyyy-MM-dd（必填）
     * @param end         结束日期，格式 yyyy-MM-dd（必填）
     * @param granularity 聚合粒度：day（按天，默认）| week（按周）
     * @param mode        统计模式（可选），如 biz_type（按业务类型分组）
     * @return 趋势统计 DTO，包含时间序列数据点
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
     * 节点效率排名。
     *
     * <p>GET /api/statistics/node-efficiency -- 统计每个审批节点的平均停留时长
     * 和超时率，用于识别流程瓶颈节点。需要登录。
     *
     * @return 节点效率 DTO，包含各节点的统计数据
     */
    @GetMapping("/node-efficiency")
    public ApiResponse<NodeEfficiencyDTO> getNodeEfficiency() {
        return ApiResponse.success(statisticsService.getNodeEfficiency());
    }

    /**
     * 导出统计 Excel 报表。
     *
     * <p>GET /api/statistics/export -- 导出指定时间范围的统计数据为 Excel 文件。
     * 文件名自动包含起止日期。需要登录。
     *
     * @param format   导出格式，默认 "excel"
     * @param start    开始日期（可选），格式 yyyy-MM-dd
     * @param end      结束日期（可选），格式 yyyy-MM-dd
     * @param response HTTP 响应，直接写入 Excel 流
     */
    @GetMapping("/export")
    public void exportExcel(@RequestParam(defaultValue = "excel") String format,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                            jakarta.servlet.http.HttpServletResponse response) throws Exception {
        // 获取导出的 Excel 字节数据
        byte[] data = statisticsService.exportExcel(start, end);
        // 根据时间范围动态生成文件名
        String filename = (start != null && end != null)
                ? "statistics_" + start + "_" + end + ".xlsx"
                : "statistics.xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);
        response.getOutputStream().write(data);
    }
}