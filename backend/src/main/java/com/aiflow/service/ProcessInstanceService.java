package com.aiflow.service;

import com.aiflow.dto.FormSubmissionDTO;
import com.aiflow.dto.ProcessInstanceDTO;
import com.aiflow.dto.SaveNodeFormRequestDTO;
import com.aiflow.dto.StartProcessPreviewRequestDTO;

import java.util.List;

public interface ProcessInstanceService {

    List<ProcessInstanceDTO> listInstances(Long templateId, String status, String keyword);

    ProcessInstanceDTO createDraft(StartProcessPreviewRequestDTO request);

    FormSubmissionDTO saveNodeForm(SaveNodeFormRequestDTO request);

    ProcessInstanceDTO submitInstance(Long instanceId);

    ProcessInstanceDTO getInstanceDetail(Long instanceId);

    List<FormSubmissionDTO> listSubmissions(Long instanceId);
}
