package com.aiflow.dto;

import lombok.Data;

@Data
/**
 * 节点表单保存请求DTO：保存流程节点中的表单数据
 */
public class SaveNodeFormRequest {
    private Long processInstanceId;
    private Long templateId;
    private String nodeKey;
    private String nodeName;
    private String businessType;
    private Long formId;
    private String formDataJson;
    private String status;
}