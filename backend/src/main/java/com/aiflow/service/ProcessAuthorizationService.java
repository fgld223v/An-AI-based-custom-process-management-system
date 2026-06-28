package com.aiflow.service;

import com.aiflow.enums.ProcessResourceType;
import com.aiflow.enums.TemplateStatus;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.model.SysUser;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.security.CurrentUser;
import com.aiflow.security.SecurityUtils;
import com.aiflow.service.impl.NodeConfigParser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 流程权限校验服务，负责对流程模板的发布、版本管理、部署及发起等操作进行权限断言。
 * 校验维度包括：用户角色（超级管理员/业务管理员）、业务类型管理范围、部门归属等。
 */
@Service
@RequiredArgsConstructor
public class ProcessAuthorizationService {

    private static final String SUPER_ADMIN = "super_admin";
    private static final String BIZ_ADMIN = "biz_admin";

    private final NodeConfigParser nodeConfigParser;
    private final SysUserRepository sysUserRepository;
    private final WorkflowRoleService workflowRoleService;

    public void assertCanPublish(ProcessTemplate template) {
        assertCanCreateVersion(template);
        if (template.getResourceType() == ProcessResourceType.SYSTEM_TEMPLATE) {
            return;
        }
        CurrentUser user = requireCurrentUser();
        if (template.getBizTypeId() == null) {
            throw new IllegalStateException("business process must select a business type before publishing");
        }
        assertManagedBizType(user, template.getBizTypeId());
        validateStartPermissionConfiguration(template);
    }

    public void assertCanCreateVersion(ProcessTemplate template) {
        requireTemplate(template);
        CurrentUser user = requireCurrentUser();

        if (template.getResourceType() == ProcessResourceType.SYSTEM_TEMPLATE) {
            if (!SUPER_ADMIN.equals(user.getSystemRole())) {
                throw new AccessDeniedException("only super admin can publish system templates");
            }
            return;
        }

        if (template.getResourceType() != ProcessResourceType.BUSINESS_PROCESS) {
            throw new IllegalStateException("process resource type is missing or unsupported");
        }
        if (!BIZ_ADMIN.equals(user.getSystemRole()) && !SUPER_ADMIN.equals(user.getSystemRole())) {
            throw new AccessDeniedException("only business admin can publish business processes");
        }
        if (template.getCreatedBy() == null || !template.getCreatedBy().equals(user.getId())) {
            throw new AccessDeniedException("only the process owner can manage this business process version");
        }
        if (template.getBizTypeId() != null) {
            assertManagedBizType(user, template.getBizTypeId());
        }
    }

    public void assertCanDeploy(ProcessTemplate template) {
        requireTemplate(template);
        assertCanPublish(template);
        if (template.getResourceType() == null) {
            throw new IllegalStateException("process resource type is required before deployment");
        }
        if (template.getStatus() != TemplateStatus.DRAFT && template.getStatus() != TemplateStatus.REVIEWING) {
            throw new IllegalStateException("only draft or reviewing resources can be deployed");
        }
        if (!hasText(template.getTemplateCode()) || !hasText(template.getTemplateName())) {
            throw new IllegalStateException("process code and name are required before deployment");
        }
        if (!hasText(template.getBpmnXml())) {
            throw new IllegalStateException("BPMN XML is required before deployment");
        }
        boolean hasDeployment = hasText(template.getFlowableDeploymentId());
        boolean hasDefinition = hasText(template.getFlowableProcessDefinitionId());
        if (hasDeployment || hasDefinition) {
            throw new IllegalStateException("process resource already contains Flowable deployment information");
        }
    }

    public boolean canCurrentUserStart(ProcessTemplate template) {
        try {
            assertCanStart(template);
            return true;
        } catch (AccessDeniedException | IllegalStateException | IllegalArgumentException ex) {
            return false;
        }
    }

    public void assertCanStart(ProcessTemplate template) {
        requireTemplate(template);
        CurrentUser user = requireCurrentUser();
        assertPublishedBusinessProcess(template);

        Map<String, Object> startConfig = findStartConfig(template);
        if (startConfig == null) {
            return;
        }

        String startMode = upper(stringValue(startConfig.get("startMode"), "MANUAL"));
        if ("TIMER".equals(startMode)) {
            throw new AccessDeniedException("timer-triggered process cannot be started manually");
        }

        String permission = upper(stringValue(startConfig.get("startPermission"), "ALL"));
        if ("ALL".equals(permission)) {
            return;
        }

        if ("CREATOR_DEPARTMENT".equals(permission)) {
            Long creatorDepartmentId = requireCreatorDepartmentId(template);
            if (creatorDepartmentId.equals(user.getDepartmentId())) {
                return;
            }
            throw new AccessDeniedException("current department is not allowed to start this process");
        }

        Set<String> allowedValues = permissionValues(startConfig);
        if (allowedValues.isEmpty()) {
            throw new AccessDeniedException("process start permission is not configured");
        }
        if ("ROLE".equals(permission) || "SYSTEM_ROLE".equals(permission)) {
            if (containsIgnoreCase(allowedValues, user.getSystemRole())
                    || containsIgnoreCase(allowedValues, user.getRole())) {
                return;
            }
            throw new AccessDeniedException("current role is not allowed to start this process");
        }
        if ("DEPARTMENT".equals(permission)) {
            if (user.getDepartmentId() != null && allowedValues.contains(String.valueOf(user.getDepartmentId()))) {
                return;
            }
            throw new AccessDeniedException("current department is not allowed to start this process");
        }
        if ("WORKFLOW_ROLE".equals(permission)) {
            boolean allowed = allowedValues.stream()
                    .anyMatch(roleCode -> workflowRoleService
                            .resolveActiveUserIds(roleCode, user.getDepartmentId())
                            .contains(user.getId()));
            if (allowed) {
                return;
            }
            throw new AccessDeniedException("current workflow role is not allowed to start this process");
        }
        throw new AccessDeniedException("unsupported process start permission: " + permission);
    }

