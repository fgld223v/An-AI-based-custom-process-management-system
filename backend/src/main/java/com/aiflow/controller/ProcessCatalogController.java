package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.DtoMapper;
import com.aiflow.dto.ProcessTemplateDTO;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.service.ProcessTemplateService;
import com.aiflow.service.ProcessAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/process-catalog")
public class ProcessCatalogController {

    private final ProcessTemplateService processTemplateService;
    private final ProcessAuthorizationService processAuthorizationService;

    @GetMapping
    public ApiResponse<List<ProcessTemplateDTO>> listAvailableProcesses() {
        List<ProcessTemplateDTO> result = processTemplateService.listPublishedBusinessProcesses()
                .stream()
                .filter(processAuthorizationService::canCurrentUserStart)
                .map(DtoMapper::toProcessTemplateDTO)
                .toList();
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProcessTemplateDTO> getAvailableProcess(@PathVariable Long id) {
        ProcessTemplate process = processTemplateService.findPublishedBusinessProcessById(id)
                .orElseThrow(() -> new IllegalArgumentException("available business process not found"));
        processAuthorizationService.assertCanStart(process);
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(process));
    }
}
