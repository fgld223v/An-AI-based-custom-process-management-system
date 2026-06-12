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
import com.aiflow.service.ProcessTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProcessTemplateServiceImpl implements ProcessTemplateService {

    private static final DateTimeFormatter COPY_CODE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final ProcessTemplateRepository processTemplateRepository;
    private final FormDefinitionRepository formDefinitionRepository;

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
        return processTemplateRepository.findById(id);
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

    private ProcessTemplate getRequiredTemplate(Long id) {
        return processTemplateRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new IllegalArgumentException("template not found"));
    }

    private void validatePublishedForm(Long formId) {
        if (formId != null) {
            getPublishedForm(formId);
        }
    }

    private FormDefinition getPublishedForm(Long formId) {
        FormDefinition form = formDefinitionRepository.findByIdAndDeleted(formId, 0)
                .orElseThrow(() -> new IllegalStateException("bound form does not exist or has been deleted"));
        if (form.getStatus() != FormStatus.PUBLISHED) {
            throw new IllegalStateException("bound form must be published");
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