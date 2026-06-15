package com.aiflow.dto;

import lombok.Data;

/**
 * 任务驳回请求。
 */
@Data
public class TaskRejectRequest {

    /** 业务 ProcessInstance 主键 */
    private Long instanceId;

    /** 驳回原因 */
    private String rejectReason;
}
