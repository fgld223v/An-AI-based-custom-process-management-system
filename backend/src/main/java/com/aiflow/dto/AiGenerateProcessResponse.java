package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiGenerateProcessResponse {

    private String bpmnXml;
    private List<NodeConfigItem> nodeConfig;
    private String summary;
}
