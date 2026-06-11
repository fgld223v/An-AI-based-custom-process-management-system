package com.aiflow.module.template.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workflow_template")
public class WorkflowTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String templateName;

    private String businessType;

    private String formJson;

    private String bpmnXml;

    private String status;

    private Long createdBy;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
