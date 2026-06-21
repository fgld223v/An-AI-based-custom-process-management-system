package com.aiflow.service;

import com.aiflow.model.ProcessTemplate;

import java.util.List;

public interface FlowableDeploymentService {

    /**
     * 部署流程模板到 Flowable 引擎。
     *
     * @param template 流程模板
     * @return 部署中产生的警告信息列表（如节点类型自动转换等），无警告时为空列表
     */
    List<String> deployProcessTemplate(ProcessTemplate template);
}