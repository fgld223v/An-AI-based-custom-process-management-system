package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * AI生成流程响应DTO：包含BPMN XML、节点配置和摘要
 */
public class AiGenerateProcessResponse {

    private String bpmnXml;
    private List<NodeConfigItem> nodeConfig;
    private String summary;
}
