package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流程实例运行时状态 — 以 Flowable Runtime 为准。
 * 不依赖 business ProcessInstance 的 currentNodeKey/currentNodeName/currentBusinessType 字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeStateDTO {

    /** 业务 ProcessInstance 主键 */
    private Long businessInstanceId;

    /** Flowable Runtime ProcessInstance ID */
    private String flowableProcessInstanceId;

    /** 当前任务 key（Flowable taskDefinitionKey） */
    private String currentTaskKey;

    /** 当前任务名称（Flowable task name） */
    private String currentTaskName;

    /** 当前节点对应的表单 ID（来自 ProcessTemplate.formBindConfig） */
    private Long formId;

    /** 流程是否已结束（无活跃任务时返回 true，前端据此展示友好提示） */
    @Builder.Default
    private boolean completed = false;
}
