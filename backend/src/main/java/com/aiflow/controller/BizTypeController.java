package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.BizTypeDTO;
import com.aiflow.dto.DtoMapper;
import com.aiflow.security.CurrentUser;
import com.aiflow.security.SecurityUtils;
import com.aiflow.service.BizTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/biz-types")
public class BizTypeController {

    private final BizTypeService bizTypeService;

    @GetMapping
    public ApiResponse<List<BizTypeDTO>> listEnabledBizTypes() {
        List<BizTypeDTO> all = bizTypeService.listEnabledBizTypes().stream()
                .map(DtoMapper::toBizTypeDTO)
                .toList();

        // 对受限的业务管理员，仅返回其管辖范围内的业务类型
        CurrentUser currentUser = SecurityUtils.currentUser();
        if (currentUser != null && "biz_admin".equals(currentUser.getSystemRole())) {
            Set<Long> managedIds = parseManagedBizTypeIds(currentUser.getManagedBizTypeIds());
            if (!managedIds.isEmpty()) {
                List<BizTypeDTO> filtered = all.stream()
                        .filter(bt -> managedIds.contains(bt.getId()))
                        .toList();
                return ApiResponse.success(filtered);
            }
        }

        return ApiResponse.success(all);
    }

    private Set<Long> parseManagedBizTypeIds(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        String cleaned = value.replace("[", "").replace("]", "").replace("\"", "");
        if (cleaned.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(item -> item.matches("\\d+"))
                .map(Long::valueOf)
                .collect(Collectors.toSet());
    }
}
