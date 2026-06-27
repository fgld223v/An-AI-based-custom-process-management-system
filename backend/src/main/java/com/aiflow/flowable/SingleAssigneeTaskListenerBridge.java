package com.aiflow.flowable;

import com.aiflow.config.SpringContextHolder;
import org.flowable.task.service.delegate.DelegateTask;
import org.flowable.task.service.delegate.TaskListener;

public class SingleAssigneeTaskListenerBridge implements TaskListener {

    @Override
    public void notify(DelegateTask delegateTask) {
        SpringContextHolder.getBean("singleAssigneeListener", TaskListener.class).notify(delegateTask);
    }
}
