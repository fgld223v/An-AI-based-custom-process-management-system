package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.model.Department;
import com.aiflow.repository.DepartmentRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 超管专属 — 部门管理。
 */
@RestController
@RequestMapping("/api/admin/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentRepository departmentRepository;

    @GetMapping
    public ApiResponse<List<Department>> listDepartments() {
        return ApiResponse.success(departmentRepository.findByDeletedOrderBySortOrder(0));
    }

    @GetMapping("/{id}")
    public ApiResponse<Department> getDepartment(@PathVariable Long id) {
        Department dept = departmentRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new IllegalArgumentException("部门不存在"));
        return ApiResponse.success(dept);
    }

    @PostMapping
    public ApiResponse<Department> createDepartment(@RequestBody CreateDeptRequest request) {
        Department dept = Department.builder()
                .parentId(request.getParentId())
                .deptCode(request.getDeptCode().trim())
                .deptName(request.getDeptName().trim())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .leaderUserId(request.getLeaderUserId())
                .status(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deleted(0)
                .build();
        return ApiResponse.success(departmentRepository.save(dept));
    }

    @PutMapping("/{id}")
    public ApiResponse<Department> updateDepartment(@PathVariable Long id, @RequestBody CreateDeptRequest request) {
        Department dept = departmentRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new IllegalArgumentException("部门不存在"));
        if (request.getParentId() != null) dept.setParentId(request.getParentId());
        if (request.getDeptCode() != null) dept.setDeptCode(request.getDeptCode().trim());
        if (request.getDeptName() != null) dept.setDeptName(request.getDeptName().trim());
        if (request.getSortOrder() != null) dept.setSortOrder(request.getSortOrder());
        if (request.getLeaderUserId() != null) dept.setLeaderUserId(request.getLeaderUserId());
        dept.setUpdatedAt(LocalDateTime.now());
        return ApiResponse.success(departmentRepository.save(dept));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> deleteDepartment(@PathVariable Long id) {
        Department dept = departmentRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new IllegalArgumentException("部门不存在"));
        dept.setDeleted(1);
        dept.setUpdatedAt(LocalDateTime.now());
        departmentRepository.save(dept);
        return ApiResponse.success(Map.of("deleted", true, "id", id));
    }

    @Data
    public static class CreateDeptRequest {
        private Long parentId;
        private String deptCode;
        private String deptName;
        private Integer sortOrder;
        private Long leaderUserId;
    }
}
