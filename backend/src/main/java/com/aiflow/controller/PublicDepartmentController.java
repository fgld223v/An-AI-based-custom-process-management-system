package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.model.Department;
import com.aiflow.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开的部门列表接口 —— 所有已登录用户均可访问。
 * 用于个人设置页面选择所属部门，不包含管理员操作。
 */
@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class PublicDepartmentController {

    private final DepartmentRepository departmentRepository;

    @GetMapping
    public ApiResponse<List<Department>> listDepartments() {
        return ApiResponse.success(departmentRepository.findByDeletedOrderBySortOrder(0));
    }
}
