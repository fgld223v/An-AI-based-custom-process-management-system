package com.aiflow.service;

import com.aiflow.model.ProcessInstance;
import org.flowable.task.api.Task;

public interface RuleEvaluatorService {

    /**
     * Evaluates configured approval rules for the current task chain and auto-completes
     * eligible approval tasks. Returns the first task that still needs manual handling,
     * or null when the process is completed.
     */
    Task evaluateAndAutoComplete(ProcessInstance instance);
}
