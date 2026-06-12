package com.aiflow.service;

import com.aiflow.dto.TaskCompleteRequest;
import com.aiflow.dto.TaskDTO;

/**
 * 任务运行时服务 — 处理任务完成等运行时操作。
 *
 * <p>审批链路：</p>
 * <ol>
 *   <li>查询 Flowable Task</li>
 *   <li>查询业务 ProcessInstance</li>
 *   <li>保存 FormSubmission（历史保留）</li>
 *   <li>更新 Flowable Variables</li>
 *   <li>TaskService.complete(taskId, variables)</li>
 *   <li>查询下一任务</li>
 *   <li>刷新 ProcessInstance 状态</li>
 * </ol>
 */
public interface TaskRuntimeService {

    /**
     * 完成任务，推动流程流转。
     *
     * @param taskId  Flowable 任务 ID
     * @param request 完成请求（业务实例 ID、节点 key、表单数据等）
     * @return 下一任务信息；如果流程已结束则返回 null
     */
    TaskDTO completeTask(String taskId, TaskCompleteRequest request);
}
