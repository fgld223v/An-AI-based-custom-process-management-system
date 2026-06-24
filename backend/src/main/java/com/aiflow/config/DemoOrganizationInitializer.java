package com.aiflow.config;

import com.aiflow.model.BizTypeDict;
import com.aiflow.model.Department;
import com.aiflow.model.SysUser;
import com.aiflow.repository.BizTypeDictRepository;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.SysUserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Creates and migrates the coherent organization used by local demonstrations. */
@Slf4j
@Order(20)
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "aiflow.initializer", name = "demo-data-enabled",
        havingValue = "true")
public class DemoOrganizationInitializer implements CommandLineRunner {

    private static final String ADMIN_PASSWORD = "admin123";
    private static final String USER_PASSWORD = "user123";

    private final SysUserRepository sysUserRepository;
    private final DepartmentRepository departmentRepository;
    private final BizTypeDictRepository bizTypeDictRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(String... args) {
        Department root = ensureDepartment(null, "root", "默认组织", 0);
        Department headquarters = ensureDepartment(root.getId(), "hq", "总部", 1, "headquarters");
        Department tech = ensureDepartment(headquarters.getId(), "tech", "技术部", 10);
        Department finance = ensureDepartment(headquarters.getId(), "finance", "财务部", 20, "finance_dept");
        Department hr = ensureDepartment(headquarters.getId(), "hr", "人事行政部", 30);
        Department market = ensureDepartment(headquarters.getId(), "market", "市场部", 40);
        Department purchase = ensureDepartment(headquarters.getId(), "purchase", "采购部", 50, "purchase_dept");

        SysUser admin = ensureUser("admin", List.of(), "系统管理员", ADMIN_PASSWORD,
                "ADMIN", "super_admin", root.getId(), null, null);
        SysUser generalManager = ensureUser("hqadmin", List.of("general_manager"), "总经理", ADMIN_PASSWORD,
                "MANAGER", "biz_admin", headquarters.getId(), null, null);
        SysUser techManager = ensureUser("techadmin", List.of("techlead"), "技术部负责人", ADMIN_PASSWORD,
                "MANAGER", "biz_admin", tech.getId(), generalManager.getId(), null);
        SysUser financeManager = ensureUser("financeadmin", List.of("bizadmin"), "财务部负责人", ADMIN_PASSWORD,
                "MANAGER", "biz_admin", finance.getId(), generalManager.getId(),
                managedBizTypes("finance", "reimbursement"));
        SysUser hrManager = ensureUser("hradmin", List.of(), "人事部负责人", ADMIN_PASSWORD,
                "MANAGER", "biz_admin", hr.getId(), generalManager.getId(),
                managedBizTypes("hr_admin", "leave", "business_trip"));
        SysUser marketManager = ensureUser("marketadmin", List.of("mkadmin"), "市场部负责人", ADMIN_PASSWORD,
                "MANAGER", "biz_admin", market.getId(), generalManager.getId(), null);
        SysUser purchaseManager = ensureUser("purchaseadmin", List.of(), "采购部负责人", ADMIN_PASSWORD,
                "MANAGER", "biz_admin", purchase.getId(), generalManager.getId(),
                managedBizTypes("logistics", "purchase"));

        ensureLeader(root, null);
        ensureLeader(headquarters, generalManager);
        ensureLeader(tech, techManager);
        ensureLeader(finance, financeManager);
        ensureLeader(hr, hrManager);
        ensureLeader(market, marketManager);
        ensureLeader(purchase, purchaseManager);

        ensureUser("finance_reviewer", List.of(), "财务审核员", USER_PASSWORD,
                "USER", "normal_user", finance.getId(), financeManager.getId(), null);
        ensureUser("hr_reviewer", List.of(), "人事审核员", USER_PASSWORD,
                "USER", "normal_user", hr.getId(), hrManager.getId(), null);
        ensureUser("purchase_reviewer", List.of(), "采购审核员", USER_PASSWORD,
                "USER", "normal_user", purchase.getId(), purchaseManager.getId(), null);

        log.info("Demo organization is ready (adminId={}, generalManagerId={})",
                admin.getId(), generalManager.getId());
    }

