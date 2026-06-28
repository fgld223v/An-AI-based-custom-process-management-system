package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.TaskCompleteRequest;
import com.aiflow.dto.TaskDTO;
import com.aiflow.dto.TaskRejectRequest;
import com.aiflow.service.TaskQueryService;
import com.aiflow.service.TaskRuntimeService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 任务控制器 -- 提供 Flowable 任务的查询、办理、驳回等操作。
 *
 * <p>端点一览：
 * <ul>
 *   <li>GET  /api/tasks/my                  -- 当前用户的待办任务列表（来自 ACT_RU_TASK）</li>
 *   <li>GET  /api/tasks/done                -- 当前用户的已办任务列表（来自 ACT_HI_TASKINST）</li>
 *   <li>GET  /api/tasks/{taskId}            -- 任务详情（先查运行表，再查历史表）</li>
 *   <li>POST /api/tasks/{taskId}/complete   -- 完成任务（提交表单数据，推进流程）</li>
 *   <li>POST /api/tasks/{taskId}/reject     -- 驳回到上一节点</li>
 * </ul>
 *
 * <p>所有端点均需要登录，根据当前用户身份过滤任务数据。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskQueryService taskQueryService;
    private final TaskRuntimeService taskRuntimeService;

    /**
     * 待办任务列表。
     *
     * <p>GET /api/tasks/my -- 查询当前用户所有待处理的 Flowable 任务（ACT_RU_TASK）。
     * 需要登录。
     *
     * @return 待办任务列表，包含流程实例、表单等上下文信息
     */
    @GetMapping("/my")
    public ApiResponse<List<TaskDTO>> listMyTasks() {
        return ApiResponse.success(taskQueryService.listMyTasks());
    }

    /**
     * 已办任务列表。
     *
     * <p>GET /api/tasks/done -- 查询当前用户已完成的历史任务（ACT_HI_TASKINST）。
     * 需要登录。
     *
     * @return 已办任务列表
     */
    @GetMapping("/done")
    public ApiResponse<List<TaskDTO>> listDoneTasks() {
        return ApiResponse.success(taskQueryService.listDoneTasks());
    }

    /**
     * 任务详情。
     *
     * <p>GET /api/tasks/{taskId} -- 查询指定任务详情，优先查运行表 ACT_RU_TASK，
     * 若不存在则从历史表 ACT_HI_TASKINST 查询。需要登录。
     *
     * @param taskId Flowable 任务 ID
     * @return 任务详细信息，含表单数据、附件等
     */
    @GetMapping("/{taskId}")
    public ApiResponse<TaskDTO> getTask(@PathVariable String taskId) {
        return ApiResponse.success(taskQueryService.getTask(taskId));
    }

    /**
     * 完成任务（审批通过 / 提交）。
     *
     * <p>POST /api/tasks/{taskId}/complete -- 办理当前任务，提交表单数据并推进流程
     * 到下一节点。操作由服务层记录到 audit_log。需要登录。
     *
     * @param taskId  Flowable 任务 ID
     * @param request 完成请求体，包含表单变量、审批意见等
     * @return 若有下一任务则返回该任务信息，否则返回 null（流程已结束）
     */
    @PostMapping("/{taskId}/complete")
    public ApiResponse<TaskDTO> completeTask(@PathVariable String taskId,
                                             @Valid @RequestBody TaskCompleteRequest request) {
        TaskDTO nextTask = taskRuntimeService.completeTask(taskId, request);
        return ApiResponse.success(nextTask);
    }

    /**
     * 驳回任务。
     *
     * <p>POST /api/tasks/{taskId}/reject -- 将当前任务驳回到流程的上一个节点，
     * 同时记录驳回原因。需要登录。
     *
     * @param taskId  Flowable 任务 ID
     * @param request 驳回请求体，包含流程实例 ID 和驳回原因
     * @return 空成功响应
     */
    @PostMapping("/{taskId}/reject")
    public ApiResponse<Void> rejectTask(@PathVariable String taskId,
                                        @Valid @RequestBody TaskRejectRequest request) {
        taskRuntimeService.rejectTask(taskId, request.getInstanceId(), request.getRejectReason());
        return ApiResponse.success();
    }
}
