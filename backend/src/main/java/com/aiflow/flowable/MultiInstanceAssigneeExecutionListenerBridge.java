package com.aiflow.flowable;

import com.aiflow.config.SpringContextHolder;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;

public class MultiInstanceAssigneeExecutionListenerBridge implements ExecutionListener {

    @Override
    public void notify(DelegateExecution execution) {
        SpringContextHolder.getBean("multiInstanceAssigneeListener", ExecutionListener.class).notify(execution);
    }
}
