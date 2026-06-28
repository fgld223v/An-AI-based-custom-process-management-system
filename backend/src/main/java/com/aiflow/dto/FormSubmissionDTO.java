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
 * 表单提交记录DTO：记录流程节点中用户提交的表单数据
 */
public class FormSubmissionDTO {
    private Long id;
    private Long processInstanceId;
    private Long templateId;
    private String nodeKey;
    private String nodeName;
    private String businessType;
    private Long formId;
    private String formDataJson;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}