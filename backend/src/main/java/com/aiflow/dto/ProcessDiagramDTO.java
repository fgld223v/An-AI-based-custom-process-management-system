package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * 流程图DTO：包含流程模板ID、名称及BPMN XML
 */
public class ProcessDiagramDTO {
    private Long templateId;
    private String templateName;
    private String bpmnXml;
}
