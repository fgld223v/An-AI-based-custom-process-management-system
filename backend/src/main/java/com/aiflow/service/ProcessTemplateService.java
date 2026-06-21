package com.aiflow.service;

import com.aiflow.dto.TemplateFormBindingDTO;
import com.aiflow.model.ProcessTemplate;

import java.util.List;
import java.util.Optional;

public interface ProcessTemplateService {

    ProcessTemplate createTemplate(ProcessTemplate template);

    ProcessTemplate updateTemplate(Long id, ProcessTemplate template);

    PublishResult publishTemplate(Long id);

    ProcessTemplate createNextVersion(Long id);

    List<ProcessTemplate> listTemplates();

    List<ProcessTemplate> listTemplatesByCreatedBy(Long createdBy);

    List<ProcessTemplate> listPublishedBusinessProcesses();

    Optional<ProcessTemplate> findPublishedBusinessProcessById(Long id);

    Optional<ProcessTemplate> findById(Long id);

    TemplateFormBindingDTO getTemplateBoundForm(Long templateId);

    /** 停用已发布版本，保留 Flowable 部署信息供历史实例追溯。 */
    ProcessTemplate unpublishTemplate(Long id);

    ProcessTemplate copyTemplate(ProcessTemplate sourceTemplate, Long createdBy, String newTemplateName);
}
