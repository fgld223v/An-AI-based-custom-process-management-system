package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.DtoMapper;
import com.aiflow.dto.ProcessInstanceCreateRequest;
import com.aiflow.dto.ProcessInstanceDTO;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.service.ProcessInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/process-instances")
public class ProcessInstanceController {

    private final ProcessInstanceService processInstanceService;
    private final ProcessTemplateRepository processTemplateRepository;

    /**
     * 发起流程
     */
    @PostMapping
    public ApiResponse<ProcessInstanceDTO> startProcess(@RequestBody ProcessInstanceCreateRequest request) {
        ProcessInstance instance = processInstanceService.startProcess(
                request.getTemplateId(),
                request.getApplicantId(),
                request.getTitle(),
                request.getFormData(),
                request.getVariables()
        );

        // 获取模板名称
        String templateName = processTemplateRepository.findById(request.getTemplateId())
                .map(ProcessTemplate::getTemplateName)
                .orElse(null);

        return ApiResponse.success(DtoMapper.toProcessInstanceDTO(instance, templateName));
    }

    /**
     * 根据ID查询流程实例
     */
    @GetMapping("/{id}")
    public ApiResponse<ProcessInstanceDTO> getProcessInstance(@PathVariable Long id) {
        ProcessInstance instance = processInstanceService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("流程实例不存在: " + id));

        String templateName = processTemplateRepository.findById(instance.getTemplateId())
                .map(ProcessTemplate::getTemplateName)
                .orElse(null);

        return ApiResponse.success(DtoMapper.toProcessInstanceDTO(instance, templateName));
    }

    /**
     * 查询我的流程列表
     */
    @GetMapping("/my")
    public ApiResponse<List<ProcessInstanceDTO>> listMyProcesses(@RequestParam Long applicantId) {
        List<ProcessInstance> instances = processInstanceService.listMyProcesses(applicantId);
        List<ProcessInstanceDTO> result = instances.stream()
                .map(instance -> {
                    String templateName = processTemplateRepository.findById(instance.getTemplateId())
                            .map(ProcessTemplate::getTemplateName)
                            .orElse(null);
                    return DtoMapper.toProcessInstanceDTO(instance, templateName);
                })
                .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    /**
     * 查询所有流程实例
     */
    @GetMapping
    public ApiResponse<List<ProcessInstanceDTO>> listAllProcesses() {
        List<ProcessInstance> instances = processInstanceService.listAllProcesses();
        List<ProcessInstanceDTO> result = instances.stream()
                .map(instance -> {
                    String templateName = processTemplateRepository.findById(instance.getTemplateId())
                            .map(ProcessTemplate::getTemplateName)
                            .orElse(null);
                    return DtoMapper.toProcessInstanceDTO(instance, templateName);
                })
                .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    /**
     * 根据状态查询流程实例
     */
    @GetMapping("/status/{status}")
    public ApiResponse<List<ProcessInstanceDTO>> listByStatus(@PathVariable String status) {
        List<ProcessInstance> instances = processInstanceService.listByStatus(status);
        List<ProcessInstanceDTO> result = instances.stream()
                .map(instance -> {
                    String templateName = processTemplateRepository.findById(instance.getTemplateId())
                            .map(ProcessTemplate::getTemplateName)
                            .orElse(null);
                    return DtoMapper.toProcessInstanceDTO(instance, templateName);
                })
                .collect(Collectors.toList());
        return ApiResponse.success(result);
    }
}
