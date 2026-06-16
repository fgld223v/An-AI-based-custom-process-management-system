package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 节点效率分析 DTO — GET /api/statistics/node-efficiency 返回体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeEfficiencyDTO {

    /** 节点排名列表（按平均停留时长降序） */
    private List<NodeRanking> rankings;

    /**
     * 单个节点的效率数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeRanking {
        private String nodeKey;
        private String nodeName;
        /** 平均停留时长（小时） */
        private Double avgDwellHours;
        /** 超时率（百分比，如 15.0 表示 15%） */
        private Double timeoutRate;
        /** 总任务数 */
        private Long totalCount;
        /** 超时任务数 */
        private Long timeoutCount;
    }
}