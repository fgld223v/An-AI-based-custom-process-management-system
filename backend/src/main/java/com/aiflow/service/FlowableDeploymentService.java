package com.aiflow.service;

import com.aiflow.model.ProcessTemplate;

public interface FlowableDeploymentService {

    ProcessTemplate deployProcessTemplate(ProcessTemplate template);
}