package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.DtoMapper;
import com.aiflow.dto.ProcessFragmentCreateRequest;
import com.aiflow.dto.ProcessFragmentDTO;
import com.aiflow.dto.ProcessFragmentUpdateRequest;
import com.aiflow.model.ProcessFragment;
import com.aiflow.service.ProcessFragmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程片段控制器，提供可复用流程片段的 CRUD 与发布接口。
 *
 * <p>基础路径: /api/process-fragments</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/process-fragments")
public class ProcessFragmentController {

    private final ProcessFragmentService processFragmentService;

    /**
     * GET /api/process-fragments — 查询所有流程片段。
     */
    @GetMapping
    public ApiResponse<List<ProcessFragmentDTO>> listFragments() {
        List<ProcessFragmentDTO> result = processFragmentService.listFragments().stream()
                .map(DtoMapper::toProcessFragmentDTO)
                .toList();
        return ApiResponse.success(result);
    }

    /**
     * GET /api/process-fragments/{id} — 查询单个流程片段详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<ProcessFragmentDTO> getFragment(@PathVariable Long id) {
        ProcessFragment fragment = processFragmentService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("fragment not found"));
        return ApiResponse.success(DtoMapper.toProcessFragmentDTO(fragment));
    }

    /**
     * POST /api/process-fragments — 创建新的流程片段。
     */
    @PostMapping
    public ApiResponse<ProcessFragmentDTO> createFragment(@RequestBody ProcessFragmentCreateRequest request) {
        ProcessFragment saved = processFragmentService.createFragment(DtoMapper.toProcessFragment(request));
        return ApiResponse.success(DtoMapper.toProcessFragmentDTO(saved));
    }

    /**
     * PUT /api/process-fragments/{id} — 更新流程片段。
     */
    @PutMapping("/{id}")
    public ApiResponse<ProcessFragmentDTO> updateFragment(@PathVariable Long id,
                                                          @RequestBody ProcessFragmentUpdateRequest request) {
        ProcessFragment saved = processFragmentService.updateFragment(id, DtoMapper.toProcessFragment(request));
        return ApiResponse.success(DtoMapper.toProcessFragmentDTO(saved));
    }

    /**
     * POST /api/process-fragments/{id}/publish — 发布流程片段。
     */
    @PostMapping("/{id}/publish")
    public ApiResponse<ProcessFragmentDTO> publishFragment(@PathVariable Long id) {
        ProcessFragment saved = processFragmentService.publishFragment(id);
        return ApiResponse.success(DtoMapper.toProcessFragmentDTO(saved));
    }
}
