package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.UserAdminDTO;
import com.aiflow.model.BizTypeDict;
import com.aiflow.model.Department;
import com.aiflow.model.SysUser;
import com.aiflow.repository.BizTypeDictRepository;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.repository.UserWorkflowRoleRepository;
import com.aiflow.security.SecurityUtils;
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
 * 管理员控制器 -- 超管专属，提供用户管理、Excel 导入导出及下拉数据接口。
 *
 * <p>使用 JPA Repository 直接操作数据库，避免 MyBatis JDK 代理问题。
 *
 * <p>端点一览：
 * <ul>
 *   <li>GET    /api/admin/users                  -- 用户列表</li>
 *   <li>GET    /api/admin/users/{id}             -- 用户详情</li>
 *   <li>POST   /api/admin/users                  -- 创建用户</li>
 *   <li>PUT    /api/admin/users/{id}             -- 更新用户</li>
 *   <li>DELETE /api/admin/users/{id}             -- 删除用户（软删除）</li>
 *   <li>GET    /api/admin/users/template          -- 下载用户导入 Excel 模板</li>
 *   <li>GET    /api/admin/users/export            -- 导出全部用户到 Excel</li>
 *   <li>POST   /api/admin/users/import            -- 批量导入用户（上传 Excel）</li>
 *   <li>GET    /api/admin/departments/options     -- 部门下拉选项</li>
 *   <li>GET    /api/admin/users/options           -- 用户下拉选项（用于选择上级/负责人）</li>
 *   <li>GET    /api/admin/biz-types/options       -- 业务类型下拉选项</li>
 * </ul>
 *
 * <p>所有端点均需超级管理员权限（建议在 SecurityConfig 中配置）。
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
    private final UserWorkflowRoleRepository userWorkflowRoleRepository;

    /**
     * 用户列表。
     *
     * <p>GET /api/admin/users -- 返回所有未删除的用户，按 ID 升序排列。
     * 需要超级管理员权限。
     *
     * @return 用户管理 DTO 列表
     */
    @GetMapping("/users")
    public ApiResponse<List<UserAdminDTO>> listUsers() {
        List<UserAdminDTO> users = sysUserRepository.findByDeletedOrderByIdAsc(0).stream()
                .map(this::toDTO)
                .toList();
        return ApiResponse.success(users);
    }

    /**
     * 用户详情。
     *
     * <p>GET /api/admin/users/{id} -- 查询指定用户的详细信息。
     * 需要超级管理员权限。
     *
     * @param id 用户 ID
     * @return 用户管理 DTO
     * @throws IllegalArgumentException 用户不存在
     */
    @GetMapping("/users/{id}")
    public ApiResponse<UserAdminDTO> getUser(@PathVariable Long id) {
        SysUser user = sysUserRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return ApiResponse.success(toDTO(user));
    }

    /**
     * 创建用户。
     *
     * <p>POST /api/admin/users -- 创建新用户，密码使用 BCrypt 加密。
     * 支持指定系统角色、部门、上级、管辖业务类型等。需要超级管理员权限。
     *
     * @param request 创建请求体
     * @return 创建成功的用户信息
     * @throws IllegalArgumentException 用户名已存在
     */
    @PostMapping("/users")
    public ApiResponse<UserAdminDTO> createUser(@Valid @RequestBody CreateUserRequest request) {
        // 检查用户名唯一性
        if (sysUserRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("用户名已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        // 构建用户实体：密码 BCrypt 加密，默认启用、未删除
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

    /**
     * 更新用户信息。
     *
     * <p>PUT /api/admin/users/{id} -- 更新用户昵称、手机、邮箱、角色、部门、上级等字段。
     * 仅更新请求中非空的字段，密码为空时不更新。需要超级管理员权限。
     *
     * @param id      用户 ID
     * @param request 更新请求体（所有字段可选）
     * @return 更新后的用户信息
     * @throws IllegalArgumentException 用户不存在
     */
    @PutMapping("/users/{id}")
    public ApiResponse<UserAdminDTO> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        SysUser user = sysUserRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        // 仅更新请求中非空的字段（部分更新语义）
        if (request.getNickname() != null) user.setNickname(request.getNickname().trim());
        if (request.getPhone() != null) user.setPhone(request.getPhone().trim());
        if (request.getEmail() != null) user.setEmail(request.getEmail().trim());
        if (request.getSystemRole() != null) {
            user.setSystemRole(request.getSystemRole());
            // 同步更新旧版 role 字段以保持兼容
            user.setRole(mapLegacyRole(request.getSystemRole()));
        }
        if (request.getDepartmentId() != null) user.setDepartmentId(request.getDepartmentId());
        if (request.getSupervisorId() != null) user.setSupervisorId(request.getSupervisorId());
        user.setManagedBizTypeIds(request.getManagedBizTypeIds());
        if (request.getEnabled() != null) user.setEnabled(request.getEnabled());
        // 密码不为空时才更新（避免误设空密码）
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword().trim()));
        }
        user.setUpdatedTime(LocalDateTime.now());
        sysUserRepository.save(user);
        return ApiResponse.success(toDTO(user));
    }

    /**
     * 删除用户（软删除）。
     *
     * <p>DELETE /api/admin/users/{id} -- 逻辑删除用户（enabled=0, deleted=1），
     * 不会物理删除数据库记录。删除前会进行多项安全校验。需要超级管理员权限。
     *
     * <p>校验规则：
     * <ul>
     *   <li>不能删除自己</li>
     *   <li>系统必须至少保留一个超级管理员</li>
     *   <li>不能删除部门负责人</li>
     *   <li>不能删除仍有下属的主管</li>
     *   <li>不能删除仍有流程角色授权的用户</li>
     * </ul>
     *
     * @param id 用户 ID
     * @return 删除结果，含 deleted=true 和用户 ID
     * @throws IllegalStateException    安全校验不通过
     * @throws IllegalArgumentException 用户不存在
     */
    @DeleteMapping("/users/{id}")
    public ApiResponse<Map<String, Object>> deleteUser(@PathVariable Long id) {
        SysUser user = sysUserRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        // 不能删除自己
        if (id.equals(SecurityUtils.currentUserId())) {
            throw new IllegalStateException("不能删除当前登录账号");
        }
        // 最后一个超管不能删除
        if ("super_admin".equals(user.getSystemRole())
                && sysUserRepository.countBySystemRoleAndDeleted("super_admin", 0) <= 1) {
            throw new IllegalStateException("系统必须至少保留一个超级管理员");
        }
        // 部门负责人不能删除
        if (departmentRepository.existsByLeaderUserIdAndDeleted(id, 0)) {
            throw new IllegalStateException("该用户仍是部门负责人，请先在部门管理中更换负责人");
        }
        // 有下属的主管不能删除
        if (sysUserRepository.existsBySupervisorIdAndDeleted(id, 0)) {
            throw new IllegalStateException("该用户仍有直属下属，请先为下属重新设置直属上级");
        }
        // 有流程角色授权的用户不能删除
        if (userWorkflowRoleRepository.existsByUserIdAndDeleted(id, 0)) {
            throw new IllegalStateException("该用户仍有流程角色授权，请先在流程角色管理中撤销授权");
        }
        // 执行软删除
        user.setEnabled(0);
        user.setDeleted(1);
        user.setUpdatedTime(LocalDateTime.now());
        sysUserRepository.save(user);
        return ApiResponse.success(Map.of("deleted", true, "id", id));
    }

    // ================================================================
    // Excel 导入 / 导出 / 模板
    // ================================================================

    /** Excel 导入模板的表头定义 */
    private static final String[] USER_TEMPLATE_HEADERS = {
            "用户名*", "昵称", "密码*", "系统角色", "所属部门编码", "主管用户名", "管辖业务类型ID", "启用"
    };

    /**
     * 下载用户导入 Excel 模板。
     *
     * <p>GET /api/admin/users/template -- 返回一个空白 Excel 文件，包含表头行，
     * 用户可按模板格式填写后上传导入。需要超级管理员权限。
     *
     * @param response HTTP 响应，直接写入 Excel 流
     */
    @GetMapping("/users/template")
    public void downloadUserTemplate(HttpServletResponse response) throws IOException {
        setExcelResponse(response, "用户导入模板.xlsx");
        try (XSSFWorkbook wb = ExcelUtil.createWorkbook("用户导入模板", USER_TEMPLATE_HEADERS, List.of())) {
            wb.write(response.getOutputStream());
        }
    }

    /**
     * 导出全部用户到 Excel。
     *
     * <p>GET /api/admin/users/export -- 导出系统中所有未删除的用户数据，
     * 字段显示为可读名称（如部门名称、系统角色中文名等），密码列不导出。
     * 需要超级管理员权限。
     *
     * @param response HTTP 响应，直接写入 Excel 流
     */
    @GetMapping("/users/export")
    public void exportUsers(HttpServletResponse response) throws IOException {
        List<SysUser> users = sysUserRepository.findByDeletedOrderByIdAsc(0);
        // 预加载 lookup 映射，避免 N+1 查询
        Map<Long, String> deptNameMap = new HashMap<>();
        Map<Long, String> bizTypeNameMap = new HashMap<>();
        for (Department d : departmentRepository.findByDeletedOrderBySortOrder(0)) {
            deptNameMap.put(d.getId(), d.getDeptName());
        }
        for (BizTypeDict b : bizTypeDictRepository.findByDeletedAndEnabledOrderBySortOrderAsc(0, 1)) {
            bizTypeNameMap.put(b.getId(), b.getTypeName());
        }

        // 逐行构建导出数据
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

    /**
     * 批量导入用户（上传 Excel）。
     *
     * <p>POST /api/admin/users/import -- 上传 Excel 文件，逐行解析并批量创建用户。
     * 返回导入统计结果（总数、成功数、失败数及失败原因）。需要超级管理员权限。
     *
     * @param file 上传的 Excel 文件（MultipartFile）
     * @return 导入结果，包含 total / success / failed / errors 字段
     */
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

        // 逐行处理，遇到错误行跳过并记录原因，不中断整体导入
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int rowNum = i + 2; // Excel 行号（从 1 开始，第 1 行是表头）
            try {
                String username = row.getOrDefault("用户名*", "").trim();
                String password = row.getOrDefault("密码*", "").trim();
                // 校验必填字段
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
                // 校验系统角色取值
                if (!List.of("super_admin", "biz_admin", "normal_user").contains(systemRole)) {
                    errors.add(Map.of("row", rowNum, "reason", "系统角色无效: " + systemRole));
                    continue;
                }

                // 解析部门编码和主管用户名
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

    /** 根据部门编码解析部门 ID */
    private Long resolveDeptId(String deptCode) {
        if (deptCode.isEmpty()) return null;
        Department dept = departmentRepository.findFirstByDeptCode(deptCode);
        if (dept == null) throw new IllegalArgumentException("部门编码不存在: " + deptCode);
        return dept.getId();
    }

    /** 根据用户名解析用户 ID */
    private Long resolveUserId(String username) {
        if (username.isEmpty()) return null;
        SysUser user = sysUserRepository.findByUsernameAndDeleted(username, 0).orElse(null);
        if (user == null) throw new IllegalArgumentException("用户不存在: " + username);
        return user.getId();
    }

    /** 根据部门 ID 获取部门编码 */
    private String deptCodeOf(Long deptId) {
        if (deptId == null) return "";
        return departmentRepository.findById(deptId).map(Department::getDeptCode).orElse("");
    }

    /** 根据部门 ID 和预加载的映射获取部门名称 */
    private String deptNameOf(Long deptId, Map<Long, String> nameMap) {
        if (deptId == null) return "";
        return nameMap.getOrDefault(deptId, "");
    }

    /** 根据用户 ID 获取用户名 */
    private String supervisorUsernameOf(Long userId) {
        if (userId == null) return "";
        return sysUserRepository.findById(userId).map(SysUser::getUsername).orElse("");
    }

    /** 将系统角色代码转换为中文显示名 */
    private String systemRoleLabel(String systemRole) {
        if (systemRole == null) return "普通用户";
        return switch (systemRole) {
            case "super_admin" -> "超级管理员";
            case "biz_admin" -> "业务管理员";
            default -> "普通用户";
        };
    }

    /**
     * 将管辖业务类型 ID 列表转为业务类型名称。
     * managedBizTypeIds 以 JSON 数组格式存储（如 [1,2,3]）。
     */
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

    /** 空字符串转 null，用于导入时处理空白单元格 */
    private String nullToEmpty(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** 设置 Excel 下载的 HTTP 响应头 */
    private void setExcelResponse(HttpServletResponse response, String filename) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
    }

    /** 将 SysUser 实体转换为管理 DTO */
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

    /**
     * 部门下拉选项。
     *
     * <p>GET /api/admin/departments/options -- 返回所有部门的下拉选择数据
     * （value=部门ID, label=部门名称），用于前端下拉框。需要登录。
     *
     * @return 部门选项列表
     */
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

    /**
     * 用户下拉选项。
     *
     * <p>GET /api/admin/users/options -- 返回所有启用用户的下拉选择数据
     * （value=用户ID, label=用户昵称/用户名），用于选择直属上级或部门负责人。需要登录。
     *
     * @return 用户选项列表（仅启用用户）
     */
    @GetMapping("/users/options")
    public ApiResponse<List<Map<String, Object>>> userOptions() {
        List<Map<String, Object>> list = sysUserRepository.findByDeletedOrderByIdAsc(0).stream()
                .filter(u -> Integer.valueOf(1).equals(u.getEnabled()))
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("value", u.getId());
                    m.put("label", u.getNickname() != null ? u.getNickname() : u.getUsername());
                    return m;
                }).toList();
        return ApiResponse.success(list);
    }

    /**
     * 业务类型下拉选项。
     *
     * <p>GET /api/admin/biz-types/options -- 返回所有启用的业务类型下拉数据
     * （value=业务类型ID, label=业务类型名称），用于选择管辖业务。需要登录。
     *
     * @return 业务类型选项列表
     */
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

    /**
     * 将新的系统角色映射为旧版 role 字段值，保持向后兼容。
     *
     * @param systemRole 系统角色（super_admin / biz_admin / normal_user）
     * @return 旧版角色名（ADMIN / MANAGER / USER）
     */
    private String mapLegacyRole(String systemRole) {
        if (systemRole == null) return "USER";
        return switch (systemRole) {
            case "super_admin" -> "ADMIN";
            case "biz_admin" -> "MANAGER";
            default -> "USER";
        };
    }

    /** 创建用户请求体 */
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

    /** 更新用户请求体（所有字段均可选，仅更新非空字段） */
    @Data
    public static class UpdateUserRequest {
        private String nickname;
        private String phone;
        private String email;
        private String systemRole;
        private String password;
        private Long departmentId;
        private Long supervisorId;
        private String managedBizTypeIds;
        private Integer enabled;
    }
}
