package com.aiflow.service.impl;

import com.aiflow.service.WorkflowNotificationService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

@Component("taskCreatedNotificationListener")
@RequiredArgsConstructor
public class TaskCreatedNotificationListener implements TaskListener {

    private final WorkflowNotificationService workflowNotificationService;

    @Override
    public void notify(DelegateTask task) {
        workflowNotificationService.notifyTaskCreated(
                task.getId(), toLong(task.getVariable("businessInstanceId")),
                task.getAssignee(), task.getName());
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
