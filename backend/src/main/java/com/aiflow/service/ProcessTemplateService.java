package com.aiflow.service;

import com.aiflow.model.ProcessTemplate;

import java.util.List;
import java.util.Optional;

public interface ProcessTemplateService {

    ProcessTemplate createTemplate(ProcessTemplate template);

    ProcessTemplate updateTemplate(Long id, ProcessTemplate template);

    ProcessTemplate publishTemplate(Long id);

    List<ProcessTemplate> listTemplates();

    Optional<ProcessTemplate> findById(Long id);

    ProcessTemplate copyTemplate(ProcessTemplate sourceTemplate, Long createdBy, String newTemplateName);
}
