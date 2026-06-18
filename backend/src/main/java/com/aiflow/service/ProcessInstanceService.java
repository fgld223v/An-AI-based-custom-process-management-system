package com.aiflow.service;

import com.aiflow.dto.FormSubmissionDTO;
import com.aiflow.dto.ProcessInstanceDTO;
import com.aiflow.dto.RuntimeStateDTO;
import com.aiflow.dto.SaveNodeFormRequest;
import com.aiflow.dto.StartProcessPreviewRequest;

import java.util.List;

public interface ProcessInstanceService {

    List<ProcessInstanceDTO> listInstances(Long templateId, String status, String keyword);

    ProcessInstanceDTO getInstance(Long id);

    List<FormSubmissionDTO> listSubmissions(Long processInstanceId);

    ProcessInstanceDTO createDraft(StartProcessPreviewRequest request);

    FormSubmissionDTO saveNodeForm(SaveNodeFormRequest request);

    ProcessInstanceDTO submitInstance(Long id);

    /**
     * 获取流程实例的运行时状态（以 Flowable Runtime 为准）。
     * 使用 TaskQuery 查询当前任务，不依赖 business ProcessInstance 的
     * currentNodeKey / currentNodeName / currentBusinessType。
     *
     * @param processInstanceId 业务 ProcessInstance 主键
     * @return 运行时状态，包含当前任务 key/name 和对应 formId
     */
    RuntimeStateDTO getRuntimeState(Long processInstanceId);
}