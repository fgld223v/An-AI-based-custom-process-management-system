package com.aiflow.flowable;

import com.aiflow.config.SpringContextHolder;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;

/**
 * 多实例审批人执行监听器桥接，Flowable ExecutionListener实现，桥接到Spring管理的监听器Bean。
 */
public class MultiInstanceAssigneeExecutionListenerBridge implements ExecutionListener {

    @Override
    public void notify(DelegateExecution execution) {
        SpringContextHolder.getBean("multiInstanceAssigneeListener", ExecutionListener.class).notify(execution);
    }
}
