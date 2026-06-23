package com.aiflow.service.impl;

import com.aiflow.dto.DtoMapper;
import com.aiflow.dto.TemplateFormBindingDTO;
import com.aiflow.enums.FormStatus;
import com.aiflow.enums.ProcessResourceType;
import com.aiflow.enums.TemplateSourceType;
import com.aiflow.enums.TemplateStatus;
import com.aiflow.model.FormDefinition;
import com.aiflow.model.Department;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.model.SysUser;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.FormDefinitionRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.security.SecurityUtils;
import com.aiflow.service.FlowableDeploymentService;
import com.aiflow.service.ProcessAuthorizationService;
import com.aiflow.service.WorkflowRoleService;
import com.aiflow.dto.WorkflowRoleDTO;
import com.aiflow.service.ProcessTemplateService;
import com.aiflow.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProcessTemplateServiceImpl implements ProcessTemplateService {

    private static final DateTimeFormatter COPY_CODE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final ProcessTemplateRepository processTemplateRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    private final FlowableDeploymentService flowableDeploymentService;
    private final ProcessAuthorizationService processAuthorizationService;
    private final ObjectMapper objectMapper;
    private final FormBindConfigParser formBindConfigParser;
    private final NodeConfigParser nodeConfigParser;
    private final WorkflowRoleService workflowRoleService;
    private final DepartmentRepository departmentRepository;
    private final SysUserRepository sysUserRepository;

    @Override
    public ProcessTemplate createTemplate(ProcessTemplate template) {
        if (template == null) {
            throw new IllegalArgumentException("template must not be null");
        }
        requireText(template.getTemplateCode(), "templateCode must not be blank");
        requireText(template.getTemplateName(), "templateName must not be blank");

        if (template.getVersion() == null) {
            template.setVersion(1);
        }
        if (processTemplateRepository.existsByTemplateCodeAndVersion(template.getTemplateCode(), template.getVersion())) {
            throw new IllegalStateException("templateCode and version already exist");
        }
        validatePublishedForm(template.getFormId(), false);
        validateFormBindConfig(template.getFormBindConfig(), false);

        LocalDateTime now = LocalDateTime.now();
        if (template.getStatus() == null) {
            template.setStatus(TemplateStatus.DRAFT);
        }
        if (template.getSourceType() == null) {
            template.setSourceType(TemplateSourceType.MANUAL);
        }
        if (template.getResourceType() == null) {
            template.setResourceType(ProcessResourceType.SYSTEM_TEMPLATE);
        }
        template.setDeleted(0);
        template.setCreatedAt(now);
        template.setUpdatedAt(now);
        return processTemplateRepository.save(template);
    }

    @Override
    public ProcessTemplate updateTemplate(Long id, ProcessTemplate template) {
        requireId(id, "id must not be null");
        if (template == null) {
            throw new IllegalArgumentException("template must not be null");
        }

        ProcessTemplate existing = getRequiredTemplate(id);
        if (existing.getStatus() != TemplateStatus.DRAFT && existing.getStatus() != TemplateStatus.REVIEWING) {
            throw new IllegalStateException("only draft or reviewing template can be updated");
        }
        requireText(template.getTemplateName(), "templateName must not be blank");
        validatePublishedForm(template.getFormId(), false);
        validateFormBindConfig(template.getFormBindConfig(), false);

        existing.setTemplateName(template.getTemplateName().trim());
        existing.setBizTypeId(template.getBizTypeId());
        existing.setFormId(template.getFormId());
        existing.setBpmnXml(template.getBpmnXml());
        existing.setNodeConfig(template.getNodeConfig());
        existing.setFormBindConfig(template.getFormBindConfig());
        existing.setUpdatedAt(LocalDateTime.now());
        return processTemplateRepository.save(existing);
    }

    @Override
    public ProcessTemplate publishTemplate(Long id) {
        requireId(id, "id must not be null");
        ProcessTemplate existing = getRequiredTemplate(id);
        if (existing.getStatus() != TemplateStatus.DRAFT && existing.getStatus() != TemplateStatus.REVIEWING) {
            throw new IllegalStateException("only draft or reviewing template can be published");
        }

        processAuthorizationService.assertCanPublish(existing);
        validateBpmnXmlForPublish(existing.getBpmnXml());
        validateNodeConfigForPublish(existing.getNodeConfig());
        validatePublishedForm(existing.getFormId(), true);
        validateFormBindConfig(existing.getFormBindConfig(), true);
        flowableDeploymentService.deployProcessTemplate(existing);

        LocalDateTime now = LocalDateTime.now();
        List<ProcessTemplate> previousPublishedVersions = processTemplateRepository
                .findByTemplateCodeAndResourceTypeAndStatusAndDeleted(
                        existing.getTemplateCode(), existing.getResourceType(), TemplateStatus.PUBLISHED, 0);
        for (ProcessTemplate previous : previousPublishedVersions) {
            if (!previous.getId().equals(existing.getId())) {
                previous.setStatus(TemplateStatus.DISABLED);
                previous.setUpdatedAt(now);
            }
        }
        if (!previousPublishedVersions.isEmpty()) {
            processTemplateRepository.saveAll(previousPublishedVersions);
        }
        existing.setStatus(TemplateStatus.PUBLISHED);
        existing.setPublishedAt(now);
        existing.setUpdatedAt(now);
        return processTemplateRepository.save(existing);
    }

    @Override
    public ProcessTemplate createNextVersion(Long id) {
        requireId(id, "id must not be null");
        ProcessTemplate requestedVersion = getRequiredTemplate(id);
        if (requestedVersion.getStatus() != TemplateStatus.PUBLISHED
                && requestedVersion.getStatus() != TemplateStatus.DISABLED) {
            throw new IllegalStateException("only published or disabled version can create a new version");
        }
        processAuthorizationService.assertCanCreateVersion(requestedVersion);

        List<ProcessTemplate> versions = processTemplateRepository
                .findByTemplateCodeAndResourceTypeAndDeletedOrderByVersionDesc(
                        requestedVersion.getTemplateCode(), requestedVersion.getResourceType(), 0);
        if (!versions.isEmpty()) {
            ProcessTemplate latest = versions.get(0);
            if (latest.getStatus() == TemplateStatus.DRAFT || latest.getStatus() == TemplateStatus.REVIEWING) {
                return latest;
            }
        }

        ProcessTemplate baseVersion = versions.stream()
                .filter(item -> item.getStatus() == TemplateStatus.PUBLISHED)
                .findFirst()
                .orElse(requestedVersion);
        int nextVersion = processTemplateRepository
                .findFirstByTemplateCodeAndResourceTypeOrderByVersionDesc(
                        requestedVersion.getTemplateCode(), requestedVersion.getResourceType())
                .map(ProcessTemplate::getVersion)
                .filter(java.util.Objects::nonNull)
                .orElse(0) + 1;
        LocalDateTime now = LocalDateTime.now();
        ProcessTemplate draft = ProcessTemplate.builder()
                .templateCode(baseVersion.getTemplateCode())
                .templateName(baseVersion.getTemplateName())
                .bizTypeId(baseVersion.getBizTypeId())
                .formId(baseVersion.getFormId())
                .version(nextVersion)
                .status(TemplateStatus.DRAFT)
                .sourceType(baseVersion.getSourceType())
                .resourceType(baseVersion.getResourceType())
                .bpmnXml(baseVersion.getBpmnXml())
                .nodeConfig(baseVersion.getNodeConfig())
                .formBindConfig(baseVersion.getFormBindConfig())
                .createdBy(baseVersion.getCreatedBy())
                .createdAt(now)
                .updatedAt(now)
                .deleted(0)
                .build();
        return processTemplateRepository.save(draft);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcessTemplate> listTemplates() {
        return processTemplateRepository.findByResourceTypeAndDeletedOrderByUpdatedAtDesc(
                ProcessResourceType.SYSTEM_TEMPLATE, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcessTemplate> listTemplatesByCreatedBy(Long createdBy) {
        requireId(createdBy, "createdBy must not be null");
        return processTemplateRepository.findByCreatedByAndResourceTypeAndDeletedOrderByUpdatedAtDesc(
                createdBy, ProcessResourceType.BUSINESS_PROCESS, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcessTemplate> listPublishedBusinessProcesses() {
        return processTemplateRepository
                .findByResourceTypeAndStatusAndFlowableDeploymentIdIsNotNullAndFlowableProcessDefinitionIdIsNotNullAndDeletedOrderByUpdatedAtDesc(
                        ProcessResourceType.BUSINESS_PROCESS, TemplateStatus.PUBLISHED, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessTemplate> findPublishedBusinessProcessById(Long id) {
        requireId(id, "id must not be null");
        return processTemplateRepository
                .findByIdAndResourceTypeAndStatusAndFlowableDeploymentIdIsNotNullAndFlowableProcessDefinitionIdIsNotNullAndDeleted(
                        id, ProcessResourceType.BUSINESS_PROCESS, TemplateStatus.PUBLISHED, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessTemplate> findById(Long id) {
        requireId(id, "id must not be null");
        // 使用 findByIdAndDeleted 确保只查询未删除的模板，并保证 LONGTEXT 字段（bpmnXml）在事务内被加载
        return processTemplateRepository.findByIdAndDeleted(id, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateFormBindingDTO getTemplateBoundForm(Long templateId) {
        requireId(templateId, "templateId must not be null");
        ProcessTemplate template = processTemplateRepository.findByIdAndDeleted(templateId, 0)
                .orElseThrow(() -> new IllegalArgumentException("template not found"));
        if (template.getFormId() == null) {
            throw new IllegalStateException("current process template has no bound form");
        }

        FormDefinition form = getPublishedForm(template.getFormId());

        return TemplateFormBindingDTO.builder()
                .template(DtoMapper.toProcessTemplateDTO(template))
                .form(DtoMapper.toFormDefinitionDTO(form))
                .build();
    }

    @Override
    public ProcessTemplate unpublishTemplate(Long id) {
        requireId(id, "id must not be null");
        ProcessTemplate existing = getRequiredTemplate(id);
        if (existing.getStatus() != TemplateStatus.PUBLISHED) {
            throw new IllegalStateException("only published version can be disabled");
        }
        processAuthorizationService.assertCanCreateVersion(existing);
        LocalDateTime now = LocalDateTime.now();
        existing.setStatus(TemplateStatus.DISABLED);
        existing.setUpdatedAt(now);
        return processTemplateRepository.save(existing);
    }

    @Override
    public void deleteTemplate(Long id) {
        requireId(id, "id must not be null");
        ProcessTemplate existing = getRequiredTemplate(id);
        if (existing.getStatus() == TemplateStatus.PUBLISHED) {
            throw new IllegalStateException("已发布流程请先停用后再删除");
        }
        if (processInstanceRepository.existsByTemplateIdAndDeleted(id, 0)) {
            throw new IllegalStateException("该流程版本已有流程实例，不能删除；可保持停用状态以保留历史记录");
        }
        existing.setDeleted(1);
        existing.setUpdatedAt(LocalDateTime.now());
        processTemplateRepository.save(existing);
    }

    @Override
    public ProcessTemplate copyTemplate(ProcessTemplate sourceTemplate, Long createdBy, String newTemplateName) {
        if (sourceTemplate == null) {
            throw new IllegalArgumentException("sourceTemplate must not be null");
        }
        requireText(sourceTemplate.getTemplateCode(), "source templateCode must not be blank");
        requireText(sourceTemplate.getTemplateName(), "source templateName must not be blank");

        LocalDateTime now = LocalDateTime.now();
        Map<Long, Long> copiedFormIds = copyBoundForms(sourceTemplate, createdBy, now);
        ProcessTemplate copied = ProcessTemplate.builder()
                .templateCode("COPY_" + sourceTemplate.getTemplateCode() + "_" + now.format(COPY_CODE_TIME_FORMATTER))
                .templateName(hasText(newTemplateName) ? newTemplateName : sourceTemplate.getTemplateName() + "-copy")
                .bizTypeId(sourceTemplate.getBizTypeId())
                .formId(remapFormId(sourceTemplate.getFormId(), copiedFormIds))
                .version(1)
                .status(TemplateStatus.DRAFT)
                .sourceType(TemplateSourceType.MARKET_COPY)
                .resourceType(ProcessResourceType.BUSINESS_PROCESS)
                .bpmnXml(sourceTemplate.getBpmnXml())
                .nodeConfig(rewriteFormReferences(sourceTemplate.getNodeConfig(), copiedFormIds))
                .formBindConfig(rewriteFormReferences(sourceTemplate.getFormBindConfig(), copiedFormIds))
                .createdBy(createdBy)
                .createdAt(now)
                .updatedAt(now)
                .deleted(0)
                .build();
        return processTemplateRepository.save(copied);
    }

    private Map<Long, Long> copyBoundForms(ProcessTemplate sourceTemplate, Long createdBy, LocalDateTime now) {
        Set<Long> sourceFormIds = new LinkedHashSet<>();
        if (sourceTemplate.getFormId() != null) {
            sourceFormIds.add(sourceTemplate.getFormId());
        }
        collectFormIds(sourceTemplate.getNodeConfig(), sourceFormIds);
        collectFormIds(sourceTemplate.getFormBindConfig(), sourceFormIds);

        Map<Long, Long> copiedFormIds = new LinkedHashMap<>();
        int sequence = 0;
        for (Long sourceFormId : sourceFormIds) {
            FormDefinition sourceForm = formDefinitionRepository.findByIdAndDeleted(sourceFormId, 0)
                    .orElseThrow(() -> new IllegalStateException(
                            "market template references missing form: " + sourceFormId));
            if (sourceForm.getStatus() != FormStatus.PUBLISHED) {
                throw new IllegalStateException(
                        "market template references unpublished form: " + sourceFormId);
            }

            FormDefinition copiedForm = FormDefinition.builder()
                    .formCode(marketCopyFormCode(sourceFormId, now, sequence++))
                    .formName(sourceForm.getFormName() + "（市场复制）")
                    .bizTypeId(sourceForm.getBizTypeId())
                    .version(1)
                    .status(FormStatus.DRAFT)
                    .fieldList(sourceForm.getFieldList())
                    .formSchema(sourceForm.getFormSchema())
                    .createdBy(createdBy)
                    .sourceType("market_copy")
                    .sourceFormId(sourceFormId)
                    .createdAt(now)
                    .updatedAt(now)
                    .deleted(0)
                    .build();
            FormDefinition savedForm = formDefinitionRepository.save(copiedForm);
            if (savedForm.getId() == null) {
                throw new IllegalStateException("copied market form id was not generated");
            }
            copiedFormIds.put(sourceFormId, savedForm.getId());
        }
        return copiedFormIds;
    }

    private String marketCopyFormCode(Long sourceFormId, LocalDateTime now, int sequence) {
        return "MKT_" + sourceFormId + "_" + now.format(COPY_CODE_TIME_FORMATTER) + "_" + sequence;
    }

    private void collectFormIds(String json, Set<Long> formIds) {
        if (!hasText(json)) return;
        try {
            collectFormIds(objectMapper.readTree(json), formIds);
        } catch (Exception ex) {
            throw new IllegalStateException("form binding JSON is invalid", ex);
        }
    }

    private void collectFormIds(JsonNode node, Set<Long> formIds) {
        if (node == null) return;
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if ("formId".equals(entry.getKey()) && entry.getValue().canConvertToLong()) {
                    formIds.add(entry.getValue().longValue());
                } else {
                    collectFormIds(entry.getValue(), formIds);
                }
            });
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectFormIds(child, formIds));
        }
    }

    private String rewriteFormReferences(String json, Map<Long, Long> copiedFormIds) {
        if (!hasText(json) || copiedFormIds.isEmpty()) return json;
        try {
            JsonNode root = objectMapper.readTree(json);
            rewriteFormReferences(root, copiedFormIds);
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw new IllegalStateException("form binding JSON is invalid", ex);
        }
    }

    private void rewriteFormReferences(JsonNode node, Map<Long, Long> copiedFormIds) {
        if (node == null) return;
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            objectNode.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if ("formId".equals(entry.getKey()) && value.canConvertToLong()) {
                    Long replacement = copiedFormIds.get(value.longValue());
                    if (replacement != null) objectNode.put(entry.getKey(), replacement);
                } else {
                    rewriteFormReferences(value, copiedFormIds);
                }
            });
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> rewriteFormReferences(child, copiedFormIds));
        }
    }

    private Long remapFormId(Long sourceFormId, Map<Long, Long> copiedFormIds) {
        return sourceFormId == null ? null : copiedFormIds.getOrDefault(sourceFormId, sourceFormId);
    }

    private void validateBpmnXmlForPublish(String bpmnXml) {
        if (!hasText(bpmnXml)) {
            throw new IllegalStateException("流程模板缺少 BPMN XML，无法发布到流程引擎。");
        }
        String normalized = bpmnXml.trim().toLowerCase();
        if ((!normalized.contains("<bpmn:definitions") && !normalized.contains("<definitions"))
                || (!normalized.contains("<bpmn:process") && !normalized.contains("<process"))) {
            throw new IllegalStateException("BPMN XML 格式不正确，无法部署。");
        }
    }

    private void validateNodeConfigForPublish(String nodeConfig) {
        if (!hasText(nodeConfig)) {
            return;
        }
        try {
            objectMapper.readTree(nodeConfig);
        } catch (Exception ex) {
            throw new IllegalStateException("节点配置 JSON 格式不正确，无法发布模板。");
        }
        for (Map<String, Object> config : nodeConfigParser.asOrderedList(nodeConfig)) {
            if (!"approval".equalsIgnoreCase(stringValue(config.get("businessType")))) {
                continue;
            }
            String nodeName = firstText(config.get("nodeName"), config.get("nodeKey"), config.get("nodeId"));
            String strategy = stringValue(config.get("assignStrategy"));
            String assignValue = firstTextOrNull(config.get("assignValue"), config.get("assigneeValue"));
            if (!hasText(strategy)) {
                throw new IllegalStateException("审批节点【" + nodeName + "】未配置审批人策略。");
            }
            if (List.of("SPECIFIC_USERS", "ROLE", "ROLE_IN_APPLICANT_DEPT", "ROLE_IN_SPECIFIED_DEPT",
                    "GLOBAL_ROLE", "SPECIFIED_DEPARTMENT_MANAGER").contains(strategy.toUpperCase())
                    && !hasText(assignValue)) {
                throw new IllegalStateException("审批节点【" + nodeName + "】未完整配置审批人范围。");
            }
            validateWorkflowRoleReference(nodeName, strategy, assignValue);
            validateSpecifiedDepartmentReference(nodeName, strategy, assignValue);
        }
    }

    private void validateWorkflowRoleReference(String nodeName, String strategy, String assignValue) {
        String normalizedStrategy = hasText(strategy) ? strategy.trim().toUpperCase() : "";
        if (!List.of("ROLE", "ROLE_IN_APPLICANT_DEPT", "ROLE_IN_SPECIFIED_DEPT", "GLOBAL_ROLE")
                .contains(normalizedStrategy)) {
            return;
        }
        String roleCode = extractRoleCode(assignValue);
        String requiredScope = "GLOBAL_ROLE".equals(normalizedStrategy) ? "global" : "department";
        List<WorkflowRoleDTO> enabledRoles = workflowRoleService.listRoles(true);
        boolean valid = enabledRoles != null && enabledRoles.stream().anyMatch(role ->
                roleCode.equalsIgnoreCase(role.getRoleCode())
                        && requiredScope.equalsIgnoreCase(role.getRoleScope()));
        if (!valid) {
            throw new IllegalStateException("审批节点【" + nodeName + "】引用的流程角色不存在、已停用或范围不匹配："
                    + roleCode);
        }
        if ("GLOBAL_ROLE".equals(normalizedStrategy)
                && workflowRoleService.resolveActiveUserIds(roleCode, null).isEmpty()) {
            throw new IllegalStateException("审批节点【" + nodeName + "】引用的全局流程角色没有有效成员："
                    + roleCode);
        }
    }

    private void validateSpecifiedDepartmentReference(
            String nodeName, String strategy, String assignValue) {
        String normalizedStrategy = hasText(strategy) ? strategy.trim().toUpperCase() : "";
        if (!List.of("ROLE_IN_SPECIFIED_DEPT", "SPECIFIED_DEPARTMENT_MANAGER")
                .contains(normalizedStrategy)) {
            return;
        }
        Long departmentId = extractDepartmentId(assignValue);
        if (departmentId == null) {
            throw new IllegalStateException("审批节点【" + nodeName + "】未配置有效的指定部门。");
        }
        Department department = departmentRepository.findByIdAndDeleted(departmentId, 0)
                .filter(item -> Integer.valueOf(1).equals(item.getStatus()))
                .orElseThrow(() -> new IllegalStateException(
                        "审批节点【" + nodeName + "】指定的部门不存在或已停用：" + departmentId));

        if ("ROLE_IN_SPECIFIED_DEPT".equals(normalizedStrategy)) {
            String roleCode = extractRoleCode(assignValue);
            if (workflowRoleService.resolveActiveUserIds(roleCode, departmentId).isEmpty()) {
                throw new IllegalStateException("审批节点【" + nodeName + "】指定部门内没有有效的流程角色成员："
                        + roleCode);
            }
            return;
        }

        Long leaderId = department.getLeaderUserId();
        SysUser leader = leaderId == null ? null : sysUserRepository.findById(leaderId).orElse(null);
        if (leader == null || Integer.valueOf(1).equals(leader.getDeleted())
                || !Integer.valueOf(1).equals(leader.getEnabled())) {
            throw new IllegalStateException("审批节点【" + nodeName + "】指定部门未配置有效负责人："
                    + departmentId);
        }
    }

    private String extractRoleCode(String assignValue) {
        if (!hasText(assignValue)) return "";
        String normalized = assignValue.trim();
        if (!normalized.startsWith("{")) return normalized;
        try {
            return objectMapper.readTree(normalized).path("roleCode").asText("").trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private Long extractDepartmentId(String assignValue) {
        if (!hasText(assignValue) || !assignValue.trim().startsWith("{")) return null;
        try {
            String value = objectMapper.readTree(assignValue).path("departmentId").asText("").trim();
            return hasText(value) ? Long.valueOf(value) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstTextOrNull(Object... values) {
        for (Object value : values) {
            String text = stringValue(value);
            if (hasText(text)) return text;
        }
        return null;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = stringValue(value);
            if (hasText(text)) return text;
        }
        return "未命名节点";
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString().trim();
    }

    private ProcessTemplate getRequiredTemplate(Long id) {
        // 确保在事务内加载，LONGTEXT 字段（bpmnXml）可被正确读取
        return processTemplateRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new IllegalArgumentException("template not found"));
    }

    /**
     * 校验顶层绑定的表单是否已发布。
     * 仅记录警告，不抛出异常 —— 草稿阶段的模板允许绑定未发布的表单。
     * 发布模板时（publishTemplate）仍会严格校验。
     */
    private void validatePublishedForm(Long formId, boolean strict) {
        if (formId != null) {
            try {
                assertCanBindForm(getPublishedForm(formId));
            } catch (IllegalStateException e) {
                if (strict) throw e;
                log.warn("模板顶层绑定的表单尚未发布 (formId={})，草稿阶段容忍此状态", formId);
            }
        }
    }

    /**
     * 校验节点级别的表单绑定配置。
     * 草稿阶段仅记录警告，不阻塞保存；发布时 publishTemplate 会再次严格校验。
     */
    private void validateFormBindConfig(String formBindConfigJson, boolean strict) {
        if (formBindConfigJson == null || formBindConfigJson.isBlank()) return;

        // 1. 格式校验（由工具类统一处理，不暴露原始 JSON）
        String validationError = formBindConfigParser.validate(formBindConfigJson);
        if (validationError != null) {
            throw new IllegalStateException(validationError);
        }

        // 2. 检查绑定的表单是否存在且已发布
        // 先尝试标准格式 {{nodeKey: {formId: N}, ...}
        Map<String, Map<String, Object>> standardMap = formBindConfigParser.tryParseAsNestedMap(formBindConfigJson);
        if (standardMap != null) {
            for (Map.Entry<String, Map<String, Object>> entry : standardMap.entrySet()) {
                Object formIdObj = entry.getValue().get("formId");
                if (formIdObj != null) {
                    Long formId = formIdObj instanceof Number number ? number.longValue() : null;
                    if (formId == null) continue;
                    try {
                        assertCanBindForm(getPublishedForm(formId));
                    } catch (IllegalStateException e) {
                        if (strict) throw e;
                        log.warn("节点 [{}] 绑定的表单尚未发布 (formId={})，草稿阶段容忍此状态", entry.getKey(), formId);
                    }
                }
            }
            return;
        }

        // 再尝试扁平格式 {formId: N}（历史遗留兼容，不抛异常阻塞保存）
        Map<String, Object> flatMap = formBindConfigParser.tryParseAsFlatMap(formBindConfigJson);
        if (flatMap != null) {
            Object formIdObj = flatMap.get("formId");
            if (formIdObj instanceof Number) {
                FormDefinition form = getPublishedForm(((Number) formIdObj).longValue());
                assertCanBindForm(form);
            }
        }
    }

    private FormDefinition getPublishedForm(Long formId) {
        FormDefinition form = formDefinitionRepository.findByIdAndDeleted(formId, 0)
                .orElseThrow(() -> new IllegalStateException("bound form (id=" + formId + ") does not exist or has been deleted"));
        if (form.getStatus() != FormStatus.PUBLISHED) {
            throw new IllegalStateException("bound form (id=" + formId + ") must be published");
        }
        return form;
    }

    private void assertCanBindForm(FormDefinition form) {
        if (SecurityUtils.isSuperAdmin()) return;
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null) {
            throw new AccessDeniedException("authenticated user is required");
        }
        if (form.getCreatedBy() == null || !form.getCreatedBy().equals(currentUserId)) {
            throw new AccessDeniedException("no permission to bind form " + form.getId());
        }
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
