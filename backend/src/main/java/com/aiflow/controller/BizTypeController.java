package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.BizTypeDTO;
import com.aiflow.dto.DtoMapper;
import com.aiflow.service.BizTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/biz-types")
public class BizTypeController {

    private final BizTypeService bizTypeService;

    @GetMapping
    public ApiResponse<List<BizTypeDTO>> listEnabledBizTypes() {
        List<BizTypeDTO> result = bizTypeService.listEnabledBizTypes().stream()
                .map(DtoMapper::toBizTypeDTO)
                .toList();
        return ApiResponse.success(result);
    }
}
