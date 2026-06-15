package com.aiflow.service;

import com.aiflow.dto.NotificationDTO;
import com.aiflow.model.ProcessInstance;
import org.flowable.task.api.Task;

public interface TaskUrgeService {

    NotificationDTO urgeCurrentTask(Long processInstanceId);

    boolean autoUrgeTask(Task task, ProcessInstance instance);
}
