package com.aiflow.service;

import com.aiflow.dto.NotificationDTO;
import com.aiflow.model.ProcessInstance;
import org.flowable.task.api.Task;

/**
 * 任务催办服务接口，提供对当前任务的手动催办和自动催办能力。
 */
public interface TaskUrgeService {

    /**
     * 对指定流程实例的当前待办任务发送催办通知。
     *
     * @param processInstanceId 流程实例ID
     * @return 催办通知
     */
    NotificationDTO urgeCurrentTask(Long processInstanceId);

    /**
     * 根据超时配置自动判断是否需要催办并执行。
     *
     * @param task     Flowable 任务对象
     * @param instance 流程实例
     * @return 是否已发送催办
     */
    boolean autoUrgeTask(Task task, ProcessInstance instance);
}
