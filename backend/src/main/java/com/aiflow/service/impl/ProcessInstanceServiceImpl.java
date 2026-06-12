package com.aiflow.service.impl;

import com.aiflow.dto.FormSubmissionDTO;
import com.aiflow.dto.ProcessInstanceDTO;
import com.aiflow.dto.SaveNodeFormRequest;
import com.aiflow.dto.StartProcessPreviewRequest;
import com.aiflow.model.FormSubmission;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.FormSubmissionRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.service.FlowableRuntimeService;
import com.aiflow.service.ProcessInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProcessInstanceServiceImpl implements ProcessInstanceService {

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_SUBMITTED = "submitted";
    private static final String STATUS_RUNNING = "running";
    private static final DateTimeFormatter CODE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final ProcessInstanceRepository processInstanceRepository;
    private final FormSubmissionRepository formSubmissionRepository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final FlowableRuntimeService flowableRuntimeService;

    @Override
    @Transactional(readOnly = true)
    public List<ProcessInstanceDTO> listInstances(Long templateId, String status, String keyword) {
        return processInstanceRepository.listInstances(templateId, normalize(status), normalize(keyword)).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessInstanceDTO getInstance(Long id) {
        return toDto(getRequiredInstance(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FormSubmissionDTO> listSubmissions(Long processInstanceId) {
        getRequiredInstance(processInstanceId);
        return formSubmissionRepository
                .findByProcessInstanceIdAndDeletedOrderByUpdatedAtDescCreatedAtDesc(processInstanceId, 0)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public ProcessInstanceDTO createDraft(StartProcessPreviewRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        requireId(request.getTemplateId(), "templateId must not be null");
        requireId(request.getFormId(), "formId must not be null");
        requireText(request.getInstanceTitle(), "instanceTitle must not be blank");
        requireText(request.getStartNodeKey(), "startNodeKey must not be blank");

        ProcessTemplate template = processTemplateRepository.findByIdAndDeleted(request.getTemplateId(), 0)
                .orElseThrow(() -> new IllegalArgumentException("process template not found"));

        LocalDateTime now = LocalDateTime.now();
        ProcessInstance instance = ProcessInstance.builder()
                .instanceCode("PI_" + now.format(CODE_TIME_FORMATTER))
                .templateId(template.getId())
                .formId(request.getFormId())
                .applicantId(1L)
                .bizTypeId(template.getBizTypeId())
                .title(request.getInstanceTitle().trim())
                .status(STATUS_DRAFT)
                .formData(request.getFormDataJson())
                .currentNodeKey(request.getStartNodeKey())
                .currentNodeName(request.getStartNodeName())
                .currentBusinessType(request.getBusinessType())
                .flowableDefinitionId(template.getFlowableProcessDefinitionId())
                .flowableDeploymentId(template.getFlowableDeploymentId())
                .createdAt(now)
                .updatedAt(now)
                .deleted(0)
                .build();
        ProcessInstance saved = processInstanceRepository.save(instance);

        saveSubmission(saved, template.getId(), request.getStartNodeKey(), request.getStartNodeName(),
                request.getBusinessType(), request.getFormId(), request.getFormDataJson(), STATUS_DRAFT, now);

        return toDto(saved);
    }

    @Override
    public FormSubmissionDTO saveNodeForm(SaveNodeFormRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        requireId(request.getProcessInstanceId(), "processInstanceId must not be null");
        requireId(request.getTemplateId(), "templateId must not be null");
        requireId(request.getFormId(), "formId must not be null");
        requireText(request.getNodeKey(), "nodeKey must not be blank");

        ProcessInstance instance = getRequiredInstance(request.getProcessInstanceId());
        if (STATUS_SUBMITTED.equals(instance.getStatus()) || STATUS_RUNNING.equals(instance.getStatus())) {
            throw new IllegalStateException("当前实例已提交或已启动流程，仅支持查看，不支持继续保存。");
        }
        if (!request.getTemplateId().equals(instance.getTemplateId())) {
            throw new IllegalArgumentException("templateId does not match current process instance");
        }

        LocalDateTime now = LocalDateTime.now();
        FormSubmission submission = saveSubmission(instance, request.getTemplateId(), request.getNodeKey(),
                request.getNodeName(), request.getBusinessType(), request.getFormId(), request.getFormDataJson(),
                normalizeStatus(request.getStatus(), STATUS_DRAFT), now);

        instance.setFormId(request.getFormId());
        instance.setFormData(request.getFormDataJson());
        instance.setCurrentNodeKey(request.getNodeKey());
        instance.setCurrentNodeName(request.getNodeName());
        instance.setCurrentBusinessType(request.getBusinessType());
        instance.setUpdatedAt(now);
        processInstanceRepository.save(instance);

        return toDto(submission);
    }

    @Override
    public ProcessInstanceDTO submitInstance(Long id) {
        ProcessInstance instance = getRequiredInstance(id);
        if (STATUS_RUNNING.equals(instance.getStatus())) {
            throw new IllegalStateException("当前实例已启动流程引擎，不能重复提交。");
        }
        if (hasText(instance.getFlowableProcessInstanceId())) {
            throw new IllegalStateException("当前实例已关联 Flowable 流程实例，不能重复启动。");
        }
        if (!STATUS_DRAFT.equals(instance.getStatus()) && !STATUS_SUBMITTED.equals(instance.getStatus())) {
            throw new IllegalStateException("当前实例状态不允许提交并启动流程。");
        }

        LocalDateTime now = LocalDateTime.now();
        ProcessInstance started = flowableRuntimeService.startProcessInstance(instance);
        started.setUpdatedAt(now);
        ProcessInstance saved = processInstanceRepository.save(started);

        List<FormSubmission> submissions = formSubmissionRepository
                .findByProcessInstanceIdAndDeletedOrderByUpdatedAtDescCreatedAtDesc(id, 0);
        for (FormSubmission submission : submissions) {
            submission.setStatus(STATUS_SUBMITTED);
            submission.setUpdatedAt(now);
        }
        formSubmissionRepository.saveAll(submissions);

        return toDto(saved);
    }

    private FormSubmission saveSubmission(ProcessInstance instance,
                                          Long templateId,
                                          String nodeKey,
                                          String nodeName,
                                          String businessType,
                                          Long formId,
                                          String formDataJson,
                                          String status,
                                          LocalDateTime now) {
        FormSubmission submission = formSubmissionRepository
                .findByProcessInstanceIdAndNodeKeyAndDeleted(instance.getId(), nodeKey, 0)
                .orElseGet(() -> FormSubmission.builder()
                        .processInstanceId(instance.getId())
                        .templateId(templateId)
                        .nodeKey(nodeKey)
                        .createdAt(now)
                        .deleted(0)
                        .build());

        submission.setTemplateId(templateId);
        submission.setNodeName(nodeName);
        submission.setBusinessType(businessType);
        submission.setFormId(formId);
        submission.setFormDataJson(formDataJson);
        submission.setStatus(status);
        submission.setUpdatedAt(now);
        return formSubmissionRepository.save(submission);
    }

    private ProcessInstance getRequiredInstance(Long id) {
        requireId(id, "id must not be null");
        return processInstanceRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new IllegalArgumentException("process instance not found"));
    }

    private ProcessInstanceDTO toDto(ProcessInstance entity) {
        return ProcessInstanceDTO.builder()
                .id(entity.getId())
                .templateId(entity.getTemplateId())
                .instanceCode(entity.getInstanceCode())
                .instanceTitle(entity.getTitle())
                .status(entity.getStatus())
                .currentNodeKey(entity.getCurrentNodeKey())
                .currentNodeName(entity.getCurrentNodeName())
                .currentBusinessType(entity.getCurrentBusinessType())
                .flowableProcessInstanceId(entity.getFlowableProcessInstanceId())
                .flowableDefinitionId(entity.getFlowableDefinitionId())
                .flowableDeploymentId(entity.getFlowableDeploymentId())
                .createTime(entity.getCreatedAt())
                .updateTime(entity.getUpdatedAt())
                .build();
    }

    private FormSubmissionDTO toDto(FormSubmission entity) {
        return FormSubmissionDTO.builder()
                .id(entity.getId())
                .processInstanceId(entity.getProcessInstanceId())
                .templateId(entity.getTemplateId())
                .nodeKey(entity.getNodeKey())
                .nodeName(entity.getNodeName())
                .businessType(entity.getBusinessType())
                .formId(entity.getFormId())
                .formDataJson(entity.getFormDataJson())
                .status(entity.getStatus())
                .createTime(entity.getCreatedAt())
                .updateTime(entity.getUpdatedAt())
                .build();
    }

    private String normalizeStatus(String value, String defaultValue) {
        String normalized = normalize(value);
        return normalized == null ? defaultValue : normalized;
    }

    private String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private void requireId(Long id, String message) {
        if (id == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}