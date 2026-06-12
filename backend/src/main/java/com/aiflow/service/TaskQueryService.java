package com.aiflow.service;

import com.aiflow.dto.TaskDTO;

import java.util.List;

/**
 * 任务查询服务 — 以 Flowable TaskQuery / HistoryService 为准。
 *
 * <p>数据来源：</p>
 * <ul>
 *   <li>待办 — ACT_RU_TASK（TaskService）</li>
 *   <li>已办 — ACT_HI_TASKINST（HistoryService）</li>
 * </ul>
 */
public interface TaskQueryService {

    /**
     * 查询当前用户相关的待处理任务（ACT_RU_TASK）。
     *
     * @return 待办任务列表，包含业务信息
     */
    List<TaskDTO> listMyTasks();

    /**
     * 查询已办任务（ACT_HI_TASKINST）。
     *
     * @return 已办任务列表，包含业务信息
     */
    List<TaskDTO> listDoneTasks();

    /**
     * 根据 Flowable taskId 查询单个任务。
     * 先查 ACT_RU_TASK，若不存在则查 ACT_HI_TASKINST。
     *
     * @param taskId Flowable 任务 ID
     * @return 任务详情，包含业务信息
     */
    TaskDTO getTask(String taskId);
}
