package com.aiflow.service;

import com.aiflow.model.ProcessInstance;

public interface FlowableRuntimeService {

    ProcessInstance startProcessInstance(ProcessInstance instance);
}