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

/**
 * 业务流程监控服务实现 — 提供业务管理员和超级管理员的流程实例监控能力。
 *
 * <p>两种权限视角：</p>
 * <ul>
 *   <li><b>业务管理员 (biz_admin)</b> — 仅查看自己创建的 BUSINESS_PROCESS 类型模板
 *       所关联的流程实例（owned 系列方法）</li>
 *   <li><b>超级管理员 (super_admin)</b> — 查看系统中所有流程实例（global 系列方法）</li>
 * </ul>
 *
 * <p>异常筛选：支持按 status=anomaly/abnormal 过滤异常实例。
 * 异常包括：超时、失败、已终止、已驳回状态的实例，
 * 以及瓶颈预测标记为异常（anomaly_reason 非空）的实例。</p>
 *
 * <p>所有查询方法均标记为只读事务。</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusinessMonitoringServiceImpl implements BusinessMonitoringService {

    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final SysUserRepository sysUserRepository;
    private final FormSubmissionRepository formSubmissionRepository;
    private final ProcessTimelineService processTimelineService;    // 时间线构建器
    private final InstanceAnomalyService instanceAnomalyService;    // 异常检测服务

    /**
     * 查询业务管理员自己创建的模板关联的流程实例列表。
     *
     * <p>支持按模板、状态、关键字筛选。状态为 anomaly/abnormal 时仅返回异常实例。</p>
     *
     * @param templateId 模板 ID（可选）
     * @param status     状态筛选（可选，支持 anomaly/abnormal 异常筛选）
     * @param keyword    关键字搜索（可选）
     * @return 流程实例 DTO 列表（含模板信息、发起人信息、异常标记）
     */
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

    /**
     * 批量转换 ProcessInstance → BusinessProcessInstanceDTO。
     *
     * <p>批量加载关联数据以减少 N+1 查询：</p>
     * <ol>
     *   <li>批量加载模板（按 templateId）</li>
     *   <li>批量加载用户（发起人 + 模板创建者）</li>
     *   <li>批量加载异常标记（按 instanceId）</li>
     * </ol>
     */
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
