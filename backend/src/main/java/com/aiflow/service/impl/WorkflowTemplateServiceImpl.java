package com.aiflow.service.impl;

import com.aiflow.common.BusinessException;
import com.aiflow.dto.WorkflowTemplatePageRequest;
import com.aiflow.dto.WorkflowTemplateRequest;
import com.aiflow.dto.WorkflowTemplateResponse;
import com.aiflow.entity.WorkflowTemplate;
import com.aiflow.mapper.WorkflowTemplateMapper;
import com.aiflow.security.SecurityUtils;
import com.aiflow.service.WorkflowTemplateService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WorkflowTemplateServiceImpl implements WorkflowTemplateService {

    private static final String DEFAULT_STATUS = "DRAFT";

    private final WorkflowTemplateMapper workflowTemplateMapper;

    public WorkflowTemplateServiceImpl(WorkflowTemplateMapper workflowTemplateMapper) {
        this.workflowTemplateMapper = workflowTemplateMapper;
    }

    @Override
    public IPage<WorkflowTemplateResponse> pageTemplates(WorkflowTemplatePageRequest request) {
        Page<WorkflowTemplate> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<WorkflowTemplate> queryWrapper = new LambdaQueryWrapper<WorkflowTemplate>()
                .like(StringUtils.hasText(request.getTemplateName()), WorkflowTemplate::getTemplateName, request.getTemplateName())
                .eq(StringUtils.hasText(request.getBusinessType()), WorkflowTemplate::getBusinessType, request.getBusinessType())
                .eq(StringUtils.hasText(request.getStatus()), WorkflowTemplate::getStatus, request.getStatus())
                .orderByDesc(WorkflowTemplate::getUpdatedTime);
        IPage<WorkflowTemplate> templatePage = workflowTemplateMapper.selectPage(page, queryWrapper);
        return templatePage.convert(WorkflowTemplateResponse::from);
    }

    @Override
    public WorkflowTemplateResponse getTemplate(Long id) {
        WorkflowTemplate template = findById(id);
        return WorkflowTemplateResponse.from(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowTemplateResponse createTemplate(WorkflowTemplateRequest request) {
        WorkflowTemplate template = new WorkflowTemplate();
        fillTemplate(template, request);
        template.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : DEFAULT_STATUS);
        template.setCreatedBy(SecurityUtils.currentUserId());
        workflowTemplateMapper.insert(template);
        return WorkflowTemplateResponse.from(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowTemplateResponse updateTemplate(Long id, WorkflowTemplateRequest request) {
        WorkflowTemplate template = findById(id);
        fillTemplate(template, request);
        template.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : template.getStatus());
        workflowTemplateMapper.updateById(template);
        return WorkflowTemplateResponse.from(findById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long id) {
        WorkflowTemplate template = findById(id);
        workflowTemplateMapper.deleteById(template.getId());
    }

    private WorkflowTemplate findById(Long id) {
        WorkflowTemplate template = workflowTemplateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(404, "流程模板不存在");
        }
        return template;
    }

    private void fillTemplate(WorkflowTemplate template, WorkflowTemplateRequest request) {
        template.setTemplateName(request.getTemplateName());
        template.setBusinessType(request.getBusinessType());
        template.setFormJson(request.getFormJson());
        template.setBpmnXml(request.getBpmnXml());
    }
}
