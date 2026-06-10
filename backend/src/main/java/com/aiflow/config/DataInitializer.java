package com.aiflow.config;

import com.aiflow.model.BizTypeDict;
import com.aiflow.model.Department;
import com.aiflow.model.SysUser;
import com.aiflow.repository.BizTypeDictRepository;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.SysUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Order(10)
@Component
public class DataInitializer implements CommandLineRunner {

    private final SysUserRepository sysUserRepository;
    private final BizTypeDictRepository bizTypeDictRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SysUserRepository sysUserRepository,
                           BizTypeDictRepository bizTypeDictRepository,
                           DepartmentRepository departmentRepository,
                           PasswordEncoder passwordEncoder) {
        this.sysUserRepository = sysUserRepository;
        this.bizTypeDictRepository = bizTypeDictRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        initRootDepartment();
        initDefaultAdmin();
        initBizTypes();
    }

    private Department initRootDepartment() {
        Department existing = departmentRepository.findFirstByDeptCode("root");
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        Department department = Department.builder()
                .parentId(null)
                .deptCode("root")
                .deptName("默认组织")
                .sortOrder(0)
                .status(1)
                .createdAt(now)
                .updatedAt(now)
                .deleted(0)
                .build();
        return departmentRepository.save(department);
    }

    private void initDefaultAdmin() {
        if (sysUserRepository.existsByUsername("admin")) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        SysUser admin = SysUser.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .nickname("系统管理员")
                .role("ADMIN")
                .enabled(1)
                .createdTime(now)
                .updatedTime(now)
                .build();
        sysUserRepository.save(admin);
    }

    private void initBizTypes() {
        BizTypeDict hrAdmin = initBizType(null, "hr_admin", "人事行政类", "人事、行政相关流程分类", 10);
        BizTypeDict finance = initBizType(null, "finance", "财务类", "财务相关流程分类", 20);
        BizTypeDict logistics = initBizType(null, "logistics", "后勤类", "后勤保障相关流程分类", 30);
        BizTypeDict management = initBizType(null, "management", "管理类", "经营管理相关流程分类", 40);

        initBizType(hrAdmin.getId(), "leave", "请假", "员工请假流程", 11);
        initBizType(hrAdmin.getId(), "business_trip", "出差", "员工出差流程", 12);
        initBizType(finance.getId(), "reimbursement", "报销", "费用报销流程", 21);
        initBizType(logistics.getId(), "purchase", "采购", "采购申请流程", 31);
        initBizType(management.getId(), "contract_approval", "合同审批", "合同审批流程", 41);
    }

    private BizTypeDict initBizType(Long parentId, String typeCode, String typeName, String description, Integer sortOrder) {
        return bizTypeDictRepository.findByTypeCode(typeCode)
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    BizTypeDict item = BizTypeDict.builder()
                            .parentId(parentId)
                            .typeCode(typeCode)
                            .typeName(typeName)
                            .description(description)
                            .sortOrder(sortOrder)
                            .enabled(1)
                            .createdAt(now)
                            .updatedAt(now)
                            .deleted(0)
                            .build();
                    return bizTypeDictRepository.save(item);
                });
    }
}
