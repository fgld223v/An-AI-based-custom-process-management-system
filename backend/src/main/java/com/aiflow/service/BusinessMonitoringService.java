package com.aiflow.service;

import com.aiflow.dto.BusinessProcessInstanceDTO;
import com.aiflow.dto.FormSubmissionDTO;
import com.aiflow.dto.TimelineDTO;

import java.util.List;

public interface BusinessMonitoringService {

    List<BusinessProcessInstanceDTO> listOwnedProcessInstances(Long templateId, String status, String keyword);

    BusinessProcessInstanceDTO getOwnedProcessInstance(Long instanceId);

    TimelineDTO getOwnedTimeline(Long instanceId);

    List<FormSubmissionDTO> listOwnedSubmissions(Long instanceId);

    List<BusinessProcessInstanceDTO> listGlobalProcessInstances(Long templateId, String status, String keyword);

    BusinessProcessInstanceDTO getGlobalProcessInstance(Long instanceId);

    TimelineDTO getGlobalTimeline(Long instanceId);

    List<FormSubmissionDTO> listGlobalSubmissions(Long instanceId);
}
