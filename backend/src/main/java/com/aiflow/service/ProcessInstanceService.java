package com.aiflow.service;

import com.aiflow.dto.FormSubmissionDTO;
import com.aiflow.dto.ProcessInstanceDTO;
import com.aiflow.dto.RuntimeStateDTO;
import com.aiflow.dto.SaveNodeFormRequest;
import com.aiflow.dto.StartProcessPreviewRequest;
import com.aiflow.dto.TimelineDTO;

import java.util.List;

public interface ProcessInstanceService {

    List<ProcessInstanceDTO> listInstances(Long templateId, String status, String keyword);

    ProcessInstanceDTO getInstance(Long id);

    List<FormSubmissionDTO> listSubmissions(Long processInstanceId);

    ProcessInstanceDTO createDraft(StartProcessPreviewRequest request);

    FormSubmissionDTO saveNodeForm(SaveNodeFormRequest request);

    ProcessInstanceDTO submitInstance(Long id);

    RuntimeStateDTO getRuntimeState(Long processInstanceId);

    /**
     * 获取流程实例的流转时间线（发起 → 审批节点 → 完成，含耗时计算）
     */
    TimelineDTO getTimeline(Long processInstanceId);
}