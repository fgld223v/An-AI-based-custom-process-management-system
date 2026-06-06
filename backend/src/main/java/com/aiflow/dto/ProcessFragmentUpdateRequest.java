package com.aiflow.dto;

import lombok.Data;

@Data
public class ProcessFragmentUpdateRequest {

    private String fragmentName;
    private Long bizTypeId;
    private String description;
    private String fragmentType;
    private String bpmnXml;
    private String nodeConfig;
}
