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
 * 流程实例DTO：包含实例状态、当前节点、Flowable关联信息
 */
public class ProcessInstanceDTO {
    private Long id;
    private Long templateId;
    private String instanceCode;
    private String instanceTitle;
    private String status;
    private String currentNodeKey;
    private String currentNodeName;
    private String currentBusinessType;
    private String flowableProcessInstanceId;
    private String flowableDefinitionId;
    private String flowableDeploymentId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}