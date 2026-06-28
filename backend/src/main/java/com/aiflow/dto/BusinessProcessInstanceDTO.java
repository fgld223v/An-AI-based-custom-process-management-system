package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * 业务流程实例DTO：包含实例完整状态、模板信息及申请人等数据
 */
public class BusinessProcessInstanceDTO {

    private Long id;
    private String instanceCode;
    private String instanceTitle;
    private String status;
    private Boolean anomaly;
    private String anomalyReason;
    private Long templateId;
    private String templateCode;
    private String templateName;
    private Integer templateVersion;
    private String templateStatus;
    private Long processOwnerId;
    private String processOwnerName;
    private Long applicantId;
    private String applicantUsername;
    private String applicantName;
    private Long applicantDepartmentId;
    private Long bizTypeId;
    private Long formId;
    private String currentNodeKey;
    private String currentNodeName;
    private String currentBusinessType;
    private String flowableProcessInstanceId;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
