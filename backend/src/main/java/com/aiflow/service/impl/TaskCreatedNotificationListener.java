package com.aiflow.service.impl;

import com.aiflow.service.WorkflowNotificationService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

@Component("taskCreatedNotificationListener")
@RequiredArgsConstructor
public class TaskCreatedNotificationListener implements TaskListener {

    private final WorkflowNotificationService workflowNotificationService;

    @Override
    public void notify(DelegateTask task) {
        Long instanceId = toLong(task.getVariable("businessInstanceId"));
        if (hasText(task.getAssignee())) {
            workflowNotificationService.notifyTaskCreated(
                    task.getId(), instanceId, task.getAssignee(), task.getName());
            return;
        }
        task.getCandidates().stream()
                .map(IdentityLink::getUserId)
                .filter(this::hasText)
                .distinct()
                .forEach(candidate -> workflowNotificationService.notifyTaskCreated(
                        task.getId(), instanceId, candidate, task.getName()));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
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
