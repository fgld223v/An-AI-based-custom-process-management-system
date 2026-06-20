package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.DtoMapper;
import com.aiflow.dto.ProcessTemplateCreateRequest;
import com.aiflow.dto.ProcessTemplateDTO;
import com.aiflow.dto.ProcessTemplateUpdateRequest;
import com.aiflow.dto.TemplateFormBindingDTO;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.security.CurrentUser;
import com.aiflow.security.SecurityUtils;
import com.aiflow.service.ProcessTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/my-processes")
public class MyProcessController {

    private static final DateTimeFormatter CODE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final ProcessTemplateService processTemplateService;

    @GetMapping
    public ApiResponse<List<ProcessTemplateDTO>> listMyProcesses() {
        CurrentUser currentUser = requireBizAdmin();
        List<ProcessTemplateDTO> result = processTemplateService.listTemplatesByCreatedBy(currentUser.getId())
                .stream()
                .map(DtoMapper::toProcessTemplateDTO)
                .toList();
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProcessTemplateDTO> getMyProcess(@PathVariable Long id) {
        ProcessTemplate template = getOwnedTemplate(id);
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(template));
    }

    @GetMapping("/{id}/form")
    public ApiResponse<TemplateFormBindingDTO> getMyProcessBoundForm(@PathVariable Long id) {
        getOwnedTemplate(id);
        return ApiResponse.success(processTemplateService.getTemplateBoundForm(id));
    }

    @PostMapping
    public ApiResponse<ProcessTemplateDTO> createMyProcess(@RequestBody ProcessTemplateCreateRequest request) {
        CurrentUser currentUser = requireBizAdmin();
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        validateManagedBizType(currentUser, request.getBizTypeId());
        if (!hasText(request.getTemplateCode())) {
            request.setTemplateCode("MY_FLOW_" + currentUser.getId() + "_" + LocalDateTime.now().format(CODE_TIME_FORMATTER));
        }
        request.setCreatedBy(currentUser.getId());
        ProcessTemplate saved = processTemplateService.createTemplate(DtoMapper.toProcessTemplate(request));
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(saved));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProcessTemplateDTO> updateMyProcess(@PathVariable Long id,
                                                           @RequestBody ProcessTemplateUpdateRequest request) {
        CurrentUser currentUser = requireBizAdmin();
        getOwnedTemplate(id);
        validateManagedBizType(currentUser, request == null ? null : request.getBizTypeId());
        ProcessTemplate saved = processTemplateService.updateTemplate(id, DtoMapper.toProcessTemplate(request));
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(saved));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<ProcessTemplateDTO> publishMyProcess(@PathVariable Long id) {
        CurrentUser currentUser = requireBizAdmin();
        ProcessTemplate template = getOwnedTemplate(id);
        validateManagedBizType(currentUser, template.getBizTypeId());
        ProcessTemplate saved = processTemplateService.publishTemplate(id);
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(saved));
    }

    @PostMapping("/{id}/unpublish")
    public ApiResponse<ProcessTemplateDTO> unpublishMyProcess(@PathVariable Long id) {
        getOwnedTemplate(id);
        ProcessTemplate saved = processTemplateService.unpublishTemplate(id);
        return ApiResponse.success(DtoMapper.toProcessTemplateDTO(saved));
    }

    private ProcessTemplate getOwnedTemplate(Long id) {
        CurrentUser currentUser = requireBizAdmin();
        ProcessTemplate template = processTemplateService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("process not found"));
        if (template.getCreatedBy() == null || !template.getCreatedBy().equals(currentUser.getId())) {
            throw new AccessDeniedException("no permission to access this process");
        }
        return template;
    }

    private CurrentUser requireBizAdmin() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("current user is required");
        }
        String role = currentUser.getSystemRole();
        if (!"biz_admin".equals(role) && !"super_admin".equals(role)) {
            throw new AccessDeniedException("biz admin role is required");
        }
        return currentUser;
    }

    private void validateManagedBizType(CurrentUser currentUser, Long bizTypeId) {
        if (bizTypeId == null || "super_admin".equals(currentUser.getSystemRole())) {
            return;
        }
        Set<Long> managedIds = parseManagedBizTypeIds(currentUser.getManagedBizTypeIds());
        if (!managedIds.isEmpty() && !managedIds.contains(bizTypeId)) {
            throw new AccessDeniedException("no permission to manage this business type");
        }
    }

    private Set<Long> parseManagedBizTypeIds(String value) {
        if (!hasText(value)) {
            return Set.of();
        }
        String cleaned = value.replace("[", "").replace("]", "").replace("\"", "");
        if (!hasText(cleaned)) {
            return Set.of();
        }
        return Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(item -> item.matches("\\d+"))
                .map(Long::valueOf)
                .collect(Collectors.toSet());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
