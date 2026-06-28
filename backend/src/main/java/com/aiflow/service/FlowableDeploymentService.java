package com.aiflow.service;

import com.aiflow.model.ProcessTemplate;

/**
 * Flowable 部署服务接口 — 负责将流程模板的 BPMN XML 部署到 Flowable 流程引擎。
 *
 * <p>部署流程包括：</p>
 * <ol>
 *   <li>校验 BPMN XML 合法性</li>
 *   <li>补充 isExecutable=true 属性</li>
 *   <li>注入多实例（会签/或签）和抄送扩展元素</li>
 *   <li>调用 Flowable RepositoryService 执行部署</li>
 *   <li>回写 flowableDeploymentId 和 flowableProcessDefinitionId 到模板</li>
 * </ol>
 */
public interface FlowableDeploymentService {

    /**
     * 将流程模板部署到 Flowable 引擎。
     *
     * @param template 待部署的流程模板（必须包含合法的 BPMN XML）
     * @return 更新后的模板（含 flowableDeploymentId 和 flowableProcessDefinitionId）
     * @throws IllegalStateException BPMN XML 不合法或 Flowable 部署失败时抛出
     */
    ProcessTemplate deployProcessTemplate(ProcessTemplate template);
}