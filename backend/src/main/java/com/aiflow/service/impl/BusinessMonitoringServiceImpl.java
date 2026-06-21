package com.aiflow.service.impl;

import com.aiflow.dto.BusinessProcessInstanceDTO;
import com.aiflow.dto.FormSubmissionDTO;
import com.aiflow.dto.TimelineDTO;
import com.aiflow.enums.ProcessResourceType;
import com.aiflow.model.FormSubmission;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.model.SysUser;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.repository.FormSubmissionRepository;
import com.aiflow.security.CurrentUser;
import com.aiflow.security.SecurityUtils;
import com.aiflow.service.BusinessMonitoringService;
import com.aiflow.service.InstanceAnomalyService;
import com.aiflow.service.ProcessTimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusinessMonitoringServiceImpl implements BusinessMonitoringService {

    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final SysUserRepository sysUserRepository;
    private final FormSubmissionRepository formSubmissionRepository;
    private final ProcessTimelineService processTimelineService;
    private final InstanceAnomalyService instanceAnomalyService;

    @Override
    public List<BusinessProcessInstanceDTO> listOwnedProcessInstances(Long templateId,
                                                                      String status,
                                                                      String keyword) {
        CurrentUser currentUser = requireBusinessAdministrator();
        boolean anomalyOnly = isAnomalyFilter(status);
        List<ProcessInstance> instances = processInstanceRepository.listInstancesOwnedByTemplateCreator(
                currentUser.getId(),
                ProcessResourceType.BUSINESS_PROCESS,
                templateId,
                anomalyOnly ? null : normalize(status),
                normalize(keyword));
        return filterAnomalies(toDtos(instances), anomalyOnly);
    }

    @Override
    public BusinessProcessInstanceDTO getOwnedProcessInstance(Long instanceId) {
        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId must not be null");
        }
        CurrentUser currentUser = requireBusinessAdministrator();
        ProcessInstance instance = processInstanceRepository.findOwnedInstance(
                        instanceId, currentUser.getId(), ProcessResourceType.BUSINESS_PROCESS)
                .orElseThrow(() -> new AccessDeniedException("no permission to monitor this process instance"));
        return toDtos(List.of(instance)).get(0);
    }

    @Override
    public TimelineDTO getOwnedTimeline(Long instanceId) {
        getOwnedProcessInstanceEntity(instanceId);
        return processTimelineService.buildTimeline(instanceId);
    }

    @Override
    public List<FormSubmissionDTO> listOwnedSubmissions(Long instanceId) {
        getOwnedProcessInstanceEntity(instanceId);
        return listSubmissions(instanceId);
    }

    @Override
    public List<BusinessProcessInstanceDTO> listGlobalProcessInstances(Long templateId,
                                                                       String status,
                                                                       String keyword) {
        requireSuperAdministrator();
        boolean anomalyOnly = isAnomalyFilter(status);
        List<ProcessInstance> instances = processInstanceRepository.listGlobalProcessInstances(
                templateId, anomalyOnly ? null : normalize(status), normalize(keyword));
        return filterAnomalies(toDtos(instances), anomalyOnly);
    }

    @Override
    public BusinessProcessInstanceDTO getGlobalProcessInstance(Long instanceId) {
        return toDtos(List.of(getGlobalProcessInstanceEntity(instanceId))).get(0);
    }

    @Override
    public TimelineDTO getGlobalTimeline(Long instanceId) {
        getGlobalProcessInstanceEntity(instanceId);
        return processTimelineService.buildTimeline(instanceId);
    }

    @Override
    public List<FormSubmissionDTO> listGlobalSubmissions(Long instanceId) {
        getGlobalProcessInstanceEntity(instanceId);
        return listSubmissions(instanceId);
    }

    private List<BusinessProcessInstanceDTO> toDtos(List<ProcessInstance> instances) {
        if (instances.isEmpty()) {
            return List.of();
        }
        Map<Long, ProcessTemplate> templates = processTemplateRepository.findAllById(distinctIds(
                        instances.stream().map(ProcessInstance::getTemplateId).toList()))
                .stream()
                .collect(Collectors.toMap(ProcessTemplate::getId, Function.identity()));
        Set<Long> userIds = new java.util.LinkedHashSet<>();
        userIds.addAll(distinctIds(instances.stream().map(ProcessInstance::getApplicantId).toList()));
        userIds.addAll(distinctIds(templates.values().stream().map(ProcessTemplate::getCreatedBy).toList()));
        Map<Long, SysUser> users = sysUserRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, String> anomalies = instanceAnomalyService.findAnomalies(
                instances.stream().map(ProcessInstance::getId).toList());

        return instances.stream()
                .map(instance -> {
                    ProcessTemplate template = templates.get(instance.getTemplateId());
                    return toDto(instance, template, users.get(instance.getApplicantId()),
                            template == null ? null : users.get(template.getCreatedBy()), anomalies.get(instance.getId()));
                })
                .toList();
    }

    private BusinessProcessInstanceDTO toDto(ProcessInstance instance,
                                               ProcessTemplate template,
                                               SysUser applicant,
                                               SysUser owner,
                                               String anomalyReason) {
        return BusinessProcessInstanceDTO.builder()
                .id(instance.getId())
                .instanceCode(instance.getInstanceCode())
                .instanceTitle(instance.getTitle())
                .status(instance.getStatus())
                .anomaly(hasText(anomalyReason) || isAnomalyStatus(instance.getStatus()))
                .anomalyReason(hasText(anomalyReason) ? anomalyReason : anomalyStatusReason(instance.getStatus()))
                .templateId(instance.getTemplateId())
                .templateCode(template == null ? null : template.getTemplateCode())
                .templateName(template == null ? null : template.getTemplateName())
                .templateVersion(template == null ? null : template.getVersion())
                .templateStatus(template == null || template.getStatus() == null ? null : template.getStatus().getValue())
                .processOwnerId(template == null ? null : template.getCreatedBy())
                .processOwnerName(resolveUserName(owner, template == null ? null : template.getCreatedBy()))
                .applicantId(instance.getApplicantId())
                .applicantUsername(applicant == null ? null : applicant.getUsername())
                .applicantName(resolveApplicantName(applicant, instance.getApplicantId()))
                .applicantDepartmentId(applicant == null ? null : applicant.getDepartmentId())
                .bizTypeId(instance.getBizTypeId())
                .formId(instance.getFormId())
                .currentNodeKey(instance.getCurrentNodeKey())
                .currentNodeName(instance.getCurrentNodeName())
                .currentBusinessType(instance.getCurrentBusinessType())
                .flowableProcessInstanceId(instance.getFlowableProcessInstanceId())
                .startedAt(instance.getStartedAt())
                .endedAt(instance.getEndedAt())
                .createdAt(instance.getCreatedAt())
                .updatedAt(instance.getUpdatedAt())
                .build();
    }

    private ProcessInstance getOwnedProcessInstanceEntity(Long instanceId) {
        if (instanceId == null) throw new IllegalArgumentException("instanceId must not be null");
        CurrentUser currentUser = requireBusinessAdministrator();
        return processInstanceRepository.findOwnedInstance(
                        instanceId, currentUser.getId(), ProcessResourceType.BUSINESS_PROCESS)
                .orElseThrow(() -> new AccessDeniedException("no permission to monitor this process instance"));
    }

    private ProcessInstance getGlobalProcessInstanceEntity(Long instanceId) {
        if (instanceId == null) throw new IllegalArgumentException("instanceId must not be null");
        requireSuperAdministrator();
        return processInstanceRepository.findGlobalProcessInstance(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("process instance not found"));
    }

    private List<FormSubmissionDTO> listSubmissions(Long instanceId) {
        return formSubmissionRepository
                .findByProcessInstanceIdAndDeletedOrderByUpdatedAtDescCreatedAtDesc(instanceId, 0)
                .stream()
                .map(this::toSubmissionDto)
                .toList();
    }

    private FormSubmissionDTO toSubmissionDto(FormSubmission submission) {
        return FormSubmissionDTO.builder()
                .id(submission.getId())
                .processInstanceId(submission.getProcessInstanceId())
                .templateId(submission.getTemplateId())
                .nodeKey(submission.getNodeKey())
                .nodeName(submission.getNodeName())
                .businessType(submission.getBusinessType())
                .formId(submission.getFormId())
                .formDataJson(submission.getFormDataJson())
                .status(submission.getStatus())
                .createTime(submission.getCreatedAt())
                .updateTime(submission.getUpdatedAt())
                .build();
    }

    private CurrentUser requireBusinessAdministrator() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("current user is required");
        }
        String systemRole = currentUser.getSystemRole();
        if (!"biz_admin".equals(systemRole) && !"super_admin".equals(systemRole)) {
            throw new AccessDeniedException("business administrator role is required");
        }
        return currentUser;
    }

    private CurrentUser requireSuperAdministrator() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        if (currentUser == null || !"super_admin".equals(currentUser.getSystemRole())) {
            throw new AccessDeniedException("super administrator role is required");
        }
        return currentUser;
    }

    private Collection<Long> distinctIds(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private String resolveApplicantName(SysUser applicant, Long applicantId) {
        return resolveUserName(applicant, applicantId);
    }

    private String resolveUserName(SysUser user, Long userId) {
        if (user == null) {
            return userId == null ? "未知用户" : "用户#" + userId;
        }
        if (hasText(user.getNickname())) {
            return user.getNickname().trim();
        }
        return hasText(user.getUsername()) ? user.getUsername().trim() : "用户#" + userId;
    }

    private List<BusinessProcessInstanceDTO> filterAnomalies(List<BusinessProcessInstanceDTO> instances,
                                                               boolean anomalyOnly) {
        return anomalyOnly ? instances.stream().filter(item -> Boolean.TRUE.equals(item.getAnomaly())).toList() : instances;
    }

    private boolean isAnomalyFilter(String status) {
        return "anomaly".equalsIgnoreCase(normalize(status)) || "abnormal".equalsIgnoreCase(normalize(status));
    }

    private boolean isAnomalyStatus(String status) {
        return status != null && Set.of("timeout", "failed", "terminated", "rejected").contains(status.toLowerCase());
    }

    private String anomalyStatusReason(String status) {
        return isAnomalyStatus(status) ? "实例状态异常" : null;
    }

    private String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
