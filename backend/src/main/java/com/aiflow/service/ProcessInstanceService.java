package com.aiflow.service;

import com.aiflow.dto.FormSubmissionDTO;
import com.aiflow.dto.ProcessInstanceDTO;
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
}