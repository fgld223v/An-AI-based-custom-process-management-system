package com.aiflow.service.impl;

import com.aiflow.dto.DtoMapper;
import com.aiflow.dto.TemplateFormBindingDTO;
import com.aiflow.enums.FormStatus;
import com.aiflow.enums.TemplateSourceType;
import com.aiflow.enums.TemplateStatus;
import com.aiflow.model.FormDefinition;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.FormDefinitionRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.service.FlowableDeploymentService;
import com.aiflow.service.ProcessTemplateService;
import com.aiflow.common.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProcessTemplateServiceImpl implements ProcessTemplateService {

    private static final DateTimeFormatter COPY_CODE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final ProcessTemplateRepository processTemplateRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    private final FlowableDeploymentService flowableDeploymentService;
    private final ObjectMapper objectMapper;

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
        validatePublishedForm(template.getFormId());
        validateFormBindConfig(template.getFormBindConfig());

        LocalDateTime now = LocalDateTime.now();
        if (template.getStatus() == null) {
            template.setStatus(TemplateStatus.DRAFT);
        }
        if (template.getSourceType() == null) {
            template.setSourceType(TemplateSourceType.MANUAL);
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
        validatePublishedForm(template.getFormId());
        validateFormBindConfig(template.getFormBindConfig());

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

        validateBpmnXmlForPublish(existing.getBpmnXml());
        validateNodeConfigForPublish(existing.getNodeConfig());
        validatePublishedForm(existing.getFormId());
        validateFormBindConfig(existing.getFormBindConfig());
        flowableDeploymentService.deployProcessTemplate(existing);

        LocalDateTime now = LocalDateTime.now();
        existing.setStatus(TemplateStatus.PUBLISHED);
        existing.setPublishedAt(now);
        existing.setUpdatedAt(now);
        return processTemplateRepository.save(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcessTemplate> listTemplates() {
        return processTemplateRepository.findByDeletedOrderByUpdatedAtDesc(0);
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
            throw new IllegalStateException("only published template can be unpublished");
        }
        LocalDateTime now = LocalDateTime.now();
        existing.setStatus(TemplateStatus.DRAFT);
        existing.setFlowableDeploymentId(null);
        existing.setFlowableProcessDefinitionId(null);
        existing.setPublishedAt(null);
        existing.setUpdatedAt(now);
        return processTemplateRepository.save(existing);
    }

    @Override
    public ProcessTemplate copyTemplate(ProcessTemplate sourceTemplate, Long createdBy, String newTemplateName) {
        if (sourceTemplate == null) {
            throw new IllegalArgumentException("sourceTemplate must not be null");
        }
        requireText(sourceTemplate.getTemplateCode(), "source templateCode must not be blank");
        requireText(sourceTemplate.getTemplateName(), "source templateName must not be blank");

        LocalDateTime now = LocalDateTime.now();
        ProcessTemplate copied = ProcessTemplate.builder()
                .templateCode("COPY_" + sourceTemplate.getTemplateCode() + "_" + now.format(COPY_CODE_TIME_FORMATTER))
                .templateName(hasText(newTemplateName) ? newTemplateName : sourceTemplate.getTemplateName() + "-copy")
                .bizTypeId(sourceTemplate.getBizTypeId())
                .formId(sourceTemplate.getFormId())
                .version(1)
                .status(TemplateStatus.DRAFT)
                .sourceType(TemplateSourceType.MARKET_COPY)
                .bpmnXml(sourceTemplate.getBpmnXml())
                .nodeConfig(sourceTemplate.getNodeConfig())
                .formBindConfig(sourceTemplate.getFormBindConfig())
                .createdBy(createdBy)
                .createdAt(now)
                .updatedAt(now)
                .deleted(0)
                .build();
        return processTemplateRepository.save(copied);
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
    private void validatePublishedForm(Long formId) {
        if (formId != null) {
            try {
                getPublishedForm(formId);
            } catch (IllegalStateException e) {
                log.warn("模板顶层绑定的表单尚未发布 (formId={})，草稿阶段容忍此状态", formId);
            }
        }
    }

    /**
     * 校验节点级别的表单绑定配置。
     * 草稿阶段仅记录警告，不阻塞保存；发布时 publishTemplate 会再次严格校验。
     */
    private void validateFormBindConfig(String formBindConfigJson) {
        if (formBindConfigJson == null || formBindConfigJson.isBlank()) return;
        try {
            Map<String, Map<String, Object>> map = objectMapper.readValue(
                formBindConfigJson, new TypeReference<Map<String, Map<String, Object>>>() {});
            for (Map.Entry<String, Map<String, Object>> entry : map.entrySet()) {
                Object formIdObj = entry.getValue().get("formId");
                if (formIdObj != null) {
                    Long formId = formIdObj instanceof Integer
                        ? ((Integer) formIdObj).longValue()
                        : (Long) formIdObj;
                    try {
                        getPublishedForm(formId);
                    } catch (IllegalStateException e) {
                        log.warn("节点 [{}] 绑定的表单尚未发布 (formId={})，草稿阶段容忍此状态", entry.getKey(), formId);
                    }
                }
            }
        } catch (BusinessException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("formBindConfig format error: " + e.getMessage());
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