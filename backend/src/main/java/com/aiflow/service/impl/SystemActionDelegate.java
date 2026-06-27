package com.aiflow.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.engine.impl.delegate.ActivityBehavior;
import org.springframework.stereotype.Component;

/**
 * 系统自动处理委托 — 双重接口实现 + Spring Bean 注册，
 * 让 Flowable 的 delegateExpression="${systemActionDelegate}" 能可靠解析。
 *
 * <p>同时实现 {@link JavaDelegate} 和 {@link ActivityBehavior}，
 * 无论 Flowable 从哪个接口检查都能匹配。</p>
 *
 * <p>通过 {@code @Component("systemActionDelegate")} 注册为 Spring Bean，
 * 与项目中其他 delegate（MultiInstanceAssigneeListener、CcNotificationDelegate 等）
 * 的注册方式保持一致，确保 {@code delegateExpression} 能从 Spring 容器中解析。</p>
 */
@Slf4j
@Component("systemActionDelegate")
public class SystemActionDelegate implements JavaDelegate, ActivityBehavior {

    @Override
    public void execute(DelegateExecution execution) {
        log.info("SystemActionDelegate: activityId={}, activityName={}, procInstId={}",
                execution.getCurrentActivityId(),
                execution.getCurrentActivityName(),
                execution.getProcessInstanceId());
    }
}