    private Department ensureDepartment(Long parentId, String code, String name, int sortOrder,
                                        String... legacyCodes) {
        Department department = departmentRepository.findFirstByDeptCode(code);
        if (department == null) {
            department = Arrays.stream(legacyCodes)
                    .map(departmentRepository::findFirstByDeptCode)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        LocalDateTime now = LocalDateTime.now();
        if (department == null) {
            return departmentRepository.save(Department.builder()
                    .parentId(parentId)
                    .deptCode(code)
                    .deptName(name)
                    .sortOrder(sortOrder)
                    .status(1)
                    .createdAt(now)
                    .updatedAt(now)
                    .deleted(0)
                    .build());
        }

        boolean changed = !Objects.equals(department.getParentId(), parentId)
                || !Objects.equals(department.getDeptCode(), code)
                || !Objects.equals(department.getDeptName(), name)
                || !Objects.equals(department.getSortOrder(), sortOrder)
                || !Integer.valueOf(1).equals(department.getStatus())
                || !Integer.valueOf(0).equals(department.getDeleted());
        if (!changed) return department;
        department.setParentId(parentId);
        department.setDeptCode(code);
        department.setDeptName(name);
        department.setSortOrder(sortOrder);
        department.setStatus(1);
        department.setDeleted(0);
        department.setUpdatedAt(now);
        return departmentRepository.save(department);
    }

    private SysUser ensureUser(String username, List<String> legacyUsernames, String nickname,
                               String rawPassword, String legacyRole, String systemRole,
                               Long departmentId, Long supervisorId, String managedBizTypeIds) {
        SysUser user = sysUserRepository.findByUsername(username).orElse(null);
        if (user == null) {
            user = legacyUsernames.stream()
                    .map(sysUserRepository::findByUsername)
                    .flatMap(java.util.Optional::stream)
                    .findFirst()
                    .orElse(null);
        }
        LocalDateTime now = LocalDateTime.now();
        if (user == null) {
            return sysUserRepository.save(SysUser.builder()
                    .username(username)
                    .password(passwordEncoder.encode(rawPassword))
                    .nickname(nickname)
                    .role(legacyRole)
                    .systemRole(systemRole)
                    .departmentId(departmentId)
                    .supervisorId(supervisorId)
                    .managedBizTypeIds(managedBizTypeIds)
                    .enabled(1)
                    .createdTime(now)
                    .updatedTime(now)
                    .deleted(0)
                    .build());
        }

        boolean passwordChanged = user.getPassword() == null
                || !passwordEncoder.matches(rawPassword, user.getPassword());
        boolean changed = !Objects.equals(user.getUsername(), username)
                || !Objects.equals(user.getNickname(), nickname)
                || !Objects.equals(user.getRole(), legacyRole)
                || !Objects.equals(user.getSystemRole(), systemRole)
                || !Objects.equals(user.getDepartmentId(), departmentId)
                || !Objects.equals(user.getSupervisorId(), supervisorId)
                || !Objects.equals(user.getManagedBizTypeIds(), managedBizTypeIds)
                || !Integer.valueOf(1).equals(user.getEnabled())
                || !Integer.valueOf(0).equals(user.getDeleted())
                || passwordChanged;
        if (!changed) return user;

        user.setUsername(username);
        user.setNickname(nickname);
        user.setRole(legacyRole);
        user.setSystemRole(systemRole);
        user.setDepartmentId(departmentId);
        user.setSupervisorId(supervisorId);
        user.setManagedBizTypeIds(managedBizTypeIds);
        user.setEnabled(1);
        user.setDeleted(0);
        if (passwordChanged) user.setPassword(passwordEncoder.encode(rawPassword));
        user.setUpdatedTime(now);
        return sysUserRepository.save(user);
    }

    private void ensureLeader(Department department, SysUser leader) {
        Long leaderId = leader == null ? null : leader.getId();
        if (Objects.equals(department.getLeaderUserId(), leaderId)) return;
        department.setLeaderUserId(leaderId);
        department.setUpdatedAt(LocalDateTime.now());
        departmentRepository.save(department);
    }

    private String managedBizTypes(String... codes) {
        List<Long> ids = Arrays.stream(codes)
                .map(code -> bizTypeDictRepository.findByTypeCode(code)
                        .map(BizTypeDict::getId)
                        .orElseThrow(() -> new IllegalStateException("Missing business type: " + code)))
                .toList();
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize managed business types", ex);
        }
    }
}
