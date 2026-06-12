package com.aiflow.dto;

import lombok.Data;

import java.util.Map;

/**
 * 任务完成请求。
 */
@Data
public class TaskCompleteRequest {

    /** 业务 ProcessInstance 主键 */
    private Long instanceId;

    /** 当前节点 key（Flowable taskDefinitionKey） */
    private String nodeKey;

    /** 当前节点对应的表单 ID */
    private Long formId;

    /** 表单数据（键值对） */
    private Map<String, Object> formData;
}
