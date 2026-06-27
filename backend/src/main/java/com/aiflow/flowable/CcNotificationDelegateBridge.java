package com.aiflow.flowable;

import com.aiflow.config.SpringContextHolder;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;

public class CcNotificationDelegateBridge implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        SpringContextHolder.getBean("ccNotificationDelegate", JavaDelegate.class).execute(execution);
    }
}
