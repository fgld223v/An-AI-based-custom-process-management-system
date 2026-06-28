package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * 流程片段DTO：包含片段编码、名称、类型、状态及BPMN数据
 */
public class ProcessFragmentDTO {

    private Long id;
    private String fragmentCode;
    private String fragmentName;
    private Long bizTypeId;
    private String description;
    private String fragmentType;
    private String status;
    private String bpmnXml;
    private String nodeConfig;
    private Long createdBy;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
