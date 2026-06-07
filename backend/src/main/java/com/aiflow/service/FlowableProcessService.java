package com.aiflow.service;

import java.util.List;
import java.util.Map;

/**
 * Flowable流程引擎服务接口
 */
public interface FlowableProcessService {

    /**
     * 部署流程定义
     * @param templateId 流程模板ID
     * @return 部署结果，包含deploymentId和processDefinitionId
     */
    Map<String, String> deployProcess(Long templateId);

    /**
     * 删除流程部署
     * @param deploymentId 部署ID
     */
    void undeployProcess(String deploymentId);

    /**
     * 启动流程实例
     * @param processDefinitionKey 流程定义Key
     * @param businessKey 业务Key（流程实例编码）
     * @param variables 流程变量
     * @return 流程实例ID
     */
    String startProcessInstance(String processDefinitionKey, String businessKey, Map<String, Object> variables);

    /**
     * 获取已部署的流程定义列表
     * @return 流程定义Key列表
     */
    List<String> listDeployedProcessDefinitions();
}
