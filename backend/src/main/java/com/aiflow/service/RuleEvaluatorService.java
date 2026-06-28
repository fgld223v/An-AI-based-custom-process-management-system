package com.aiflow.service;

import com.aiflow.model.ProcessInstance;
import org.flowable.task.api.Task;

/**
 * 规则评估服务接口，负责评估审批链中配置的自动审批规则，
 * 对满足条件的任务进行自动完成，返回仍需人工处理的第一个任务。
 */
public interface RuleEvaluatorService {

    /**
     * Evaluates configured approval rules for the current task chain and auto-completes
     * eligible approval tasks. Returns the first task that still needs manual handling,
     * or null when the process is completed.
     */
    Task evaluateAndAutoComplete(ProcessInstance instance);
}
