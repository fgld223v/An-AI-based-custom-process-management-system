package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.model.Department;
import com.aiflow.model.SysUser;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.util.ExcelUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 超管专属 — 部门管理。
 */
@RestController
@RequestMapping("/api/admin/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentRepository departmentRepository;
    private final SysUserRepository sysUserRepository;

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
        if (request.getParentId() != null) {
            departmentRepository.findByIdAndDeleted(request.getParentId(), 0)
                    .orElseThrow(() -> new IllegalArgumentException("父部门不存在或已删除"));
        }
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
        if (request.getParentId() != null) {
            if (!request.getParentId().equals(dept.getId())) {
                departmentRepository.findByIdAndDeleted(request.getParentId(), 0)
                        .orElseThrow(() -> new IllegalArgumentException("父部门不存在或已删除"));
            }
            dept.setParentId(request.getParentId());
        }
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
        if (departmentRepository.existsByParentIdAndDeleted(id, 0)) {
            throw new IllegalStateException("该部门仍有下级部门，请先调整组织层级");
        }
        if (sysUserRepository.existsByDepartmentIdAndDeleted(id, 0)) {
            throw new IllegalStateException("该部门仍有在职用户，请先转移或删除部门成员");
        }
        dept.setStatus(0);
        dept.setDeleted(1);
        dept.setUpdatedAt(LocalDateTime.now());
        departmentRepository.save(dept);
        return ApiResponse.success(Map.of("deleted", true, "id", id));
    }

    // ================================================================
    // Excel 导入 / 导出 / 模板
    // ================================================================

    private static final String[] DEPT_TEMPLATE_HEADERS = {
            "父部门编码", "部门编码*", "部门名称*", "排序", "负责人用户名"
    };

    /** 下载部门导入模板 */
    @GetMapping("/template")
    public void downloadDeptTemplate(HttpServletResponse response) throws IOException {
        setExcelResponse(response, "部门导入模板.xlsx");
        try (XSSFWorkbook wb = ExcelUtil.createWorkbook("部门导入模板", DEPT_TEMPLATE_HEADERS, List.of())) {
            wb.write(response.getOutputStream());
        }
    }

    /** 导出全部部门到 Excel */
    @GetMapping("/export")
    public void exportDepartments(HttpServletResponse response) throws IOException {
        List<Department> depts = departmentRepository.findByDeletedOrderBySortOrder(0);
        List<String[]> rows = new ArrayList<>();
        for (Department d : depts) {
            rows.add(new String[]{
                    d.getParentId() != null ? deptCodeOf(d.getParentId()) : "",
                    d.getDeptCode(),
                    d.getDeptName(),
                    String.valueOf(d.getSortOrder()),
                    d.getLeaderUserId() != null ? usernameOf(d.getLeaderUserId()) : ""
            });
        }
        setExcelResponse(response, "部门数据.xlsx");
        try (XSSFWorkbook wb = ExcelUtil.createWorkbook("部门数据", DEPT_TEMPLATE_HEADERS, rows)) {
            wb.write(response.getOutputStream());
        }
    }

    /** 导入部门 — 上传 Excel，批量创建 */
    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importDepartments(@RequestParam("file") MultipartFile file) {
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
            int rowNum = i + 2;
            try {
                String deptCode = row.getOrDefault("部门编码*", "").trim();
                String deptName = row.getOrDefault("部门名称*", "").trim();
                if (deptCode.isEmpty() || deptName.isEmpty()) {
                    errors.add(Map.of("row", rowNum, "reason", "部门编码或部门名称为空"));
                    continue;
                }
                if (departmentRepository.existsByDeptCode(deptCode)) {
                    errors.add(Map.of("row", rowNum, "reason", "部门编码已存在: " + deptCode));
                    continue;
                }

                String parentCode = row.getOrDefault("父部门编码", "").trim();
                Long parentId = !parentCode.isEmpty() ? resolveDeptId(parentCode) : null;
                String sortStr = row.getOrDefault("排序", "").trim();
                int sortOrder = 0;
                if (!sortStr.isEmpty()) {
                    try { sortOrder = Integer.parseInt(sortStr); }
                    catch (NumberFormatException e) { sortOrder = 0; }
                }
                String leaderUsername = row.getOrDefault("负责人用户名", "").trim();
                Long leaderUserId = !leaderUsername.isEmpty() ? resolveUserId(leaderUsername) : null;

                Department dept = Department.builder()
                        .parentId(parentId)
                        .deptCode(deptCode)
                        .deptName(deptName)
                        .sortOrder(sortOrder)
                        .leaderUserId(leaderUserId)
                        .status(1)
                        .createdAt(now)
                        .updatedAt(now)
                        .deleted(0)
                        .build();
                departmentRepository.save(dept);
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
        Department dept = departmentRepository.findFirstByDeptCode(deptCode);
        if (dept == null || !Integer.valueOf(0).equals(dept.getDeleted())) {
            throw new IllegalArgumentException("父部门编码不存在或已删除: " + deptCode);
        }
        return dept.getId();
    }

    private Long resolveUserId(String username) {
        SysUser user = sysUserRepository.findByUsername(username).orElse(null);
        if (user == null) throw new IllegalArgumentException("用户不存在: " + username);
        return user.getId();
    }

    private String deptCodeOf(Long deptId) {
        return departmentRepository.findById(deptId).map(Department::getDeptCode).orElse("");
    }

    private String usernameOf(Long userId) {
        return sysUserRepository.findById(userId).map(SysUser::getUsername).orElse("");
    }

    private void setExcelResponse(HttpServletResponse response, String filename) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
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
