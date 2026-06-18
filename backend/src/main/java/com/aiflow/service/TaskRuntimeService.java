package com.aiflow.service;

import com.aiflow.dto.TaskCompleteRequest;
import com.aiflow.dto.TaskDTO;

/**
 * 任务运行时服务 — 处理任务完成/驳回等运行时操作。
 *
 * <p>审批链路（不可跳跃）：</p>
 * <ol>
 *   <li>查询 Flowable Task</li>
 *   <li>查询业务 ProcessInstance，校验状态</li>
 *   <li>保存 FormSubmission（历史保留）</li>
 *   <li>更新 Flowable Variables</li>
 *   <li>TaskService.complete(taskId, variables)</li>
 *   <li>查询下一任务</li>
 *   <li>刷新 ProcessInstance 状态</li>
 * </ol>
 *
 * <p>驳回链路：</p>
 * <ol>
 *   <li>查询 Flowable Task</li>
 *   <li>查询业务 ProcessInstance</li>
 *   <li>保存驳回 FormSubmission（status=rejected）</li>
 *   <li>TaskService.complete(taskId) — 结束当前任务</li>
 *   <li>回退 currentNodeKey 到上一节点</li>
 *   <li>设置 status=rejected，清除 flowable 关联</li>
 * </ol>
 */
public interface TaskRuntimeService {

    /**
     * 完成任务，推动流程流转。
     */
    TaskDTO completeTask(String taskId, TaskCompleteRequest request);

    /**
     * 驳回任务 — 退回上一节点，发起人可修改重提。
     *
     * @param taskId Flowable 任务 ID
     * @param instanceId 业务实例 ID
     * @param rejectReason 驳回原因
     */
    void rejectTask(String taskId, Long instanceId, String rejectReason);
}
