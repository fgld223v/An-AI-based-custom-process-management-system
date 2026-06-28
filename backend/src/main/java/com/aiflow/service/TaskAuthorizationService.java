package com.aiflow.service;

import com.aiflow.security.CurrentUser;
import com.aiflow.security.SecurityUtils;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 任务权限服务，校验当前用户是否有权查看或操作 Flowable 任务，
 * 包含候选人的自动认领逻辑。
 */
@Service
public class TaskAuthorizationService {

    private final TaskService taskService;

    public TaskAuthorizationService(TaskService taskService) {
        this.taskService = taskService;
    }

    public void assertCanView(Task task) {
        CurrentUser user = requireCurrentUser();
        if (matchesCurrentUser(task.getAssignee(), user) || isCandidate(task.getId(), user)) {
            return;
        }
        throw new AccessDeniedException("current user cannot view this task");
    }

    public void assertCanView(HistoricTaskInstance task) {
        CurrentUser user = requireCurrentUser();
        if (matchesCurrentUser(task.getAssignee(), user)) {
            return;
        }
        throw new AccessDeniedException("current user cannot view this historic task");
    }

    /**
     * Validates operation permission and claims an unassigned candidate task.
     * The refreshed task is returned so callers operate on the final assignee state.
     */
    public Task requireOperableTask(Task task) {
        CurrentUser user = requireCurrentUser();
        if (hasText(task.getAssignee())) {
            if (matchesCurrentUser(task.getAssignee(), user)) {
                return task;
            }
            throw new AccessDeniedException("current user is not the task assignee");
        }
        if (!isCandidate(task.getId(), user)) {
            throw new AccessDeniedException("current user is not a task candidate");
        }

        String userId = String.valueOf(user.getId());
        try {
            taskService.claim(task.getId(), userId);
        } catch (RuntimeException claimFailure) {
            Task latest = taskService.createTaskQuery().taskId(task.getId()).singleResult();
            if (latest == null || !matchesCurrentUser(latest.getAssignee(), user)) {
                throw new AccessDeniedException("task has already been claimed by another user");
            }
            return latest;
        }
        Task claimed = taskService.createTaskQuery().taskId(task.getId()).singleResult();
        if (claimed == null || !matchesCurrentUser(claimed.getAssignee(), user)) {
            throw new IllegalStateException("task claim did not produce a valid assignee");
        }
        return claimed;
    }

    private boolean isCandidate(String taskId, CurrentUser user) {
        for (String actor : actorIdentifiers(user)) {
            if (taskService.createTaskQuery()
                    .taskId(taskId)
                    .taskCandidateUser(actor)
                    .count() > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesCurrentUser(String assignee, CurrentUser user) {
        return hasText(assignee) && actorIdentifiers(user).contains(assignee.trim());
    }

    private Set<String> actorIdentifiers(CurrentUser user) {
        Set<String> values = new LinkedHashSet<>();
        values.add(String.valueOf(user.getId()));
        if (hasText(user.getUsername())) values.add(user.getUsername().trim());
        return values;
    }

    private CurrentUser requireCurrentUser() {
        CurrentUser user = SecurityUtils.currentUser();
        if (user == null || !user.isEnabled()) {
            throw new AccessDeniedException("authenticated enabled user is required");
        }
        return user;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
