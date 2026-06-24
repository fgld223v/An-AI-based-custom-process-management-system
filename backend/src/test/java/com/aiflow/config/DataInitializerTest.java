package com.aiflow.config;

import com.aiflow.model.BizTypeDict;
import com.aiflow.model.Department;
import com.aiflow.model.SysUser;
import com.aiflow.repository.BizTypeDictRepository;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.SysUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataInitializerTest {

    @Test
    void migratesLegacyDemoOrganizationAndPreservesOtherUsers() {
        SysUserRepository userRepository = mock(SysUserRepository.class);
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        BizTypeDictRepository bizTypeRepository = mock(BizTypeDictRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        Map<String, Department> departments = new LinkedHashMap<>();
        departments.put("root", department(1L, null, "root", "默认组织"));
        departments.put("headquarters", department(6L, null, "headquarters", "总部"));
        departments.put("tech", department(2L, 6L, "tech", "技术部"));
        departments.put("finance_dept", department(3L, 6L, "finance_dept", "财务部"));
        departments.put("hr", department(4L, 6L, "hr", "人事行政部"));
        departments.put("market", department(5L, 6L, "market", "市场部"));
        departments.put("purchase_dept", department(7L, 6L, "purchase_dept", "采购部"));
        when(departmentRepository.findFirstByDeptCode(any())).thenAnswer(invocation ->
                departments.get(invocation.getArgument(0)));
        when(departmentRepository.save(any(Department.class))).thenAnswer(invocation -> {
            Department value = invocation.getArgument(0);
            departments.entrySet().removeIf(entry -> entry.getValue() == value);
            departments.put(value.getDeptCode(), value);
            return value;
        });

        Map<String, SysUser> users = new LinkedHashMap<>();
        users.put("admin", user(1L, "admin"));
        users.put("bizadmin", user(2L, "bizadmin"));
        SysUser untouchedUser = user(3L, "user1");
        users.put("user1", untouchedUser);
        users.put("techlead", user(9L, "techlead"));
        users.put("hradmin", user(10L, "hradmin"));
        users.put("purchaseadmin", user(11L, "purchaseadmin"));
        users.put("finance_reviewer", user(12L, "finance_reviewer"));
        users.put("hr_reviewer", user(13L, "hr_reviewer"));
        users.put("purchase_reviewer", user(14L, "purchase_reviewer"));
        users.put("general_manager", user(15L, "general_manager"));
        users.put("mkadmin", user(16L, "mkadmin"));
        when(userRepository.findByUsername(any())).thenAnswer(invocation ->
                Optional.ofNullable(users.get(invocation.getArgument(0))));
        when(userRepository.save(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser value = invocation.getArgument(0);
            users.entrySet().removeIf(entry -> entry.getValue() == value);
            users.put(value.getUsername(), value);
            return value;
        });

        Map<String, Long> bizTypeIds = Map.of(
                "finance", 1L, "reimbursement", 2L,
                "hr_admin", 3L, "leave", 4L, "business_trip", 5L,
                "logistics", 6L, "purchase", 7L);
        when(bizTypeRepository.findByTypeCode(any())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            return Optional.of(BizTypeDict.builder().id(bizTypeIds.get(code)).typeCode(code).build());
        });
        when(passwordEncoder.matches(any(), any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenAnswer(invocation -> "encoded:" + invocation.getArgument(0));

        new DemoOrganizationInitializer(userRepository, departmentRepository,
                bizTypeRepository, passwordEncoder, new ObjectMapper()).run();

        assertThat(users).containsKeys("admin", "hqadmin", "techadmin", "financeadmin",
                "hradmin", "marketadmin", "purchaseadmin",
                "finance_reviewer", "hr_reviewer", "purchase_reviewer", "user1");
        assertThat(users).doesNotContainKeys("general_manager", "techlead", "bizadmin", "mkadmin");
        assertThat(users.get("user1")).isSameAs(untouchedUser);

        SysUser generalManager = users.get("hqadmin");
        assertThat(generalManager.getSystemRole()).isEqualTo("biz_admin");
        assertThat(generalManager.getDepartmentId()).isEqualTo(6L);
        assertThat(generalManager.getPassword()).isEqualTo("encoded:admin123");
        assertThat(users.get("admin").getPassword()).isEqualTo("encoded:admin123");

        assertManager(users.get("techadmin"), 2L, generalManager.getId());
        assertManager(users.get("financeadmin"), 3L, generalManager.getId());
        assertManager(users.get("hradmin"), 4L, generalManager.getId());
        assertManager(users.get("marketadmin"), 5L, generalManager.getId());
        assertManager(users.get("purchaseadmin"), 7L, generalManager.getId());
        assertThat(users.get("finance_reviewer").getSupervisorId()).isEqualTo(2L);
        assertThat(users.get("hr_reviewer").getSupervisorId()).isEqualTo(10L);
        assertThat(users.get("purchase_reviewer").getSupervisorId()).isEqualTo(11L);
        assertThat(users.get("finance_reviewer").getPassword()).isEqualTo("encoded:user123");

        assertThat(departments).containsOnlyKeys("root", "hq", "tech", "finance", "hr", "market", "purchase");
        assertThat(departments.get("hq").getParentId()).isEqualTo(1L);
        assertThat(departments.get("tech").getParentId()).isEqualTo(6L);
        assertThat(departments.get("finance").getLeaderUserId()).isEqualTo(2L);
        assertThat(departments.get("hq").getLeaderUserId()).isEqualTo(15L);
    }

    private void assertManager(SysUser user, Long departmentId, Long supervisorId) {
        assertThat(user.getSystemRole()).isEqualTo("biz_admin");
        assertThat(user.getDepartmentId()).isEqualTo(departmentId);
        assertThat(user.getSupervisorId()).isEqualTo(supervisorId);
        assertThat(user.getPassword()).isEqualTo("encoded:admin123");
    }

    private Department department(Long id, Long parentId, String code, String name) {
        return Department.builder()
                .id(id)
                .parentId(parentId)
                .deptCode(code)
                .deptName(name)
                .sortOrder(0)
                .status(1)
                .deleted(0)
                .build();
    }

    private SysUser user(Long id, String username) {
        return SysUser.builder()
                .id(id)
                .username(username)
                .password("old-password")
                .nickname(username)
                .role("USER")
                .systemRole("normal_user")
                .enabled(1)
                .deleted(0)
                .build();
    }
}
