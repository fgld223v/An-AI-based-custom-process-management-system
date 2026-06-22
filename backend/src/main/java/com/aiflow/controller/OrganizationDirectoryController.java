package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/organization-directory")
public class OrganizationDirectoryController {

    private final DepartmentRepository departmentRepository;
    private final SysUserRepository sysUserRepository;

    @GetMapping("/departments")
    public ApiResponse<List<Map<String, Object>>> listDepartments() {
        List<Map<String, Object>> result = departmentRepository.findByDeletedOrderBySortOrder(0).stream()
                .filter(item -> Integer.valueOf(1).equals(item.getStatus()))
                .map(item -> {
                    Map<String, Object> option = new LinkedHashMap<>();
                    option.put("id", item.getId());
                    option.put("name", item.getDeptName());
                    option.put("parentId", item.getParentId());
                    return option;
                })
                .toList();
        return ApiResponse.success(result);
    }

    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> listUsers() {
        List<Map<String, Object>> result = sysUserRepository.findAllByOrderByIdAsc().stream()
                .filter(item -> !Integer.valueOf(1).equals(item.getDeleted()))
                .filter(item -> Integer.valueOf(1).equals(item.getEnabled()))
                .map(item -> {
                    Map<String, Object> option = new LinkedHashMap<>();
                    option.put("id", item.getId());
                    option.put("name", item.getNickname() != null ? item.getNickname() : item.getUsername());
                    option.put("departmentId", item.getDepartmentId());
                    return option;
                })
                .toList();
        return ApiResponse.success(result);
    }
}
