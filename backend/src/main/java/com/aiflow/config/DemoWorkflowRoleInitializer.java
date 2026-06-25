package com.aiflow.config;

import com.aiflow.model.Department;
import com.aiflow.model.SysUser;
import com.aiflow.model.UserWorkflowRole;
import com.aiflow.model.WorkflowRole;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.repository.UserWorkflowRoleRepository;
import com.aiflow.repository.WorkflowRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Order(30)
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "aiflow.initializer", name = "demo-data-enabled",
        havingValue = "true")
public class DemoWorkflowRoleInitializer implements CommandLineRunner {

    private static final long GLOBAL_DEPARTMENT_ID = 0L;

    private final WorkflowRoleRepository workflowRoleRepository;
    private final UserWorkflowRoleRepository assignmentRepository;
    private final SysUserRepository sysUserRepository;
    private final DepartmentRepository departmentRepository;

    @Override
    @Transactional
    public void run(String... args) {
        WorkflowRole deptReviewer = ensureRole("DEPT_REVIEWER", "部门审核员", "department",
                "处理所在部门的通用业务审核");
        WorkflowRole financeReviewer = ensureRole("FINANCE_REVIEWER", "财务审核员", "department",
                "处理财务合规与费用审核");
        WorkflowRole hrReviewer = ensureRole("HR_REVIEWER", "人事审核员", "department",
                "处理人事行政业务审核");
        WorkflowRole purchaseReviewer = ensureRole("PURCHASE_REVIEWER", "采购审核员", "department",
                "处理采购需求与供应审核");
        WorkflowRole legalReviewer = ensureRole("LEGAL_REVIEWER", "法务审核员", "global",
                "处理全公司的合同和合规审核");
        WorkflowRole generalManager = ensureRole("GENERAL_MANAGER", "总经理", "global",
                "处理需要公司级决策的审批");

        SysUser admin = requiredUser("admin");
        assign(deptReviewer, requiredUser("finance_reviewer"), requiredDepartment("finance"), admin.getId());
        assign(financeReviewer, requiredUser("finance_reviewer"), requiredDepartment("finance"), admin.getId());
        assign(hrReviewer, requiredUser("hr_reviewer"), requiredDepartment("hr"), admin.getId());
        assign(purchaseReviewer, requiredUser("purchase_reviewer"), requiredDepartment("purchase"), admin.getId());
        assign(legalReviewer, requiredUser("hqadmin"), null, admin.getId());
        assign(generalManager, requiredUser("hqadmin"), null, admin.getId());
        log.info("Demo workflow roles and assignments are ready");
    }

    private WorkflowRole ensureRole(String code, String name, String scope, String description) {
        return workflowRoleRepository.findByRoleCodeAndDeleted(code, 0).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            return workflowRoleRepository.save(WorkflowRole.builder()
                    .roleCode(code)
                    .roleName(name)
                    .description(description)
                    .roleScope(scope)
                    .enabled(1)
                    .createdAt(now)
                    .updatedAt(now)
                    .deleted(0)
                    .build());
        });
    }

    private void assign(WorkflowRole role, SysUser user, Department department, Long createdBy) {
        long departmentId = department == null ? GLOBAL_DEPARTMENT_ID : department.getId();
        UserWorkflowRole existing = assignmentRepository
                .findByUserIdAndRoleIdAndDepartmentId(user.getId(), role.getId(), departmentId)
                .orElse(null);
        if (existing != null) {
            if (Integer.valueOf(1).equals(existing.getDeleted())) {
                existing.setDeleted(0);
                existing.setUpdatedAt(LocalDateTime.now());
                assignmentRepository.save(existing);
            }
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        assignmentRepository.save(UserWorkflowRole.builder()
                .userId(user.getId())
                .roleId(role.getId())
                .departmentId(departmentId)
                .createdBy(createdBy)
                .createdAt(now)
                .updatedAt(now)
                .deleted(0)
                .build());
    }

    private SysUser requiredUser(String username) {
        return sysUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Missing demo user: " + username));
    }

    private Department requiredDepartment(String code) {
        Department department = departmentRepository.findFirstByDeptCode(code);
        if (department == null) throw new IllegalStateException("Missing demo department: " + code);
        return department;
    }
}
