package com.aiflow.flowable;

import com.aiflow.config.SpringContextHolder;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;

/**
 * 抄送通知代理桥接，Flowable JavaDelegate实现，通过Spring容器获取实际代理Bean。
 */
public class CcNotificationDelegateBridge implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        SpringContextHolder.getBean("ccNotificationDelegate", JavaDelegate.class).execute(execution);
    }
}
