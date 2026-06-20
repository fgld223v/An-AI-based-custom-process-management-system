package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineDTO {

    private List<TimelineNode> nodes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineNode {
        /** 节点类型：start | approval | end */
        private String type;
        /** 节点名称 */
        private String nodeName;
        /** 操作人 */
        private String operatorName;
        /** 操作时间 */
        private String time;
        /** 距上一步耗时（如 "2h30m"），第一步为 null */
        private String duration;
        /** 动作描述 */
        private String action;
        /** 审批意见，可空 */
        private String comment;
    }
}