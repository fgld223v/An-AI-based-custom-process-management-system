package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.TaskCompleteRequest;
import com.aiflow.dto.TaskDTO;
import com.aiflow.service.TaskQueryService;
import com.aiflow.service.TaskRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskQueryService taskQueryService;
    private final TaskRuntimeService taskRuntimeService;

    /** 待办列表 — ACT_RU_TASK */
    @GetMapping("/my")
    public ApiResponse<List<TaskDTO>> listMyTasks() {
        return ApiResponse.success(taskQueryService.listMyTasks());
    }

    /** 已办列表 — ACT_HI_TASKINST */
    @GetMapping("/done")
    public ApiResponse<List<TaskDTO>> listDoneTasks() {
        return ApiResponse.success(taskQueryService.listDoneTasks());
    }

    /** 任务详情 — 先查 ACT_RU_TASK，不存在则 ACT_HI_TASKINST */
    @GetMapping("/{taskId}")
    public ApiResponse<TaskDTO> getTask(@PathVariable String taskId) {
        return ApiResponse.success(taskQueryService.getTask(taskId));
    }

    /** 完成任务 */
    @PostMapping("/{taskId}/complete")
    public ApiResponse<TaskDTO> completeTask(@PathVariable String taskId,
                                             @RequestBody TaskCompleteRequest request) {
        TaskDTO nextTask = taskRuntimeService.completeTask(taskId, request);
        return ApiResponse.success(nextTask);
    }
}
