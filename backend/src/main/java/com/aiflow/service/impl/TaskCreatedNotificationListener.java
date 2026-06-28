package com.aiflow.service.impl;

import com.aiflow.service.WorkflowNotificationService;
import lombok.RequiredArgsConstructor;
import org.flowable.task.service.delegate.TaskListener;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

/**
 * 任务创建通知监听器 — 在 Flowable 任务创建时自动发送通知。
 *
 * <p>监听 event=create，由 BPMN XML 中注入的 {@code flowable:taskListener} 触发。
 * 在任务创建时自动通知 assignee 或候选人。</p>
 *
 * <p>处理逻辑：</p>
 * <ol>
 *   <li>先检查任务是否有 assignee（单人审批），有则直接通知</li>
 *   <li>无 assignee 但有候选人（角色分配），遍历候选人列表逐一通知</li>
 * </ol>
 *
 * <p>Spring Bean 名称：{@code taskCreatedNotificationListener}，
 * BPMN XML 中通过 {@code delegateExpression="${taskCreatedNotificationListener}"} 引用。</p>
 */
@Component("taskCreatedNotificationListener")
@RequiredArgsConstructor
public class TaskCreatedNotificationListener implements TaskListener {

    private final WorkflowNotificationService workflowNotificationService;

    /**
     * 任务创建时触发 — 从流程变量获取业务实例 ID，向任务处理人发送通知。
     *
     * <p>通知路径：</p>
     * <ol>
     *   <li>有 assignee → 直接通知该用户</li>
     *   <li>无 assignee 有候选人 → 遍历候选人去重后逐一通知</li>
     * </ol>
     *
     * @param task Flowable 委托任务对象（含变量、assignee、候选人信息）
     */
    @Override
    public void notify(DelegateTask task) {
        // 从流程变量中获取业务实例 ID
        Long instanceId = toLong(task.getVariable("businessInstanceId"));
        // 路径1：有明确的 assignee（单人审批），直接通知
        if (hasText(task.getAssignee())) {
            workflowNotificationService.notifyTaskCreated(
                    task.getId(), instanceId, task.getAssignee(), task.getName());
            return;
        }
        // 路径2：无 assignee，但有候选人列表（角色分配场景），逐一通知
        task.getCandidates().stream()
                .map(IdentityLink::getUserId)
                .filter(this::hasText)
                .distinct()  // 去重，防止同一用户重复通知
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
