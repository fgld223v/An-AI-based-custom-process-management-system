package com.aiflow.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 系统自动处理委托。
 *
 * <p>对应 businessType = "system_action" 的 BPMN serviceTask 节点，
 * 在流程到达时自动执行并立即完成（非阻塞），不执行任何业务逻辑。
 * 仅作为占位实现满足 Flowable 对 serviceTask 必须有实现属性的硬性要求。</p>
 *
 * <p>Spring Bean 名称：{@code systemActionDelegate}，
 * BPMN XML 中通过 {@code delegateExpression="${systemActionDelegate}"} 引用。</p>
 */
@Slf4j
@Component("systemActionDelegate")
public class SystemActionDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        String activityId = execution.getCurrentActivityId();
        String activityName = execution.getCurrentActivityName();
        String processInstanceId = execution.getProcessInstanceId();

        log.info("SystemActionDelegate: 系统自动处理节点完成 — activityId={}, activityName={}, processInstanceId={}",
                activityId, activityName, processInstanceId);
    }
}
