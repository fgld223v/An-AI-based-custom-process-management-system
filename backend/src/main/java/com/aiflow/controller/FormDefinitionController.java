package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.DtoMapper;
import com.aiflow.dto.FormCreateRequest;
import com.aiflow.dto.FormDefinitionDTO;
import com.aiflow.dto.FormUpdateRequest;
import com.aiflow.model.FormDefinition;
import com.aiflow.service.FormDefinitionService;
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
@RequestMapping("/api/forms")
public class FormDefinitionController {

    private final FormDefinitionService formDefinitionService;

    @GetMapping("/published")
    public ApiResponse<List<FormDefinitionDTO>> listPublishedForms() {
        List<FormDefinitionDTO> result = formDefinitionService.listPublishedForms().stream()
                .map(DtoMapper::toFormDefinitionDTO)
                .toList();
        return ApiResponse.success(result);
    }

    @PostMapping
    public ApiResponse<FormDefinitionDTO> createForm(@RequestBody FormCreateRequest request) {
        FormDefinition saved = formDefinitionService.createForm(DtoMapper.toFormDefinition(request));
        return ApiResponse.success(DtoMapper.toFormDefinitionDTO(saved));
    }

    @PutMapping("/{id}")
    public ApiResponse<FormDefinitionDTO> updateForm(@PathVariable Long id, @RequestBody FormUpdateRequest request) {
        FormDefinition saved = formDefinitionService.updateForm(id, DtoMapper.toFormDefinition(request));
        return ApiResponse.success(DtoMapper.toFormDefinitionDTO(saved));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<FormDefinitionDTO> publishForm(@PathVariable Long id) {
        FormDefinition saved = formDefinitionService.publishForm(id);
        return ApiResponse.success(DtoMapper.toFormDefinitionDTO(saved));
    }

    @GetMapping("/{id}")
    public ApiResponse<FormDefinitionDTO> getForm(@PathVariable Long id) {
        FormDefinition form = formDefinitionService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("form not found"));
        return ApiResponse.success(DtoMapper.toFormDefinitionDTO(form));
    }
}
