package com.aiflow.service.impl;

import com.aiflow.enums.TemplateStatus;
import com.aiflow.model.FormSubmission;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.FormSubmissionRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.service.FlowableRuntimeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FlowableRuntimeServiceImpl implements FlowableRuntimeService {

    private final RuntimeService runtimeService;
    private final ProcessTemplateRepository processTemplateRepository;
    private final FormSubmissionRepository formSubmissionRepository;
    private final ObjectMapper objectMapper;

    @Override
    public ProcessInstance startProcessInstance(ProcessInstance instance) {
        if (instance == null || instance.getId() == null) {
            throw new IllegalArgumentException("流程实例不存在。");
        }

        ProcessTemplate template = processTemplateRepository.findByIdAndDeleted(instance.getTemplateId(), 0)
                .orElseThrow(() -> new IllegalArgumentException("流程模板不存在。"));
        if (template.getStatus() != TemplateStatus.PUBLISHED) {
            throw new IllegalStateException("当前流程模板未发布，不能启动流程实例。");
        }
        if (!hasText(template.getFlowableProcessDefinitionId()) || !hasText(template.getFlowableDeploymentId())) {
            throw new IllegalStateException("当前流程模板尚未部署到 Flowable，请先发布模板。");
        }

        List<FormSubmission> submissions = formSubmissionRepository
                .findByProcessInstanceIdAndDeletedOrderByUpdatedAtDescCreatedAtDesc(instance.getId(), 0);
        if (submissions.isEmpty()) {
            throw new IllegalStateException("当前流程实例没有表单提交数据，无法启动流程。");
        }

        Map<String, Object> variables = buildVariables(instance, submissions);
        try {
            org.flowable.engine.runtime.ProcessInstance flowableInstance = runtimeService.startProcessInstanceById(
                    template.getFlowableProcessDefinitionId(), String.valueOf(instance.getId()), variables);
            instance.setFlowableProcessInstanceId(flowableInstance.getId());
            instance.setFlowableDefinitionId(template.getFlowableProcessDefinitionId());
            instance.setFlowableDeploymentId(template.getFlowableDeploymentId());
            instance.setStatus("running");
            return instance;
        } catch (FlowableException ex) {
            throw new IllegalStateException("Flowable 流程实例启动失败：" + safeMessage(ex), ex);
        }
    }

    private Map<String, Object> buildVariables(ProcessInstance instance, List<FormSubmission> submissions) {
        Map<String, Object> variables = new HashMap<>();
        Map<String, Object> allFormData = new LinkedHashMap<>();
        Map<String, Object> startFormData = null;

        for (FormSubmission submission : submissions) {
            Map<String, Object> data = parseFormData(submission);
            allFormData.put(submission.getNodeKey(), data);
            if (startFormData == null && "start".equalsIgnoreCase(submission.getBusinessType())) {
                startFormData = data;
            }
        }
        if (startFormData == null && !submissions.isEmpty()) {
            startFormData = parseFormData(submissions.get(submissions.size() - 1));
        }

        variables.put("businessInstanceId", instance.getId());
        variables.put("templateId", instance.getTemplateId());
        variables.put("instanceCode", instance.getInstanceCode());
        variables.put("instanceTitle", instance.getTitle());
        variables.put("allFormData", allFormData);
        variables.put("startFormData", startFormData == null ? Map.of() : startFormData);
        return variables;
    }

    private Map<String, Object> parseFormData(FormSubmission submission) {
        if (!hasText(submission.getFormDataJson())) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(submission.getFormDataJson(), new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception ex) {
            String nodeName = hasText(submission.getNodeName()) ? submission.getNodeName() : submission.getNodeKey();
            throw new IllegalStateException("节点【" + nodeName + "】表单数据 JSON 解析失败，无法启动流程。", ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safeMessage(Exception ex) {
        return hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}