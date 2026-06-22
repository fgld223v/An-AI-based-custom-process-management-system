package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.UserAdminDTO;
import com.aiflow.model.BizTypeDict;
import com.aiflow.model.Department;
import com.aiflow.model.SysUser;
import com.aiflow.repository.BizTypeDictRepository;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.util.ExcelUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private final ObjectMapper objectMapper;

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

    // ================================================================
    // Excel 导入 / 导出 / 模板
    // ================================================================

    private static final String[] USER_TEMPLATE_HEADERS = {
            "用户名*", "昵称", "密码*", "系统角色", "所属部门编码", "主管用户名", "管辖业务类型ID", "启用"
    };

    /** 下载用户导入模板 */
    @GetMapping("/users/template")
    public void downloadUserTemplate(HttpServletResponse response) throws IOException {
        setExcelResponse(response, "用户导入模板.xlsx");
        try (XSSFWorkbook wb = ExcelUtil.createWorkbook("用户导入模板", USER_TEMPLATE_HEADERS, List.of())) {
            wb.write(response.getOutputStream());
        }
    }

    /** 导出全部用户到 Excel — 字段均显示可读名称 */
    @GetMapping("/users/export")
    public void exportUsers(HttpServletResponse response) throws IOException {
        List<SysUser> users = sysUserRepository.findAllByOrderByIdAsc();
        // 预加载 lookup 映射
        Map<Long, String> deptNameMap = new HashMap<>();
        Map<Long, String> bizTypeNameMap = new HashMap<>();
        for (Department d : departmentRepository.findByDeletedOrderBySortOrder(0)) {
            deptNameMap.put(d.getId(), d.getDeptName());
        }
        for (BizTypeDict b : bizTypeDictRepository.findByDeletedAndEnabledOrderBySortOrderAsc(0, 1)) {
            bizTypeNameMap.put(b.getId(), b.getTypeName());
        }

        List<String[]> rows = new ArrayList<>();
        for (SysUser u : users) {
            rows.add(new String[]{
                    u.getUsername(),
                    u.getNickname() != null ? u.getNickname() : "",
                    "", // 密码不导出
                    systemRoleLabel(u.getSystemRole()),
                    deptNameOf(u.getDepartmentId(), deptNameMap),
                    supervisorUsernameOf(u.getSupervisorId()),
                    bizTypeNamesOf(u.getManagedBizTypeIds(), bizTypeNameMap),
                    u.getEnabled() != null && u.getEnabled() == 1 ? "是" : "否"
            });
        }
        setExcelResponse(response, "用户数据.xlsx");
        try (XSSFWorkbook wb = ExcelUtil.createWorkbook("用户数据", USER_TEMPLATE_HEADERS, rows)) {
            wb.write(response.getOutputStream());
        }
    }

    /** 导入用户 — 上传 Excel，批量创建 */
    @PostMapping("/users/import")
    public ApiResponse<Map<String, Object>> importUsers(@RequestParam("file") MultipartFile file) {
        List<Map<String, String>> rows;
        try {
            rows = ExcelUtil.readExcel(file.getInputStream());
        } catch (Exception e) {
            throw new IllegalArgumentException("无法读取 Excel 文件，请检查文件格式");
        }

        int success = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int rowNum = i + 2; // Excel 行号（从 1 开始，第 1 行是表头）
            try {
                String username = row.getOrDefault("用户名*", "").trim();
                String password = row.getOrDefault("密码*", "").trim();
                if (username.isEmpty() || password.isEmpty()) {
                    errors.add(Map.of("row", rowNum, "reason", "用户名或密码为空"));
                    continue;
                }
                if (sysUserRepository.existsByUsername(username)) {
                    errors.add(Map.of("row", rowNum, "reason", "用户名已存在: " + username));
                    continue;
                }

                String nickname = row.getOrDefault("昵称", "").trim();
                String systemRole = row.getOrDefault("系统角色", "").trim();
                if (systemRole.isEmpty()) systemRole = "normal_user";
                if (!List.of("super_admin", "biz_admin", "normal_user").contains(systemRole)) {
                    errors.add(Map.of("row", rowNum, "reason", "系统角色无效: " + systemRole));
                    continue;
                }

                Long departmentId = resolveDeptId(row.getOrDefault("所属部门编码", "").trim());
                Long supervisorId = resolveUserId(row.getOrDefault("主管用户名", "").trim());
                String enabledStr = row.getOrDefault("启用", "").trim();
                int enabled = enabledStr.isEmpty() || "是".equals(enabledStr) || "1".equals(enabledStr) ? 1 : 0;

                SysUser user = SysUser.builder()
                        .username(username)
                        .password(passwordEncoder.encode(password))
                        .nickname(!nickname.isEmpty() ? nickname : username)
                        .role(mapLegacyRole(systemRole))
                        .systemRole(systemRole)
                        .departmentId(departmentId)
                        .supervisorId(supervisorId)
                        .managedBizTypeIds(nullToEmpty(row.getOrDefault("管辖业务类型ID", "").trim()))
                        .enabled(enabled)
                        .deleted(0)
                        .createdTime(now)
                        .updatedTime(now)
                        .build();
                sysUserRepository.save(user);
                success++;
            } catch (Exception e) {
                errors.add(Map.of("row", rowNum, "reason", e.getMessage()));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", rows.size());
        result.put("success", success);
        result.put("failed", errors.size());
        result.put("errors", errors);
        return ApiResponse.success(result);
    }

    private Long resolveDeptId(String deptCode) {
        if (deptCode.isEmpty()) return null;
        Department dept = departmentRepository.findFirstByDeptCode(deptCode);
        if (dept == null) throw new IllegalArgumentException("部门编码不存在: " + deptCode);
        return dept.getId();
    }

    private Long resolveUserId(String username) {
        if (username.isEmpty()) return null;
        SysUser user = sysUserRepository.findByUsername(username).orElse(null);
        if (user == null) throw new IllegalArgumentException("用户不存在: " + username);
        return user.getId();
    }

    private String deptCodeOf(Long deptId) {
        if (deptId == null) return "";
        return departmentRepository.findById(deptId).map(Department::getDeptCode).orElse("");
    }

    private String deptNameOf(Long deptId, Map<Long, String> nameMap) {
        if (deptId == null) return "";
        return nameMap.getOrDefault(deptId, "");
    }

    private String supervisorUsernameOf(Long userId) {
        if (userId == null) return "";
        return sysUserRepository.findById(userId).map(SysUser::getUsername).orElse("");
    }

    private String systemRoleLabel(String systemRole) {
        if (systemRole == null) return "普通用户";
        return switch (systemRole) {
            case "super_admin" -> "超级管理员";
            case "biz_admin" -> "业务管理员";
            default -> "普通用户";
        };
    }

    private String bizTypeNamesOf(String managedBizTypeIds, Map<Long, String> nameMap) {
        if (managedBizTypeIds == null || managedBizTypeIds.isBlank()) return "";
        try {
            List<Long> ids = objectMapper.readValue(managedBizTypeIds,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Long>>() {});
            return ids.stream()
                    .map(id -> nameMap.getOrDefault(id, String.valueOf(id)))
                    .collect(Collectors.joining("、"));
        } catch (Exception e) {
            return managedBizTypeIds; // 非标准格式时原样返回
        }
    }

    private String nullToEmpty(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private void setExcelResponse(HttpServletResponse response, String filename) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
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