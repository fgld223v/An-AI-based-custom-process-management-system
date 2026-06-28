package com.aiflow.flowable;

import com.aiflow.config.SpringContextHolder;
import org.flowable.task.service.delegate.DelegateTask;
import org.flowable.task.service.delegate.TaskListener;

/**
 * 单人审批任务监听器桥接，Flowable TaskListener实现，桥接到Spring管理的监听器Bean。
 */
public class SingleAssigneeTaskListenerBridge implements TaskListener {

    @Override
    public void notify(DelegateTask delegateTask) {
        SpringContextHolder.getBean("singleAssigneeListener", TaskListener.class).notify(delegateTask);
    }
}
