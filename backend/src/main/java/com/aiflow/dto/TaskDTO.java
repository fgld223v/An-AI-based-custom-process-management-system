package com.aiflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务 DTO — 以 Flowable Task / HistoricTaskInstance 为准，附加业务信息。
 *
 * <p>数据来源：</p>
 * <ul>
 *   <li>待办 — ACT_RU_TASK（TaskService）</li>
 *   <li>已办 — ACT_HI_TASKINST（HistoryService）</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDTO {

    /** Flowable 任务 ID */
    private String taskId;

    /** 任务名称（Flowable task name） */
    private String taskName;

    /** 任务定义 key（Flowable taskDefinitionKey） */
    private String taskDefinitionKey;

    /**
     * Flowable 流程实例 ID。
     * <p>JSON 序列化为 {@code processInstanceId}（与 ProcessInstanceDTO 的
     * {@code flowableProcessInstanceId} 指代同一概念，命名差异属于历史遗留，
     * TaskDTO 遵循 V0.7 第四阶段 spec 定义）。</p>
     */
    @JsonProperty("processInstanceId")
    private String flowableProcessInstanceId;

    /** 业务 ProcessInstance 主键 */
    private Long businessInstanceId;

    /** 业务实例编号 */
    private String instanceCode;

    /** 业务实例标题 */
    private String instanceTitle;

    /** 任务处理人 */
    private String assignee;

    /** 任务创建时间（Flowable） */
    private LocalDateTime createTime;

    /** 任务到期时间 */
    private LocalDateTime dueDate;

    /** 任务结束时间（仅已办任务） */
    private LocalDateTime endTime;

    /** 任务状态：active（待办）、completed（已办） */
    private String status;

    /** 当前任务对应的表单 ID（来自 ProcessTemplate.formBindConfig） */
    private Long formId;
}