    public void assertPublishedBusinessProcess(ProcessTemplate template) {
        if (template.getResourceType() != ProcessResourceType.BUSINESS_PROCESS) {
            throw new IllegalStateException("only business processes can be started");
        }
        if (template.getStatus() != TemplateStatus.PUBLISHED) {
            throw new IllegalStateException("business process is not published");
        }
        if (!hasText(template.getFlowableDeploymentId()) || !hasText(template.getFlowableProcessDefinitionId())) {
            throw new IllegalStateException("business process is not deployed to Flowable");
        }
    }

    private void validateStartPermissionConfiguration(ProcessTemplate template) {
        Map<String, Object> startConfig = findStartConfig(template);
        if (startConfig == null) {
            return;
        }
        String permission = upper(stringValue(startConfig.get("startPermission"), "ALL"));
        if (!List.of("ALL", "ROLE", "SYSTEM_ROLE", "DEPARTMENT", "CREATOR_DEPARTMENT", "WORKFLOW_ROLE")
                .contains(permission)) {
            throw new IllegalStateException("unsupported process start permission: " + permission);
        }
        if ("CREATOR_DEPARTMENT".equals(permission)) {
            requireCreatorDepartmentId(template);
            return;
        }
        if (!"ALL".equals(permission) && permissionValues(startConfig).isEmpty()) {
            throw new IllegalStateException("startPermissionValue is required for " + permission + " permission");
        }
    }

    private Long requireCreatorDepartmentId(ProcessTemplate template) {
        SysUser creator = template.getCreatedBy() == null
                ? null
                : sysUserRepository.findById(template.getCreatedBy()).orElse(null);
        if (creator == null
                || Integer.valueOf(1).equals(creator.getDeleted())
                || !Integer.valueOf(1).equals(creator.getEnabled())
                || creator.getDepartmentId() == null) {
            throw new IllegalStateException("process creator must belong to an active department");
        }
        return creator.getDepartmentId();
    }

    private Map<String, Object> findStartConfig(ProcessTemplate template) {
        return nodeConfigParser.asOrderedList(template.getNodeConfig()).stream()
                .filter(config -> "start".equalsIgnoreCase(stringValue(config.get("businessType"), ""))
                        || stringValue(config.get("bpmnType"), "").toLowerCase(Locale.ROOT).endsWith("startevent"))
                .findFirst()
                .orElse(null);
    }

    private Set<String> permissionValues(Map<String, Object> config) {
        Object raw = firstNonNull(
                config.get("startPermissionValues"),
                config.get("startPermissionValue"),
                config.get("allowedRoles"),
                config.get("allowedDepartmentIds"));
        Set<String> values = new LinkedHashSet<>();
        if (raw instanceof Collection<?> collection) {
            collection.stream().map(String::valueOf).map(String::trim).filter(this::hasText).forEach(values::add);
            return values;
        }
        if (raw != null) {
            String cleaned = String.valueOf(raw).replace("[", "").replace("]", "").replace("\"", "");
            Arrays.stream(cleaned.split(","))
                    .map(String::trim)
                    .filter(this::hasText)
                    .forEach(values::add);
        }
        return values;
    }

    private void assertManagedBizType(CurrentUser user, Long bizTypeId) {
        if (SUPER_ADMIN.equals(user.getSystemRole()) || !hasText(user.getManagedBizTypeIds())) {
            return;
        }
        String cleaned = user.getManagedBizTypeIds().replace("[", "").replace("]", "").replace("\"", "");
        boolean allowed = Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .anyMatch(String.valueOf(bizTypeId)::equals);
        if (!allowed) {
            throw new AccessDeniedException("business type is outside current administrator scope");
        }
    }

    private CurrentUser requireCurrentUser() {
        CurrentUser user = SecurityUtils.currentUser();
        if (user == null || !user.isEnabled()) {
            throw new AccessDeniedException("authenticated enabled user is required");
        }
        return user;
    }

    private void requireTemplate(ProcessTemplate template) {
        if (template == null || template.getId() == null) {
            throw new IllegalArgumentException("process resource is required");
        }
    }

    private boolean containsIgnoreCase(Set<String> values, String expected) {
        return hasText(expected) && values.stream().anyMatch(value -> value.equalsIgnoreCase(expected));
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private String stringValue(Object value, String fallback) {
        return value == null || !hasText(value.toString()) ? fallback : value.toString().trim();
    }

    private String upper(String value) {
        return value.toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
