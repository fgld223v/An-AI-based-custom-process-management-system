package com.aiflow.service;

import com.aiflow.model.ProcessInstance;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProcessInstanceService {

    ProcessInstance startProcess(Long templateId, Long applicantId, String title, String formData, Map<String, Object> variables);

    Optional<ProcessInstance> findById(Long id);

    ProcessInstance findByInstanceCode(String instanceCode);

    List<ProcessInstance> listMyProcesses(Long applicantId);

    List<ProcessInstance> listAllProcesses();

    List<ProcessInstance> listByStatus(String status);
}
