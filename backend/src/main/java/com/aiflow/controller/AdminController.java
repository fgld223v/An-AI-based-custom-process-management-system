package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.UserAdminDTO;
import com.aiflow.model.Department;
import com.aiflow.model.SysUser;
import com.aiflow.repository.BizTypeDictRepository;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.SysUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 超管专属 — 用户管理 + 部门/用户/业务类型下拉数据，使用 JPA 避免 MyBatis JDK 代理问题。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final SysUserRepository sysUserRepository;
    private final DepartmentRepository departmentRepository;
    private final BizTypeDictRepository bizTypeDictRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/users")
    public ApiResponse<List<UserAdminDTO>> listUsers() {
        List<UserAdminDTO> users = sysUserRepository.findAllByOrderByIdAsc().stream()
                .map(this::toDTO)
                .toList();
        return ApiResponse.success(users);
    }

    @GetMapping("/users/{id}")
    public ApiResponse<UserAdminDTO> getUser(@PathVariable Long id) {
        SysUser user = sysUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return ApiResponse.success(toDTO(user));
    }

    @PostMapping("/users")
    public ApiResponse<UserAdminDTO> createUser(@Valid @RequestBody CreateUserRequest request) {
        if (sysUserRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("用户名已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        SysUser user = SysUser.builder()
                .username(request.getUsername().trim())
                .password(passwordEncoder.encode(request.getPassword().trim()))
                .nickname(request.getNickname() != null ? request.getNickname().trim() : request.getUsername())
                .role(mapLegacyRole(request.getSystemRole()))
                .systemRole(request.getSystemRole() != null ? request.getSystemRole() : "normal_user")
                .departmentId(request.getDepartmentId())
                .supervisorId(request.getSupervisorId())
                .managedBizTypeIds(request.getManagedBizTypeIds())
                .enabled(request.getEnabled() != null ? request.getEnabled() : 1)
                .deleted(0)
                .createdTime(now)
                .updatedTime(now)
                .build();
        sysUserRepository.save(user);
        return ApiResponse.success(toDTO(user));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<UserAdminDTO> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        SysUser user = sysUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (request.getNickname() != null) user.setNickname(request.getNickname().trim());
        if (request.getSystemRole() != null) {
            user.setSystemRole(request.getSystemRole());
            user.setRole(mapLegacyRole(request.getSystemRole()));
        }
        if (request.getDepartmentId() != null) user.setDepartmentId(request.getDepartmentId());
        if (request.getSupervisorId() != null) user.setSupervisorId(request.getSupervisorId());
        user.setManagedBizTypeIds(request.getManagedBizTypeIds());
        if (request.getEnabled() != null) user.setEnabled(request.getEnabled());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword().trim()));
        }
        user.setUpdatedTime(LocalDateTime.now());
        sysUserRepository.save(user);
        return ApiResponse.success(toDTO(user));
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Map<String, Object>> deleteUser(@PathVariable Long id) {
        SysUser user = sysUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        user.setDeleted(1);
        user.setUpdatedTime(LocalDateTime.now());
        sysUserRepository.save(user);
        return ApiResponse.success(Map.of("deleted", true, "id", id));
    }

    private UserAdminDTO toDTO(SysUser u) {
        UserAdminDTO dto = new UserAdminDTO();
        dto.setId(u.getId());
        dto.setUsername(u.getUsername());
        dto.setNickname(u.getNickname());
        dto.setRole(u.getRole());
        dto.setSystemRole(u.getSystemRole());
        dto.setDepartmentId(u.getDepartmentId());
        dto.setSupervisorId(u.getSupervisorId());
        dto.setManagedBizTypeIds(u.getManagedBizTypeIds());
        dto.setEnabled(u.getEnabled());
        dto.setCreatedTime(u.getCreatedTime());
        dto.setUpdatedTime(u.getUpdatedTime());
        return dto;
    }

    // ================================================================
    // 下拉选择数据
    // ================================================================

    /** 部门选项（id + 名称） */
    @GetMapping("/departments/options")
    public ApiResponse<List<Map<String, Object>>> departmentOptions() {
        List<Map<String, Object>> list = departmentRepository.findByDeletedOrderBySortOrder(0).stream()
                .map(d -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("value", d.getId());
                    m.put("label", d.getDeptName());
                    return m;
                }).toList();
        return ApiResponse.success(list);
    }

    /** 用户选项（id + 名称），用于选择上级/负责人 */
    @GetMapping("/users/options")
    public ApiResponse<List<Map<String, Object>>> userOptions() {
        List<Map<String, Object>> list = sysUserRepository.findAllByOrderByIdAsc().stream()
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("value", u.getId());
                    m.put("label", u.getNickname() != null ? u.getNickname() : u.getUsername());
                    return m;
                }).toList();
        return ApiResponse.success(list);
    }

    /** 业务类型选项（id + 名称），用于选择管辖业务 */
    @GetMapping("/biz-types/options")
    public ApiResponse<List<Map<String, Object>>> bizTypeOptions() {
        List<Map<String, Object>> list = bizTypeDictRepository.findByDeletedAndEnabledOrderBySortOrderAsc(0, 1).stream()
                .map(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("value", b.getId());
                    m.put("label", b.getTypeName());
                    return m;
                }).toList();
        return ApiResponse.success(list);
    }

    private String mapLegacyRole(String systemRole) {
        if (systemRole == null) return "USER";
        return switch (systemRole) {
            case "super_admin" -> "ADMIN";
            case "biz_admin" -> "MANAGER";
            default -> "USER";
        };
    }

    @Data
    public static class CreateUserRequest {
        @NotBlank private String username;
        @NotBlank private String password;
        private String nickname;
        private String systemRole;
        private Long departmentId;
        private Long supervisorId;
        private String managedBizTypeIds;
        private Integer enabled;
    }

    @Data
    public static class UpdateUserRequest {
        private String nickname;
        private String systemRole;
        private String password;
        private Long departmentId;
        private Long supervisorId;
        private String managedBizTypeIds;
        private Integer enabled;
    }
}