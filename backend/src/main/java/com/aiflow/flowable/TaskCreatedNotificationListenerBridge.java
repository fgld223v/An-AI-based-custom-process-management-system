package com.aiflow.flowable;

import com.aiflow.config.SpringContextHolder;
import org.flowable.task.service.delegate.DelegateTask;
import org.flowable.task.service.delegate.TaskListener;

/**
 * 任务创建通知监听器桥接，Flowable TaskListener实现，在任务创建时触发通知。
 */
public class TaskCreatedNotificationListenerBridge implements TaskListener {

    @Override
    public void notify(DelegateTask delegateTask) {
        SpringContextHolder.getBean("taskCreatedNotificationListener", TaskListener.class).notify(delegateTask);
    }
}
