package com.aiflow.service;

/**
 * Flowable 运行时服务 — 负责启动 Flowable Runtime ProcessInstance
 * 并与业务 ProcessInstance 建立关联。
 */
public interface FlowableRuntimeService {

    /**
     * 根据业务 ProcessInstance ID 启动 Flowable 流程实例。
     * 内部完成：查询/校验/聚合表单/构建变量/启动/回写。
     *
     * @param processInstanceId 业务 ProcessInstance 主键
     * @throws IllegalArgumentException 实例或模板不存在
     * @throws IllegalStateException    状态异常、已启动、模板未发布/未部署
     */
    void startProcess(Long processInstanceId);
}