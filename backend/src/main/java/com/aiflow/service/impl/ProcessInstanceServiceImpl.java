package com.aiflow.service.impl;

import com.aiflow.dto.FormSubmissionDTO;
import com.aiflow.dto.ProcessInstanceDTO;
import com.aiflow.dto.SaveNodeFormRequestDTO;
import com.aiflow.dto.StartProcessPreviewRequestDTO;
import com.aiflow.enums.FormStatus;
import com.aiflow.model.FormDefinition;
import com.aiflow.model.FormSubmission;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.FormDefinitionRepository;
import com.aiflow.repository.FormSubmissionRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
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
    private static final DateTimeFormatter CODE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final ProcessInstanceRepository processInstanceRepository;
    private final FormSubmissionRepository formSubmissionRepository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final FormDefinitionRepository formDefinitionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProcessInstanceDTO> listInstances(Long templateId, String status, String keyword) {
        String normalizedStatus = normalizeInstanceStatusFilter(status);
        String normalizedKeyword = normalizeText(keyword);
        return processInstanceRepository.searchInstances(templateId, normalizedStatus, normalizedKeyword).stream()
                .map(this::toProcessInstanceDTO)
                .toList();
    }
    @Override
    public ProcessInstanceDTO createDraft(StartProcessPreviewRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        ProcessTemplate template = getRequiredTemplate(request.getTemplateId());
        Long formId = request.getFormId();
        if (formId != null) {
            getPublishedForm(formId);
        }

        LocalDateTime now = LocalDateTime.now();
        ProcessInstance instance = ProcessInstance.builder()
                .templateId(template.getId())
                .instanceCode("PI_" + now.format(CODE_TIME_FORMATTER))
                .instanceTitle(hasText(request.getInstanceTitle()) ? request.getInstanceTitle().trim() : template.getTemplateName() + "-" + now.toLocalDate())
                .status(STATUS_DRAFT)
                .formId(formId)
                .applicantId(0L)
                .bizTypeId(template.getBizTypeId())
                .currentNodeKey(normalizeText(request.getStartNodeKey()))
                .currentNodeName(normalizeText(request.getStartNodeName()))
                .currentBusinessType(normalizeText(request.getBusinessType()))
                .formDataJson(normalizeText(request.getFormDataJson()))
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        ProcessInstance saved = processInstanceRepository.save(instance);

        if (hasText(request.getFormDataJson()) && formId != null && hasText(request.getStartNodeKey())) {
            saveSubmission(saved.getId(), template.getId(), request.getStartNodeKey(), request.getStartNodeName(), request.getBusinessType(), formId, request.getFormDataJson(), STATUS_DRAFT, now);
        }

        return toProcessInstanceDTO(saved);
    }

    @Override
    public FormSubmissionDTO saveNodeForm(SaveNodeFormRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        ProcessInstance instance = getRequiredInstance(request.getProcessInstanceId());
        ProcessTemplate template = getRequiredTemplate(request.getTemplateId());
        if (!template.getId().equals(instance.getTemplateId())) {
            throw new IllegalArgumentException("流程模板与流程实例不匹配");
        }
        getPublishedForm(request.getFormId());
        requireText(request.getNodeKey(), "节点ID不能为空");
        requireText(request.getFormDataJson(), "表单数据不能为空");
        if (!STATUS_DRAFT.equals(instance.getStatus())) {
            throw new IllegalStateException("当前实例已提交，暂不可继续编辑。");
        }
        String status = normalizeSubmissionStatus(request.getStatus());

        LocalDateTime now = LocalDateTime.now();
        FormSubmission saved = saveSubmission(instance.getId(), template.getId(), request.getNodeKey(), request.getNodeName(), request.getBusinessType(), request.getFormId(), request.getFormDataJson(), status, now);

        instance.setCurrentNodeKey(normalizeText(request.getNodeKey()));
        instance.setCurrentNodeName(normalizeText(request.getNodeName()));
        instance.setCurrentBusinessType(normalizeText(request.getBusinessType()));
        instance.setFormId(request.getFormId());
        instance.setFormDataJson(normalizeText(request.getFormDataJson()));
        instance.setUpdateTime(now);
        processInstanceRepository.save(instance);

        return toFormSubmissionDTO(saved);
    }

    @Override
    public ProcessInstanceDTO submitInstance(Long instanceId) {
        ProcessInstance instance = getRequiredInstance(instanceId);
        if (!STATUS_DRAFT.equals(instance.getStatus())) {
            throw new IllegalStateException(STATUS_SUBMITTED.equals(instance.getStatus()) ? "当前实例已提交，不能重复提交。" : "只有草稿实例可以提交。");
        }
LocalDateTime now = LocalDateTime.now();
        instance.setStatus(STATUS_SUBMITTED);
        instance.setUpdateTime(now);
        formSubmissionRepository.findByProcessInstanceIdAndDeleted(instance.getId(), 0).forEach(submission -> {
            submission.setStatus(STATUS_SUBMITTED);
            submission.setUpdateTime(now);
            formSubmissionRepository.save(submission);
        });
        return toProcessInstanceDTO(processInstanceRepository.save(instance));
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessInstanceDTO getInstanceDetail(Long instanceId) {
        return toProcessInstanceDTO(getRequiredInstance(instanceId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FormSubmissionDTO> listSubmissions(Long instanceId) {
        getRequiredInstance(instanceId);
        return formSubmissionRepository.findByProcessInstanceIdAndDeletedOrderByUpdateTimeDescCreateTimeDesc(instanceId, 0).stream()
                .map(this::toFormSubmissionDTO)
                .toList();
    }

    private FormSubmission saveSubmission(Long instanceId,
                                          Long templateId,
                                          String nodeKey,
                                          String nodeName,
                                          String businessType,
                                          Long formId,
                                          String formDataJson,
                                          String status,
                                          LocalDateTime now) {
        String normalizedNodeKey = normalizeText(nodeKey);
        FormSubmission submission = formSubmissionRepository
                .findByProcessInstanceIdAndNodeKeyAndDeleted(instanceId, normalizedNodeKey, 0)
                .orElseGet(() -> FormSubmission.builder()
                        .processInstanceId(instanceId)
                        .templateId(templateId)
                        .nodeKey(normalizedNodeKey)
                        .createTime(now)
                        .deleted(0)
                        .build());

        submission.setTemplateId(templateId);
        submission.setNodeName(normalizeText(nodeName));
        submission.setBusinessType(normalizeText(businessType));
        submission.setFormId(formId);
        submission.setFormDataJson(normalizeText(formDataJson));
        submission.setStatus(status);
        submission.setUpdateTime(now);
        return formSubmissionRepository.save(submission);
    }

    private ProcessTemplate getRequiredTemplate(Long templateId) {
        if (templateId == null) {
            throw new IllegalArgumentException("流程模板ID不能为空");
        }
        return processTemplateRepository.findByIdAndDeleted(templateId, 0)
                .orElseThrow(() -> new IllegalArgumentException("流程模板不存在。"));
    }

    private ProcessInstance getRequiredInstance(Long instanceId) {
        if (instanceId == null) {
            throw new IllegalArgumentException("流程实例ID不能为空");
        }
        return processInstanceRepository.findByIdAndDeleted(instanceId, 0)
                .orElseThrow(() -> new IllegalArgumentException("流程实例不存在。"));
    }

    private FormDefinition getPublishedForm(Long formId) {
        if (formId == null) {
            throw new IllegalArgumentException("表单ID不能为空");
        }
        FormDefinition form = formDefinitionRepository.findByIdAndDeleted(formId, 0)
                .orElseThrow(() -> new IllegalArgumentException("表单不存在。"));
        if (form.getStatus() != FormStatus.PUBLISHED) {
            throw new IllegalStateException("表单必须先发布后才能绑定或提交。");
        }
        return form;
    }

    private String normalizeInstanceStatusFilter(String status) {
        if (!hasText(status)) {
            return null;
        }
        String value = status.trim().toLowerCase();
        if (!STATUS_DRAFT.equals(value) && !STATUS_SUBMITTED.equals(value)) {
            throw new IllegalArgumentException("状态只能是 draft 或 submitted。");
        }
        return value;
    }
    private String normalizeSubmissionStatus(String status) {
        if (!hasText(status)) {
            return STATUS_DRAFT;
        }
        String value = status.trim().toLowerCase();
        if (!STATUS_DRAFT.equals(value) && !STATUS_SUBMITTED.equals(value)) {
            throw new IllegalArgumentException("状态只能是 draft 或 submitted。");
        }
        return value;
    }

    private void requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalizeText(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private ProcessInstanceDTO toProcessInstanceDTO(ProcessInstance entity) {
        if (entity == null) return null;
        return ProcessInstanceDTO.builder()
                .id(entity.getId())
                .templateId(entity.getTemplateId())
                .instanceCode(entity.getInstanceCode())
                .instanceTitle(entity.getInstanceTitle())
                .status(entity.getStatus())
                .currentNodeKey(entity.getCurrentNodeKey())
                .currentNodeName(entity.getCurrentNodeName())
                .currentBusinessType(entity.getCurrentBusinessType())
                .flowableProcessInstanceId(entity.getFlowableProcessInstanceId())
                .flowableDefinitionId(entity.getFlowableDefinitionId())
                .flowableDeploymentId(entity.getFlowableDeploymentId())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    private FormSubmissionDTO toFormSubmissionDTO(FormSubmission entity) {
        if (entity == null) return null;
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
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
