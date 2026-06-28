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
/**
 * 流程路由预览DTO：展示审批步骤及各级审批人信息
 */
public class ProcessRoutePreviewDTO {

    private Long templateId;
    private Long applicantId;
    private String applicantName;
    private List<ApprovalStep> approvalSteps;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalStep {
        private String nodeKey;
        private String nodeName;
        private String approvalMode;
        private String assignStrategy;
        private List<Approver> approvers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Approver {
        private Long userId;
        private String userName;
        private Long departmentId;
    }
}
