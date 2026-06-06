package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.DtoMapper;
import com.aiflow.dto.ProcessTemplateCreateRequest;
import com.aiflow.dto.ProcessTemplateDTO;
import com.aiflow.dto.ProcessTemplateUpdateRequest;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.service.ProcessTemplateService;
import lombok.RequiredArgsConstructor;
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
        ProcessTemplate template = processTemplateService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("template not found"));
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(template));
    }

    @PostMapping
    public ApiResponse<ProcessTemplateDTO> createTemplate(@RequestBody ProcessTemplateCreateRequest request) {
        ProcessTemplate saved = processTemplateService.createTemplate(DtoMapper.toProcessTemplate(request));
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(saved));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProcessTemplateDTO> updateTemplate(@PathVariable Long id,
                                                          @RequestBody ProcessTemplateUpdateRequest request) {
        ProcessTemplate saved = processTemplateService.updateTemplate(id, DtoMapper.toProcessTemplate(request));
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(saved));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<ProcessTemplateDTO> publishTemplate(@PathVariable Long id) {
        ProcessTemplate saved = processTemplateService.publishTemplate(id);
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(saved));
    }
}
