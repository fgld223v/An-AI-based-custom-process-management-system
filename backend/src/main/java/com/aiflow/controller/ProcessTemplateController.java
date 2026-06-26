package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.DtoMapper;
import com.aiflow.dto.ProcessTemplateCreateRequest;
import com.aiflow.dto.ProcessTemplateDTO;
import com.aiflow.dto.ProcessTemplateUpdateRequest;
import com.aiflow.dto.TemplateFormBindingDTO;
import com.aiflow.enums.ProcessResourceType;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.security.SecurityUtils;
import com.aiflow.service.ProcessTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/process-templates")
public class ProcessTemplateController {

    private final ProcessTemplateService processTemplateService;

    @GetMapping
    public ApiResponse<List<ProcessTemplateDTO>> listTemplates() {
        List<ProcessTemplateDTO> result = processTemplateService.listTemplates().stream()
                .map(DtoMapper::toProcessTemplateDTO)
                .toList();
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProcessTemplateDTO> getTemplate(@PathVariable Long id) {
        ProcessTemplate template = getSystemTemplate(id);
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(template));
    }

    @GetMapping("/{id}/form")
    public ApiResponse<TemplateFormBindingDTO> getTemplateBoundForm(@PathVariable Long id) {
        getSystemTemplate(id);
        return ApiResponse.success(processTemplateService.getTemplateBoundForm(id));
    }

    @PostMapping
    public ApiResponse<ProcessTemplateDTO> createTemplate(@RequestBody ProcessTemplateCreateRequest request) {
        ProcessTemplate template = DtoMapper.toProcessTemplate(request);
        template.setResourceType(ProcessResourceType.SYSTEM_TEMPLATE);
        template.setCreatedBy(SecurityUtils.currentUserId());
        ProcessTemplate saved = processTemplateService.createTemplate(template);
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(saved));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProcessTemplateDTO> updateTemplate(@PathVariable Long id,
                                                          @RequestBody ProcessTemplateUpdateRequest request) {
        getSystemTemplate(id);
        ProcessTemplate saved = processTemplateService.updateTemplate(id, DtoMapper.toProcessTemplate(request));
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(saved));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<ProcessTemplateDTO> publishTemplate(@PathVariable Long id) {
        getSystemTemplate(id);
        ProcessTemplate saved = processTemplateService.publishTemplate(id);
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(saved));
    }

    @PostMapping("/{id}/new-version")
    public ApiResponse<ProcessTemplateDTO> createNewVersion(@PathVariable Long id) {
        getSystemTemplate(id);
        ProcessTemplate saved = processTemplateService.createNextVersion(id);
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(saved));
    }

    /** 停用已发布版本，历史部署信息保持不变。 */
    @PostMapping("/{id}/unpublish")
    public ApiResponse<ProcessTemplateDTO> unpublishTemplate(@PathVariable Long id) {
        getSystemTemplate(id);
        ProcessTemplate saved = processTemplateService.unpublishTemplate(id);
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(saved));
    }

    /** 删除未发布或已停用的模板版本；已有实例或市场引用时由服务层拒绝。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long id) {
        getSystemTemplate(id);
        processTemplateService.deleteTemplate(id);
        return ApiResponse.success();
    }

    private ProcessTemplate getSystemTemplate(Long id) {
        ProcessTemplate template = processTemplateService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("template not found"));
        if (template.getResourceType() != ProcessResourceType.SYSTEM_TEMPLATE) {
            throw new IllegalArgumentException("system template not found");
        }
        return template;
    }
}
